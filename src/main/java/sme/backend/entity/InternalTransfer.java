package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "internal_transfers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InternalTransfer extends BaseEntity {

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(name = "from_warehouse_id", nullable = false)
    private UUID fromWarehouseId;

    @Column(name = "to_warehouse_id", nullable = false)
    private UUID toWarehouseId;

    // ĐÃ SỬA Ở ĐÂY: Đổi tên cột thành created_by_user_id để không trùng với BaseEntity
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "received_by")
    private UUID receivedByUserId;

    /**
     * DRAFT      → Mới tạo
     * DISPATCHED → Đã xuất kho nguồn, hàng đang trên đường
     * RECEIVED   → Kho đích đã nhận hàng
     * CANCELLED  → Đã hủy
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TransferStatus status = TransferStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransferItem> items = new ArrayList<>();

    public enum TransferStatus {
        DRAFT, DISPATCHED, RECEIVED, CANCELLED
    }

    public void addItem(TransferItem item) {
        items.add(item);
        item.setTransfer(this);
    }

    @Column(name = "reference_order_id")
    private UUID referenceOrderId;

    @Column(name = "transfer_reason")
    private String transferReason;
}