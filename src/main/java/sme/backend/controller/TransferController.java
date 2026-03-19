package sme.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.entity.InternalTransfer;
import sme.backend.security.UserPrincipal;
import sme.backend.service.TransferService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /** POST /transfers — Tạo phiếu chuyển kho */
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<InternalTransfer>> create(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID fromWid = UUID.fromString((String) body.get("fromWarehouseId"));
        UUID toWid   = UUID.fromString((String) body.get("toWarehouseId"));
        String note  = (String) body.get("note");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<TransferService.TransferItemRequest> items = rawItems.stream()
                .map(i -> new TransferService.TransferItemRequest(
                        UUID.fromString((String) i.get("productId")),
                        Integer.parseInt(i.get("quantity").toString())
                )).toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.created(transferService.createTransfer(
                        fromWid, toWid, items, note, principal.getId())));
    }

    /** GET /transfers — Danh sách phiếu chuyển kho theo kho */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<InternalTransfer>>> getAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID wid = principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
                transferService.getByWarehouse(wid, PageRequest.of(page, size)))));
    }

    /** GET /transfers/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<InternalTransfer>> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.getById(id)));
    }

    /** POST /transfers/{id}/dispatch — Xuất kho */
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<InternalTransfer>> dispatch(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Xuất kho thành công",
                transferService.dispatch(id, principal.getId())));
    }

    /** POST /transfers/{id}/receive — Nhận hàng */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<InternalTransfer>> receive(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Nhận hàng thành công",
                transferService.receive(id, principal.getId())));
    }
}
