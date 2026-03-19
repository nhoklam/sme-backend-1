package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CreateCashbookEntryRequest;
import sme.backend.dto.request.PaySupplierDebtRequest;
import sme.backend.entity.*;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceService {

    private final CashbookTransactionRepository cashbookRepository;
    private final SupplierDebtRepository supplierDebtRepository;
    private final OrderRepository orderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    // ─────────────────────────────────────────────────────────
    // SỔ QUỸ — Tạo Phiếu Thu/Chi thủ công (FIN-02)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public CashbookTransaction createManualEntry(CreateCashbookEntryRequest req, String createdBy) {

        CashbookTransaction.FundType fundType;
        CashbookTransaction.TransactionType txnType;
        try {
            fundType = CashbookTransaction.FundType.valueOf(req.getFundType().toUpperCase());
            txnType  = CashbookTransaction.TransactionType.valueOf(req.getTransactionType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ENUM", "fundType hoặc transactionType không hợp lệ");
        }

        // Lấy số dư trước khi ghi
        BigDecimal balanceBefore = cashbookRepository.getCurrentBalance(req.getWarehouseId(), fundType);
        BigDecimal balanceAfter  = txnType == CashbookTransaction.TransactionType.IN
                ? balanceBefore.add(req.getAmount())
                : balanceBefore.subtract(req.getAmount());

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INSUFFICIENT_FUNDS",
                    "Số dư quỹ không đủ. Hiện có: " + balanceBefore);
        }

        CashbookTransaction txn = CashbookTransaction.builder()
                .warehouseId(req.getWarehouseId())
                .fundType(fundType)
                .transactionType(txnType)
                .referenceType(req.getReferenceType())
                .amount(req.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(req.getDescription())
                .createdBy(createdBy)
                .build();

        return cashbookRepository.save(txn);
    }

    // ─────────────────────────────────────────────────────────
    // THANH TOÁN CÔNG NỢ NHÀ CUNG CẤP (FIN-04)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public SupplierDebt paySupplierDebt(PaySupplierDebtRequest req, String paidBy) {

        SupplierDebt debt = supplierDebtRepository.findById(req.getSupplierDebtId())
                .orElseThrow(() -> new ResourceNotFoundException("SupplierDebt", req.getSupplierDebtId()));

        if (debt.getStatus() == SupplierDebt.DebtStatus.PAID) {
            throw new BusinessException("DEBT_ALREADY_PAID", "Công nợ này đã được thanh toán đầy đủ");
        }

        // 1. Cập nhật công nợ (domain method validate)
        debt.pay(req.getAmount());
        supplierDebtRepository.save(debt);

        // 2. Ghi Phiếu Chi ra Sổ quỹ
        CashbookTransaction.FundType fundType;
        try {
            fundType = CashbookTransaction.FundType.valueOf(req.getFundType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_FUND_TYPE", "fundType không hợp lệ: " + req.getFundType());
        }

        BigDecimal balanceBefore = cashbookRepository.getCurrentBalance(
                getWarehouseFromDebt(debt), fundType);
        BigDecimal balanceAfter = balanceBefore.subtract(req.getAmount());

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INSUFFICIENT_FUNDS",
                    "Số dư quỹ không đủ để thanh toán. Hiện có: " + balanceBefore);
        }

        CashbookTransaction paymentTxn = CashbookTransaction.builder()
                .warehouseId(getWarehouseFromDebt(debt))
                .fundType(fundType)
                .transactionType(CashbookTransaction.TransactionType.OUT)
                .referenceType("SUPPLIER_PAYMENT")
                .referenceId(debt.getPurchaseOrderId())
                .amount(req.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description(req.getNote() != null ? req.getNote()
                        : "Thanh toán công nợ NCC - PO: " + debt.getPurchaseOrderId())
                .createdBy(paidBy)
                .build();
        cashbookRepository.save(paymentTxn);

        log.info("Supplier debt paid: {} | amount: {} | status: {}",
                debt.getId(), req.getAmount(), debt.getStatus());
        return debt;
    }

    // ─────────────────────────────────────────────────────────
    // ĐỐI SOÁT COD (FIN-05) — batch import từ Excel
    // ─────────────────────────────────────────────────────────
    @Transactional
    public CodReconciliationResult reconcileCOD(List<CodReconciliationItem> items,
                                                 UUID warehouseId, String reconciledBy) {
        int matched = 0, notFound = 0;
        BigDecimal totalReceived = BigDecimal.ZERO;
        BigDecimal totalShippingFee = BigDecimal.ZERO;

        for (CodReconciliationItem item : items) {
            Order order = orderRepository.findByCode(item.orderCode()).orElse(null);
            if (order == null) {
                notFound++;
                continue;
            }

            if (Boolean.TRUE.equals(order.getCodReconciled())) continue;

            // Đánh dấu đã đối soát
            order.setCodReconciled(true);
            order.setPaymentStatus(Order.PaymentStatus.PAID);
            orderRepository.save(order);

            // Ghi Phiếu Thu (tiền COD từ đơn vị vận chuyển về)
            cashbookRepository.save(CashbookTransaction.builder()
                    .warehouseId(warehouseId)
                    .fundType(CashbookTransaction.FundType.BANK_112)
                    .transactionType(CashbookTransaction.TransactionType.IN)
                    .referenceType("COD_RECONCILIATION")
                    .referenceId(order.getId())
                    .amount(item.amountReceived())
                    .description("COD đơn #" + order.getCode() + " - " + item.shippingProvider())
                    .createdBy(reconciledBy)
                    .build());

            // Ghi Phiếu Chi (phí vận chuyển)
            if (item.shippingFee().compareTo(BigDecimal.ZERO) > 0) {
                cashbookRepository.save(CashbookTransaction.builder()
                        .warehouseId(warehouseId)
                        .fundType(CashbookTransaction.FundType.BANK_112)
                        .transactionType(CashbookTransaction.TransactionType.OUT)
                        .referenceType("COD_RECONCILIATION")
                        .referenceId(order.getId())
                        .amount(item.shippingFee())
                        .description("Phí ship đơn #" + order.getCode() + " - " + item.shippingProvider())
                        .createdBy(reconciledBy)
                        .build());
            }

            totalReceived   = totalReceived.add(item.amountReceived());
            totalShippingFee = totalShippingFee.add(item.shippingFee());
            matched++;
        }

        return new CodReconciliationResult(matched, notFound,
                totalReceived, totalShippingFee,
                totalReceived.subtract(totalShippingFee));
    }

    // ─────────────────────────────────────────────────────────
    // CASHBOOK REPORT (REP-04)
    // ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public BigDecimal getCurrentBalance(UUID warehouseId, String fundType) {
        CashbookTransaction.FundType type =
                CashbookTransaction.FundType.valueOf(fundType.toUpperCase());
        return cashbookRepository.getCurrentBalance(warehouseId, type);
    }

    @Transactional(readOnly = true)
    public List<CashbookTransaction> getCashbookReport(UUID warehouseId,
                                                        Instant from, Instant to) {
        return cashbookRepository.findByWarehouseAndDateRange(warehouseId, from, to);
    }

    @Transactional(readOnly = true)
    public List<SupplierDebt> getOutstandingDebts() {
        return supplierDebtRepository.findByStatus(SupplierDebt.DebtStatus.UNPAID);
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────
    private UUID getWarehouseFromDebt(SupplierDebt debt) {
        return purchaseOrderRepository.findById(debt.getPurchaseOrderId())
                .map(PurchaseOrder::getWarehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PurchaseOrder", debt.getPurchaseOrderId()));
    }

    // ─────────────────────────────────────────────────────────
    // RECORDS (DTO nội bộ)
    // ─────────────────────────────────────────────────────────
    public record CodReconciliationItem(
            String orderCode,
            BigDecimal amountReceived,
            BigDecimal shippingFee,
            String shippingProvider
    ) {}

    public record CodReconciliationResult(
            int matched,
            int notFound,
            BigDecimal totalReceived,
            BigDecimal totalShippingFee,
            BigDecimal netAmount
    ) {}
}
