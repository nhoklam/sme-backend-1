package sme.backend.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import sme.backend.entity.Inventory;
import sme.backend.repository.InventoryRepository;
import sme.backend.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Batch Jobs:
 *
 * 1. inventorySnapshotJob  — Chạy 23:59 mỗi ngày
 *    → Tính giá trị tồn kho (qty * MAC) và lưu snapshot
 *    → Phục vụ REP-03 (Báo cáo giá trị tồn kho)
 *
 * 2. lowStockScanJob        — Chạy mỗi 30 phút
 *    → Quét tồn kho thấp, gửi notification hàng loạt
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    // ─────────────────────────────────────────────────────────
    // JOB 1: INVENTORY SNAPSHOT (23:59 daily)
    // ─────────────────────────────────────────────────────────

    @Bean
    public Job inventorySnapshotJob(Step inventorySnapshotStep) {
        return new JobBuilder("inventorySnapshotJob", jobRepository)
                .start(inventorySnapshotStep)
                .build();
    }

    @Bean
    public Step inventorySnapshotStep() {
        return new StepBuilder("inventorySnapshotStep", jobRepository)
                .<Inventory, InventorySnapshot>chunk(100, transactionManager)
                .reader(inventorySnapshotReader())
                .processor(inventorySnapshotProcessor())
                .writer(inventorySnapshotWriter())
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<Inventory> inventorySnapshotReader() {
        List<Inventory> all = inventoryRepository.findAll();
        log.info("InventorySnapshot: processing {} records", all.size());
        return new ListItemReader<>(all);
    }

    @Bean
    public ItemProcessor<Inventory, InventorySnapshot> inventorySnapshotProcessor() {
        return inv -> {
            BigDecimal mac = productRepository.findById(inv.getProductId())
                    .map(p -> p.getMacPrice())
                    .orElse(BigDecimal.ZERO);
            BigDecimal value = mac.multiply(BigDecimal.valueOf(inv.getQuantity()));
            return new InventorySnapshot(
                    inv.getProductId(),
                    inv.getWarehouseId(),
                    inv.getQuantity(),
                    mac,
                    value
            );
        };
    }

    @Bean
    public ItemWriter<InventorySnapshot> inventorySnapshotWriter() {
        return snapshots -> {
            BigDecimal totalValue = snapshots.getItems().stream()
                    .map(InventorySnapshot::totalValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            log.info("InventorySnapshot completed: {} items, total value: {}",
                    snapshots.getItems().size(), totalValue);
            // TODO: lưu vào bảng inventory_snapshots (có thể thêm sau)
        };
    }

    // ─────────────────────────────────────────────────────────
    // JOB 2: LOW STOCK SCAN (Scheduled, không dùng Batch đầy đủ)
    // ─────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 1800000) // 30 phút
    public void scanLowStock() {
        log.debug("Running scheduled low-stock scan...");
        List<Inventory> lowStock = inventoryRepository.findAll().stream()
                .filter(Inventory::isLowStock)
                .toList();

        if (!lowStock.isEmpty()) {
            log.info("Low stock scan found {} products below threshold", lowStock.size());
        }
    }

    // ─────────────────────────────────────────────────────────
    // RECORD
    // ─────────────────────────────────────────────────────────
    public record InventorySnapshot(
            java.util.UUID productId,
            java.util.UUID warehouseId,
            int quantity,
            BigDecimal macPrice,
            BigDecimal totalValue
    ) {}
}
