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

import java.util.UUID;

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
            throw new BusinessException("DUPLICATE_BARCODE",
                    "Mã vạch/ISBN '" + req.getIsbnBarcode() + "' đã tồn tại");
        }
        categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

        Product product = Product.builder()
                .categoryId(req.getCategoryId())
                .supplierId(req.getSupplierId())
                .isbnBarcode(req.getIsbnBarcode())
                .sku(req.getSku())
                .name(req.getName())
                .description(req.getDescription())
                .retailPrice(req.getRetailPrice())
                .wholesalePrice(req.getWholesalePrice())
                .imageUrl(req.getImageUrl())
                .unit(req.getUnit() != null ? req.getUnit() : "Cuốn")
                .weight(req.getWeight())
                .isActive(true)
                .build();
        product = productRepository.save(product);
        log.info("Product created: {} | ISBN: {}", product.getName(), product.getIsbnBarcode());
        return mapToResponse(product, null);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(UUID id, UpdateProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (req.getName() != null)          product.setName(req.getName());
        if (req.getDescription() != null)   product.setDescription(req.getDescription());
        if (req.getRetailPrice() != null)   product.setRetailPrice(req.getRetailPrice());
        if (req.getWholesalePrice() != null) product.setWholesalePrice(req.getWholesalePrice());
        if (req.getImageUrl() != null)      product.setImageUrl(req.getImageUrl());
        if (req.getCategoryId() != null)    product.setCategoryId(req.getCategoryId());
        if (req.getIsActive() != null)      product.setIsActive(req.getIsActive());
        return mapToResponse(productRepository.save(product), null);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#barcode")
    public ProductResponse getByBarcode(String barcode, UUID warehouseId) {
        Product product = productRepository.findByIsbnBarcodeAndIsActiveTrue(barcode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy sản phẩm với mã vạch: " + barcode));

        Integer available = null;
        if (warehouseId != null) {
            available = inventoryRepository
                    .findByProductIdAndWarehouseId(product.getId(), warehouseId)
                    .map(inv -> inv.getAvailableQuantity())
                    .orElse(0);
            if (available <= 0) {
                throw new BusinessException("OUT_OF_STOCK",
                        "Sản phẩm '" + product.getName() + "' đã hết hàng tại chi nhánh này");
            }
        }
        return mapToResponse(product, available);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String keyword, UUID categoryId, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return productRepository.searchByKeyword(keyword, pageable)
                    .map(p -> mapToResponse(p, null));
        }
        if (categoryId != null) {
            return productRepository.findByCategoryIdAndIsActiveTrue(categoryId, pageable)
                    .map(p -> mapToResponse(p, null));
        }
        return productRepository.findAll(pageable).map(p -> mapToResponse(p, null));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        return mapToResponse(p, null);
    }

    public ProductResponse mapToResponse(Product p, Integer availableQty) {
        return ProductResponse.builder()
                .id(p.getId())
                .categoryId(p.getCategoryId())
                .isbnBarcode(p.getIsbnBarcode())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .retailPrice(p.getRetailPrice())
                .wholesalePrice(p.getWholesalePrice())
                .macPrice(p.getMacPrice())
                .imageUrl(p.getImageUrl())
                .unit(p.getUnit())
                .weight(p.getWeight())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .availableQuantity(availableQty)
                .build();
    }
}
