package sme.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameAndIsActiveTrue(String username);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    List<User> findByWarehouseIdAndIsActiveTrue(UUID warehouseId);

    List<User> findByRoleAndIsActiveTrue(User.UserRole role);

    @Query("SELECT u FROM User u WHERE u.isActive = true ORDER BY u.fullName")
    List<User> findAllActive();

    @Query("SELECT u FROM User u WHERE u.warehouseId = :wid " +
           "AND u.role = 'ROLE_CASHIER' AND u.isActive = true")
    List<User> findActiveCashiersByWarehouse(@Param("wid") UUID warehouseId);
}
