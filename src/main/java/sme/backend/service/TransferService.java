package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.entity.*;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final InternalTransferRepository transferRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    @Transactional
    public InternalTransfer createTransfer(UUID fromWarehouseId, UUID toWarehouseId,
                                           List<TransferItemRequest> items,
                                           String note, UUID createdBy) {
        if (fromWarehouseId.equals(toWarehouseId)) {
            throw new BusinessException("SAME_WAREHOUSE",
                    "Kho nguồn và kho đích không thể giống nhau");
        }

        // Validate tồn kho trước khi tạo
        for (TransferItemRequest item : items) {
            Inventory inv = inventoryRepository
                    .findByProductIdAndWarehouseId(item.productId(), fromWarehouseId)
                    .orElseThrow(() -> new BusinessException("NO_INVENTORY",
                            "Không tìm thấy tồn kho sản phẩm: " + item.productId()));
            if (inv.getAvailableQuantity() < item.quantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Không đủ hàng để chuyển. Khả dụng: " + inv.getAvailableQuantity());
            }
        }

        InternalTransfer transfer = InternalTransfer.builder()
                .code("TRF-" + System.currentTimeMillis())
                .fromWarehouseId(fromWarehouseId)
                .toWarehouseId(toWarehouseId)
                .createdByUserId(createdBy)
                .status(InternalTransfer.TransferStatus.DRAFT)
                .note(note)
                .build();

        items.forEach(i -> transfer.addItem(
                TransferItem.builder()
                        .productId(i.productId())
                        .quantity(i.quantity())
                        .build()
        ));

        return transferRepository.save(transfer);
    }

    // Xuất kho: Kho nguồn trừ hàng, đánh dấu in_transit
    @Transactional
    public InternalTransfer dispatch(UUID transferId, UUID dispatchedBy) {
        InternalTransfer transfer = transferRepository.findByIdWithItems(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != InternalTransfer.TransferStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS",
                    "Chỉ có thể xuất kho phiếu ở trạng thái DRAFT");
        }

        for (TransferItem item : transfer.getItems()) {
            Inventory inv = inventoryRepository
                    .findByProductAndWarehouseWithLock(item.getProductId(), transfer.getFromWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory product=" + item.getProductId()));
            inv.dispatchForTransfer(item.getQuantity());
            inventoryRepository.save(inv);
        }

        transfer.setStatus(InternalTransfer.TransferStatus.DISPATCHED);
        transfer.setDispatchedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        notificationService.notifyTransferArrived(transfer.getId(), transfer.getToWarehouseId());
        log.info("Transfer dispatched: {}", transfer.getCode());
        return transfer;
    }

    // Nhận hàng: Kho đích nhận hàng, in_transit giảm
    @Transactional
    public InternalTransfer receive(UUID transferId, UUID receivedBy) {
        InternalTransfer transfer = transferRepository.findByIdWithItems(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != InternalTransfer.TransferStatus.DISPATCHED) {
            throw new BusinessException("INVALID_STATUS",
                    "Chỉ có thể nhận hàng phiếu ở trạng thái DISPATCHED");
        }

        for (TransferItem item : transfer.getItems()) {
            // Cộng kho đích
            Inventory destInv = inventoryService.getOrCreate(
                    item.getProductId(), transfer.getToWarehouseId());
            destInv.receiveTransfer(0);   // in_transit đã trừ tại kho nguồn
            destInv.addQuantity(item.getQuantity());
            inventoryRepository.save(destInv);

            // Giảm in_transit tại kho nguồn
            inventoryRepository.findByProductIdAndWarehouseId(
                    item.getProductId(), transfer.getFromWarehouseId())
                    .ifPresent(srcInv -> {
                        srcInv.setInTransit(Math.max(0, srcInv.getInTransit() - item.getQuantity()));
                        inventoryRepository.save(srcInv);
                    });

            item.setReceivedQty(item.getQuantity());
        }

        transfer.setStatus(InternalTransfer.TransferStatus.RECEIVED);
        transfer.setReceivedByUserId(receivedBy);
        transfer.setReceivedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        log.info("Transfer received: {}", transfer.getCode());
        return transfer;
    }

    @Transactional(readOnly = true)
    public Page<InternalTransfer> getByWarehouse(UUID warehouseId, Pageable pageable) {
        return transferRepository.findByFromWarehouseIdOrToWarehouseIdOrderByCreatedAtDesc(
                warehouseId, warehouseId, pageable);
    }

    @Transactional(readOnly = true)
    public InternalTransfer getById(UUID id) {
        return transferRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", id));
    }

    public record TransferItemRequest(UUID productId, int quantity) {}
}
