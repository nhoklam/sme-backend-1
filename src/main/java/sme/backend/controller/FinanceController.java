package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.CreateCashbookEntryRequest;
import sme.backend.dto.request.PaySupplierDebtRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.entity.CashbookTransaction;
import sme.backend.entity.SupplierDebt;
import sme.backend.security.UserPrincipal;
import sme.backend.service.FinanceService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    // ── SỔ QUỸ ──────────────────────────────────────────────

    /** POST /finance/cashbook — Tạo Phiếu Thu/Chi thủ công (FIN-02) */
    @PostMapping("/cashbook")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<CashbookTransaction>> createEntry(
            @Valid @RequestBody CreateCashbookEntryRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                financeService.createManualEntry(req, principal.getUsername())));
    }

    /** GET /finance/cashbook/balance — Số dư quỹ hiện tại */
    @GetMapping("/cashbook/balance")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> getBalance(
            @RequestParam UUID warehouseId) {
        BigDecimal cash = financeService.getCurrentBalance(warehouseId, "CASH_111");
        BigDecimal bank = financeService.getCurrentBalance(warehouseId, "BANK_112");
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "CASH_111", cash,
                "BANK_112", bank,
                "total",    cash.add(bank)
        )));
    }

    /** GET /finance/cashbook — Sổ quỹ theo khoảng thời gian (REP-04) */
    @GetMapping("/cashbook")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<CashbookTransaction>>> getCashbook(
            @RequestParam UUID warehouseId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(ApiResponse.ok(
                financeService.getCashbookReport(warehouseId, from, to)));
    }

    // ── CÔNG NỢ NHÀ CUNG CẤP ─────────────────────────────────

    /** GET /finance/supplier-debts — Danh sách công nợ chưa thanh toán (FIN-03) */
    @GetMapping("/supplier-debts")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<SupplierDebt>>> getOutstandingDebts() {
        return ResponseEntity.ok(ApiResponse.ok(financeService.getOutstandingDebts()));
    }

    /** POST /finance/supplier-debts/pay — Thanh toán công nợ (FIN-04) */
    @PostMapping("/supplier-debts/pay")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<SupplierDebt>> payDebt(
            @Valid @RequestBody PaySupplierDebtRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Thanh toán công nợ thành công",
                financeService.paySupplierDebt(req, principal.getUsername())));
    }

    // ── ĐỐI SOÁT COD ─────────────────────────────────────────

    /** POST /finance/cod-reconciliation — Đối soát COD từ Excel (FIN-05) */
    @PostMapping("/cod-reconciliation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinanceService.CodReconciliationResult>> reconcileCOD(
            @RequestBody List<Map<String, Object>> body,
            @RequestParam UUID warehouseId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<FinanceService.CodReconciliationItem> items = body.stream().map(row ->
                new FinanceService.CodReconciliationItem(
                        (String) row.get("orderCode"),
                        new BigDecimal(row.get("amountReceived").toString()),
                        new BigDecimal(row.get("shippingFee").toString()),
                        (String) row.get("shippingProvider")
                )).toList();

        return ResponseEntity.ok(ApiResponse.ok(
                financeService.reconcileCOD(items, warehouseId, principal.getUsername())));
    }
}
