package sme.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaySupplierDebtRequest {

    @NotNull(message = "supplierDebtId bắt buộc")
    private UUID supplierDebtId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Số tiền phải > 0")
    private BigDecimal amount;

    @NotBlank(message = "Loại quỹ không được để trống")
    private String fundType;    // CASH_111, BANK_112

    private String note;
}
