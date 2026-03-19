package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.CheckoutRequest;
import sme.backend.dto.request.CloseShiftRequest;
import sme.backend.dto.request.OpenShiftRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.InvoiceResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.dto.response.ShiftResponse;
import sme.backend.exception.BusinessException;
import sme.backend.repository.InvoiceRepository;
import sme.backend.security.UserPrincipal;
import sme.backend.service.POSService;
import sme.backend.service.ShiftService;

import java.util.List;
import java.util.UUID;

/**
 * POSController — Module 0: Bán hàng tại quầy
 * Bảo mật: Backend tự động extract warehouseId từ JWT Token
 * Cashier không thể bán hàng của chi nhánh khác
 */
@RestController
@RequestMapping("/pos")
@RequiredArgsConstructor
public class POSController {

    private final ShiftService shiftService;
    private final POSService posService;
    private final InvoiceRepository invoiceRepository;

    // ── CA LÀM VIỆC ──────────────────────────────────────────

    /** POST /pos/shifts/open — POS-01: Mở ca */
    @PostMapping("/shifts/open")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER')")
    public ResponseEntity<ApiResponse<ShiftResponse>> openShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OpenShiftRequest req) {

        if (principal.getWarehouseId() == null) {
            throw new BusinessException("NO_WAREHOUSE", "Tài khoản chưa được gán vào chi nhánh");
        }
        ShiftResponse shift = shiftService.openShift(
                principal.getId(), principal.getWarehouseId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Mở ca thành công", shift));
    }

    /** POST /pos/shifts/close — POS-09: Đóng ca mù */
    @PostMapping("/shifts/close")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER')")
    public ResponseEntity<ApiResponse<ShiftResponse>> closeShift(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CloseShiftRequest req) {
        ShiftResponse shift = shiftService.closeShift(principal.getId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Đóng ca thành công", shift));
    }

    /** GET /pos/shifts/current — Lấy thông tin ca đang mở */
    @GetMapping("/shifts/current")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER')")
    public ResponseEntity<ApiResponse<ShiftResponse>> getCurrentShift(
            @AuthenticationPrincipal UserPrincipal principal) {
        var shift = shiftService.getOpenShiftByCashier(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(shiftService.mapToResponse(shift)));
    }

    /** GET /pos/shifts/pending — Ca chờ Manager duyệt */
    @GetMapping("/shifts/pending")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ShiftResponse>>> getPendingShifts(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID warehouseId = principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getPendingShifts(warehouseId)));
    }

    /** POST /pos/shifts/{id}/approve — Duyệt chốt ca */
    @PostMapping("/shifts/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<ShiftResponse>> approveShift(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ShiftResponse shift = shiftService.approveShift(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok("Duyệt ca thành công", shift));
    }

    // ── THANH TOÁN ────────────────────────────────────────────

    /**
     * POST /pos/checkout — POS-04, POS-05: Thanh toán đa phương thức
     * Toàn bộ xử lý là 1 transaction ACID
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> checkout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CheckoutRequest req) {

        if (principal.getWarehouseId() == null) {
            throw new BusinessException("NO_WAREHOUSE", "Tài khoản chưa được gán chi nhánh");
        }
        InvoiceResponse invoice = posService.checkout(
                req, principal.getId(), principal.getWarehouseId());
        return ResponseEntity.ok(ApiResponse.ok("Thanh toán thành công", invoice));
    }

    /** GET /pos/invoices/{id} — Chi tiết hóa đơn (in hóa đơn) */
    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getInvoice(@PathVariable UUID id) {
        var invoice = invoiceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new sme.backend.exception.ResourceNotFoundException("Invoice", id));
        // Map to response inline (simple version)
        return ResponseEntity.ok(ApiResponse.ok(
                InvoiceResponse.builder()
                        .id(invoice.getId())
                        .code(invoice.getCode())
                        .type(invoice.getType().name())
                        .totalAmount(invoice.getTotalAmount())
                        .discountAmount(invoice.getDiscountAmount())
                        .finalAmount(invoice.getFinalAmount())
                        .pointsUsed(invoice.getPointsUsed())
                        .pointsEarned(invoice.getPointsEarned())
                        .createdAt(invoice.getCreatedAt())
                        .build()
        ));
    }

    /** GET /pos/invoices — Danh sách hóa đơn theo ca */
    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceResponse>>> getInvoicesByShift(
            @RequestParam UUID shiftId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var paged = invoiceRepository.findByShiftIdOrderByCreatedAtDesc(
                shiftId, PageRequest.of(page, size));
        // Map entities to response
        var mapped = paged.map(inv -> InvoiceResponse.builder()
                .id(inv.getId())
                .code(inv.getCode())
                .type(inv.getType().name())
                .totalAmount(inv.getTotalAmount())
                .finalAmount(inv.getFinalAmount())
                .customerId(inv.getCustomerId())
                .createdAt(inv.getCreatedAt())
                .build());
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(mapped)));
    }
}
