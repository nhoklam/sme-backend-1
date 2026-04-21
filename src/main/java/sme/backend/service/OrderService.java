package sme.backend.service;

import jakarta.persistence.EntityManager;
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
import java.util.stream.Collectors;

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
    private final InternalTransferRepository transferRepository;
    
    // ĐÃ THÊM: Dùng EntityManager để trực tiếp gọi vào Database nếu cần lấy ID User
    private final EntityManager entityManager; 

    // ĐÃ SỬA: Hàm tự động dò tìm User ID của người đang đăng nhập cực kỳ an toàn
    private UUID getCurrentUserIdSafe() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "Không tìm thấy thông tin đăng nhập.");
        }

        // LỚP BẢO VỆ 1: Lấy ID trực tiếp từ Class UserPrincipal thông qua Reflection
        try {
            Object principal = auth.getPrincipal();
            java.lang.reflect.Method method = principal.getClass().getMethod("getId");
            Object id = method.invoke(principal);
            if (id instanceof UUID) return (UUID) id;
            if (id != null) return UUID.fromString(id.toString());
        } catch (Exception e) {
            log.warn("Không lấy được ID qua Token, chuyển sang quét DB bằng username...");
        }

        // LỚP BẢO VỆ 2: Query thẳng vào DB tìm UUID bằng username (Ví dụ: "admin")
        try {
            String username = auth.getName();
            if (username != null && !username.isEmpty()) {
                List<?> results = entityManager.createNativeQuery("SELECT id FROM users WHERE username = :username LIMIT 1")
                        .setParameter("username", username)
                        .getResultList();
                if (!results.isEmpty() && results.get(0) != null) {
                    return UUID.fromString(results.get(0).toString());
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi khi query bảng users: {}", e.getMessage());
        }

        throw new BusinessException("USER_NOT_FOUND", "Hệ thống không lấy được ID của bạn. Hãy chắc chắn bảng 'users' có dữ liệu tài khoản này.");
    }

    // ─────────────────────────────────────────────────────────
    // TẠO ĐƠN HÀNG (TÍCH HỢP TỰ ĐỘNG GOM HÀNG)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        Customer customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", req.getCustomerId()));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Bắt buộc phải có UserID hợp lệ thì Database mới cho tạo Phiếu Chuyển Kho
        UUID currentUserId = getCurrentUserIdSafe();

        for (CreateOrderRequest.OrderItemRequest itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId()).orElseThrow();
            
            Integer totalAvailObj = inventoryRepository.getTotalAvailableQuantity(product.getId());
            int available = totalAvailObj != null ? totalAvailObj : 0;
            
            if (available < itemReq.getQuantity()) {
                throw new BusinessException("INSUFFICIENT_STOCK", "Sản phẩm '" + product.getName() + "' không đủ tồn kho trên toàn hệ thống.");
            }
            BigDecimal subtotal = product.getRetailPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            orderItems.add(OrderItem.builder().productId(product.getId()).quantity(itemReq.getQuantity())
                    .unitPrice(product.getRetailPrice()).macPrice(product.getMacPrice()).subtotal(subtotal).build());
            totalAmount = totalAmount.add(subtotal);
        }

        UUID assignedWarehouseId = req.getAssignedWarehouseId();
        Map<String, Object> chosenPlan = null;
        
        List<Map<String, Object>> plans = suggestBranchesForOrder(req.getProvinceCode(), req.getItems());
        if (plans.isEmpty()) throw new BusinessException("NO_WAREHOUSE", "Không có kho nào đáp ứng được đơn hàng này.");

        if (assignedWarehouseId != null) {
            chosenPlan = plans.stream().filter(p -> p.get("warehouseId").equals(req.getAssignedWarehouseId())).findFirst().orElse(null);
        } else {
            chosenPlan = plans.get(0); 
            assignedWarehouseId = (UUID) chosenPlan.get("warehouseId");
        }
        
        if (chosenPlan == null) throw new BusinessException("INVALID_WAREHOUSE", "Kho được chọn không hợp lệ.");

        Order.OrderType orderType = "BOPIS".equalsIgnoreCase(req.getType()) ? Order.OrderType.BOPIS : Order.OrderType.DELIVERY;
        boolean isReadyToShip = (Boolean) chosenPlan.get("isReadyToShip");

        Order order = Order.builder()
                .code(generateOrderCode()).customerId(customer.getId()).assignedWarehouseId(assignedWarehouseId)
                .type(orderType).shippingName(req.getShippingName()).shippingPhone(req.getShippingPhone())
                .shippingAddress(req.getShippingAddress()).provinceCode(req.getProvinceCode()).totalAmount(totalAmount)
                .shippingFee(BigDecimal.ZERO).finalAmount(totalAmount).paymentMethod(req.getPaymentMethod())
                .paymentStatus(Order.PaymentStatus.UNPAID).note(req.getNote())
                .status(isReadyToShip ? Order.OrderStatus.PENDING : Order.OrderStatus.WAITING_FOR_CONSOLIDATION)
                .build();
        orderItems.forEach(order::addItem);
        order = orderRepository.save(order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> availableItems = (List<Map<String, Object>>) chosenPlan.get("availableItems");
        for (Map<String, Object> avItem : availableItems) {
            inventoryService.reserveForOnlineOrder((UUID) avItem.get("productId"), assignedWarehouseId, (Integer) avItem.get("quantity"), order.getId(), "SYSTEM");
        }

        if (!isReadyToShip) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transferReqs = (List<Map<String, Object>>) chosenPlan.get("transferRequirements");
            
            Map<UUID, List<Map<String, Object>>> transfersBySource = transferReqs.stream().collect(Collectors.groupingBy(reqMap -> (UUID) reqMap.get("fromWarehouseId")));

            for (Map.Entry<UUID, List<Map<String, Object>>> entry : transfersBySource.entrySet()) {
                UUID sourceWarehouseId = entry.getKey();
                
                InternalTransfer transfer = InternalTransfer.builder()
                        .code("TRF-AUTO-" + System.currentTimeMillis() + "-" + sourceWarehouseId.toString().substring(0, 4))
                        .fromWarehouseId(sourceWarehouseId).toWarehouseId(assignedWarehouseId)
                        .createdByUserId(currentUserId) // ĐÃ FIX TẠI ĐÂY: Truyền ID thực tế
                        .status(InternalTransfer.TransferStatus.DRAFT)
                        .referenceOrderId(order.getId())
                        .note("Tự động tạo - Gom hàng cho Đơn #" + order.getCode())
                        .build();

                if (transfer.getItems() == null) {
                    transfer.setItems(new ArrayList<>());
                }

                for (Map<String, Object> reqItem : entry.getValue()) {
                    UUID pId = (UUID) reqItem.get("productId");
                    int qty = (Integer) reqItem.get("quantity");
                    transfer.addItem(TransferItem.builder().productId(pId).quantity(qty).build());
                    
                    inventoryService.reserveForOnlineOrder(pId, sourceWarehouseId, qty, order.getId(), "SYSTEM_CONSOLIDATION");
                }
                transferRepository.save(transfer);
                notificationService.notifyTransferArrived(transfer.getId(), sourceWarehouseId);
            }
        }

        notificationService.notifyNewOrder(order, assignedWarehouseId);
        log.info("Order created: {} → status: {}", order.getCode(), order.getStatus());
        return mapToResponse(order);
    }

    // ─────────────────────────────────────────────────────────
    // THUẬT TOÁN GỢI Ý KẾ HOẠCH GOM HÀNG 
    // ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> suggestBranchesForOrder(String provinceCode, List<CreateOrderRequest.OrderItemRequest> items) {
        if (items == null || items.isEmpty()) return List.of();

        List<Warehouse> activeWarehouses = warehouseRepository.findByIsActiveTrueOrderByName();
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        List<UUID> productIds = items.stream().map(CreateOrderRequest.OrderItemRequest::getProductId).toList();
        
        List<Inventory> allInventories = inventoryRepository.findByProductIdIn(productIds); 
        
        Map<UUID, Map<UUID, Integer>> stockMatrix = new HashMap<>();
        for (Inventory inv : allInventories) {
            stockMatrix.computeIfAbsent(inv.getWarehouseId(), k -> new HashMap<>())
                       .put(inv.getProductId(), inv.getAvailableQuantity());
        }

        for (Warehouse targetWarehouse : activeWarehouses) {
            boolean isSameProvince = provinceCode != null && provinceCode.equals(targetWarehouse.getProvinceCode());
            
            List<Map<String, Object>> availableItems = new ArrayList<>();
            List<Map<String, Object>> transferRequirements = new ArrayList<>();
            boolean isReadyToShip = true;
            
            Map<UUID, Integer> targetStock = stockMatrix.getOrDefault(targetWarehouse.getId(), Collections.emptyMap());

            for (CreateOrderRequest.OrderItemRequest item : items) {
                int requiredQty = item.getQuantity();
                int currentStock = targetStock.getOrDefault(item.getProductId(), 0);

                if (currentStock >= requiredQty) {
                    availableItems.add(Map.of("productId", item.getProductId(), "quantity", requiredQty));
                } else {
                    isReadyToShip = false;
                    if (currentStock > 0) {
                        availableItems.add(Map.of("productId", item.getProductId(), "quantity", currentStock));
                    }
                    int missingQty = requiredQty - currentStock;
                    
                    int remainingToFind = missingQty;
                    for (Warehouse sourceWarehouse : activeWarehouses) {
                        if (sourceWarehouse.getId().equals(targetWarehouse.getId())) continue;
                        if (remainingToFind <= 0) break;

                        Map<UUID, Integer> sourceStockMap = stockMatrix.getOrDefault(sourceWarehouse.getId(), Collections.emptyMap());
                        int sourceStock = sourceStockMap.getOrDefault(item.getProductId(), 0);
                                
                        if (sourceStock > 0) {
                            int takeQty = Math.min(sourceStock, remainingToFind);
                            remainingToFind -= takeQty;
                            
                            Product prod = productRepository.findById(item.getProductId()).orElseThrow();
                            transferRequirements.add(Map.of(
                                "fromWarehouseId", sourceWarehouse.getId(),
                                "fromWarehouseName", sourceWarehouse.getName(),
                                "productId", item.getProductId(),
                                "productName", prod.getName(),
                                "quantity", takeQty
                            ));
                        }
                    }
                    if (remainingToFind > 0) {
                        isReadyToShip = false;
                        transferRequirements.clear(); 
                        break; 
                    }
                }
            }

            if (!isReadyToShip && transferRequirements.isEmpty()) continue;

            Map<String, Object> plan = new HashMap<>();
            plan.put("warehouseId", targetWarehouse.getId());
            plan.put("warehouseName", targetWarehouse.getName());
            plan.put("isSameProvince", isSameProvince);
            plan.put("isReadyToShip", isReadyToShip);
            plan.put("availableItems", availableItems);
            plan.put("transferRequirements", transferRequirements);
            
            int score = 0;
            if (isReadyToShip) score += 1000;
            if (isSameProvince) score += 500;
            score -= transferRequirements.size() * 10; 
            plan.put("sortScore", score);
            
            suggestions.add(plan);
        }

        suggestions.sort((a, b) -> Integer.compare((Integer) b.get("sortScore"), (Integer) a.get("sortScore")));

        return suggestions;
    }

    // ─────────────────────────────────────────────────────────
    // CÁC HÀM CÒN LẠI 
    // ─────────────────────────────────────────────────────────
    @Transactional
    public OrderResponse updateStatus(UUID orderId, String newStatus, String note, String trackingCode, String shippingProvider, String changedBy) {
        Order order = orderRepository.findByIdWithDetails(orderId).orElseThrow();
        Order.OrderStatus status = Order.OrderStatus.valueOf(newStatus.toUpperCase());
        order.transitionTo(status, note, changedBy);

        if (trackingCode != null) order.setTrackingCode(trackingCode);
        if (shippingProvider != null) order.setShippingProvider(shippingProvider);

        if (status == Order.OrderStatus.PACKING) {
            try { order.setPackedBy(UUID.fromString(changedBy)); } catch (Exception ignored) {}
            order.setPackedAt(Instant.now());
        }

        if (status == Order.OrderStatus.SHIPPING && order.getAssignedWarehouseId() != null) {
            order.getItems().forEach(item -> inventoryService.confirmOnlineShipment(item.getProductId(), order.getAssignedWarehouseId(), item.getQuantity(), orderId, changedBy));
        }

        if (status == Order.OrderStatus.CANCELLED && order.getAssignedWarehouseId() != null) {
            order.getItems().forEach(item -> inventoryService.releaseReservation(item.getProductId(), order.getAssignedWarehouseId(), item.getQuantity(), orderId, changedBy));
            order.setCancelledReason(note);
        }

        if (status == Order.OrderStatus.DELIVERED && "COD".equals(order.getPaymentMethod())) {
            order.setPaymentStatus(Order.PaymentStatus.PAID);
            recordCODRevenue(order);
        }

        return mapToResponse(orderRepository.save(order));
    }

    private void recordCODRevenue(Order order) {
        if (order.getAssignedWarehouseId() == null) return;
        cashbookRepository.save(CashbookTransaction.builder().warehouseId(order.getAssignedWarehouseId()).fundType(CashbookTransaction.FundType.CASH_111).transactionType(CashbookTransaction.TransactionType.IN).referenceType("SALE_ONLINE").referenceId(order.getId()).amount(order.getFinalAmount()).description("Thu COD đơn hàng #" + order.getCode()).createdBy("SYSTEM").build());
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(UUID warehouseId, Order.OrderStatus status, Order.OrderType type, String keyword, Pageable pageable) {
        return orderRepository.searchOrders(warehouseId, status, type, keyword, pageable).map(this::mapToSimpleResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(UUID orderId) {
        return mapToResponse(orderRepository.findByIdWithDetails(orderId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getPendingOrders(UUID warehouseId) {
        return orderRepository.findPendingOrdersByWarehouse(warehouseId).stream().map(this::mapToSimpleResponse).toList();
    }

    private String generateOrderCode() { return "ORD-" + System.currentTimeMillis(); }

    public OrderResponse mapToSimpleResponse(Order order) {
        String custName = "Khách lẻ", custPhone = null;
        if (order.getCustomerId() != null) {
            var customer = customerRepository.findById(order.getCustomerId()).orElse(null);
            if (customer != null) { custName = customer.getFullName(); custPhone = customer.getPhoneNumber(); }
        }
        String warehouseName = null;
        if (order.getAssignedWarehouseId() != null) {
            warehouseName = warehouseRepository.findById(order.getAssignedWarehouseId()).map(Warehouse::getName).orElse(null);
        }
        return OrderResponse.builder().id(order.getId()).code(order.getCode()).customerId(order.getCustomerId()).customerName(custName).customerPhone(custPhone).assignedWarehouseId(order.getAssignedWarehouseId()).assignedWarehouseName(warehouseName).status(order.getStatus() != null ? order.getStatus().name() : null).type(order.getType() != null ? order.getType().name() : null).shippingName(order.getShippingName()).shippingPhone(order.getShippingPhone()).shippingAddress(order.getShippingAddress()).provinceCode(order.getProvinceCode()).totalAmount(order.getTotalAmount()).shippingFee(order.getShippingFee()).discountAmount(order.getDiscountAmount()).finalAmount(order.getFinalAmount()).paymentMethod(order.getPaymentMethod()).paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null).trackingCode(order.getTrackingCode()).shippingProvider(order.getShippingProvider()).codReconciled(order.getCodReconciled()).note(order.getNote()).cancelledReason(order.getCancelledReason()).packedBy(order.getPackedBy()).packedAt(order.getPackedAt()).createdAt(order.getCreatedAt()).updatedAt(order.getUpdatedAt()).items(List.of()).statusHistory(List.of()).build();
    }

    public OrderResponse mapToResponse(Order order) {
        String custName = "Khách lẻ", custPhone = null;
        if (order.getCustomerId() != null) {
            var customer = customerRepository.findById(order.getCustomerId()).orElse(null);
            if (customer != null) { custName = customer.getFullName(); custPhone = customer.getPhoneNumber(); }
        }
        String warehouseName = null;
        if (order.getAssignedWarehouseId() != null) {
            warehouseName = warehouseRepository.findById(order.getAssignedWarehouseId()).map(Warehouse::getName).orElse(null);
        }
        List<OrderResponse.ItemResponse> items = order.getItems() == null ? List.of() :
                order.getItems().stream().map(i -> {
                    var product = productRepository.findById(i.getProductId()).orElse(null);
                    return OrderResponse.ItemResponse.builder().productId(i.getProductId()).productName(product != null ? product.getName() : null).isbnBarcode(product != null ? product.getIsbnBarcode() : null).quantity(i.getQuantity()).unitPrice(i.getUnitPrice()).subtotal(i.getSubtotal()).build();
                }).toList();

        List<OrderResponse.StatusHistoryResponse> history = order.getStatusHistory() == null ? List.of() :
                order.getStatusHistory().stream().map(h -> OrderResponse.StatusHistoryResponse.builder().oldStatus(h.getOldStatus()).newStatus(h.getNewStatus()).note(h.getNote()).changedBy(h.getChangedBy()).createdAt(h.getCreatedAt()).build()).toList();

        return OrderResponse.builder().id(order.getId()).code(order.getCode()).customerId(order.getCustomerId()).customerName(custName).customerPhone(custPhone).assignedWarehouseId(order.getAssignedWarehouseId()).assignedWarehouseName(warehouseName).status(order.getStatus() != null ? order.getStatus().name() : null).type(order.getType() != null ? order.getType().name() : null).shippingName(order.getShippingName()).shippingPhone(order.getShippingPhone()).shippingAddress(order.getShippingAddress()).provinceCode(order.getProvinceCode()).totalAmount(order.getTotalAmount()).shippingFee(order.getShippingFee()).discountAmount(order.getDiscountAmount()).finalAmount(order.getFinalAmount()).paymentMethod(order.getPaymentMethod()).paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null).trackingCode(order.getTrackingCode()).shippingProvider(order.getShippingProvider()).codReconciled(order.getCodReconciled()).note(order.getNote()).cancelledReason(order.getCancelledReason()).packedBy(order.getPackedBy()).packedAt(order.getPackedAt()).createdAt(order.getCreatedAt()).updatedAt(order.getUpdatedAt()).items(items).statusHistory(history).build();
    }
}