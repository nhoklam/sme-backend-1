package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.entity.Customer;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CustomerRepository;

import java.util.UUID;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;

    /** GET /customers/lookup?phone=... — POS-03: Định danh khách (F3) */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Customer>> lookupByPhone(@RequestParam String phone) {
        Customer customer = customerRepository.findByPhoneNumberAndIsActiveTrue(phone)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khách hàng với SĐT: " + phone));
        return ResponseEntity.ok(ApiResponse.ok(customer));
    }

    /** GET /customers — Tìm kiếm CRM */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<Customer>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size);
        if (keyword != null && !keyword.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    PageResponse.of(customerRepository.search(keyword, pageable))));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(customerRepository.findAll(pageable))));
    }

    /** POST /customers — Tạo khách hàng mới */
    @PostMapping
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Customer>> create(
            @RequestBody java.util.Map<String, String> body) {
        String phone    = body.get("phoneNumber");
        String fullName = body.get("fullName");
        if (phone == null || fullName == null) {
            throw new BusinessException("MISSING_FIELDS", "phoneNumber và fullName bắt buộc");
        }
        if (customerRepository.existsByPhoneNumber(phone)) {
            throw new BusinessException("DUPLICATE_PHONE",
                    "Số điện thoại '" + phone + "' đã được đăng ký");
        }
        Customer customer = Customer.builder()
                .phoneNumber(phone)
                .fullName(fullName)
                .email(body.get("email"))
                .isActive(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(customerRepository.save(customer)));
    }

    /** GET /customers/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Customer>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer", id))));
    }
}
