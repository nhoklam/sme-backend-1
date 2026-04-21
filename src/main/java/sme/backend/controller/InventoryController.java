package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.AdjustInventoryRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.LowStockItem;
import sme.backend.dto.response.PageResponse;
import sme.backend.dto.response.InventoryResponse;
import sme.backend.dto.response.InventoryTransactionResponse;
import sme.backend.entity.Inventory;
import sme.backend.entity.User;
import sme.backend.repository.InventoryRepository;
import sme.backend.security.UserPrincipal;
import sme.backend.service.InventoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;

    @GetMapping("/warehouse/{warehouseId}/search")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<PageResponse<InventoryResponse>>> searchInventory(
            @PathVariable UUID warehouseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        UUID wid = (principal.getRole() == User.UserRole.ROLE_ADMIN) && warehouseId != null ? warehouseId : principal.getWarehouseId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "p.createdAt"));
        
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
                inventoryService.searchInventory(wid, keyword, categoryId, status, pageable))));
    }

    @GetMapping("/{productId}/warehouse/{warehouseId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getOne(
            @PathVariable UUID productId,
            @PathVariable UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getInventoryByProduct(warehouseId, productId)));
    }

    @GetMapping("/{inventoryId}/transactions")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> getTransactions(
            @PathVariable UUID inventoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryTransactionResponse> txns = inventoryService.getTransactionsWithReferenceCode(inventoryId, pageable);
        
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(txns)));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<LowStockItem>>> getLowStock(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID warehouseId) {
        UUID wid = (principal.getRole() == User.UserRole.ROLE_ADMIN) && warehouseId != null ? warehouseId : principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryRepository.findLowStockWithNameByWarehouse(wid)));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> adjustInventory(
            @Valid @RequestBody AdjustInventoryRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        inventoryService.adjustInventory(req, UUID.randomUUID(), principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Điều chỉnh tồn kho thành công", null));
    }

    // ====================================================================================
    // KHÔI PHỤC LẠI NGUYÊN BẢN: Nhận inventoryId
    // ====================================================================================
    @PutMapping("/{inventoryId}/min-quantity")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> updateMinQuantity(
            @PathVariable UUID inventoryId,
            @RequestBody java.util.Map<String, Integer> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        Integer minQty = body.get("minQuantity");
        if (minQty == null) {
            throw new sme.backend.exception.BusinessException("INVALID_INPUT", "Vui lòng cung cấp minQuantity");
        }
        inventoryService.updateMinQuantity(inventoryId, minQty, principal.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật định mức an toàn thành công", null));
    }
}