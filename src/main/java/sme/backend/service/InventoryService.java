package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.config.AppProperties;
import sme.backend.dto.request.AdjustInventoryRequest;
import sme.backend.entity.*;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.*;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository txnRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final AppProperties appProperties;

    // ─────────────────────────────────────────────────────────
    // GET hoặc CREATE inventory record
    // ─────────────────────────────────────────────────────────
    @Transactional
    public Inventory getOrCreate(UUID productId, UUID warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> {
                    Inventory inv = Inventory.builder()
                            .productId(productId)
                            .warehouseId(warehouseId)
                            .quantity(0)
                            .reservedQuantity(0)
                            .inTransit(0)
                            .minQuantity(appProperties.getBusiness().getLowStockThreshold())
                            .build();
                    return inventoryRepository.save(inv);
                });
    }

    // ─────────────────────────────────────────────────────────
    // NHẬP KHO (gọi từ PurchaseService sau khi approve PO)
    // ─────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void importStock(UUID productId, UUID warehouseId,
                            int quantity, java.math.BigDecimal importPrice,
                            UUID referenceId, String operator) {

        Inventory inv = getOrCreate(productId, warehouseId);
        int before = inv.getQuantity();

        // Tái tính MAC trên Product
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        product.recalculateMAC(before, quantity, importPrice);
        productRepository.save(product);

        // Cộng kho
        inv.addQuantity(quantity);
        inventoryRepository.save(inv);

        // Ghi thẻ kho
        recordTransaction(inv, referenceId, "IMPORT",
                quantity, before, inv.getQuantity(), operator);

        // Kiểm tra cảnh báo tồn kho thấp
        checkLowStockAlert(inv);
    }

    // ─────────────────────────────────────────────────────────
    // XUẤT KHO POS (gọi từ POSService)
    // ─────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void deductForPOSSale(UUID productId, UUID warehouseId,
                                 int quantity, UUID invoiceId, String operator) {

        Inventory inv = inventoryRepository
                .findByProductAndWarehouseWithLock(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tồn kho cho sản phẩm: " + productId));

        int before = inv.getQuantity();
        inv.deductPhysicalQuantity(quantity);   // throws InsufficientStockException nếu hết
        inventoryRepository.save(inv);

        recordTransaction(inv, invoiceId, "SALE_POS",
                -quantity, before, inv.getQuantity(), operator);
        checkLowStockAlert(inv);
    }

    // ─────────────────────────────────────────────────────────
    // HOÀN KHO (Trả hàng về kho bán / kho lỗi)
    // ─────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void returnToStock(UUID productId, UUID warehouseId,
                              int quantity, UUID invoiceId, String destination, String operator) {

        Inventory inv = getOrCreate(productId, warehouseId);
        int before = inv.getQuantity();
        String txnType;

        if ("STOCK".equals(destination)) {
            inv.addQuantity(quantity);
            txnType = "RETURN_TO_STOCK";
        } else {
            // DEFECT → cộng vào kho nhưng đánh dấu defect (hiện tại cộng thẳng; tương lai có kho lỗi riêng)
            txnType = "RETURN_TO_DEFECT";
            // không cộng quantity bán lẻ
        }

        inventoryRepository.save(inv);
        recordTransaction(inv, invoiceId, txnType,
                "STOCK".equals(destination) ? quantity : 0,
                before, inv.getQuantity(), operator);
    }

    // ─────────────────────────────────────────────────────────
    // GIỮ CHỖ cho đơn Online
    // ─────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void reserveForOnlineOrder(UUID productId, UUID warehouseId,
                                      int quantity, UUID orderId, String operator) {
        Inventory inv = inventoryRepository
                .findByProductAndWarehouseWithLock(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tồn kho: product=" + productId));

        inv.reserveQuantity(quantity);
        inventoryRepository.save(inv);
        recordTransaction(inv, orderId, "RESERVE", 0,
                inv.getAvailableQuantity() + quantity, inv.getAvailableQuantity(), operator);
    }

    // ─────────────────────────────────────────────────────────
    // XÁC NHẬN XUẤT KHO đơn Online (khi chuyển sang SHIPPING)
    // ─────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void confirmOnlineShipment(UUID productId, UUID warehouseId,
                                      int quantity, UUID orderId, String operator) {
        Inventory inv = inventoryRepository
                .findByProductAndWarehouseWithLock(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tồn kho: product=" + productId));

        int before = inv.getQuantity();
        inv.confirmShipment(quantity);
        inventoryRepository.save(inv);
        recordTransaction(inv, orderId, "SALE_ONLINE",
                -quantity, before, inv.getQuantity(), operator);
        checkLowStockAlert(inv);
    }

    // ─────────────────────────────────────────────────────────
    // ĐIỀU CHỈNH KIỂM KÊ (INV-03)
    // ─────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void adjustInventory(AdjustInventoryRequest req, UUID referenceId, String operator) {
        Inventory inv = getOrCreate(req.getProductId(), req.getWarehouseId());
        int before = inv.getQuantity();
        int diff   = req.getActualQuantity() - before;

        inv.setQuantity(req.getActualQuantity());
        inventoryRepository.save(inv);

        recordTransaction(inv, referenceId, "ADJUSTMENT",
                diff, before, req.getActualQuantity(), operator + ": " + req.getReason());
    }

    // ─────────────────────────────────────────────────────────
    // GIẢI PHÓNG RESERVED (khi hủy đơn)
    // ─────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(value = "inventories", allEntries = true)
    public void releaseReservation(UUID productId, UUID warehouseId,
                                   int quantity, UUID orderId, String operator) {
        inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .ifPresent(inv -> {
                    inv.releaseReservedQuantity(quantity);
                    inventoryRepository.save(inv);
                    recordTransaction(inv, orderId, "RELEASE", 0,
                            inv.getAvailableQuantity(), inv.getAvailableQuantity(), operator);
                });
    }

    // ─────────────────────────────────────────────────────────
    // QUERY
    // ─────────────────────────────────────────────────────────
    @Cacheable(value = "inventories", key = "#productId + '_' + #warehouseId")
    @Transactional(readOnly = true)
    public Inventory getInventory(UUID productId, UUID warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tồn kho: product=" + productId + ", warehouse=" + warehouseId));
    }

    @Transactional(readOnly = true)
    public List<Inventory> getLowStockAlerts(UUID warehouseId) {
        return inventoryRepository.findLowStockByWarehouse(warehouseId);
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────
    private void recordTransaction(Inventory inv, UUID referenceId, String type,
                                   int change, int before, int after, String operator) {
        InventoryTransaction txn = InventoryTransaction.builder()
                .inventoryId(inv.getId())
                .referenceId(referenceId)
                .transactionType(type)
                .quantityChange(change)
                .quantityBefore(before)
                .quantityAfter(after)
                .createdBy(operator)
                .build();
        txnRepository.save(txn);
    }

    private void checkLowStockAlert(Inventory inv) {
        if (inv.isLowStock()) {
            notificationService.notifyLowStock(inv);
        }
    }
}
