package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Thẻ kho - Lịch sử mọi biến động tồn kho.
 * Bất biến (Immutable): Không bao giờ update, chỉ insert.
 */
@Entity
@Table(name = "inventory_transactions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InventoryTransaction extends BaseSimpleEntity {

    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    /** ID của chứng từ gốc: Invoice.id / Order.id / PurchaseOrder.id / Transfer.id */
    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    /**
     * SALE_POS       → Bán tại quầy
     * SALE_ONLINE    → Bán online
     * IMPORT         → Nhập kho từ NCC
     * TRANSFER_OUT   → Xuất chuyển kho
     * TRANSFER_IN    → Nhận chuyển kho
     * ADJUSTMENT     → Điều chỉnh kiểm kê
     * RETURN_TO_STOCK   → Trả hàng về kho bán
     * RETURN_TO_DEFECT  → Trả hàng về kho lỗi
     * RESERVE        → Giữ chỗ đơn Online
     * RELEASE        → Giải phóng giữ chỗ
     */
    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    /** Dương (+) = nhập, Âm (-) = xuất */
    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    @Column(name = "quantity_before", nullable = false)
    private Integer quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private Integer quantityAfter;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
