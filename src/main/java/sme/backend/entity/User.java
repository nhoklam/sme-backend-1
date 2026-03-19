package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User extends BaseEntity {

    /**
     * Gắn nhân viên với 1 kho cụ thể.
     * ADMIN có warehouse_id = NULL (toàn quyền).
     * MANAGER/CASHIER bị giới hạn theo warehouse_id này.
     */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    /**
     * Role cố định theo RBAC.
     * ROLE_ADMIN: Toàn quyền.
     * ROLE_MANAGER: Quản lý kho được phân công.
     * ROLE_CASHIER: Chỉ bán hàng POS tại kho được phân công.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public enum UserRole {
        ROLE_ADMIN,
        ROLE_MANAGER,
        ROLE_CASHIER
    }
}
