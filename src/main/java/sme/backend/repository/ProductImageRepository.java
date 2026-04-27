package sme.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sme.backend.entity.ProductImage;

import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAscCreatedAtAsc(UUID productId);

    long countByProductId(UUID productId);

    void deleteByProductIdAndId(UUID productId, UUID id);

    // 👇 THÊM DÒNG NÀY ĐỂ XÓA TOÀN BỘ ẢNH CỦA 1 SẢN PHẨM
    void deleteByProductId(UUID productId);
}