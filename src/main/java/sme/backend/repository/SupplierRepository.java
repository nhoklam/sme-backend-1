package sme.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findByIsActiveTrueOrderByName();

    Optional<Supplier> findByTaxCode(String taxCode);

    boolean existsByTaxCode(String taxCode);

    @Query("""
        SELECT s FROM Supplier s
        WHERE s.isActive = true
        AND LOWER(s.name) LIKE LOWER(CONCAT('%', :kw, '%'))
        ORDER BY s.name
        """)
    Page<Supplier> searchByName(@Param("kw") String keyword, Pageable pageable);
}
