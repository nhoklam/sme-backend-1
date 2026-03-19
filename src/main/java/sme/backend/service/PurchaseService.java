package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CreatePurchaseOrderRequest;
import sme.backend.entity.*;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierDebtRepository supplierDebtRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    // ─────────────────────────────────────────────────────────
    // TẠO PHIẾU NHẬP KHO (INV-02)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderRequest req, UUID createdBy) {

        Supplier supplier = supplierRepository.findById(req.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.getSupplierId()));

        PurchaseOrder po = PurchaseOrder.builder()
                .code(generatePOCode())
                .supplierId(req.getSupplierId())
                .warehouseId(req.getWarehouseId())
                .createdByUserId(createdBy)
                .note(req.getNote())
                .status(PurchaseOrder.PurchaseStatus.PENDING)
                .build();

        for (CreatePurchaseOrderRequest.PurchaseItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            PurchaseItem item = PurchaseItem.builder()
                    .productId(product.getId())
                    .quantity(itemReq.getQuantity())
                    .importPrice(itemReq.getImportPrice())
                    .build();
            po.addItem(item);
        }
        po.recalculateTotal();

        po = purchaseOrderRepository.save(po);
        log.info("PurchaseOrder created: {} for supplier: {}", po.getCode(), supplier.getName());
        return po;
    }

    // ─────────────────────────────────────────────────────────
    // DUYỆT PHIẾU NHẬP KHO (INV-02) — Manager/Admin
    // Đây là bước kích hoạt tất cả side effects
    // ─────────────────────────────────────────────────────────
    @Transactional
    public PurchaseOrder approvePurchaseOrder(UUID poId, UUID approvedBy) {

        PurchaseOrder po = purchaseOrderRepository.findByIdWithItems(poId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));

        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS",
                    "Chỉ có thể duyệt phiếu ở trạng thái PENDING. Trạng thái hiện tại: " + po.getStatus());
        }

        // 1. Nhập kho + tái tính MAC cho từng sản phẩm
        for (PurchaseItem item : po.getItems()) {
            inventoryService.importStock(
                    item.getProductId(),
                    po.getWarehouseId(),
                    item.getQuantity(),
                    item.getImportPrice(),
                    po.getId(),
                    approvedBy.toString()
            );
        }

        // 2. Cập nhật trạng thái PO
        po.setStatus(PurchaseOrder.PurchaseStatus.COMPLETED);
        po.setApprovedBy(approvedBy);
        po.setApprovedAt(Instant.now());
        po = purchaseOrderRepository.save(po);

        // 3. Tự động tạo bản ghi Công nợ Nhà cung cấp (FIN-03)
        Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
        int paymentTerms = supplier != null ? supplier.getPaymentTerms() : 30;

        SupplierDebt debt = SupplierDebt.builder()
                .supplierId(po.getSupplierId())
                .purchaseOrderId(po.getId())
                .totalDebt(po.getTotalAmount())
                .paidAmount(BigDecimal.ZERO)
                .status(SupplierDebt.DebtStatus.UNPAID)
                .dueDate(LocalDate.now().plusDays(paymentTerms))
                .build();
        supplierDebtRepository.save(debt);

        log.info("PurchaseOrder approved: {} | debt created: {}", po.getCode(), po.getTotalAmount());
        return po;
    }

    @Transactional
    public PurchaseOrder cancelPurchaseOrder(UUID poId, String reason) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));

        if (po.getStatus() == PurchaseOrder.PurchaseStatus.COMPLETED) {
            throw new BusinessException("CANNOT_CANCEL",
                    "Không thể hủy phiếu nhập kho đã hoàn thành");
        }
        po.setStatus(PurchaseOrder.PurchaseStatus.CANCELLED);
        po.setNote((po.getNote() != null ? po.getNote() + " | " : "") + "Lý do hủy: " + reason);
        return purchaseOrderRepository.save(po);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> getByWarehouse(UUID warehouseId, Pageable pageable) {
        return purchaseOrderRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getById(UUID id) {
        return purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    }

    private String generatePOCode() {
        return "PO-" + System.currentTimeMillis();
    }
}
