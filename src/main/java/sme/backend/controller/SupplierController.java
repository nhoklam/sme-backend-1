package sme.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.dto.response.PageResponse;
import sme.backend.entity.Supplier;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.SupplierRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository supplierRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Supplier>>> getAll(
            @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    supplierRepository.searchByName(keyword, PageRequest.of(0, 50)).getContent()));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                supplierRepository.findByIsActiveTrueOrderByName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Supplier>> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                supplierRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Supplier", id))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Supplier>> create(@RequestBody Map<String, Object> body) {
        String taxCode = (String) body.get("taxCode");
        if (taxCode != null && supplierRepository.existsByTaxCode(taxCode)) {
            throw new BusinessException("DUPLICATE_TAX_CODE",
                    "Mã số thuế '" + taxCode + "' đã tồn tại");
        }
        Supplier supplier = Supplier.builder()
                .taxCode(taxCode)
                .name((String) body.get("name"))
                .contactPerson((String) body.get("contactPerson"))
                .phone((String) body.get("phone"))
                .email((String) body.get("email"))
                .address((String) body.get("address"))
                .bankAccount((String) body.get("bankAccount"))
                .bankName((String) body.get("bankName"))
                .paymentTerms(body.get("paymentTerms") != null
                        ? Integer.parseInt(body.get("paymentTerms").toString()) : 30)
                .isActive(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(supplierRepository.save(supplier)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Supplier>> update(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Supplier s = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
        if (body.containsKey("name"))          s.setName((String) body.get("name"));
        if (body.containsKey("contactPerson")) s.setContactPerson((String) body.get("contactPerson"));
        if (body.containsKey("phone"))         s.setPhone((String) body.get("phone"));
        if (body.containsKey("email"))         s.setEmail((String) body.get("email"));
        if (body.containsKey("bankAccount"))   s.setBankAccount((String) body.get("bankAccount"));
        if (body.containsKey("bankName"))      s.setBankName((String) body.get("bankName"));
        if (body.containsKey("notes"))         s.setNotes((String) body.get("notes"));
        return ResponseEntity.ok(ApiResponse.ok(supplierRepository.save(s)));
    }
}
