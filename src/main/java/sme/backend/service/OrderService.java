package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CreateOrderRequest;
import sme.backend.dto.response.OrderResponse;
import sme.backend.entity.*;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * OrderService - Xử lý đơn hàng Online.
 *
 * Core logic: Smart Order Routing
 * Thuật toán chọn kho đóng gói tối ưu:
 * 1. Ưu tiên kho cùng tỉnh/thành với địa chỉ giao hàng
 * 2. Trong cùng tỉnh, ưu tiên kho có nhiều hàng nhất
 * 3. Nếu không có kho nào trong tỉnh → chọn kho gần nhất có đủ hàng
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final CashbookTransactionRepository cashbookRepository;

    // ─────────────────────────────────────────────────────────
    // TẠO ĐƠN HÀNG (khách đặt từ Web)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {

        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", req.getCustomerId()));

        // 1. Load products + tính giá
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            // Kiểm tra tồn kho khả dụng toàn quốc
            int available = inventoryRepository.getTotalAvailableQuantity(product.getId());
            if (available < itemReq.getQuantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Sản phẩm '" + product.getName() + "' không đủ hàng. Tồn: " + available);
            }

            BigDecimal subtotal = product.getRetailPrice()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getRetailPrice())
                    .macPrice(product.getMacPrice())
                    .subtotal(subtotal)
                    .build();
            orderItems.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        // 2. Smart Order Routing → chọn kho tối ưu
        UUID assignedWarehouseId = routeOrder(req.getProvinceCode(), req.getItems());

        // 3. Tạo Order
        Order.OrderType orderType = "BOPIS".equalsIgnoreCase(req.getType())
                ? Order.OrderType.BOPIS : Order.OrderType.DELIVERY;

        Order order = Order.builder()
                .code(generateOrderCode())
                .customerId(customer.getId())
                .assignedWarehouseId(assignedWarehouseId)
                .type(orderType)
                .shippingName(req.getShippingName())
                .shippingPhone(req.getShippingPhone())
                .shippingAddress(req.getShippingAddress())
                .provinceCode(req.getProvinceCode())
                .totalAmount(totalAmount)
                .shippingFee(BigDecimal.ZERO) // Ghi chú: Có thể tích hợp API tính phí ship (GHTK/GHN) sau
                .finalAmount(totalAmount)
                .paymentMethod(req.getPaymentMethod())
                .paymentStatus(Order.PaymentStatus.UNPAID)
                .note(req.getNote())
                .build();

        orderItems.forEach(order::addItem);

        // 4. Giữ chỗ tồn kho ngay (nếu đã routing thành công)
        if (assignedWarehouseId != null) {
            for (CreateOrderRequest.OrderItemRequest itemReq : req.getItems()) {
                inventoryService.reserveForOnlineOrder(
                        itemReq.getProductId(), assignedWarehouseId,
                        itemReq.getQuantity(), order.getId(), "SYSTEM");
            }
        }

        order = orderRepository.save(order);

        // 5. Notify Manager của kho được chỉ định
        if (assignedWarehouseId != null) {
            notificationService.notifyNewOrder(order, assignedWarehouseId);
        }

        log.info("Order created: {} → routed to warehouse: {}", order.getCode(), assignedWarehouseId);
        return mapToResponse(order);
    }

    // ─────────────────────────────────────────────────────────
    // CHUYỂN TRẠNG THÁI ĐƠN HÀNG
    // ─────────────────────────────────────────────────────────
    @Transactional
    public OrderResponse updateStatus(UUID orderId, String newStatus,
                                      String note, String trackingCode,
                                      String shippingProvider, String changedBy) {

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        Order.OrderStatus status;
        try {
            status = Order.OrderStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_STATUS", "Trạng thái không hợp lệ: " + newStatus);
        }

        // Domain method validate transition
        order.transitionTo(status, note, changedBy);

        if (trackingCode != null)    order.setTrackingCode(trackingCode);
        if (shippingProvider != null) order.setShippingProvider(shippingProvider);

        // Khi chuyển sang PACKING → cập nhật packed info
        if (status == Order.OrderStatus.PACKING) {
            try {
                order.setPackedBy(UUID.fromString(changedBy));
            } catch (Exception ignored) {}
            order.setPackedAt(Instant.now());
        }

        // Khi chuyển sang SHIPPING → trừ kho thực
        if (status == Order.OrderStatus.SHIPPING && order.getAssignedWarehouseId() != null) {
            // ĐÃ SỬA: Lấy assignedWarehouseId ra biến final để dùng an toàn trong Lambda
            final UUID wid = order.getAssignedWarehouseId();
            order.getItems().forEach(item ->
                    inventoryService.confirmOnlineShipment(
                            item.getProductId(), wid,
                            item.getQuantity(), orderId, changedBy)
            );
        }

        // Khi hủy đơn → giải phóng reserved
        if (status == Order.OrderStatus.CANCELLED && order.getAssignedWarehouseId() != null) {
            // ĐÃ SỬA: Lấy assignedWarehouseId ra biến final để dùng an toàn trong Lambda
            final UUID wid = order.getAssignedWarehouseId();
            order.getItems().forEach(item ->
                    inventoryService.releaseReservation(
                            item.getProductId(), wid,
                            item.getQuantity(), orderId, changedBy)
            );
            order.setCancelledReason(note);
        }

        // Khi DELIVERED COD → ghi nhận doanh thu vào Cashbook
        if (status == Order.OrderStatus.DELIVERED
                && "COD".equals(order.getPaymentMethod())) {
            order.setPaymentStatus(Order.PaymentStatus.PAID);
            recordCODRevenue(order);
        }

        order = orderRepository.save(order);
        return mapToResponse(order);
    }

    // ─────────────────────────────────────────────────────────
    // SMART ORDER ROUTING ALGORITHM
    // ─────────────────────────────────────────────────────────
    private UUID routeOrder(String provinceCode,
                             List<CreateOrderRequest.OrderItemRequest> items) {
        List<Warehouse> activeWarehouses = warehouseRepository.findByIsActiveTrueOrderByName();
        if (activeWarehouses.isEmpty()) return null;

        // Ưu tiên kho cùng tỉnh
        List<Warehouse> sameProvince = activeWarehouses.stream()
                .filter(w -> provinceCode.equals(w.getProvinceCode()))
                .toList();

        List<Warehouse> candidates = sameProvince.isEmpty() ? activeWarehouses : sameProvince;

        // Tìm kho có đủ tồn kho cho tất cả items
        for (Warehouse warehouse : candidates) {
            boolean canFulfill = items.stream().allMatch(item -> {
                Optional<Inventory> inv = inventoryRepository
                        .findByProductIdAndWarehouseId(item.getProductId(), warehouse.getId());
                return inv.map(i -> i.getAvailableQuantity() >= item.getQuantity())
                        .orElse(false);
            });

            if (canFulfill) {
                log.info("Smart Routing: order routed to warehouse={} province={}",
                        warehouse.getName(), warehouse.getProvinceCode());
                return warehouse.getId();
            }
        }

        // Không kho nào đủ hàng toàn bộ → log warning, trả null (manual routing)
        log.warn("Smart Routing: no single warehouse can fulfill order for province={}. Manual routing required.", provinceCode);
        return null;
    }

    // ─────────────────────────────────────────────────────────
    // COD REVENUE RECORDING
    // ─────────────────────────────────────────────────────────
    private void recordCODRevenue(Order order) {
        if (order.getAssignedWarehouseId() == null) return;
        CashbookTransaction txn = CashbookTransaction.builder()
                .warehouseId(order.getAssignedWarehouseId())
                .fundType(CashbookTransaction.FundType.CASH_111)
                .transactionType(CashbookTransaction.TransactionType.IN)
                .referenceType("SALE_ONLINE")
                .referenceId(order.getId())
                .amount(order.getFinalAmount())
                .description("Thu COD đơn hàng #" + order.getCode())
                .createdBy("SYSTEM")
                .build();
        cashbookRepository.save(txn);
    }

    // ─────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(UUID warehouseId, Order.OrderStatus status, Pageable pageable) {
        if (warehouseId != null && status != null) {
            return orderRepository
                    .findByAssignedWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, status, pageable)
                    .map(this::mapToResponse);
        }
        return orderRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(UUID orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getPendingOrders(UUID warehouseId) {
        return orderRepository.findPendingOrdersByWarehouse(warehouseId)
                .stream().map(this::mapToResponse).toList();
    }

    // ─────────────────────────────────────────────────────────
    // MAPPER
    // ─────────────────────────────────────────────────────────
    private String generateOrderCode() {
        return "ORD-" + System.currentTimeMillis();
    }

    public OrderResponse mapToResponse(Order order) {
        List<OrderResponse.ItemResponse> items = order.getItems() == null ? List.of() :
                order.getItems().stream()
                        .map(i -> OrderResponse.ItemResponse.builder()
                                .productId(i.getProductId())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .subtotal(i.getSubtotal())
                                .build())
                        .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .code(order.getCode())
                .customerId(order.getCustomerId())
                .assignedWarehouseId(order.getAssignedWarehouseId())
                .status(order.getStatus().name())
                .type(order.getType().name())
                .shippingName(order.getShippingName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddress(order.getShippingAddress())
                .provinceCode(order.getProvinceCode())
                .totalAmount(order.getTotalAmount())
                .shippingFee(order.getShippingFee())
                .finalAmount(order.getFinalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus().name())
                .trackingCode(order.getTrackingCode())
                .shippingProvider(order.getShippingProvider())
                .codReconciled(order.getCodReconciled())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
    }
}