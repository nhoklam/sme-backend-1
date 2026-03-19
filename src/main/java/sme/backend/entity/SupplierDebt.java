package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "supplier_debts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SupplierDebt extends BaseSimpleEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "total_debt", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebt;

    @Column(name = "paid_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private DebtStatus status = DebtStatus.UNPAID;

    @Column(name = "due_date")
    private LocalDate dueDate;

    public enum DebtStatus {
        UNPAID, PARTIAL, PAID
    }

    /**
     * Thanh toán một phần hoặc toàn bộ công nợ
     */
    public void pay(BigDecimal amount) {
        BigDecimal remaining = this.totalDebt.subtract(this.paidAmount);
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "Số tiền thanh toán vượt quá công nợ còn lại: " + remaining
            );
        }
        this.paidAmount = this.paidAmount.add(amount);

        if (this.paidAmount.compareTo(this.totalDebt) >= 0) {
            this.status = DebtStatus.PAID;
        } else {
            this.status = DebtStatus.PARTIAL;
        }
    }

    public BigDecimal getRemainingAmount() {
        return this.totalDebt.subtract(this.paidAmount);
    }
}
