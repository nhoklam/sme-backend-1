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

    // ĐÃ THÊM: Hỗ trợ Filter, Search theo Mã PO cho màn hình Quản lý
    @Query("""
        SELECT po FROM PurchaseOrder po
        WHERE (:warehouseId IS NULL OR po.warehouseId = :warehouseId)
        AND (:status IS NULL OR po.status = :status)
        AND (:keyword IS NULL OR :keyword = '' OR LOWER(po.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY po.createdAt DESC
        """)
    Page<PurchaseOrder> searchPurchaseOrders(
            @Param("warehouseId") UUID warehouseId,
            @Param("status") PurchaseOrder.PurchaseStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    Page<PurchaseOrder> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId, Pageable pageable);

    @Query("""
        SELECT po FROM PurchaseOrder po
        LEFT JOIN FETCH po.items
        WHERE po.id = :id
        """)
    Optional<PurchaseOrder> findByIdWithItems(@Param("id") UUID id);
}