package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Kho được thuật toán Smart Routing chỉ định đóng gói */
    @Column(name = "assigned_warehouse_id")
    private UUID assignedWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /** DELIVERY = giao tận nơi | BOPIS = mua online lấy tại quầy */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private OrderType type = OrderType.DELIVERY;

    @Column(name = "shipping_name", length = 150)
    private String shippingName;

    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;

    /** Mã tỉnh/thành phố của địa chỉ giao hàng - dùng cho routing */
    @Column(name = "province_code", nullable = false, length = 20)
    private String provinceCode;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal finalAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod; // COD, VNPAY, CREDIT_CARD, MOMO

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 50)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "shipping_provider", length = 50)
    private String shippingProvider; // GHTK, VIETTEL_POST, GHN

    @Column(name = "cod_reconciled")
    @Builder.Default
    private Boolean codReconciled = false;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "packed_by")
    private UUID packedBy;

    @Column(name = "packed_at")
    private Instant packedAt;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    public enum OrderStatus {
        PENDING,    // Chờ xử lý
        PACKING,    // Đang đóng gói
        SHIPPING,   // Đang vận chuyển
        DELIVERED,  // Đã giao
        CANCELLED,  // Đã hủy
        RETURNED    // Hoàn trả
    }

    public enum OrderType {
        DELIVERY,   // Giao tận nhà
        BOPIS       // Buy Online Pick Up In Store
    }

    public enum PaymentStatus {
        UNPAID, PAID, REFUNDED
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void addStatusHistory(OrderStatusHistory history) {
        statusHistory.add(history);
        history.setOrder(this);
    }

    /**
     * Chuyển trạng thái đơn hàng với validation luồng hợp lệ
     */
    public void transitionTo(OrderStatus newStatus, String note, String changedBy) {
        validateTransition(this.status, newStatus);
        OrderStatusHistory history = OrderStatusHistory.builder()
                .oldStatus(this.status.name())
                .newStatus(newStatus.name())
                .note(note)
                .changedBy(changedBy)
                .build();
        this.status = newStatus;
        this.addStatusHistory(history);
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        boolean valid = switch (from) {
            case PENDING   -> to == OrderStatus.PACKING || to == OrderStatus.CANCELLED;
            case PACKING   -> to == OrderStatus.SHIPPING || to == OrderStatus.CANCELLED;
            case SHIPPING  -> to == OrderStatus.DELIVERED || to == OrderStatus.RETURNED;
            case DELIVERED -> to == OrderStatus.RETURNED;
            default        -> false;
        };
        if (!valid) {
            throw new IllegalStateException(
                    String.format("Không thể chuyển trạng thái từ %s sang %s", from, to)
            );
        }
    }
}
