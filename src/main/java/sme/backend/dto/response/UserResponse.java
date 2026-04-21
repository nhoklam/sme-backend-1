package sme.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private UUID warehouseId;
    private String warehouseName;
    private Boolean isActive;
    private Instant createdAt;
    private String posSettings;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant lastLoginAt;
}