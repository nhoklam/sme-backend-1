package sme.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CloseShiftRequest {
    @NotNull(message = "Tiền thực đếm không được để trống")
    @DecimalMin(value = "0", message = "Tiền thực đếm không được âm")
    private BigDecimal reportedCash;

    private String discrepancyReason;
}
