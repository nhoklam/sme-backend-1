package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.entity.Inventory;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.InventoryTransactionRepository;
import sme.backend.security.UserPrincipal;
import sme.backend.service.InventoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository txnRepository;

    /** GET /inventory/warehouse/{wid} — Tồn kho theo kho */
    @GetMapping("/warehouse/{wid}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Inventory>>> getByWarehouse(@PathVariable UUID wid) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryRepository.findByWarehouseId(wid)));
    }

    /** GET /inventory/{productId}/warehouse/{wid} — Tồn kho 1 sản phẩm */
    @GetMapping("/{productId}/warehouse/{wid}")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Inventory>> getOne(
            @PathVariable UUID productId, @PathVariable UUID wid) {
        return ResponseEntity.ok(ApiResponse.ok(
                inventoryService.getInventory(productId, wid)));
    }

    /** GET /inventory/low-stock — Cảnh báo tồn kho thấp */
    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStock(
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID wid = principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getLowStockAlerts(wid)));
    }

    /** GET /inventory/{inventoryId}/transactions — Thẻ kho */
    @GetMapping("/{inventoryId}/transactions")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<?>> getTransactions(@PathVariable UUID inventoryId) {
        return ResponseEntity.ok(ApiResponse.ok(
                txnRepository.findByInventoryIdOrderByCreatedAtDesc(inventoryId)));
    }
}
