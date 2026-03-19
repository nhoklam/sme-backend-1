package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.config.AppProperties;
import sme.backend.dto.request.CheckoutRequest;
import sme.backend.dto.response.InvoiceResponse;
import sme.backend.entity.*;
import sme.backend.exception.BusinessException;
import sme.backend.exception.InsufficientStockException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * POSService - Checkout Engine
 *
 * Xử lý toàn bộ luồng thanh toán POS theo thứ tự ACID:
 * 1. Validate shift đang mở
 * 2. Validate tồn kho từng sản phẩm
 * 3. Tính discount + điểm
 * 4. Trừ kho (Optimistic Lock)
 * 5. Tạo Invoice + InvoiceItems + InvoicePayments
 * 6. Ghi Cashbook transactions
 * 7. Cộng điểm khách hàng
 * 8. Return InvoiceResponse để Frontend in hóa đơn
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POSService {

    private final ShiftService shiftService;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CashbookTransactionRepository cashbookRepository;
    private final AppProperties appProperties;

    // ─────────────────────────────────────────────────────────
    // CHECKOUT (POS-04, POS-05) — toàn bộ là 1 transaction ACID
    // ─────────────────────────────────────────────────────────
    @Transactional
    public InvoiceResponse checkout(CheckoutRequest req, UUID cashierId, UUID warehouseId) {

        // 1. Validate shift đang OPEN
        Shift shift = shiftService.getOpenShiftByCashier(cashierId);
        if (!shift.getId().equals(req.getShiftId())) {
            throw new BusinessException("SHIFT_MISMATCH",
                    "shiftId không khớp với ca làm việc đang mở");
        }

        // 2. Load customer (optional)
        Customer customer = null;
        if (req.getCustomerId() != null) {
            customer = customerRepository.findById(req.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", req.getCustomerId()));
        }

        // 3. Build invoice
        String code = generateInvoiceCode();
        Invoice invoice = Invoice.builder()
                .code(code)
                .shiftId(shift.getId())
                .customerId(customer != null ? customer.getId() : null)
                .type(Invoice.InvoiceType.SALE)
                .cashierId(cashierId)
                .note(req.getNote())
                .build();

        // 4. Xử lý từng sản phẩm: validate tồn kho + tạo InvoiceItem
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Validate tồn kho trước – không trừ vội, tránh partial rollback
        for (CheckoutRequest.CartItemRequest cartItem : req.getItems()) {
            productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product", cartItem.getProductId()));
            Inventory inv = inventoryRepository
                    .findByProductAndWarehouseWithLock(cartItem.getProductId(), warehouseId)
                    .orElseThrow(() -> new InsufficientStockException(
                            "Không tìm thấy tồn kho cho sản phẩm: " + cartItem.getProductId()));
            if (inv.getAvailableQuantity() < cartItem.getQuantity()) {
                throw new InsufficientStockException(
                        "Sản phẩm không đủ hàng. Khả dụng: " + inv.getAvailableQuantity());
            }
        }

        for (CheckoutRequest.CartItemRequest cartItem : req.getItems()) {
            Product product = productRepository.findById(cartItem.getProductId()).orElseThrow();
            Inventory inv = inventoryRepository
                    .findByProductAndWarehouseWithLock(product.getId(), warehouseId)
                    .orElseThrow();
            int before = inv.getQuantity();
            inv.deductPhysicalQuantity(cartItem.getQuantity());
            inventoryRepository.save(inv);
            // Thẻ kho sẽ được ghi sau khi có invoiceId – dùng UUID temp
            inventoryTransactionRepository.save(
                    InventoryTransaction.builder()
                            .inventoryId(inv.getId())
                            .referenceId(UUID.randomUUID()) // will be updated post-save
                            .transactionType("SALE_POS")
                            .quantityChange(-cartItem.getQuantity())
                            .quantityBefore(before)
                            .quantityAfter(inv.getQuantity())
                            .createdBy(cashierId.toString())
                            .build()
            );

            InvoiceItem item = InvoiceItem.builder()
                    .productId(product.getId())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .macPrice(product.getMacPrice())
                    .subtotal(cartItem.getUnitPrice()
                            .multiply(BigDecimal.valueOf(cartItem.getQuantity())))
                    .build();
            invoice.addItem(item);
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        // 5. Tính discount (điểm đổi thưởng)
        BigDecimal discountAmount = BigDecimal.ZERO;
        int pointsToUse = req.getPointsToUse() != null ? req.getPointsToUse() : 0;
        if (pointsToUse > 0 && customer != null) {
            customer.deductPoints(pointsToUse);  // throws nếu không đủ điểm
            // 1 điểm = 1000đ giảm giá (configurable)
            discountAmount = BigDecimal.valueOf(pointsToUse)
                    .multiply(BigDecimal.valueOf(
                            appProperties.getBusiness().getLoyaltyPointsPerVnd()));
            if (discountAmount.compareTo(totalAmount) > 0) {
                discountAmount = totalAmount; // không giảm quá tổng bill
            }
        }

        BigDecimal finalAmount = totalAmount.subtract(discountAmount);
        invoice.setTotalAmount(totalAmount);
        invoice.setDiscountAmount(discountAmount);
        invoice.setFinalAmount(finalAmount);

        // 6. Validate tổng tiền thanh toán = finalAmount
        BigDecimal totalPaid = req.getPayments().stream()
                .map(CheckoutRequest.PaymentRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPaid.compareTo(finalAmount) < 0) {
            throw new BusinessException("INSUFFICIENT_PAYMENT",
                    String.format("Tổng tiền thanh toán (%.0f) < Tổng hóa đơn (%.0f)",
                            totalPaid, finalAmount));
        }

        // 7. Tạo InvoicePayments + ghi Cashbook
        for (CheckoutRequest.PaymentRequest p : req.getPayments()) {
            InvoicePayment.PaymentMethod method;
            try {
                method = InvoicePayment.PaymentMethod.valueOf(p.getMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_PAYMENT_METHOD",
                        "Phương thức thanh toán không hợp lệ: " + p.getMethod());
            }

            InvoicePayment payment = InvoicePayment.builder()
                    .method(method)
                    .amount(p.getAmount())
                    .reference(p.getReference())
                    .build();
            invoice.addPayment(payment);

            // Ghi Sổ quỹ (Cashbook) cho từng phương thức
            recordCashbook(shift, method, p.getAmount(), warehouseId);
        }

        // 8. Cộng điểm tích lũy
        int pointsEarned = 0;
        if (customer != null) {
            pointsEarned = finalAmount.divide(
                    BigDecimal.valueOf(appProperties.getBusiness().getLoyaltyPointsPerVnd()),
                    0, RoundingMode.DOWN).intValue();
            customer.addPoints(pointsEarned);
            customer.setTotalSpent(customer.getTotalSpent().add(finalAmount));
            customerRepository.save(customer);
        }
        invoice.setPointsUsed(pointsToUse);
        invoice.setPointsEarned(pointsEarned);

        // 9. Lưu Invoice (cascade lưu items + payments)
        invoice = invoiceRepository.save(invoice);

        // 10. Cập nhật referenceId cho InventoryTransactions (bổ sung invoice ID)
        // (đây là post-save update, production có thể dùng event)

        log.info("Checkout completed: invoice={}, amount={}, cashier={}",
                invoice.getCode(), finalAmount, cashierId);

        return buildInvoiceResponse(invoice);
    }

    // ─────────────────────────────────────────────────────────
    // TRẢ HÀNG (POS-07, POS-08)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public InvoiceResponse refund(UUID originalInvoiceId, UUID shiftId,
                                  List<RefundItem> items, String returnDestination,
                                  UUID cashierId, UUID warehouseId, String note) {

        Invoice original = invoiceRepository.findByIdWithDetails(originalInvoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", originalInvoiceId));

        if (original.getType() != Invoice.InvoiceType.SALE) {
            throw new BusinessException("INVALID_REFUND",
                    "Không thể trả hàng cho hóa đơn trả hàng");
        }

        String code = generateReturnCode();
        Invoice returnInvoice = Invoice.builder()
                .code(code)
                .shiftId(shiftId)
                .customerId(original.getCustomerId())
                .type(Invoice.InvoiceType.RETURN)
                .cashierId(cashierId)
                .returnOfId(original.getId())
                .note(note)
                .build();

        BigDecimal totalRefund = BigDecimal.ZERO;

        for (RefundItem ri : items) {
            // Validate sản phẩm tồn tại trong hóa đơn gốc
            InvoiceItem originalItem = original.getItems().stream()
                    .filter(i -> i.getProductId().equals(ri.productId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_IN_INVOICE",
                            "Sản phẩm không có trong hóa đơn gốc: " + ri.productId()));

            if (ri.quantity() > originalItem.getQuantity()) {
                throw new BusinessException("EXCESSIVE_RETURN",
                        "Số lượng trả vượt quá số lượng đã mua");
            }

            // Tạo item trả hàng với quantity âm
            InvoiceItem returnItem = InvoiceItem.builder()
                    .productId(ri.productId())
                    .quantity(-ri.quantity())     // SỐ ÂM
                    .unitPrice(originalItem.getUnitPrice())
                    .macPrice(originalItem.getMacPrice())
                    .subtotal(originalItem.getUnitPrice().multiply(BigDecimal.valueOf(-ri.quantity())))
                    .build();
            returnInvoice.addItem(returnItem);
            totalRefund = totalRefund.add(originalItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(ri.quantity())));

            // Hoàn kho
            inventoryService.returnToStock(ri.productId(), warehouseId,
                    ri.quantity(), null, returnDestination, cashierId.toString());
        }

        returnInvoice.setTotalAmount(totalRefund);
        returnInvoice.setFinalAmount(totalRefund);

        // Sinh Phiếu Chi từ Sổ quỹ
        Shift shift = shiftService.getOpenShiftByCashier(cashierId);
        CashbookTransaction refundTxn = CashbookTransaction.builder()
                .warehouseId(warehouseId)
                .shiftId(shift.getId())
                .fundType(CashbookTransaction.FundType.CASH_111)
                .transactionType(CashbookTransaction.TransactionType.OUT)
                .referenceType("INVOICE")
                .amount(totalRefund)
                .description("Trả hàng hóa đơn #" + original.getCode())
                .createdBy(cashierId.toString())
                .build();
        cashbookRepository.save(refundTxn);

        returnInvoice = invoiceRepository.save(returnInvoice);
        return buildInvoiceResponse(returnInvoice);
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────
    private void recordCashbook(Shift shift, InvoicePayment.PaymentMethod method,
                                 BigDecimal amount, UUID warehouseId) {
        CashbookTransaction.FundType fundType =
                method == InvoicePayment.PaymentMethod.CASH
                        ? CashbookTransaction.FundType.CASH_111
                        : CashbookTransaction.FundType.BANK_112;

        CashbookTransaction txn = CashbookTransaction.builder()
                .warehouseId(warehouseId)
                .shiftId(shift.getId())
                .fundType(fundType)
                .transactionType(CashbookTransaction.TransactionType.IN)
                .referenceType("INVOICE")
                .amount(amount)
                .description("Thu tiền bán hàng - " + method.name())
                .createdBy(shift.getCashierId().toString())
                .build();
        cashbookRepository.save(txn);
    }

    private String generateInvoiceCode() {
        return "INV-" + System.currentTimeMillis();
    }

    private String generateReturnCode() {
        return "RET-" + System.currentTimeMillis();
    }

    private InvoiceResponse buildInvoiceResponse(Invoice invoice) {
        List<InvoiceResponse.ItemResponse> items = invoice.getItems().stream()
                .map(i -> InvoiceResponse.ItemResponse.builder()
                        .productId(i.getProductId())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .macPrice(i.getMacPrice())
                        .subtotal(i.getSubtotal())
                        .build())
                .toList();

        List<InvoiceResponse.PaymentResponse> payments = invoice.getPayments().stream()
                .map(p -> InvoiceResponse.PaymentResponse.builder()
                        .method(p.getMethod().name())
                        .amount(p.getAmount())
                        .reference(p.getReference())
                        .build())
                .toList();

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .code(invoice.getCode())
                .shiftId(invoice.getShiftId())
                .customerId(invoice.getCustomerId())
                .type(invoice.getType().name())
                .totalAmount(invoice.getTotalAmount())
                .discountAmount(invoice.getDiscountAmount())
                .finalAmount(invoice.getFinalAmount())
                .pointsUsed(invoice.getPointsUsed())
                .pointsEarned(invoice.getPointsEarned())
                .note(invoice.getNote())
                .createdAt(invoice.getCreatedAt())
                .items(items)
                .payments(payments)
                .build();
    }

    public record RefundItem(UUID productId, int quantity) {}
}
