package sme.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateProductRequest {
    private UUID categoryId;

    @Size(max = 255)
    private String name;

    private String description;

    @DecimalMin(value = "0")
    private BigDecimal retailPrice;

    @DecimalMin(value = "0")
    private BigDecimal wholesalePrice;

    private String imageUrl;

    private String unit;

    @DecimalMin(value = "0")
    private BigDecimal weight;

    private Boolean isActive;
}
