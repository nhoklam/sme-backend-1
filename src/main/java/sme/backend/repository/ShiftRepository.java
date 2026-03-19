package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Shift;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    // Tìm ca đang OPEN của thu ngân → dùng khi tạo Invoice
    Optional<Shift> findByCashierIdAndStatus(UUID cashierId, Shift.ShiftStatus status);

    // Kiểm tra xem cashier có đang có ca mở không
    boolean existsByCashierIdAndStatus(UUID cashierId, Shift.ShiftStatus status);

    // Danh sách ca theo kho, sắp xếp mới nhất
    Page<Shift> findByWarehouseIdOrderByOpenedAtDesc(UUID warehouseId, Pageable pageable);

    // Ca chờ Manager duyệt
    List<Shift> findByWarehouseIdAndStatus(UUID warehouseId, Shift.ShiftStatus status);

    // Lấy ca gần nhất đã approved của cashier để tính theoretical cash tiếp theo
    @Query("""
        SELECT s FROM Shift s
        WHERE s.cashierId = :cashierId
        AND s.status = 'MANAGER_APPROVED'
        ORDER BY s.closedAt DESC
        """)
    List<Shift> findLatestApprovedByCashier(@Param("cashierId") UUID cashierId, Pageable pageable);

    // Tính tổng thu tiền mặt trong ca (dùng cho theoretical cash)
    @Query("""
        SELECT COALESCE(SUM(ct.amount), 0)
        FROM CashbookTransaction ct
        WHERE ct.shiftId = :shiftId
        AND ct.fundType = 'CASH_111'
        AND ct.transactionType = 'IN'
        """)
    java.math.BigDecimal sumCashInByShift(@Param("shiftId") UUID shiftId);

    @Query("""
        SELECT COALESCE(SUM(ct.amount), 0)
        FROM CashbookTransaction ct
        WHERE ct.shiftId = :shiftId
        AND ct.fundType = 'CASH_111'
        AND ct.transactionType = 'OUT'
        """)
    java.math.BigDecimal sumCashOutByShift(@Param("shiftId") UUID shiftId);
}
