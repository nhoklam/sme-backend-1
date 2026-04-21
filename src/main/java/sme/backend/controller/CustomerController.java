package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.entity.Customer;
import sme.backend.entity.Invoice;
import sme.backend.entity.Order;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.CustomerRepository;
import sme.backend.repository.InvoiceRepository;
import sme.backend.repository.OrderRepository;
import sme.backend.service.CustomerService; // <-- THÊM IMPORT NÀY

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository; 
    private final OrderRepository orderRepository;
    private final CustomerService customerService; // <-- THÊM DÒNG NÀY

    /** GET /customers/lookup?phone=... — POS-03: Định danh khách (F3) */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Customer>> lookupByPhone(@RequestParam String phone) {
        Customer customer = customerRepository.findByPhoneNumberAndIsActiveTrue(phone)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khách hàng với SĐT: " + phone));
        return ResponseEntity.ok(ApiResponse.ok(customer));
    }

    /** GET /customers — Tìm kiếm CRM (ĐÃ NÂNG CẤP THÊM LỌC THEO TIER) */
    @GetMapping
    @PreAuthorize("hasAnyRole('CASHIER','MANAGER','ADMIN')") 
    public ResponseEntity<ApiResponse<PageResponse<Customer>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String kw = (keyword == null) ? "" : keyword.trim();
        
        Customer.CustomerTier customerTier = null;
        if (tier != null && !tier.isBlank()) {
            try {
                customerTier = Customer.CustomerTier.valueOf(tier.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(customerRepository.searchWithFilters(kw, customerTier, pageable))));
    }

    /** GET /customers/top — Lấy top khách hàng chi tiêu cao */
    @GetMapping("/top")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<Customer>>> getTopSpenders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(customerRepository.findTopCustomers(pageable))));
    }

    /** GET /customers/{id}/history — Lấy lịch sử mua hàng (POS & Online) */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomerHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Invoice> invoices = invoiceRepository.findByCustomerIdOrderByCreatedAtDesc(id, pageable);
        Page<Order> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(id, pageable);

        var invoiceSummary = invoices.getContent().stream().map(inv -> Map.of(
                "id", inv.getId(),
                "code", inv.getCode(),
                "type", "POS",
                "totalAmount", inv.getTotalAmount(),
                "finalAmount", inv.getFinalAmount(),
                "createdAt", inv.getCreatedAt()
        )).toList();

        var orderSummary = orders.getContent().stream().map(ord -> Map.of(
                "id", ord.getId(),
                "code", ord.getCode(),
                "type", "ONLINE",
                "status", ord.getStatus().name(),
                "totalAmount", ord.getTotalAmount(),
                "finalAmount", ord.getFinalAmount(),
                "createdAt", ord.getCreatedAt()
        )).toList();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "invoices", invoiceSummary,
            "orders", orderSummary
        )));
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
                .address(body.get("address"))
                .gender(body.get("gender"))
                .notes(body.get("notes"))
                .isActive(true)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(customerRepository.save(customer)));
    }

    // === THÊM ENDPOINT MỚI: POST /customers/bulk ===
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> importBulk(@RequestBody List<Customer> requests) {
        int importedCount = customerService.importBulkCustomers(requests);
        return ResponseEntity.ok(ApiResponse.ok("Import thành công " + importedCount + " khách hàng", importedCount));
    }

    /** PUT /customers/{id} — Cập nhật thông tin / Khóa tài khoản */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Customer>> update(
            @PathVariable UUID id,
            @RequestBody java.util.Map<String, Object> body) {
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        if (body.containsKey("phoneNumber") && body.get("phoneNumber") != null) {
            String newPhone = (String) body.get("phoneNumber");
            if (!newPhone.equals(customer.getPhoneNumber()) && customerRepository.existsByPhoneNumber(newPhone)) {
                throw new BusinessException("DUPLICATE_PHONE", "Số điện thoại này đã được sử dụng bởi khách hàng khác");
            }
            customer.setPhoneNumber(newPhone);
        }

        if (body.containsKey("fullName")) customer.setFullName((String) body.get("fullName"));
        if (body.containsKey("email")) customer.setEmail((String) body.get("email"));
        if (body.containsKey("address")) customer.setAddress((String) body.get("address"));
        
        if (body.containsKey("dateOfBirth")) {
            String dobStr = (String) body.get("dateOfBirth");
            if (dobStr != null && !dobStr.isBlank()) {
                customer.setDateOfBirth(java.time.LocalDate.parse(dobStr));
            } else {
                customer.setDateOfBirth(null);
            }
        }
        
        if (body.containsKey("gender")) customer.setGender((String) body.get("gender"));
        if (body.containsKey("notes")) customer.setNotes((String) body.get("notes"));
        if (body.containsKey("isActive")) customer.setIsActive((Boolean) body.get("isActive"));

        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thành công", customerRepository.save(customer)));
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