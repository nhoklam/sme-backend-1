package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByCode(String code);

    boolean existsByCode(String code);

    Page<Invoice> findByShiftIdOrderByCreatedAtDesc(UUID shiftId, Pageable pageable);

    Page<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    // Lấy hóa đơn với items (tránh N+1 query)
    @Query("""
        SELECT DISTINCT i FROM Invoice i
        LEFT JOIN FETCH i.items
        LEFT JOIN FETCH i.payments
        WHERE i.id = :id
        """)
    Optional<Invoice> findByIdWithDetails(@Param("id") UUID id);

    // Doanh thu theo ca
    @Query("""
        SELECT COALESCE(SUM(i.finalAmount), 0) FROM Invoice i
        WHERE i.shiftId = :shiftId AND i.type = 'SALE'
        """)
    BigDecimal sumRevenueByShift(@Param("shiftId") UUID shiftId);

    // Báo cáo doanh thu theo khoảng thời gian và kho
    @Query(value = """
        SELECT
            DATE_TRUNC(:period, i.created_at) AS period,
            COUNT(i.id)                        AS invoice_count,
            SUM(i.final_amount)                AS revenue,
            SUM(ii.quantity * ii.mac_price)    AS cogs,
            SUM(i.final_amount) - SUM(ii.quantity * ii.mac_price) AS gross_profit
        FROM invoices i
        JOIN invoice_items ii ON ii.invoice_id = i.id
        JOIN shifts s ON s.id = i.shift_id
        WHERE s.warehouse_id = :wid
        AND i.type = 'SALE'
        AND i.created_at BETWEEN :from AND :to
        GROUP BY DATE_TRUNC(:period, i.created_at)
        ORDER BY period
        """, nativeQuery = true)
    List<Object[]> getRevenueReport(@Param("wid")    UUID warehouseId,
                                    @Param("from")   Instant from,
                                    @Param("to")     Instant to,
                                    @Param("period") String period);

    // Báo cáo doanh thu ALL branches (Admin)
    @Query(value = """
        SELECT
            w.name                             AS warehouse_name,
            COUNT(DISTINCT i.id)               AS invoice_count,
            SUM(i.final_amount)                AS revenue
        FROM invoices i
        JOIN shifts s ON s.id = i.shift_id
        JOIN warehouses w ON w.id = s.warehouse_id
        WHERE i.type = 'SALE'
        AND i.created_at BETWEEN :from AND :to
        GROUP BY w.id, w.name
        ORDER BY revenue DESC
        """, nativeQuery = true)
    List<Object[]> getRevenueByWarehouse(@Param("from") Instant from,
                                         @Param("to")   Instant to);
}
