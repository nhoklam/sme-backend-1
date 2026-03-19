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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // POS: Quét mã vạch → tìm sản phẩm
    Optional<Product> findByIsbnBarcodeAndIsActiveTrue(String isbnBarcode);

    Optional<Product> findBySkuAndIsActiveTrue(String sku);

    boolean existsByIsbnBarcode(String isbnBarcode);

    // Tìm kiếm full-text (cho POS search bar)
    @Query("""
        SELECT p FROM Product p
        WHERE p.isActive = true
        AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR p.isbnBarcode LIKE CONCAT('%', :keyword, '%')
          OR p.sku LIKE CONCAT('%', :keyword, '%'))
        ORDER BY p.name
        """)
    Page<Product> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // Lọc theo danh mục
    Page<Product> findByCategoryIdAndIsActiveTrue(UUID categoryId, Pageable pageable);

    // Cập nhật MAC price sau nhập kho
    @Modifying
    @Query("UPDATE Product p SET p.macPrice = :macPrice WHERE p.id = :id")
    void updateMacPrice(@Param("id") UUID id, @Param("macPrice") BigDecimal macPrice);

    // ĐÃ SỬA: Chuyển sang Native Query trỏ thẳng xuống bảng Database
    // Products cần vector hóa AI (chưa có embedding)
    @Query(value = """
        SELECT p.* FROM products p
        WHERE p.is_active = true
        AND NOT EXISTS (
            SELECT 1 FROM product_vectors pv WHERE pv.product_id = p.id
        )
        """, nativeQuery = true)
    List<Product> findProductsWithoutEmbedding();

    // Báo cáo: top sản phẩm bán chạy theo invoices
    @Query(value = """
        SELECT p.id, p.name, p.isbn_barcode, SUM(ii.quantity) as total_sold
        FROM products p
        JOIN invoice_items ii ON ii.product_id = p.id
        JOIN invoices i ON i.id = ii.invoice_id
        WHERE i.created_at BETWEEN :from AND :to
        AND i.type = 'SALE'
        GROUP BY p.id, p.name, p.isbn_barcode
        ORDER BY total_sold DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopSellingProducts(@Param("from") java.time.Instant from,
                                          @Param("to")   java.time.Instant to,
                                          @Param("limit") int limit);
}