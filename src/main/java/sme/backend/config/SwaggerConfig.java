package sme.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SME Omnichannel ERP & POS API")
                        .description("Tài liệu API cho hệ thống quản lý bán hàng đa kênh")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Admin")
                                .email("admin@sme.vn"))
                        .license(new License().name("SME License").url("https://sme.vn")))
                // Cấu hình nút Authorize nhập Token (JWT)
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components().addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()));
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer")
                .description("Hãy nhập JWT Token của bạn vào đây (Không cần gõ chữ Bearer ở đầu)");
    }
}