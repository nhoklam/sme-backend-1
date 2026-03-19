package sme.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sme.backend.entity.InventoryTransaction;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, UUID> {

    List<InventoryTransaction> findByInventoryIdOrderByCreatedAtDesc(UUID inventoryId);

    List<InventoryTransaction> findByReferenceId(UUID referenceId);
}
