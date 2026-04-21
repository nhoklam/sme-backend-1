package sme.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.entity.User;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.InvoiceRepository;
import sme.backend.repository.ProductRepository;
import sme.backend.security.UserPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final InvoiceRepository invoiceRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    // Hàm tiện ích để đảm bảo Manager luôn chỉ truy cập được kho của họ
    private UUID getEffectiveWarehouseId(UserPrincipal principal, UUID requestedWarehouseId) {
        if (principal.getRole() == User.UserRole.ROLE_ADMIN) {
            return requestedWarehouseId;
        }
        return principal.getWarehouseId();
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRevenueReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) UUID warehouseId) { 

        UUID wid = getEffectiveWarehouseId(principal, warehouseId);

        List<Map<String, Object>> result;
        if ("year".equalsIgnoreCase(period)) {
            result = invoiceRepository.getRevenueReportYearly(wid, from, to);
        } else if ("month".equalsIgnoreCase(period)) {
            result = invoiceRepository.getRevenueReportMonthly(wid, from, to);
        } else if ("week".equalsIgnoreCase(period)) {
            result = invoiceRepository.getRevenueReportWeekly(wid, from, to);
        } else {
            result = invoiceRepository.getRevenueReportDaily(wid, from, to);
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/inventory-value")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInventoryValue(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID warehouseId) {

        UUID wid = getEffectiveWarehouseId(principal, warehouseId);
                
        return ResponseEntity.ok(ApiResponse.ok(
                inventoryRepository.getInventoryValueReport(wid)));
    }

    @GetMapping("/dead-stock")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDeadStock(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(required = false) UUID warehouseId) { 

        UUID wid = getEffectiveWarehouseId(principal, warehouseId);
                
        return ResponseEntity.ok(ApiResponse.ok(
                inventoryRepository.findDeadStockByWarehouse(wid, days)));
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopProducts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") int limit) {

        UUID wid = getEffectiveWarehouseId(principal, warehouseId);

        return ResponseEntity.ok(ApiResponse.ok(
                productRepository.findTopSellingProducts(wid, from, to, limit)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID warehouseId) { 
        
        UUID wid = getEffectiveWarehouseId(principal, warehouseId);
                
        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant todayEnd   = todayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        List<Map<String, Object>> revenue = invoiceRepository.getRevenueReportDaily(wid, todayStart, todayEnd);
        
        int lowStockCount = (wid != null) ? inventoryRepository.findLowStockByWarehouse(wid).size() : 0;

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "warehouseId",   wid != null ? wid.toString() : "ALL",
                "revenueToday",  revenue,
                "lowStockCount", lowStockCount
        )));
    }
}