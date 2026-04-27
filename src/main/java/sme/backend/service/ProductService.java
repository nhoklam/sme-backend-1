package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.CreateProductRequest;
import sme.backend.dto.request.UpdateProductRequest;
import sme.backend.dto.response.ProductResponse;
import sme.backend.entity.Category;
import sme.backend.entity.Product;
import sme.backend.entity.ProductImage;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CategoryRepository;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.ProductImageRepository;
import sme.backend.repository.ProductRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private static final int MAX_IMAGES_PER_PRODUCT = 10; // ← GIỚI HẠN TỐI ĐA

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository; // ← THÊM MỚI

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(CreateProductRequest req) {
        if (productRepository.existsByIsbnBarcode(req.getIsbnBarcode())) {
            throw new BusinessException("DUPLICATE_BARCODE", "Mã vạch/ISBN '" + req.getIsbnBarcode() + "' đã tồn tại");
        }
        categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

        // Lấy ảnh đầu tiên làm ảnh đại diện
        String primaryImage = null;
        if (req.getImageUrls() != null && !req.getImageUrls().isEmpty()) {
            primaryImage = req.getImageUrls().get(0);
        } else if (req.getImageUrl() != null) {
            primaryImage = req.getImageUrl();
        }

        Product product = Product.builder()
                .categoryId(req.getCategoryId()).supplierId(req.getSupplierId())
                .isbnBarcode(req.getIsbnBarcode()).sku(req.getSku()).name(req.getName())
                .description(req.getDescription()).retailPrice(req.getRetailPrice())
                .wholesalePrice(req.getWholesalePrice()).imageUrl(primaryImage)
                .unit(req.getUnit() != null ? req.getUnit() : "Cuốn").weight(req.getWeight())
                .isActive(true).build();

        Product saved = productRepository.save(product);

        // Lưu tất cả ảnh vào bảng product_images
        List<String> allUrls = req.getImageUrls() != null ? req.getImageUrls() :
                (req.getImageUrl() != null ? List.of(req.getImageUrl()) : List.of());
        saveImageList(saved.getId(), allUrls);

        return mapToResponse(saved, 0);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(UUID id, UpdateProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getRetailPrice() != null) product.setRetailPrice(req.getRetailPrice());
        if (req.getWholesalePrice() != null) product.setWholesalePrice(req.getWholesalePrice());
        if (req.getCategoryId() != null) product.setCategoryId(req.getCategoryId());
        if (req.getIsActive() != null) product.setIsActive(req.getIsActive());
        if (req.getUnit() != null) product.setUnit(req.getUnit());
        if (req.getWeight() != null) product.setWeight(req.getWeight());
        if (req.getHasSupplierId() != null && req.getHasSupplierId()) {
            product.setSupplierId(req.getSupplierId());
        }

        // Nếu client gửi imageUrl đơn lẻ (backward compat)
        if (req.getImageUrls() != null) {
            // 1. Xóa toàn bộ ảnh cũ trong DB của sản phẩm này
            productImageRepository.deleteByProductId(id);
            
            // 2. Lưu lại danh sách ảnh mới mà Frontend gửi lên
            saveImageList(id, req.getImageUrls());
            
            // 3. Cập nhật lại ảnh đại diện (imageUrl chính)
            if (req.getImageUrls().isEmpty()) {
                product.setImageUrl(null); // Nếu xóa hết ảnh thì set null
            } else {
                product.setImageUrl(req.getImageUrls().get(0)); // Lấy ảnh đầu tiên làm đại diện
            }
        } 
        // Backward compatibility: Nếu client chỉ gửi imageUrl đơn lẻ
        else if (req.getImageUrl() != null) {
            product.setImageUrl(req.getImageUrl());
        }

        // 👆 KẾT THÚC ĐOẠN SỬA 👆

        Product saved = productRepository.save(product);
        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(id);
        return mapToResponse(saved, availableQty);
    }

    // ─── API THÊM MỘT ẢNH ───────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse addImage(UUID productId, String imageUrl) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        long currentCount = productImageRepository.countByProductId(productId);
        if (currentCount >= MAX_IMAGES_PER_PRODUCT) {
            throw new BusinessException("IMAGE_LIMIT_EXCEEDED",
                    "Sản phẩm chỉ được tối đa " + MAX_IMAGES_PER_PRODUCT + " ảnh");
        }

        // Lưu vào product_images
        ProductImage img = ProductImage.builder()
                .productId(productId)
                .imageUrl(imageUrl)
                .sortOrder((int) currentCount)
                .build();
        productImageRepository.save(img);

        // Nếu đây là ảnh đầu tiên, cập nhật luôn ảnh đại diện
        if (currentCount == 0) {
            product.setImageUrl(imageUrl);
            productRepository.save(product);
        }

        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(productId);
        return mapToResponse(productRepository.findById(productId).get(), availableQty);
    }

    // ─── API XÓA MỘT ẢNH ───────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse deleteImage(UUID productId, UUID imageId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        productImageRepository.deleteByProductIdAndId(productId, imageId);

        // Cập nhật lại ảnh đại diện (ảnh sort_order nhỏ nhất còn lại)
        List<ProductImage> remaining = productImageRepository
                .findByProductIdOrderBySortOrderAscCreatedAtAsc(productId);

        Product product = productRepository.findById(productId).get();
        product.setImageUrl(remaining.isEmpty() ? null : remaining.get(0).getImageUrl());
        productRepository.save(product);

        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(productId);
        return mapToResponse(product, availableQty);
    }

    // ─── API SẮP XẾP LẠI THỨ TỰ ẢNH ──────────────────────────────
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse reorderImages(UUID productId, List<UUID> orderedImageIds) {
        for (int i = 0; i < orderedImageIds.size(); i++) {
            UUID imgId = orderedImageIds.get(i);
            productImageRepository.findById(imgId).ifPresent(img -> {
                img.setSortOrder(orderedImageIds.indexOf(imgId));
                productImageRepository.save(img);
            });
        }
        // Cập nhật ảnh đại diện
        if (!orderedImageIds.isEmpty()) {
            productImageRepository.findById(orderedImageIds.get(0)).ifPresent(img -> {
                Product product = productRepository.findById(productId).orElseThrow();
                product.setImageUrl(img.getImageUrl());
                productRepository.save(product);
            });
        }
        Product product = productRepository.findById(productId).orElseThrow();
        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(productId);
        return mapToResponse(product, availableQty);
    }

    // ─── CÁC METHOD HIỆN CÓ (giữ nguyên, chỉ thêm imageUrls vào mapToResponse) ──

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#barcode + '_' + (#warehouseId != null ? #warehouseId.toString() : 'ALL')")
    public ProductResponse getByBarcode(String barcode, UUID warehouseId) {
        Product product = productRepository.findByIsbnBarcodeAndIsActiveTrue(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với mã vạch: " + barcode));
        Integer available = null;
        if (warehouseId != null) {
            available = inventoryRepository.findByProductIdAndWarehouseId(product.getId(), warehouseId)
                    .map(inv -> inv.getAvailableQuantity()).orElse(0);
            if (available <= 0) throw new BusinessException("OUT_OF_STOCK",
                    "Sản phẩm '" + product.getName() + "' đã hết hàng tại chi nhánh này");
        } else {
            available = inventoryRepository.getTotalAvailableQuantity(product.getId());
        }
        return mapToResponse(product, available);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String keyword, UUID categoryId, UUID supplierId,
                                         Boolean isActive, Pageable pageable) {
        Page<Product> productPage = productRepository.searchProducts(keyword, categoryId, supplierId, isActive, pageable);
        if (productPage.isEmpty()) return productPage.map(p -> mapToResponse(p, 0));

        List<UUID> categoryIds = productPage.getContent().stream()
                .map(Product::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        List<UUID> productIds = productPage.getContent().stream().map(Product::getId).toList();
        List<Object[]> bulkInventory = inventoryRepository.getBulkTotalAvailableQuantity(productIds);
        Map<UUID, Integer> inventoryMap = bulkInventory.stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));

        // Tải imageUrls cho tất cả sản phẩm trong trang (bulk load)
        // Với danh sách thì chỉ cần imageUrl đại diện (đã có sẵn), không cần load imageUrls
        return productPage.map(p -> {
            String catName = categoryMap.getOrDefault(p.getCategoryId(), "Chưa phân loại");
            Integer availableQty = inventoryMap.getOrDefault(p.getId(), 0);
            return buildResponse(p, catName, availableQty, null); // null = không load imageUrls cho list
        });
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(p.getId());
        // Load đầy đủ imageUrls khi xem chi tiết
        List<String> imageUrls = productImageRepository
                .findByProductIdOrderBySortOrderAscCreatedAtAsc(id)
                .stream().map(ProductImage::getImageUrl).toList();
        String catName = categoryRepository.findById(p.getCategoryId())
                .map(Category::getName).orElse("Chưa phân loại");
        return buildResponse(p, catName, availableQty, imageUrls);
    }

    public ProductResponse mapToResponse(Product p, Integer availableQty) {
        String catName = categoryRepository.findById(p.getCategoryId())
                .map(Category::getName).orElse("Chưa phân loại");
        List<String> imageUrls = productImageRepository
                .findByProductIdOrderBySortOrderAscCreatedAtAsc(p.getId())
                .stream().map(ProductImage::getImageUrl).toList();
        return buildResponse(p, catName, availableQty, imageUrls);
    }

    private ProductResponse buildResponse(Product p, String catName, Integer availableQty,
                                           List<String> imageUrls) {
        return ProductResponse.builder()
                .id(p.getId()).categoryId(p.getCategoryId()).categoryName(catName)
                .supplierId(p.getSupplierId()).isbnBarcode(p.getIsbnBarcode()).sku(p.getSku())
                .name(p.getName()).description(p.getDescription()).retailPrice(p.getRetailPrice())
                .wholesalePrice(p.getWholesalePrice()).macPrice(p.getMacPrice())
                .imageUrl(p.getImageUrl())       // ảnh đại diện (nhanh)
                .imageUrls(imageUrls)            // toàn bộ ảnh (khi cần)
                .unit(p.getUnit()).weight(p.getWeight()).isActive(p.getIsActive())
                .createdAt(p.getCreatedAt()).availableQuantity(availableQty).build();
    }

    private void saveImageList(UUID productId, List<String> urls) {
        for (int i = 0; i < Math.min(urls.size(), MAX_IMAGES_PER_PRODUCT); i++) {
            productImageRepository.save(ProductImage.builder()
                    .productId(productId).imageUrl(urls.get(i)).sortOrder(i).build());
        }
    }
}