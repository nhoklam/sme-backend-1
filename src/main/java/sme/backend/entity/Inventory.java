package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import sme.backend.exception.InsufficientStockException;

import java.util.UUID;

/**
 * ENTITY QUAN TRỌNG NHẤT HỆ THỐNG.
 *
 * Chứa @Version để kích hoạt Optimistic Locking của Hibernate.
 * Khi 2 request đồng thời cập nhật cùng 1 dòng, Hibernate sẽ:
 * - Request đến trước: Thành công, tăng version lên 1.
 * - Request đến sau: Ném OptimisticLockException -> 409 Conflict.
 * Frontend phải retry khi gặp 409.
 *
 * Rich Domain Model: Chứa logic nghiệp vụ thay vì chỉ get/set.
 */
@Entity
@Table(name = "inventories", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "warehouse_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    /** Tồn kho vật lý thực tế trên kệ */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /** Số lượng đang được giữ chỗ cho đơn Online chưa đóng gói */
    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    /** Số lượng đang trên đường chuyển kho (đã xuất khỏi kho nguồn, chưa vào kho đích) */
    @Column(name = "in_transit", nullable = false)
    @Builder.Default
    private Integer inTransit = 0;

    /** Ngưỡng tối thiểu - nếu quantity <= minQuantity thì gửi cảnh báo */
    @Column(name = "min_quantity", nullable = false)
    @Builder.Default
    private Integer minQuantity = 0;

    /** [QUAN TRỌNG] Optimistic Locking - KHÔNG bao giờ set thủ công */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    // =====================================================================
    // COMPUTED PROPERTIES (Transient - không lưu DB)
    // =====================================================================

    /**
     * Tồn kho khả dụng = Tồn vật lý - Đã giữ chỗ
     * Đây là số hiển thị trên Web E-commerce.
     */
    @Transient
    public int getAvailableQuantity() {
        return this.quantity - this.reservedQuantity;
    }

    /**
     * Kiểm tra có đang cảnh báo tồn kho thấp không
     */
    @Transient
    public boolean isLowStock() {
        return this.quantity <= this.minQuantity && this.minQuantity > 0;
    }

    // =====================================================================
    // BUSINESS LOGIC METHODS (Rich Domain Model)
    // =====================================================================

    /**
     * Trừ kho khi bán POS (Offline).
     * Trừ thẳng vào quantity - không đi qua reserved.
     *
     * @throws InsufficientStockException nếu không đủ hàng
     */
    public void deductPhysicalQuantity(int amount) {
        if (this.getAvailableQuantity() < amount) {
            throw new InsufficientStockException(
                    String.format("Không đủ tồn kho. Khả dụng: %d, Yêu cầu: %d",
                            this.getAvailableQuantity(), amount)
            );
        }
        this.quantity -= amount;
    }

    /**
     * Cộng kho (Nhập hàng, Trả hàng về kho bán).
     */
    public void addQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số lượng nhập phải > 0");
        }
        this.quantity += amount;
    }

    /**
     * Giữ chỗ khi khách đặt Online.
     * reservedQuantity tăng, quantity KHÔNG đổi.
     *
     * @throws InsufficientStockException nếu không đủ hàng khả dụng
     */
    public void reserveQuantity(int amount) {
        if (this.getAvailableQuantity() < amount) {
            throw new InsufficientStockException(
                    String.format("Sản phẩm đã hết hàng trên Web. Khả dụng: %d, Yêu cầu: %d",
                            this.getAvailableQuantity(), amount)
            );
        }
        this.reservedQuantity += amount;
    }

    /**
     * Giải phóng giữ chỗ khi đơn Online bị Hủy.
     */
    public void releaseReservedQuantity(int amount) {
        if (this.reservedQuantity < amount) {
            throw new IllegalStateException(
                    String.format("Lỗi logic: reserved=%d, giải phóng=%d", this.reservedQuantity, amount)
            );
        }
        this.reservedQuantity -= amount;
    }

    /**
     * Xác nhận giao hàng: Chuyển từ reserved sang trừ thực.
     * Gọi khi đơn Online chuyển sang trạng thái SHIPPING.
     */
    public void confirmShipment(int amount) {
        if (this.reservedQuantity < amount) {
            throw new IllegalStateException(
                    String.format("Lỗi logic: reserved=%d, xác nhận=%d", this.reservedQuantity, amount)
            );
        }
        this.quantity -= amount;
        this.reservedQuantity -= amount;
    }

    /**
     * Xuất kho chuyển: Trừ quantity + ghi in_transit.
     */
    public void dispatchForTransfer(int amount) {
        if (this.getAvailableQuantity() < amount) {
            throw new InsufficientStockException(
                    "Không đủ tồn kho để chuyển kho. Khả dụng: " + this.getAvailableQuantity()
            );
        }
        this.quantity -= amount;
        this.inTransit += amount;
    }

    /**
     * Nhận hàng chuyển kho: Tăng quantity, giảm in_transit.
     */
    public void receiveTransfer(int amount) {
        this.inTransit -= amount;
        this.quantity += amount;
    }
}
