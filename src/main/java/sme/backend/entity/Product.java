package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    /**
     * ISBN-13 hoặc barcode tùy chỉnh.
     * Đây là key chính để quét mã vạch tại POS.
     */
    @Column(name = "isbn_barcode", unique = true, nullable = false, length = 50)
    private String isbnBarcode;

    @Column(unique = true, length = 100)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "retail_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal retailPrice;

    @Column(name = "wholesale_price", precision = 19, scale = 4)
    private BigDecimal wholesalePrice;

    /**
     * MAC (Moving Average Cost) - Giá vốn bình quân gia quyền.
     * KHÔNG bao giờ tự cập nhật trực tiếp.
     * Phải gọi recalculateMAC() sau mỗi lần nhập hàng.
     */
    @Column(name = "mac_price", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal macPrice = BigDecimal.ZERO;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(length = 50)
    @Builder.Default
    private String unit = "Cuốn";

    @Column(precision = 10, scale = 2)
    private BigDecimal weight;      // Gram

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Tính lại giá vốn bình quân sau khi nhập thêm hàng.
     * Công thức: MAC_mới = (Tồn_hiện_tại * MAC_cũ + Qty_nhập * Giá_nhập) / (Tồn + Qty_nhập)
     *
     * @param currentQty    Tồn kho hiện tại trước khi nhập
     * @param importQty     Số lượng nhập thêm
     * @param importPrice   Giá nhập của lô hàng mới
     */
    public void recalculateMAC(int currentQty, int importQty, BigDecimal importPrice) {
        if (importQty <= 0) return;

        BigDecimal totalExistingValue = this.macPrice.multiply(BigDecimal.valueOf(currentQty));
        BigDecimal totalNewValue = importPrice.multiply(BigDecimal.valueOf(importQty));
        BigDecimal totalQty = BigDecimal.valueOf((long) currentQty + importQty);

        if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
            this.macPrice = totalExistingValue
                    .add(totalNewValue)
                    .divide(totalQty, 4, RoundingMode.HALF_UP);
        }
    }
}
