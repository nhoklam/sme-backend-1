package sme.backend.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trigger Spring Batch jobs theo lịch.
 * Cron: "0 59 23 * * *" = 23:59 mỗi ngày
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobLauncher {

    private final JobLauncher jobLauncher;
    private final Job inventorySnapshotJob;

    // Chạy lúc 23:59 mỗi ngày
    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runInventorySnapshot() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            var execution = jobLauncher.run(inventorySnapshotJob, params);
            log.info("InventorySnapshotJob completed with status: {}", execution.getStatus());
        } catch (Exception e) {
            log.error("InventorySnapshotJob failed: {}", e.getMessage(), e);
        }
    }
}
