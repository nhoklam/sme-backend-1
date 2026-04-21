package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.InternalTransfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InternalTransferRepository extends JpaRepository<InternalTransfer, UUID> {

    Optional<InternalTransfer> findByCode(String code);

    Page<InternalTransfer> findByFromWarehouseIdOrToWarehouseIdOrderByCreatedAtDesc(
            UUID fromWarehouseId, UUID toWarehouseId, Pageable pageable);

    List<InternalTransfer> findByToWarehouseIdAndStatus(
            UUID toWarehouseId, InternalTransfer.TransferStatus status);

    @Query("""
        SELECT t FROM InternalTransfer t
        LEFT JOIN FETCH t.items
        WHERE t.id = :id
        """)
    Optional<InternalTransfer> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT COUNT(t) FROM InternalTransfer t WHERE t.referenceOrderId = :orderId AND t.status != 'RECEIVED' AND t.status != 'CANCELLED'")
    long countPendingTransfersByOrderId(@Param("orderId") UUID orderId);

    // =========================================================================
    // CÁC HÀM TÌM KIẾM ĐÃ TỐI ƯU ĐỂ TRÁNH LỖI NULL ENUM TRÊN POSTGRESQL
    // =========================================================================

    // 1. Tìm tất cả (cho ADMIN) - Khi KHÔNG lọc trạng thái
    @Query("""
        SELECT t FROM InternalTransfer t
        WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY t.createdAt DESC
        """)
    Page<InternalTransfer> searchAllTransfers(
            @Param("keyword") String keyword, 
            Pageable pageable);

    // 2. Tìm tất cả (cho ADMIN) - Khi CÓ lọc trạng thái cụ thể
    @Query("""
        SELECT t FROM InternalTransfer t
        WHERE t.status = :status
        AND (:keyword IS NULL OR :keyword = '' OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY t.createdAt DESC
        """)
    Page<InternalTransfer> searchAllTransfersWithStatus(
            @Param("status") InternalTransfer.TransferStatus status,
            @Param("keyword") String keyword, 
            Pageable pageable);

    // 3. Tìm theo kho (cho MANAGER) - Khi KHÔNG lọc trạng thái
    @Query("""
        SELECT t FROM InternalTransfer t
        WHERE (t.fromWarehouseId = :wid OR t.toWarehouseId = :wid)
        AND (:keyword IS NULL OR :keyword = '' OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY t.createdAt DESC
        """)
    Page<InternalTransfer> searchTransfersByWarehouse(
            @Param("wid") UUID warehouseId,
            @Param("keyword") String keyword, 
            Pageable pageable);

    // 4. Tìm theo kho (cho MANAGER) - Khi CÓ lọc trạng thái cụ thể
    @Query("""
        SELECT t FROM InternalTransfer t
        WHERE (t.fromWarehouseId = :wid OR t.toWarehouseId = :wid)
        AND t.status = :status
        AND (:keyword IS NULL OR :keyword = '' OR LOWER(t.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY t.createdAt DESC
        """)
    Page<InternalTransfer> searchTransfersByWarehouseWithStatus(
            @Param("wid") UUID warehouseId,
            @Param("status") InternalTransfer.TransferStatus status,
            @Param("keyword") String keyword, 
            Pageable pageable);

    List<InternalTransfer> findByReferenceOrderId(UUID referenceOrderId);
}