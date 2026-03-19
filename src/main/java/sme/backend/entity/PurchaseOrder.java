package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PurchaseOrder extends BaseEntity {

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    // ĐÃ SỬA: Đổi tên biến và tên cột để không xung đột với BaseEntity
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /**
     * DRAFT      → Mới tạo, chưa gửi
     * PENDING    → Chờ duyệt
     * COMPLETED  → Đã nhập kho thành công
     * CANCELLED  → Đã hủy
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();

    public enum PurchaseStatus {
        DRAFT, PENDING, COMPLETED, CANCELLED
    }

    public void addItem(PurchaseItem item) {
        items.add(item);
        item.setPurchaseOrder(this);
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(i -> i.getImportPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}