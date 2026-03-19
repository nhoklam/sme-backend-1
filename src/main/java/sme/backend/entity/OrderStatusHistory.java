package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_status_history")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OrderStatusHistory extends BaseSimpleEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "old_status", length = 50)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "changed_by", length = 100)
    private String changedBy;
}
