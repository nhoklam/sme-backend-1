package sme.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Inventory;
import sme.backend.dto.response.LowStockItem;
import sme.backend.dto.response.InventoryResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :pid AND i.warehouseId = :wid")
    Optional<Inventory> findByProductAndWarehouseWithLock(
            @Param("pid") UUID productId,
            @Param("wid") UUID warehouseId);

    List<Inventory> findByWarehouseId(UUID warehouseId);
    List<Inventory> findByProductId(UUID productId);
    List<Inventory> findByProductIdIn(List<UUID> productIds);

    // ĐÃ SỬA: Thêm i.quantity > 0
    @Query("""
        SELECT i FROM Inventory i
        WHERE (CAST(:wid AS uuid) IS NULL OR i.warehouseId = CAST(:wid AS uuid))
        AND i.minQuantity > 0
        AND i.quantity > 0
        AND i.quantity <= i.minQuantity
        ORDER BY i.quantity ASC
        """)
    List<Inventory> findLowStockByWarehouse(@Param("wid") UUID warehouseId);

    // ĐÃ SỬA: Thêm i.quantity > 0
    @Query("""
        SELECT new sme.backend.dto.response.LowStockItem(
            i.id, i.productId, p.name, p.sku, i.warehouseId, w.name,
            i.quantity, i.minQuantity, i.reservedQuantity
        )
        FROM Inventory i
        JOIN sme.backend.entity.Product p ON p.id = i.productId
        JOIN sme.backend.entity.Warehouse w ON w.id = i.warehouseId
        WHERE (CAST(:wid AS uuid) IS NULL OR i.warehouseId = CAST(:wid AS uuid))
        AND i.minQuantity > 0
        AND i.quantity > 0
        AND i.quantity <= i.minQuantity
        ORDER BY i.quantity ASC
        """)
    List<LowStockItem> findLowStockWithNameByWarehouse(@Param("wid") UUID warehouseId);

    @Query("SELECT COALESCE(SUM(i.quantity - i.reservedQuantity), 0) FROM Inventory i WHERE i.productId = :pid")
    Integer getTotalAvailableQuantity(@Param("pid") UUID productId);

    @Query(value = """
        SELECT w.name AS warehouse_name, COUNT(i.id) AS sku_count, SUM(i.quantity) AS total_qty, SUM(i.quantity * p.mac_price) AS total_value
        FROM inventories i JOIN warehouses w ON w.id = i.warehouse_id JOIN products p ON p.id = i.product_id
        WHERE (CAST(:wid AS uuid) IS NULL OR i.warehouse_id = CAST(:wid AS uuid)) GROUP BY w.id, w.name
        """, nativeQuery = true)
    List<Map<String, Object>> getInventoryValueReport(@Param("wid") UUID warehouseId);

    @Query(value = """
        SELECT i.id, i.quantity, p.name AS product_name, p.isbn_barcode
        FROM inventories i JOIN products p ON p.id = i.product_id
        WHERE (CAST(:wid AS uuid) IS NULL OR i.warehouse_id = CAST(:wid AS uuid)) AND i.quantity > 0
        AND NOT EXISTS (
            SELECT 1 FROM inventory_transactions t WHERE t.inventory_id = i.id
            AND t.transaction_type IN ('SALE_POS','SALE_ONLINE') AND t.created_at > NOW() - INTERVAL '1 day' * :days
        ) ORDER BY i.quantity DESC
        """, nativeQuery = true)
    List<Map<String, Object>> findDeadStockByWarehouse(@Param("wid") UUID warehouseId, @Param("days") int days);

    @Query("SELECT i.productId, COALESCE(SUM(i.quantity - i.reservedQuantity), 0) FROM Inventory i WHERE i.productId IN :productIds GROUP BY i.productId")
    List<Object[]> getBulkTotalAvailableQuantity(@Param("productIds") List<UUID> productIds);

    // ĐÃ SỬA: Thêm i.quantity > 0 vào CASE WHEN và điều kiện LOW_STOCK
    @Query("""
        SELECT new sme.backend.dto.response.InventoryResponse(
            i.id, p.id, p.name, p.sku, p.isbnBarcode, p.imageUrl, c.name, 
            i.quantity, 
            i.reservedQuantity, 
            i.inTransit, 
            i.minQuantity, 
            CASE WHEN (i.minQuantity > 0 AND i.quantity > 0 AND i.quantity <= i.minQuantity) THEN true ELSE false END
        )
        FROM Inventory i 
        JOIN Product p ON p.id = i.productId
        LEFT JOIN Category c ON c.id = p.categoryId
        WHERE i.warehouseId = :warehouseId
        AND p.isActive = true
        AND (:keyword IS NULL OR :keyword = '' 
             OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
             OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')) 
             OR p.isbnBarcode LIKE CONCAT('%', :keyword, '%'))
        AND (:categoryId IS NULL OR p.categoryId = :categoryId)
        AND (:status IS NULL OR :status = '' OR :status = 'ALL'
            OR (:status = 'IN_STOCK' AND i.quantity > 0 AND (i.minQuantity = 0 OR i.quantity > i.minQuantity))
            OR (:status = 'LOW_STOCK' AND i.minQuantity > 0 AND i.quantity > 0 AND i.quantity <= i.minQuantity)
            OR (:status = 'OUT_OF_STOCK' AND i.quantity <= 0)
        )
    """)
    Page<InventoryResponse> searchInventoryWithProductDetails(
            @Param("warehouseId") UUID warehouseId,
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            Pageable pageable
    );
}