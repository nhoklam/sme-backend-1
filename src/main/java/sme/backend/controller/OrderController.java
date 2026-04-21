package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.CreateOrderRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.OrderResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.entity.Order;
import sme.backend.entity.User;
import sme.backend.security.UserPrincipal;
import sme.backend.service.OrderService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(orderService.createOrder(req)));
    }

    // ĐÃ SỬA BƯỚC 4: Nhận thêm param `type`
    @GetMapping
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword, 
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID warehouseId) {

        UUID wid = (principal.getRole() == User.UserRole.ROLE_ADMIN) && warehouseId != null ? warehouseId : principal.getWarehouseId();

        Order.OrderStatus orderStatus = null;
        if (status != null && !status.isBlank() && !status.equals("ALL")) {
            try { orderStatus = Order.OrderStatus.valueOf(status.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }

        Order.OrderType orderType = null;
        if (type != null && !type.isBlank() && !type.equals("ALL")) {
            try { orderType = Order.OrderType.valueOf(type.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(orderService.getOrders(wid, orderStatus, orderType, keyword, pageable))));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getPendingOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID warehouseId) {
        UUID wid = (principal.getRole() == User.UserRole.ROLE_ADMIN) && warehouseId != null ? warehouseId : principal.getWarehouseId();
        return ResponseEntity.ok(ApiResponse.ok(orderService.getPendingOrders(wid)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrderDetail(id)));
    }
    @PostMapping("/suggest-branch")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> suggestBranchForOrder(@RequestBody CreateOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.suggestBranchesForOrder(req.getProvinceCode(), req.getItems())));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String newStatus      = body.get("status");
        String note           = body.get("note");
        String trackingCode   = body.get("trackingCode");
        String shippingProvider = body.get("shippingProvider");

        OrderResponse updated = orderService.updateStatus(id, newStatus, note, trackingCode, shippingProvider, principal.getId().toString());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái thành công", updated));
    }
}