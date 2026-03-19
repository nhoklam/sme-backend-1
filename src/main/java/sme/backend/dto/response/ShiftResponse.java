package sme.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class ShiftResponse {
    private UUID id;
    private UUID warehouseId;
    private String warehouseName;
    private UUID cashierId;
    private String cashierName;
    private BigDecimal startingCash;
    private BigDecimal reportedCash;
    private BigDecimal theoreticalCash;
    private BigDecimal discrepancyAmount;
    private String discrepancyReason;
    private String status;
    private Instant openedAt;
    private Instant closedAt;
    private Instant approvedAt;
    // Summary
    private Long invoiceCount;
    private BigDecimal totalRevenue;
}
