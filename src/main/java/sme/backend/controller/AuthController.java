package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.request.ChangePasswordRequest;
import sme.backend.dto.request.CreateUserRequest;
import sme.backend.dto.request.LoginRequest;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.AuthResponse;
import sme.backend.dto.response.UserResponse;
import sme.backend.security.UserPrincipal;
import sme.backend.service.AuthService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /auth/login */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req) {
        AuthResponse response = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", response));
    }

    /** POST /auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<AuthResponse>builder()
                            .success(false).message("refreshToken bắt buộc").build());
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(refreshToken)));
    }

    /** GET /auth/me */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        // ĐÃ SỬA: Loại bỏ authService.mapToResponse() vì luồng này đã trả về UserResponse
        return ResponseEntity.ok(ApiResponse.ok(
                authService.getAllUsers().stream()
                        .filter(u -> u.getId().equals(principal.getId()))
                        .findFirst()
                        .orElseThrow()
        ));
    }

    /** PUT /auth/change-password */
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(principal.getId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công", null));
    }

    // ── User Management (ADMIN only) ──────────────────────────

    /** GET /auth/users */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(authService.getAllUsers()));
    }

    /** POST /auth/users */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(authService.createUser(req)));
    }

    /** PATCH /auth/users/{id}/activate */
    @PatchMapping("/users/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(authService.toggleUserActive(id, true)));
    }

    /** PATCH /auth/users/{id}/deactivate */
    @PatchMapping("/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(authService.toggleUserActive(id, false)));
    }
}