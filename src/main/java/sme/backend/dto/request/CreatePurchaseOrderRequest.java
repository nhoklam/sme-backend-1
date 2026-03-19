package sme.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePurchaseOrderRequest {

    @NotNull private UUID supplierId;
    @NotNull private UUID warehouseId;

    @NotEmpty @Valid
    private List<PurchaseItemRequest> items;

    private String note;

    @Data
    public static class PurchaseItemRequest {
        @NotNull private UUID productId;
        @NotNull @Min(1) private Integer quantity;
        @NotNull @DecimalMin("0.01") private BigDecimal importPrice;
    }
}
