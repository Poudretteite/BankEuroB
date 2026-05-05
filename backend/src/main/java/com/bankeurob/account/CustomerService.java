package com.bankeurob.account;

import com.bankeurob.account.dto.UpdateCustomerRequest;
import com.bankeurob.security.CustomerUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Customer getMyProfile(Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        return customerRepository.findById(userDetails.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Konto nie znalezione"));
    }

    @Transactional
    public Customer updateContactData(UpdateCustomerRequest request, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Customer customer = customerRepository.findById(userDetails.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Konto nie znalezione"));

        // Aktualizacja tylko dozwolonych pól kontaktowych
        if (request.getPhone() != null) customer.setPhone(request.getPhone());
        if (request.getAddressStreet() != null) customer.setAddressStreet(request.getAddressStreet());
        if (request.getAddressCity() != null) customer.setAddressCity(request.getAddressCity());
        if (request.getAddressCountry() != null) customer.setAddressCountry(request.getAddressCountry());

        return customerRepository.save(customer);
    }
}
