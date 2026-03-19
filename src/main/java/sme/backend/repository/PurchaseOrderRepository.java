package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.PurchaseOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByCode(String code);

    boolean existsByCode(String code);

    Page<PurchaseOrder> findByWarehouseIdOrderByCreatedAtDesc(UUID warehouseId, Pageable pageable);

    Page<PurchaseOrder> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId, Pageable pageable);

    List<PurchaseOrder> findByStatus(PurchaseOrder.PurchaseStatus status);

    @Query("""
        SELECT po FROM PurchaseOrder po
        LEFT JOIN FETCH po.items
        WHERE po.id = :id
        """)
    Optional<PurchaseOrder> findByIdWithItems(@Param("id") UUID id);
}
