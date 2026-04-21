package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.NotAudited;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Audited
@AuditTable("users_audit") // <-- THÊM DÒNG NÀY
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User extends BaseEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @NotAudited
    @Column(name = "pos_settings", columnDefinition = "TEXT")
    private String posSettings;

    public enum UserRole {
        ROLE_ADMIN,
        ROLE_MANAGER,
        ROLE_CASHIER
    }
}