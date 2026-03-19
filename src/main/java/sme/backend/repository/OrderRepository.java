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

    // Đơn hàng mới đổ vào kho (PACKING queue)
    @Query("""
        SELECT o FROM Order o
        WHERE o.assignedWarehouseId = :wid
        AND o.status IN ('PENDING','PACKING')
        ORDER BY o.createdAt ASC
        """)
    List<Order> findPendingOrdersByWarehouse(@Param("wid") UUID warehouseId);

    // Đơn BOPIS cần bàn giao tại quầy
    @Query("""
        SELECT o FROM Order o
        WHERE o.assignedWarehouseId = :wid
        AND o.type = 'BOPIS'
        AND o.status = 'PACKING'
        ORDER BY o.createdAt ASC
        """)
    List<Order> findBOPISReadyByWarehouse(@Param("wid") UUID warehouseId);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Order> findByAssignedWarehouseIdAndStatusOrderByCreatedAtDesc(
            UUID warehouseId, Order.OrderStatus status, Pageable pageable);

    // Đơn COD chưa đối soát
    @Query("""
        SELECT o FROM Order o
        WHERE o.paymentMethod = 'COD'
        AND o.status = 'DELIVERED'
        AND o.codReconciled = false
        """)
    List<Order> findUnreconciledCODOrders();

    // Đơn hàng chờ routing (chưa được gán kho)
    List<Order> findByAssignedWarehouseIdIsNullAndStatus(Order.OrderStatus status);

    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.items
        LEFT JOIN FETCH o.statusHistory
        WHERE o.id = :id
        """)
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);
}
