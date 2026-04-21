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

    @Transactional
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderRequest req, UUID createdBy) {
        Supplier supplier = supplierRepository.findById(req.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.getSupplierId()));

        PurchaseOrder po = PurchaseOrder.builder()
                .code(generatePOCode()).supplierId(req.getSupplierId()).warehouseId(req.getWarehouseId())
                .createdByUserId(createdBy).note(req.getNote()).status(PurchaseOrder.PurchaseStatus.PENDING)
                .build();

        for (CreatePurchaseOrderRequest.PurchaseItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId()).orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));
            PurchaseItem item = PurchaseItem.builder()
                    .productId(product.getId())
                    .quantity(itemReq.getQuantity() != null ? itemReq.getQuantity() : 0)
                    .importPrice(itemReq.getImportPrice() != null ? itemReq.getImportPrice() : BigDecimal.ZERO)
                    .build();
            po.addItem(item);
        }
        po.recalculateTotal();
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public PurchaseOrder approvePurchaseOrder(UUID poId, UUID approvedBy) {
        PurchaseOrder po = purchaseOrderRepository.findByIdWithItems(poId).orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));
        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Chỉ có thể duyệt phiếu ở trạng thái PENDING.");
        }
        try {
            po.setStatus(PurchaseOrder.PurchaseStatus.COMPLETED);
            po.setApprovedBy(approvedBy);
            po.setApprovedAt(Instant.now());
            po = purchaseOrderRepository.save(po);

            String operator = approvedBy != null ? approvedBy.toString() : "SYSTEM";

            if (po.getItems() != null) {
                for (PurchaseItem item : po.getItems()) {
                    BigDecimal importPrice = item.getImportPrice() != null ? item.getImportPrice() : BigDecimal.ZERO;
                    int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
                    inventoryService.importStock(item.getProductId(), po.getWarehouseId(), quantity, importPrice, po.getId(), operator);
                }
            }

            Supplier supplier = supplierRepository.findById(po.getSupplierId()).orElse(null);
            int paymentTerms = (supplier != null && supplier.getPaymentTerms() != null) ? supplier.getPaymentTerms() : 30;
            BigDecimal totalDebt = po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO;

            SupplierDebt debt = SupplierDebt.builder()
                    .supplierId(po.getSupplierId()).purchaseOrderId(po.getId())
                    .totalDebt(totalDebt).paidAmount(BigDecimal.ZERO)
                    .status(SupplierDebt.DebtStatus.UNPAID)
                    .dueDate(LocalDate.now().plusDays(paymentTerms)).build();
            supplierDebtRepository.save(debt);
            return po;
        } catch (BusinessException be) { throw be; } 
        catch (Exception e) { throw new BusinessException("APPROVE_CRASH", "Lỗi xử lý hệ thống: " + e.getMessage()); }
    }

    @Transactional
    public PurchaseOrder cancelPurchaseOrder(UUID poId, String reason) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId).orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", poId));
        if (po.getStatus() == PurchaseOrder.PurchaseStatus.COMPLETED) { throw new BusinessException("CANNOT_CANCEL", "Không thể hủy phiếu đã hoàn thành"); }
        po.setStatus(PurchaseOrder.PurchaseStatus.CANCELLED);
        po.setNote((po.getNote() != null ? po.getNote() + " | " : "") + "Lý do hủy: " + reason);
        return purchaseOrderRepository.save(po);
    }

    // ĐÃ SỬA: Cập nhật hàm để Search & Filter
    @Transactional(readOnly = true)
    public Page<PurchaseOrder> searchOrders(UUID warehouseId, String keyword, PurchaseOrder.PurchaseStatus status, Pageable pageable) {
        return purchaseOrderRepository.searchPurchaseOrders(warehouseId, status, keyword, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> getBySupplier(UUID supplierId, Pageable pageable) {
        return purchaseOrderRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId, pageable);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getById(UUID id) {
        return purchaseOrderRepository.findByIdWithItems(id).orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", id));
    }

    private String generatePOCode() { return "PO-" + System.currentTimeMillis(); }
}