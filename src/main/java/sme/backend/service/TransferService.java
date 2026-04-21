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
    private final OrderRepository orderRepository; 

    @Transactional
    public InternalTransfer createTransfer(UUID fromWarehouseId, UUID toWarehouseId,
                                           List<TransferItemRequest> items,
                                           String note, UUID createdBy) {
        if (fromWarehouseId.equals(toWarehouseId)) {
            throw new BusinessException("SAME_WAREHOUSE", "Kho nguồn và kho đích không thể giống nhau");
        }

        for (TransferItemRequest item : items) {
            Inventory inv = inventoryRepository
                    .findByProductIdAndWarehouseId(item.productId(), fromWarehouseId)
                    .orElseThrow(() -> new BusinessException("NO_INVENTORY", "Không tìm thấy tồn kho sản phẩm: " + item.productId()));
            if (inv.getAvailableQuantity() < item.quantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK", "Không đủ hàng để chuyển. Khả dụng: " + inv.getAvailableQuantity());
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
                TransferItem.builder().productId(i.productId()).quantity(i.quantity()).build()
        ));

        return transferRepository.save(transfer);
    }

    @Transactional
    public InternalTransfer updateTransfer(UUID transferId, UUID toWarehouseId,
                                           List<TransferItemRequest> items,
                                           String note, UUID updatedBy) {
        
        InternalTransfer transfer = transferRepository.findByIdWithItems(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != InternalTransfer.TransferStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "Chỉ có thể sửa phiếu ở trạng thái DRAFT");
        }
        
        if (transfer.getReferenceOrderId() != null) {
            throw new BusinessException("AUTO_TRANSFER_LOCKED", "Không thể sửa thủ công phiếu chuyển kho gom hàng hệ thống tự tạo. Vui lòng hủy đơn hàng nếu cần thay đổi.");
        }

        if (transfer.getFromWarehouseId().equals(toWarehouseId)) {
            throw new BusinessException("SAME_WAREHOUSE", "Kho nguồn và kho đích không thể giống nhau");
        }

        for (TransferItemRequest item : items) {
            Inventory inv = inventoryRepository
                    .findByProductIdAndWarehouseId(item.productId(), transfer.getFromWarehouseId())
                    .orElseThrow(() -> new BusinessException("NO_INVENTORY", "Không tìm thấy tồn kho sản phẩm: " + item.productId()));

            if (inv.getAvailableQuantity() < item.quantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK", "Không đủ hàng để chuyển. Khả dụng: " + inv.getAvailableQuantity());
            }
        }

        transfer.setToWarehouseId(toWarehouseId);
        transfer.setNote(note);
        transfer.getItems().clear();
        items.forEach(i -> transfer.addItem(
                TransferItem.builder().productId(i.productId()).quantity(i.quantity()).build()
        ));

        return transferRepository.save(transfer);
    }

    // XUẤT KHO
    @Transactional
    public InternalTransfer dispatch(UUID transferId, UUID dispatchedBy) {
        InternalTransfer transfer = transferRepository.findByIdWithItems(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != InternalTransfer.TransferStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "Chỉ có thể xuất kho phiếu ở trạng thái DRAFT");
        }

        boolean isAutoTransfer = transfer.getReferenceOrderId() != null;

        for (TransferItem item : transfer.getItems()) {
            if (isAutoTransfer) {
                inventoryService.releaseReservation(
                        item.getProductId(), transfer.getFromWarehouseId(), 
                        item.getQuantity(), transfer.getReferenceOrderId(), dispatchedBy.toString()
                );
            }

            Inventory inv = inventoryRepository
                    .findByProductAndWarehouseWithLock(item.getProductId(), transfer.getFromWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory product=" + item.getProductId()));
            
            if (!isAutoTransfer && inv.getAvailableQuantity() < item.getQuantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK", "Không đủ hàng để chuyển. Khả dụng: " + inv.getAvailableQuantity());
            }

            int before = inv.getQuantity() != null ? inv.getQuantity() : 0;
            inv.dispatchForTransfer(item.getQuantity());
            inv = inventoryRepository.save(inv);

            inventoryService.recordTransaction(inv, transfer.getId(), "TRANSFER_OUT", 
                    -item.getQuantity(), before, inv.getQuantity(), dispatchedBy.toString(), "Xuất luân chuyển kho");
        }

        transfer.setStatus(InternalTransfer.TransferStatus.DISPATCHED);
        transfer.setDispatchedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        notificationService.notifyTransferArrived(transfer.getId(), transfer.getToWarehouseId());
        log.info("Transfer dispatched: {}", transfer.getCode());
        return transfer;
    }

    // NHẬN HÀNG (ĐÃ SỬA: XỬ LÝ NHẬN MỘT PHẦN)
    @Transactional
    public InternalTransfer receive(UUID transferId, List<ReceiveItemRequest> receivedItems, UUID receivedBy) {
        InternalTransfer transfer = transferRepository.findByIdWithItems(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != InternalTransfer.TransferStatus.DISPATCHED) {
            throw new BusinessException("INVALID_STATUS", "Chỉ có thể nhận hàng phiếu ở trạng thái DISPATCHED");
        }

        boolean isAutoTransfer = transfer.getReferenceOrderId() != null;

        for (TransferItem item : transfer.getItems()) {
            // Tìm số lượng thực nhận do Frontend gửi lên (mặc định là 0 nếu không tìm thấy)
            int actualReceivedQty = receivedItems.stream()
                    .filter(ri -> ri.productId().equals(item.getProductId()))
                    .map(ReceiveItemRequest::receivedQty)
                    .findFirst()
                    .orElse(0);

            // Kiểm tra số lượng thực nhận không được lớn hơn số lượng xuất đi
            if (actualReceivedQty > item.getQuantity() || actualReceivedQty < 0) {
                throw new BusinessException("INVALID_QUANTITY", 
                    "Số lượng thực nhận không hợp lệ đối với sản phẩm: " + item.getProductId());
            }

            Inventory destInv = inventoryService.getOrCreate(item.getProductId(), transfer.getToWarehouseId());
            int before = destInv.getQuantity() != null ? destInv.getQuantity() : 0;
            
            // CHỈ CỘNG VÀO KHO ĐÍCH SỐ LƯỢNG THỰC NHẬN
            destInv.addQuantity(actualReceivedQty);
            destInv = inventoryRepository.save(destInv);

            inventoryService.recordTransaction(destInv, transfer.getId(), "TRANSFER_IN", 
                    actualReceivedQty, before, destInv.getQuantity(), receivedBy.toString(), 
                    "Nhận hàng luân chuyển (Thực nhận: " + actualReceivedQty + "/" + item.getQuantity() + ")");

            // TRỪ ĐI TOÀN BỘ SỐ LƯỢNG ĐÃ XUẤT KHỎI HÀNG ĐANG ĐI ĐƯỜNG CỦA KHO NGUỒN
            inventoryRepository.findByProductIdAndWarehouseId(item.getProductId(), transfer.getFromWarehouseId())
                    .ifPresent(srcInv -> {
                        srcInv.setInTransit(Math.max(0, srcInv.getInTransit() - item.getQuantity()));
                        inventoryRepository.save(srcInv);
                    });

            // Ghi nhận số lượng thực nhận vào database
            item.setReceivedQty(actualReceivedQty);

            if (isAutoTransfer) {
                inventoryService.reserveForOnlineOrder(
                        item.getProductId(), transfer.getToWarehouseId(), 
                        actualReceivedQty, // Chỉ reserve đúng số lượng thực nhận
                        transfer.getReferenceOrderId(), receivedBy.toString()
                );
            }
        }

        transfer.setStatus(InternalTransfer.TransferStatus.RECEIVED);
        transfer.setReceivedByUserId(receivedBy);
        transfer.setReceivedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        log.info("Transfer received: {} with partial check", transfer.getCode());
        notificationService.notifyTransferArrived(transfer.getId(), transfer.getFromWarehouseId());
        
        // ĐÁNH THỨC ĐƠN HÀNG KHI ĐÃ NHẬN ĐỦ HÀNG GOM
        if (isAutoTransfer) {
            UUID orderId = transfer.getReferenceOrderId();
            transferRepository.flush();
            List<InternalTransfer> allTransfersForOrder = transferRepository.findByReferenceOrderId(orderId);
            
            boolean isAllReceived = allTransfersForOrder.stream()
                    .allMatch(t -> t.getStatus() == InternalTransfer.TransferStatus.RECEIVED || 
                                   t.getStatus() == InternalTransfer.TransferStatus.CANCELLED);

            if (isAllReceived) {
                orderRepository.findById(orderId).ifPresent(order -> {
                    if (order.getStatus() == Order.OrderStatus.WAITING_FOR_CONSOLIDATION) {
                        order.transitionTo(Order.OrderStatus.PENDING, "Hệ thống tự động: Đã nhận đủ hàng luân chuyển", "SYSTEM");
                        orderRepository.save(order);
                        log.info("Đơn hàng {} đã gom đủ hàng. Trạng thái cập nhật thành PENDING", order.getCode());
                    }
                });
            }
        }

        return transfer;
    }

    // ─────────────────────────────────────────────────────────
    // HỦY PHIẾU CHUYỂN KHO (CANCEL)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public InternalTransfer cancelTransfer(UUID transferId, UUID cancelledBy, String reason) {
        InternalTransfer transfer = transferRepository.findByIdWithItems(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (transfer.getStatus() != InternalTransfer.TransferStatus.DRAFT) {
            throw new BusinessException("INVALID_STATUS", "Chỉ có thể hủy phiếu ở trạng thái DRAFT");
        }

        if (transfer.getReferenceOrderId() != null) {
            throw new BusinessException("AUTO_TRANSFER_LOCKED", "Không thể hủy thủ công phiếu chuyển kho gom hàng do hệ thống tự tạo. Vui lòng hủy đơn hàng liên quan.");
        }

        transfer.setStatus(InternalTransfer.TransferStatus.CANCELLED);
        
        String currentNote = transfer.getNote() != null ? transfer.getNote() : "";
        String cancelNote = "Đã hủy bởi người dùng. Lý do: " + (reason != null && !reason.isBlank() ? reason : "Không có");
        transfer.setNote(currentNote.isEmpty() ? cancelNote : currentNote + " | " + cancelNote);

        log.info("Transfer cancelled: {} by user: {}", transfer.getCode(), cancelledBy);
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public Page<InternalTransfer> searchTransfers(UUID warehouseId, String statusStr, String keyword, Pageable pageable) {
        InternalTransfer.TransferStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try { status = InternalTransfer.TransferStatus.valueOf(statusStr.toUpperCase()); } 
            catch (IllegalArgumentException ignored) {}
        }
        
        String kw = (keyword == null) ? "" : keyword.trim();

        if (warehouseId == null) {
            if (status == null) return transferRepository.searchAllTransfers(kw, pageable);
            else return transferRepository.searchAllTransfersWithStatus(status, kw, pageable);
        } else {
            if (status == null) return transferRepository.searchTransfersByWarehouse(warehouseId, kw, pageable);
            else return transferRepository.searchTransfersByWarehouseWithStatus(warehouseId, status, kw, pageable);
        }
    }

    @Transactional(readOnly = true)
    public InternalTransfer getById(UUID id) {
        return transferRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", id));
    }

    public record TransferItemRequest(UUID productId, int quantity) {}
    
    // THÊM MỚI RECORD NÀY ĐỂ NHẬN DỮ LIỆU THỰC NHẬN
    public record ReceiveItemRequest(UUID productId, int receivedQty) {}
}