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
}
