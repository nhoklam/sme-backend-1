package sme.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
@Data @Builder
public class ProductResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private UUID supplierId; // ĐÃ BỔ SUNG TRƯỜNG NÀY
    private String isbnBarcode;
    private String sku;
    private String name;
    private String description;
    private BigDecimal retailPrice;
    private BigDecimal wholesalePrice;
    private BigDecimal macPrice;
    private String imageUrl;
    private List<String> imageUrls;  
    private String unit;
    private BigDecimal weight;
    private Boolean isActive;
    private Instant createdAt;
    private Integer availableQuantity;
}