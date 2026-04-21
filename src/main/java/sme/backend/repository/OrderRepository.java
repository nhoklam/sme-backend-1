package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByCode(String code);
    boolean existsByCode(String code);

    @Query("""
        SELECT o FROM Order o
        WHERE (:wid IS NULL OR o.assignedWarehouseId = :wid)
        AND o.status IN ('PENDING','PACKING')
        ORDER BY o.createdAt ASC
        """)
    List<Order> findPendingOrdersByWarehouse(@Param("wid") UUID warehouseId);

    @Query("""
        SELECT o FROM Order o
        WHERE o.assignedWarehouseId = :wid
        AND o.type = 'BOPIS'
        AND o.status = 'PACKING'
        ORDER BY o.createdAt ASC
        """)
    List<Order> findBOPISReadyByWarehouse(@Param("wid") UUID warehouseId);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Order> findByAssignedWarehouseIdAndStatusOrderByCreatedAtDesc(UUID warehouseId, Order.OrderStatus status, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        WHERE o.paymentMethod = 'COD'
        AND o.status = 'DELIVERED'
        AND o.codReconciled = false
        """)
    List<Order> findUnreconciledCODOrders();

    List<Order> findByAssignedWarehouseIdIsNullAndStatus(Order.OrderStatus status);

    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.items
        WHERE o.id = :id
        """)
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    // ĐÃ SỬA BƯỚC 4: Thêm tham số `type` vào Query
    @Query("""
        SELECT o FROM Order o
        WHERE (:warehouseId IS NULL OR o.assignedWarehouseId = :warehouseId)
        AND (:status IS NULL OR o.status = :status)
        AND (:type IS NULL OR o.type = :type)
        AND (:keyword IS NULL OR :keyword = ''
             OR LOWER(o.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(o.shippingPhone) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(o.shippingName) LIKE LOWER(CONCAT('%', :keyword, '%'))
             )
        ORDER BY o.createdAt DESC
        """)
    Page<Order> searchOrders(@Param("warehouseId") UUID warehouseId,
                             @Param("status") Order.OrderStatus status,
                             @Param("type") Order.OrderType type,
                             @Param("keyword") String keyword,
                             Pageable pageable);
}