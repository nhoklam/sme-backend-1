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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByCode(String code);

    boolean existsByCode(String code);

    Page<Invoice> findByShiftIdOrderByCreatedAtDesc(UUID shiftId, Pageable pageable);

    Page<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    @Query("""
        SELECT DISTINCT i FROM Invoice i
        LEFT JOIN FETCH i.items
        WHERE i.id = :id
        """)
    Optional<Invoice> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
        SELECT COALESCE(SUM(i.finalAmount), 0) FROM Invoice i
        WHERE i.shiftId = :shiftId AND i.type = 'SALE'
        """)
    BigDecimal sumRevenueByShift(@Param("shiftId") UUID shiftId);

    // ĐÃ SỬA: GỘP DOANH THU POS VÀ DOANH THU ĐƠN HÀNG ONLINE
    @Query(value = """
        SELECT
            period,
            COUNT(DISTINCT doc_id) AS invoice_count,
            SUM(revenue) AS revenue,
            SUM(cogs) AS cogs,
            SUM(revenue) - SUM(cogs) AS gross_profit
        FROM (
            SELECT
                DATE_TRUNC('day', i.created_at) AS period,
                i.id AS doc_id,
                i.final_amount AS revenue,
                COALESCE(item_agg.total_cogs, 0) AS cogs
            FROM invoices i
            JOIN shifts s ON s.id = i.shift_id
            LEFT JOIN (
                SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
                FROM invoice_items GROUP BY invoice_id
            ) item_agg ON item_agg.invoice_id = i.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(s.warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND i.type = 'SALE'
              AND i.created_at BETWEEN :from AND :to

            UNION ALL

            SELECT
                DATE_TRUNC('day', o.created_at) AS period,
                o.id AS doc_id,
                o.final_amount AS revenue,
                COALESCE(ord_item_agg.total_cogs, 0) AS cogs
            FROM orders o
            LEFT JOIN (
                SELECT order_id, SUM(quantity * mac_price) AS total_cogs
                FROM order_items GROUP BY order_id
            ) ord_item_agg ON ord_item_agg.order_id = o.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(o.assigned_warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND o.status = 'DELIVERED'
              AND o.created_at BETWEEN :from AND :to
        ) combined_data
        GROUP BY period
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportDaily(@Param("wid") UUID warehouseId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT
            period,
            COUNT(DISTINCT doc_id) AS invoice_count,
            SUM(revenue) AS revenue,
            SUM(cogs) AS cogs,
            SUM(revenue) - SUM(cogs) AS gross_profit
        FROM (
            SELECT
                DATE_TRUNC('week', i.created_at) AS period,
                i.id AS doc_id,
                i.final_amount AS revenue,
                COALESCE(item_agg.total_cogs, 0) AS cogs
            FROM invoices i
            JOIN shifts s ON s.id = i.shift_id
            LEFT JOIN (
                SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
                FROM invoice_items GROUP BY invoice_id
            ) item_agg ON item_agg.invoice_id = i.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(s.warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND i.type = 'SALE'
              AND i.created_at BETWEEN :from AND :to

            UNION ALL

            SELECT
                DATE_TRUNC('week', o.created_at) AS period,
                o.id AS doc_id,
                o.final_amount AS revenue,
                COALESCE(ord_item_agg.total_cogs, 0) AS cogs
            FROM orders o
            LEFT JOIN (
                SELECT order_id, SUM(quantity * mac_price) AS total_cogs
                FROM order_items GROUP BY order_id
            ) ord_item_agg ON ord_item_agg.order_id = o.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(o.assigned_warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND o.status = 'DELIVERED'
              AND o.created_at BETWEEN :from AND :to
        ) combined_data
        GROUP BY period
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportWeekly(@Param("wid") UUID warehouseId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT
            period,
            COUNT(DISTINCT doc_id) AS invoice_count,
            SUM(revenue) AS revenue,
            SUM(cogs) AS cogs,
            SUM(revenue) - SUM(cogs) AS gross_profit
        FROM (
            SELECT
                DATE_TRUNC('month', i.created_at) AS period,
                i.id AS doc_id,
                i.final_amount AS revenue,
                COALESCE(item_agg.total_cogs, 0) AS cogs
            FROM invoices i
            JOIN shifts s ON s.id = i.shift_id
            LEFT JOIN (
                SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
                FROM invoice_items GROUP BY invoice_id
            ) item_agg ON item_agg.invoice_id = i.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(s.warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND i.type = 'SALE'
              AND i.created_at BETWEEN :from AND :to

            UNION ALL

            SELECT
                DATE_TRUNC('month', o.created_at) AS period,
                o.id AS doc_id,
                o.final_amount AS revenue,
                COALESCE(ord_item_agg.total_cogs, 0) AS cogs
            FROM orders o
            LEFT JOIN (
                SELECT order_id, SUM(quantity * mac_price) AS total_cogs
                FROM order_items GROUP BY order_id
            ) ord_item_agg ON ord_item_agg.order_id = o.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(o.assigned_warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND o.status = 'DELIVERED'
              AND o.created_at BETWEEN :from AND :to
        ) combined_data
        GROUP BY period
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportMonthly(@Param("wid") UUID warehouseId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT
            period,
            COUNT(DISTINCT doc_id) AS invoice_count,
            SUM(revenue) AS revenue,
            SUM(cogs) AS cogs,
            SUM(revenue) - SUM(cogs) AS gross_profit
        FROM (
            SELECT
                DATE_TRUNC('year', i.created_at) AS period,
                i.id AS doc_id,
                i.final_amount AS revenue,
                COALESCE(item_agg.total_cogs, 0) AS cogs
            FROM invoices i
            JOIN shifts s ON s.id = i.shift_id
            LEFT JOIN (
                SELECT invoice_id, SUM(quantity * mac_price) AS total_cogs
                FROM invoice_items GROUP BY invoice_id
            ) item_agg ON item_agg.invoice_id = i.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(s.warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND i.type = 'SALE'
              AND i.created_at BETWEEN :from AND :to

            UNION ALL

            SELECT
                DATE_TRUNC('year', o.created_at) AS period,
                o.id AS doc_id,
                o.final_amount AS revenue,
                COALESCE(ord_item_agg.total_cogs, 0) AS cogs
            FROM orders o
            LEFT JOIN (
                SELECT order_id, SUM(quantity * mac_price) AS total_cogs
                FROM order_items GROUP BY order_id
            ) ord_item_agg ON ord_item_agg.order_id = o.id
            WHERE (CAST(:wid AS VARCHAR) IS NULL OR CAST(o.assigned_warehouse_id AS VARCHAR) = CAST(:wid AS VARCHAR))
              AND o.status = 'DELIVERED'
              AND o.created_at BETWEEN :from AND :to
        ) combined_data
        GROUP BY period
        ORDER BY period
        """, nativeQuery = true)
    List<Map<String, Object>> getRevenueReportYearly(@Param("wid") UUID warehouseId, @Param("from") Instant from, @Param("to") Instant to);
}