package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIsbnBarcodeAndIsActiveTrue(String isbnBarcode);
    Optional<Product> findBySkuAndIsActiveTrue(String sku);
    boolean existsByIsbnBarcode(String isbnBarcode);

    @Query("""
        SELECT p FROM Product p
        WHERE p.isActive = true
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR p.isbnBarcode LIKE CONCAT('%', :keyword, '%')
          OR p.sku LIKE CONCAT('%', :keyword, '%'))
        ORDER BY p.name
        """)
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    Page<Product> findByCategoryIdAndIsActiveTrue(UUID categoryId, Pageable pageable);

    @Modifying
    @Query("UPDATE Product p SET p.macPrice = :macPrice WHERE p.id = :id")
    void updateMacPrice(@Param("id") UUID id, @Param("macPrice") BigDecimal macPrice);

    @Query(value = """
        SELECT p.* FROM products p
        WHERE p.is_active = true
        AND NOT EXISTS (
            SELECT 1 FROM product_vectors pv WHERE pv.product_id = p.id
        )
        """, nativeQuery = true)
    List<Product> findProductsWithoutEmbedding();

    // ĐÃ SỬA: GỘP SỐ LƯỢNG BÁN TỪ CẢ POS VÀ ONLINE
    @Query(value = """
        SELECT p.id as id, p.name as name, SUM(combined.qty) as total_sold
        FROM products p
        JOIN (
            SELECT ii.product_id, ii.quantity AS qty
            FROM invoice_items ii
            JOIN invoices i ON ii.invoice_id = i.id
            JOIN shifts s ON i.shift_id = s.id
            WHERE i.created_at BETWEEN :fromDate AND :toDate
              AND (CAST(:warehouseId AS VARCHAR) IS NULL OR CAST(s.warehouse_id AS VARCHAR) = CAST(:warehouseId AS VARCHAR))
              AND i.type = 'SALE'

            UNION ALL

            SELECT oi.product_id, oi.quantity AS qty
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            WHERE o.created_at BETWEEN :fromDate AND :toDate
              AND (CAST(:warehouseId AS VARCHAR) IS NULL OR CAST(o.assigned_warehouse_id AS VARCHAR) = CAST(:warehouseId AS VARCHAR))
              AND o.status = 'DELIVERED'
        ) combined ON p.id = combined.product_id
        GROUP BY p.id, p.name
        ORDER BY total_sold DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Map<String, Object>> findTopSellingProducts(
            @Param("warehouseId") UUID warehouseId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("limit") int limit);
            
    @Query("""
            SELECT p FROM Product p
            WHERE (:categoryId IS NULL OR p.categoryId = :categoryId)
            AND (:supplierId IS NULL OR p.supplierId = :supplierId)
            AND (:isActive IS NULL OR p.isActive = :isActive)
            AND (:keyword IS NULL OR :keyword = ''
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR p.isbnBarcode LIKE CONCAT('%', :keyword, '%')
            OR p.sku LIKE CONCAT('%', :keyword, '%'))
            ORDER BY p.name
            """)
    Page<Product> searchProducts(@Param("keyword") String keyword, 
                                 @Param("categoryId") UUID categoryId, 
                                 @Param("supplierId") UUID supplierId, 
                                 @Param("isActive") Boolean isActive,
                                 Pageable pageable);
}