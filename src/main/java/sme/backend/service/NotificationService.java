package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sme.backend.entity.*;
import sme.backend.repository.NotificationRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NotificationService — gửi real-time notifications qua WebSocket (STOMP).
 *
 * Topics:
 *   /topic/warehouse/{warehouseId}/low-stock   → Tồn kho thấp
 *   /topic/warehouse/{warehouseId}/new-order   → Đơn hàng mới
 *   /topic/warehouse/{warehouseId}/shift-alert → Ca chờ duyệt
 *   /user/{username}/queue/notifications       → Notification cá nhân
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    // ─────────────────────────────────────────────────────────
    // LOW STOCK ALERT
    // ─────────────────────────────────────────────────────────
    @Async
    public void notifyLowStock(Inventory inventory) {
        String topic = "/topic/warehouse/" + inventory.getWarehouseId() + "/low-stock";
        Map<String, Object> payload = Map.of(
                "type",        "LOW_STOCK",
                "productId",   inventory.getProductId(),
                "warehouseId", inventory.getWarehouseId(),
                "quantity",    inventory.getQuantity(),
                "minQuantity", inventory.getMinQuantity()
        );
        messagingTemplate.convertAndSend(topic, payload);

        // Lưu vào DB để hiển thị khi reconnect
        Notification notification = Notification.builder()
                .type("LOW_STOCK")
                .title("⚠️ Cảnh báo tồn kho thấp")
                .message(String.format("Sản phẩm ID %s tại kho %s chỉ còn %d sản phẩm",
                        inventory.getProductId(), inventory.getWarehouseId(), inventory.getQuantity()))
                .payload(payload)
                .build();
        notificationRepository.save(notification);
        log.debug("Low stock alert sent for product={}", inventory.getProductId());
    }

    // ─────────────────────────────────────────────────────────
    // NEW ORDER
    // ─────────────────────────────────────────────────────────
    @Async
    public void notifyNewOrder(Order order, UUID warehouseId) {
        String topic = "/topic/warehouse/" + warehouseId + "/new-order";
        Map<String, Object> payload = Map.of(
                "type",      "NEW_ORDER",
                "orderId",   order.getId(),
                "orderCode", order.getCode(),
                "amount",    order.getFinalAmount(),
                "type_order", order.getType().name()
        );
        messagingTemplate.convertAndSend(topic, payload);
        log.debug("New order notification sent: order={}", order.getCode());
    }

    // ─────────────────────────────────────────────────────────
    // SHIFT CLOSED — cần Manager duyệt
    // ─────────────────────────────────────────────────────────
    @Async
    public void notifyShiftClosed(Shift shift) {
        String topic = "/topic/warehouse/" + shift.getWarehouseId() + "/shift-alert";
        Map<String, Object> payload = Map.of(
                "type",              "SHIFT_PENDING_APPROVAL",
                "shiftId",           shift.getId(),
                "cashierId",         shift.getCashierId(),
                "discrepancyAmount", shift.getDiscrepancyAmount() != null
                                        ? shift.getDiscrepancyAmount() : 0
        );
        messagingTemplate.convertAndSend(topic, payload);
        log.debug("Shift closed notification sent: shift={}", shift.getId());
    }

    // ─────────────────────────────────────────────────────────
    // TRANSFER ARRIVED
    // ─────────────────────────────────────────────────────────
    @Async
    public void notifyTransferArrived(UUID transferId, UUID toWarehouseId) {
        String topic = "/topic/warehouse/" + toWarehouseId + "/transfer";
        Map<String, Object> payload = Map.of(
                "type",       "TRANSFER_ARRIVED",
                "transferId", transferId
        );
        messagingTemplate.convertAndSend(topic, payload);
    }

    // ─────────────────────────────────────────────────────────
    // MARK AS READ
    // ─────────────────────────────────────────────────────────
    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
    }

    public List<Notification> getUnread(UUID userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
}
