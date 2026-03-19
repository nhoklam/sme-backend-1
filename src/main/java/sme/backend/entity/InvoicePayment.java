package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_payments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InvoicePayment extends BaseSimpleEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /**
     * Phương thức thanh toán:
     * CASH   → Tiền mặt (TK 111)
     * CARD   → Quẹt thẻ (TK 112)
     * MOMO   → Ví MoMo (TK 112)
     * VNPAY  → VNPay (TK 112)
     * POINTS → Đổi điểm
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentMethod method;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Mã giao dịch từ cổng thanh toán (VNPAY/MoMo) */
    @Column(length = 255)
    private String reference;

    public enum PaymentMethod {
        CASH, CARD, MOMO, VNPAY, POINTS
    }
}
