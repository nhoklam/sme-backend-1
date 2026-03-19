package sme.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AdjustInventoryRequest {

    @NotNull(message = "productId bắt buộc")
    private UUID productId;

    @NotNull(message = "warehouseId bắt buộc")
    private UUID warehouseId;

    @NotNull(message = "Số lượng thực tế bắt buộc")
    private Integer actualQuantity;

    @NotBlank(message = "Lý do điều chỉnh bắt buộc")
    private String reason;
}
