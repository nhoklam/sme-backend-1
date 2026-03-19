package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.CreatePurchaseOrderRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.entity.PurchaseOrder;
import sme.backend.security.UserPrincipal;
import sme.backend.service.PurchaseService;

import java.util.UUID;

@RestController
@RequestMapping("/purchase-orders")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    /** POST /purchase-orders */
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrder>> create(
            @Valid @RequestBody CreatePurchaseOrderRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        purchaseService.createPurchaseOrder(req, principal.getId())));
    }

    /** POST /purchase-orders/{id}/approve */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrder>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Duyệt phiếu nhập kho thành công",
                purchaseService.approvePurchaseOrder(id, principal.getId())));
    }

    /** POST /purchase-orders/{id}/cancel */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrder>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.cancelPurchaseOrder(id, reason)));
    }

    /** GET /purchase-orders */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrder>>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID wid = principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(purchaseService.getByWarehouse(
                        wid, PageRequest.of(page, size)))));
    }

    /** GET /purchase-orders/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PurchaseOrder>> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseService.getById(id)));
    }
}
