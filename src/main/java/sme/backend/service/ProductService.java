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
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CategoryRepository;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.ProductRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(CreateProductRequest req) {
        if (productRepository.existsByIsbnBarcode(req.getIsbnBarcode())) {
            throw new BusinessException("DUPLICATE_BARCODE", "Mã vạch/ISBN '" + req.getIsbnBarcode() + "' đã tồn tại");
        }
        categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

        Product product = Product.builder()
                .categoryId(req.getCategoryId()).supplierId(req.getSupplierId())
                .isbnBarcode(req.getIsbnBarcode()).sku(req.getSku()).name(req.getName())
                .description(req.getDescription()).retailPrice(req.getRetailPrice())
                .wholesalePrice(req.getWholesalePrice()).imageUrl(req.getImageUrl())
                .unit(req.getUnit() != null ? req.getUnit() : "Cuốn").weight(req.getWeight())
                .isActive(true).build();

        return mapToResponse(productRepository.save(product), 0);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(UUID id, UpdateProductRequest req) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getRetailPrice() != null) product.setRetailPrice(req.getRetailPrice());
        if (req.getWholesalePrice() != null) product.setWholesalePrice(req.getWholesalePrice());
        if (req.getImageUrl() != null) product.setImageUrl(req.getImageUrl());
        if (req.getCategoryId() != null) product.setCategoryId(req.getCategoryId());
        if (req.getIsActive() != null) product.setIsActive(req.getIsActive());
        
        if (req.getHasSupplierId() != null && req.getHasSupplierId()) {
            product.setSupplierId(req.getSupplierId());
        }

        Product savedProduct = productRepository.save(product);
        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(id);
        return mapToResponse(savedProduct, availableQty);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#barcode + '_' + (#warehouseId != null ? #warehouseId.toString() : 'ALL')")
    public ProductResponse getByBarcode(String barcode, UUID warehouseId) {
        Product product = productRepository.findByIsbnBarcodeAndIsActiveTrue(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với mã vạch: " + barcode));
        Integer available = null;
        if (warehouseId != null) {
            available = inventoryRepository.findByProductIdAndWarehouseId(product.getId(), warehouseId).map(inv -> inv.getAvailableQuantity()).orElse(0);
            if (available <= 0) throw new BusinessException("OUT_OF_STOCK", "Sản phẩm '" + product.getName() + "' đã hết hàng tại chi nhánh này");
        } else {
             available = inventoryRepository.getTotalAvailableQuantity(product.getId());
        }
        return mapToResponse(product, available);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String keyword, UUID categoryId, UUID supplierId, Boolean isActive, Pageable pageable) {
        Page<Product> productPage = productRepository.searchProducts(keyword, categoryId, supplierId, isActive, pageable);

        if (productPage.isEmpty()) {
            return productPage.map(p -> mapToResponse(p, 0));
        }

        List<UUID> categoryIds = productPage.getContent().stream()
                .map(Product::getCategoryId).filter(Objects::nonNull).distinct().toList();
                
        Map<UUID, String> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        List<UUID> productIds = productPage.getContent().stream().map(Product::getId).toList();
                
        List<Object[]> bulkInventory = inventoryRepository.getBulkTotalAvailableQuantity(productIds);
        Map<UUID, Integer> inventoryMap = bulkInventory.stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));

        return productPage.map(p -> {
            String catName = categoryMap.getOrDefault(p.getCategoryId(), "Chưa phân loại");
            Integer availableQty = inventoryMap.getOrDefault(p.getId(), 0);
            
            return ProductResponse.builder()
                .id(p.getId()).categoryId(p.getCategoryId()).categoryName(catName)
                .supplierId(p.getSupplierId()).isbnBarcode(p.getIsbnBarcode()).sku(p.getSku())
                .name(p.getName()).description(p.getDescription()).retailPrice(p.getRetailPrice())
                .wholesalePrice(p.getWholesalePrice()).macPrice(p.getMacPrice()).imageUrl(p.getImageUrl())
                .unit(p.getUnit()).weight(p.getWeight()).isActive(p.getIsActive())
                .createdAt(p.getCreatedAt()).availableQuantity(availableQty).build();
        });
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product p = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
        Integer availableQty = inventoryRepository.getTotalAvailableQuantity(p.getId());
        return mapToResponse(p, availableQty);
    }

    public ProductResponse mapToResponse(Product p, Integer availableQty) {
        String catName = categoryRepository.findById(p.getCategoryId()).map(Category::getName).orElse("Chưa phân loại");
        return ProductResponse.builder()
                .id(p.getId()).categoryId(p.getCategoryId()).categoryName(catName)
                .supplierId(p.getSupplierId()).isbnBarcode(p.getIsbnBarcode()).sku(p.getSku())
                .name(p.getName()).description(p.getDescription()).retailPrice(p.getRetailPrice())
                .wholesalePrice(p.getWholesalePrice()).macPrice(p.getMacPrice()).imageUrl(p.getImageUrl())
                .unit(p.getUnit()).weight(p.getWeight()).isActive(p.getIsActive())
                .createdAt(p.getCreatedAt()).availableQuantity(availableQty).build();
    }
}