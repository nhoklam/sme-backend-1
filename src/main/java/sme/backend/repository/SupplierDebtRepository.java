package sme.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.SupplierDebt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierDebtRepository extends JpaRepository<SupplierDebt, UUID> {

    Optional<SupplierDebt> findByPurchaseOrderId(UUID purchaseOrderId);

    List<SupplierDebt> findBySupplierIdAndStatusNot(UUID supplierId, SupplierDebt.DebtStatus status);

    List<SupplierDebt> findByStatus(SupplierDebt.DebtStatus status);

    @Query("""
        SELECT COALESCE(SUM(sd.totalDebt - sd.paidAmount), 0)
        FROM SupplierDebt sd
        WHERE sd.supplierId = :sid
        AND sd.status != 'PAID'
        """)
    BigDecimal getTotalOutstandingBySupplierId(@Param("sid") UUID supplierId);
}
