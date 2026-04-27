package sme.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateProductRequest {
    private UUID categoryId;
    
    // ĐÃ BỔ SUNG 2 TRƯỜNG NÀY (Hỗ trợ thay đổi hoặc gỡ Nhà cung cấp)
    private UUID supplierId;
    private Boolean hasSupplierId = false;

    @Size(max = 255)
    private String name;

    private String description;

    @DecimalMin(value = "0")
    private BigDecimal retailPrice;

    @DecimalMin(value = "0")
    private BigDecimal wholesalePrice;

    private String imageUrl;
    private List<String> imageUrls;
    private String unit;

    @DecimalMin(value = "0")
    private BigDecimal weight;

    private Boolean isActive;

    // Custom setter để tự động kích hoạt cờ cập nhật Supplier
    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
        this.hasSupplierId = true;
    }
}