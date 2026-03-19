package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.CashbookTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CashbookTransactionRepository
        extends JpaRepository<CashbookTransaction, UUID> {

    List<CashbookTransaction> findByShiftIdOrderByCreatedAtAsc(UUID shiftId);

    Page<CashbookTransaction> findByWarehouseIdAndFundTypeOrderByCreatedAtDesc(
            UUID warehouseId,
            CashbookTransaction.FundType fundType,
            Pageable pageable);

    // Số dư hiện tại của quỹ
    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN ct.transactionType = 'IN' THEN ct.amount
                 ELSE -ct.amount END
        ), 0)
        FROM CashbookTransaction ct
        WHERE ct.warehouseId = :wid
        AND ct.fundType = :fundType
        """)
    BigDecimal getCurrentBalance(@Param("wid")      UUID warehouseId,
                                 @Param("fundType") CashbookTransaction.FundType fundType);

    // Báo cáo sổ quỹ theo khoảng thời gian
    @Query("""
        SELECT ct FROM CashbookTransaction ct
        WHERE ct.warehouseId = :wid
        AND ct.createdAt BETWEEN :from AND :to
        ORDER BY ct.createdAt ASC
        """)
    List<CashbookTransaction> findByWarehouseAndDateRange(
            @Param("wid")  UUID warehouseId,
            @Param("from") Instant from,
            @Param("to")   Instant to);

    // Tổng thu/chi theo loại quỹ trong khoảng thời gian
    @Query(value = """
        SELECT
            fund_type,
            transaction_type,
            SUM(amount) AS total
        FROM cashbook_transactions
        WHERE warehouse_id = :wid
        AND created_at BETWEEN :from AND :to
        GROUP BY fund_type, transaction_type
        """, nativeQuery = true)
    List<Object[]> getCashflowSummary(@Param("wid")  UUID warehouseId,
                                      @Param("from") Instant from,
                                      @Param("to")   Instant to);
}
