package sme.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sme.backend.entity.Customer;
import sme.backend.repository.CustomerRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Import hàng loạt khách hàng từ Excel
     * Bỏ qua các khách hàng có số điện thoại đã tồn tại
     */
    @Transactional
    public int importBulkCustomers(List<Customer> requests) {
        int importedCount = 0;
        for (Customer req : requests) {
            // Chỉ lưu nếu Số điện thoại chưa tồn tại trong hệ thống (chống trùng lặp)
            if (!customerRepository.existsByPhoneNumber(req.getPhoneNumber())) {
                Customer customer = Customer.builder()
                        .fullName(req.getFullName())
                        .phoneNumber(req.getPhoneNumber())
                        .email(req.getEmail())
                        .address(req.getAddress())
                        .gender(req.getGender() != null ? req.getGender() : "OTHER")
                        .loyaltyPoints(0)
                        .totalSpent(BigDecimal.ZERO)
                        .customerTier(Customer.CustomerTier.STANDARD)
                        .isActive(true)
                        .notes(req.getNotes())
                        .build();
                customerRepository.save(customer);
                importedCount++;
            }
        }
        return importedCount;
    }
}