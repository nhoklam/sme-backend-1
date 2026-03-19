package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_items")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InvoiceItem extends BaseSimpleEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Số lượng bán. Âm (-) nếu Invoice.type = RETURN */
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    /**
     * SNAPSHOT giá vốn tại thời điểm bán.
     * KHÔNG được lấy từ product.macPrice hiện tại.
     * Dùng để tính lợi nhuận gộp chính xác trong báo cáo.
     */
    @Column(name = "mac_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal macPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @PrePersist
    @PreUpdate
    public void computeSubtotal() {
        if (unitPrice != null && quantity != null) {
            this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
