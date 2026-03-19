package sme.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "customers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;  // MALE, FEMALE, OTHER

    @Column(name = "loyalty_points")
    @Builder.Default
    private Integer loyaltyPoints = 0;

    /**
     * Hạng thành viên tự động nâng cấp theo điểm tích lũy:
     * STANDARD: 0 - 499 điểm
     * SILVER:   500 - 1999 điểm
     * GOLD:     2000+ điểm
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_tier", length = 50)
    @Builder.Default
    private CustomerTier customerTier = CustomerTier.STANDARD;

    @Column(name = "total_spent", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    public enum CustomerTier {
        STANDARD, SILVER, GOLD
    }

    /**
     * Cộng điểm và tự động nâng hạng
     */
    public void addPoints(int points) {
        this.loyaltyPoints += points;
        updateTier();
    }

    /**
     * Trừ điểm (đổi thưởng)
     */
    public void deductPoints(int points) {
        if (this.loyaltyPoints < points) {
            throw new IllegalStateException("Điểm tích lũy không đủ: hiện có " + this.loyaltyPoints);
        }
        this.loyaltyPoints -= points;
        updateTier();
    }

    private void updateTier() {
        if (this.loyaltyPoints >= 2000) {
            this.customerTier = CustomerTier.GOLD;
        } else if (this.loyaltyPoints >= 500) {
            this.customerTier = CustomerTier.SILVER;
        } else {
            this.customerTier = CustomerTier.STANDARD;
        }
    }
}
