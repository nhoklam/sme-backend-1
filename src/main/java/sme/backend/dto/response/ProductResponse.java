package sme.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// ─────────────────────────────────────────────────────────────
// PRODUCT
// ─────────────────────────────────────────────────────────────
@Data @Builder
public class ProductResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String isbnBarcode;
    private String sku;
    private String name;
    private String description;
    private BigDecimal retailPrice;
    private BigDecimal wholesalePrice;
    private BigDecimal macPrice;
    private String imageUrl;
    private String unit;
    private BigDecimal weight;
    private Boolean isActive;
    private Instant createdAt;
    // Tồn kho khả dụng - điền khi cần
    private Integer availableQuantity;
}
