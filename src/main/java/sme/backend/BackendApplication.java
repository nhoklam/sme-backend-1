package sme.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * SME Omnichannel ERP & POS - Backend Application
 *
 * Tech Stack:
 * - Java 21, Spring Boot 3.4.3
 * - PostgreSQL + pgvector, Redis
 * - Spring Security (JWT), Spring AI (Gemini)
 * - WebSocket (STOMP), Spring Batch
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")  // Tự động điền createdBy / updatedBy
@EnableJpaRepositories(basePackages = "sme.backend.repository")
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
