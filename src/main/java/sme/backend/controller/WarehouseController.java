package sme.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sme.backend.dto.response.ApiResponse;
import sme.backend.entity.Warehouse;
import sme.backend.exception.BusinessException;
import sme.backend.exception.ResourceNotFoundException;
import sme.backend.repository.WarehouseRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseRepository warehouseRepository;

    /** GET /warehouses */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Warehouse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(
                warehouseRepository.findByIsActiveTrueOrderByName()));
    }

    /** GET /warehouses/{id} */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Warehouse>> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                warehouseRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id))));
    }

    /** POST /warehouses — SYS-01 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Warehouse>> create(
            @RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (warehouseRepository.existsByCode(code)) {
            throw new BusinessException("DUPLICATE_CODE",
                    "Mã chi nhánh '" + code + "' đã tồn tại");
        }
        Warehouse warehouse = Warehouse.builder()
                .code(code)
                .name(body.get("name"))
                .provinceCode(body.get("provinceCode"))
                .address(body.get("address"))
                .phone(body.get("phone"))
                .isActive(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(warehouseRepository.save(warehouse)));
    }

    /** PUT /warehouses/{id} */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Warehouse>> update(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
        if (body.containsKey("name"))         w.setName(body.get("name"));
        if (body.containsKey("provinceCode")) w.setProvinceCode(body.get("provinceCode"));
        if (body.containsKey("address"))      w.setAddress(body.get("address"));
        if (body.containsKey("phone"))        w.setPhone(body.get("phone"));
        return ResponseEntity.ok(ApiResponse.ok(warehouseRepository.save(w)));
    }

    /** PATCH /warehouses/{id}/deactivate */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Warehouse>> deactivate(@PathVariable UUID id) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
        w.setIsActive(false);
        return ResponseEntity.ok(ApiResponse.ok(warehouseRepository.save(w)));
    }
}
