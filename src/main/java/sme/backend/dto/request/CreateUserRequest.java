package sme.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 4, max = 100)
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150)
    private String fullName;

    private String email;

    private String phone;

    @NotBlank(message = "Vai trò không được để trống")
    private String role;  // ROLE_ADMIN, ROLE_MANAGER, ROLE_CASHIER

    private UUID warehouseId;
}
