package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "warehouses")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Warehouse extends BaseEntity {

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Mã tỉnh/thành phố theo tiêu chuẩn ĐVHCVN
     * Đây là "mỏ neo" cho thuật toán Smart Order Routing
     * VD: "79" = TP.HCM, "01" = Hà Nội
     */
    @Column(name = "province_code", nullable = false, length = 20)
    private String provinceCode;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
