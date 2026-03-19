package sme.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sme.backend.entity.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByParentIdIsNullAndIsActiveTrueOrderBySortOrder();

    List<Category> findByParentIdAndIsActiveTrueOrderBySortOrder(UUID parentId);

    List<Category> findByIsActiveTrueOrderBySortOrder();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
