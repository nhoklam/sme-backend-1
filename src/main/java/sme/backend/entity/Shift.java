package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ca làm việc - Chốt chặn chống gian lận tiền mặt.
 * Mọi giao dịch POS (Invoice) đều bắt buộc gắn với một Shift đang OPEN.
 */
@Entity
@Table(name = "shifts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Shift extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "cashier_id", nullable = false)
    private UUID cashierId;

    /** Số tiền lẻ đầu ca (thu ngân tự nhập khi mở ca) */
    @Column(name = "starting_cash", nullable = false, precision = 19, scale = 4)
    private BigDecimal startingCash;

    /** Số tiền thu ngân tự đếm khi đóng ca (Blind Close - không biết lý thuyết) */
    @Column(name = "reported_cash", precision = 19, scale = 4)
    private BigDecimal reportedCash;

    /** Số tiền hệ thống tự tính: startingCash + Thu tiền mặt - Chi tiền mặt */
    @Column(name = "theoretical_cash", precision = 19, scale = 4)
    private BigDecimal theoreticalCash;

    /** Chênh lệch = reportedCash - theoreticalCash. Dương = thừa, Âm = thiếu */
    @Column(name = "discrepancy_amount", precision = 19, scale = 4)
    private BigDecimal discrepancyAmount;

    /** Bắt buộc nhập nếu discrepancyAmount != 0 */
    @Column(name = "discrepancy_reason", columnDefinition = "TEXT")
    private String discrepancyReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.OPEN;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "opened_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public enum ShiftStatus {
        OPEN,
        CLOSED,
        MANAGER_APPROVED
    }

    // =====================================================================
    // BUSINESS LOGIC
    // =====================================================================

    /**
     * Đóng ca mù (Blind Close).
     * Thu ngân không biết lý thuyết trước khi nhập số thực đếm.
     *
     * @param reportedCash    Tiền thực đếm từ két
     * @param theoreticalCash Tiền hệ thống tính (do Service tính rồi truyền vào)
     * @param reason          Lý do giải trình (bắt buộc nếu lệch)
     */
    public void closeShift(BigDecimal reportedCash, BigDecimal theoreticalCash, String reason) {
        if (this.status != ShiftStatus.OPEN) {
            throw new IllegalStateException("Ca làm việc không ở trạng thái OPEN");
        }

        this.reportedCash = reportedCash;
        this.theoreticalCash = theoreticalCash;
        this.discrepancyAmount = reportedCash.subtract(theoreticalCash);

        if (this.discrepancyAmount.compareTo(BigDecimal.ZERO) != 0
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(
                    "Bắt buộc phải nhập lý do giải trình khi lệch quỹ! Chênh lệch: " + this.discrepancyAmount
            );
        }

        this.discrepancyReason = reason;
        this.status = ShiftStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    /**
     * Manager duyệt chốt ca.
     * Sau bước này số liệu mới chính thức và được đẩy vào báo cáo.
     */
    public void approve(UUID managerId) {
        if (this.status != ShiftStatus.CLOSED) {
            throw new IllegalStateException("Chỉ có thể duyệt ca đã CLOSED");
        }
        this.status = ShiftStatus.MANAGER_APPROVED;
        this.approvedBy = managerId;
        this.approvedAt = Instant.now();
    }
}
