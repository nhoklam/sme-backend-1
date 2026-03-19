package sme.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class OrderResponse {
    private UUID id;
    private String code;
    private UUID customerId;
    private String customerName;
    private String customerPhone;
    private UUID assignedWarehouseId;
    private String assignedWarehouseName;
    private String status;
    private String type;
    private String shippingName;
    private String shippingPhone;
    private String shippingAddress;
    private String provinceCode;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String trackingCode;
    private String shippingProvider;
    private Boolean codReconciled;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ItemResponse> items;
    private List<StatusHistoryResponse> statusHistory;

    @Data @Builder
    public static class ItemResponse {
        private UUID productId;
        private String productName;
        private String isbnBarcode;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    @Data @Builder
    public static class StatusHistoryResponse {
        private String oldStatus;
        private String newStatus;
        private String note;
        private String changedBy;
        private Instant createdAt;
    }
}
