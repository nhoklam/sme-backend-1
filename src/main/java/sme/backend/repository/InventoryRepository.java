package sme.backend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    /**
     * PESSIMISTIC_WRITE lock - dùng khi cần đảm bảo tuyệt đối
     * (backup cho Optimistic Locking trong các trường hợp đặc biệt)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :pid AND i.warehouseId = :wid")
    Optional<Inventory> findByProductAndWarehouseWithLock(
            @Param("pid") UUID productId,
            @Param("wid") UUID warehouseId);

    List<Inventory> findByWarehouseId(UUID warehouseId);

    // Lấy tồn kho của 1 sản phẩm trên tất cả các kho
    List<Inventory> findByProductId(UUID productId);

    // Hàng tồn kho thấp cần cảnh báo
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.warehouseId = :wid
        AND i.minQuantity > 0
        AND i.quantity <= i.minQuantity
        ORDER BY i.quantity ASC
        """)
    List<Inventory> findLowStockByWarehouse(@Param("wid") UUID warehouseId);

    // Tổng tồn kho khả dụng trên tất cả kho (cho Web E-commerce)
    @Query("""
        SELECT COALESCE(SUM(i.quantity - i.reservedQuantity), 0)
        FROM Inventory i
        WHERE i.productId = :pid
        """)
    Integer getTotalAvailableQuantity(@Param("pid") UUID productId);

    // Báo cáo giá trị tồn kho theo kho
    @Query(value = """
        SELECT w.name AS warehouse_name,
               COUNT(i.id) AS sku_count,
               SUM(i.quantity) AS total_qty,
               SUM(i.quantity * p.mac_price) AS total_value
        FROM inventories i
        JOIN warehouses w ON w.id = i.warehouse_id
        JOIN products p ON p.id = i.product_id
        WHERE (:wid IS NULL OR i.warehouse_id = :wid)
        GROUP BY w.id, w.name
        """, nativeQuery = true)
    List<Object[]> getInventoryValueReport(@Param("wid") UUID warehouseId);

    // Dead stock: sản phẩm chưa bán trong X ngày
    @Query(value = """
        SELECT i.*, p.name AS product_name, p.isbn_barcode
        FROM inventories i
        JOIN products p ON p.id = i.product_id
        WHERE i.warehouse_id = :wid
        AND i.quantity > 0
        AND NOT EXISTS (
            SELECT 1 FROM inventory_transactions t
            WHERE t.inventory_id = i.id
            AND t.transaction_type IN ('SALE_POS','SALE_ONLINE')
            AND t.created_at > NOW() - INTERVAL '1 day' * :days
        )
        ORDER BY i.quantity DESC
        """, nativeQuery = true)
    List<Object[]> findDeadStockByWarehouse(@Param("wid") UUID warehouseId,
                                            @Param("days") int days);

    // Xóa cache sau khi cập nhật - check tồn kho cho tất cả sản phẩm trong danh sách
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.warehouseId = :wid
        AND i.productId IN :productIds
        """)
    List<Inventory> findByWarehouseAndProducts(
            @Param("wid") UUID warehouseId,
            @Param("productIds") List<UUID> productIds);
}
