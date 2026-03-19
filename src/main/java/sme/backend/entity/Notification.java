package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification extends BaseSimpleEntity {

    @Column(name = "user_id")
    private UUID userId;

    /**
     * LOW_STOCK         → Tồn kho thấp
     * NEW_ORDER         → Đơn hàng mới
     * SHIFT_PENDING     → Ca chờ duyệt
     * COD_RECONCILED    → Đối soát COD xong
     * TRANSFER_ARRIVED  → Hàng chuyển kho đến nơi
     */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;
}
