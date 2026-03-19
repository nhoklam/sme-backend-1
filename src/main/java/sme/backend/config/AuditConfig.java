package sme.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import sme.backend.security.UserPrincipal;

import java.util.Optional;

/**
 * Cung cấp thông tin "ai đang thực hiện hành động" cho Spring Data Auditing.
 * Tự động điền vào cột created_by / updated_by của BaseEntity.
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || auth.getPrincipal().equals("anonymousUser")) {
                return Optional.of("SYSTEM");
            }
            if (auth.getPrincipal() instanceof UserPrincipal principal) {
                return Optional.of(principal.getUsername());
            }
            return Optional.of(auth.getName());
        };
    }
}
