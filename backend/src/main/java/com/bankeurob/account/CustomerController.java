package com.bankeurob.account;

import com.bankeurob.account.dto.UpdateCustomerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/me")
    public ResponseEntity<Customer> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(customerService.getMyProfile(authentication));
    }

    @PutMapping("/me")
    public ResponseEntity<Customer> updateContactData(@RequestBody UpdateCustomerRequest request, Authentication authentication) {
        return ResponseEntity.ok(customerService.updateContactData(request, authentication));
    }
}
