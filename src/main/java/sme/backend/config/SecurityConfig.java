package sme.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import sme.backend.security.CustomUserDetailsService;
import sme.backend.security.filter.JwtAuthEntryPoint;
import sme.backend.security.filter.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // Bật @PreAuthorize / @PostAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Tắt CSRF (dùng JWT stateless)
            .csrf(AbstractHttpConfigurer::disable)

            // CORS xử lý bởi CorsConfig bean
            .cors(cors -> {})

            // Stateless session (JWT)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Entry point 401
            .exceptionHandling(ex ->
                    ex.authenticationEntryPoint(jwtAuthEntryPoint))

            // ============================================================
            // PHÂN QUYỀN THEO ROLE (RBAC)
            // ============================================================
            .authorizeHttpRequests(auth -> auth

                // Public endpoints
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/ws/**").permitAll()          // WebSocket handshake

                // ── MODULE 0: POS ──────────────────────────────────────
                .requestMatchers("/pos/**")
                    .hasAnyRole("CASHIER", "MANAGER", "ADMIN")

                // ── MODULE 1: INVENTORY ────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/inventory/**")
                    .hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                .requestMatchers("/inventory/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── MODULE 2: PURCHASE ORDERS ──────────────────────────
                .requestMatchers("/purchase-orders/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── MODULE 3: TRANSFERS ────────────────────────────────
                .requestMatchers("/transfers/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── MODULE 4: E-COMMERCE & ORDERS ─────────────────────
                .requestMatchers(HttpMethod.GET, "/orders/**")
                    .hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                .requestMatchers("/orders/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── MODULE 5: CRM ──────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/customers/**")
                    .hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                .requestMatchers("/customers/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── MODULE 6: FINANCE ──────────────────────────────────
                .requestMatchers("/finance/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── MODULE 7: REPORTS ──────────────────────────────────
                .requestMatchers("/reports/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── MODULE 8: ADMIN SETTINGS ───────────────────────────
                .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                .requestMatchers("/users/**")
                    .hasRole("ADMIN")
                .requestMatchers("/warehouses/**")
                    .hasRole("ADMIN")

                // ── MODULE AI ──────────────────────────────────────────
                .requestMatchers("/ai/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── NOTIFICATIONS ──────────────────────────────────────
                .requestMatchers("/notifications/**")
                    .hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                .anyRequest().authenticated()
            )

            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
