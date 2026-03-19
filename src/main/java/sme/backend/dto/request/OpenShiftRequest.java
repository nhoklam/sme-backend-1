package sme.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class OpenShiftRequest {
    @NotNull(message = "Tiền đầu ca không được để trống")
    @DecimalMin(value = "0", message = "Tiền đầu ca không được âm")
    private BigDecimal startingCash;
}
