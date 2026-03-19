package sme.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.InvoiceRepository;
import sme.backend.repository.ProductRepository;
import sme.backend.security.UserPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /**
     * GET /reports/revenue — REP-01: Báo cáo doanh thu
     * period: day | week | month
     */
    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Object[]>>> getRevenueReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "day") String period) {

        UUID warehouseId = principal.getRole().name().equals("ROLE_ADMIN")
                ? null : principal.getWarehouseId();

        if (warehouseId != null) {
            return ResponseEntity.ok(ApiResponse.ok(
                    invoiceRepository.getRevenueReport(warehouseId, from, to, period)));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                invoiceRepository.getRevenueByWarehouse(from, to)));
    }

    /**
     * GET /reports/inventory-value — REP-03: Báo cáo giá trị tồn kho
     */
    @GetMapping("/inventory-value")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Object[]>>> getInventoryValue(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID warehouseId) {

        UUID wid = principal.getRole().name().equals("ROLE_ADMIN")
                ? warehouseId : principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(
                inventoryRepository.getInventoryValueReport(wid)));
    }

    /**
     * GET /reports/dead-stock — REP-03b: Hàng tồn chậm
     */
    @GetMapping("/dead-stock")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Object[]>>> getDeadStock(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "90") int days) {
        UUID wid = principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(
                inventoryRepository.findDeadStockByWarehouse(wid, days)));
    }

    /**
     * GET /reports/top-products — Top sản phẩm bán chạy
     */
    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Object[]>>> getTopProducts(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                productRepository.findTopSellingProducts(from, to, limit)));
    }

    /**
     * GET /reports/summary — Dashboard tổng quan nhanh
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardSummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID wid = principal.getWarehouseId();
        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant todayEnd   = todayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        List<Object[]> revenue = (wid != null)
                ? invoiceRepository.getRevenueReport(wid, todayStart, todayEnd, "day")
                : invoiceRepository.getRevenueByWarehouse(todayStart, todayEnd);

        int lowStockCount = (wid != null)
                ? inventoryRepository.findLowStockByWarehouse(wid).size() : 0;

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "warehouseId",   wid != null ? wid.toString() : "ALL",
                "revenueToday",  revenue,
                "lowStockCount", lowStockCount
        )));
    }
}
