package sme.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.dto.request.ChangePasswordRequest;
import sme.backend.dto.request.CreateUserRequest;
import sme.backend.dto.request.LoginRequest;
import sme.backend.dto.response.AuthResponse;
import sme.backend.dto.response.UserResponse;
import sme.backend.entity.User;
import sme.backend.entity.Warehouse;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.UserRepository;
import sme.backend.repository.WarehouseRepository;
import sme.backend.security.UserPrincipal;
import sme.backend.security.jwt.JwtTokenProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // Cập nhật last_login_at
        userRepository.findByUsernameAndIsActiveTrue(principal.getUsername())
                .ifPresent(u -> {
                    u.setLastLoginAt(Instant.now());
                    userRepository.save(u);
                });

        String accessToken  = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal.getUsername());

        String warehouseName = null;
        if (principal.getWarehouseId() != null) {
            warehouseName = warehouseRepository.findById(principal.getWarehouseId())
                    .map(Warehouse::getName).orElse(null);
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .user(toUserResponse(principal, warehouseName))
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // REFRESH TOKEN
    // ─────────────────────────────────────────────────────────
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("INVALID_TOKEN", "Refresh token không hợp lệ hoặc đã hết hạn");
        }
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsernameAndIsActiveTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        UserPrincipal principal = UserPrincipal.build(user);
        String newAccessToken  = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // USER MANAGEMENT (ADMIN)
    // ─────────────────────────────────────────────────────────
    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("DUPLICATE_USERNAME",
                    "Tên đăng nhập '" + req.getUsername() + "' đã tồn tại");
        }

        User.UserRole role;
        try {
            role = User.UserRole.valueOf(req.getRole());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ROLE",
                    "Role không hợp lệ: " + req.getRole());
        }

        // MANAGER / CASHIER bắt buộc có warehouseId
        if (role != User.UserRole.ROLE_ADMIN && req.getWarehouseId() == null) {
            throw new BusinessException("WAREHOUSE_REQUIRED",
                    "Manager và Cashier phải được gán vào một chi nhánh");
        }

        User user = User.builder()
                .username(req.getUsername())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .role(role)
                .warehouseId(req.getWarehouseId())
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("Created user: {} with role: {}", user.getUsername(), user.getRole());
        return mapToResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse toggleUserActive(UUID userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setIsActive(active);
        return mapToResponse(userRepository.save(user));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAllActive().stream()
                .map(this::mapToResponse).toList();
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────
    private UserResponse toUserResponse(UserPrincipal p, String warehouseName) {
        return UserResponse.builder()
                .id(p.getId())
                .username(p.getUsername())
                .fullName(p.getFullName())
                .role(p.getRole().name())
                .warehouseId(p.getWarehouseId())
                .warehouseName(warehouseName)
                .isActive(p.isEnabled())
                .build();
    }

    public UserResponse mapToResponse(User user) {
        String warehouseName = null;
        if (user.getWarehouseId() != null) {
            warehouseName = warehouseRepository.findById(user.getWarehouseId())
                    .map(Warehouse::getName).orElse(null);
        }
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .warehouseId(user.getWarehouseId())
                .warehouseName(warehouseName)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
