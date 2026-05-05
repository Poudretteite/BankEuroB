package com.bankeurob.account;

import com.bankeurob.account.dto.AccountDto;
import com.bankeurob.security.CustomerUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import com.bankeurob.account.dto.JuniorAccountRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<AccountDto> getMyAccounts(Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        UUID customerId = userDetails.getCustomerId();
        return accountRepository.findByCustomerId(customerId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountById(UUID accountId, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Konto nie znalezione: " + accountId));
        if (!account.getCustomer().getId().equals(userDetails.getCustomerId())) {
            throw new AccessDeniedException("Brak dostępu do tego konta");
        }
        return toDto(account);
    }

    @Transactional
    public void createJuniorAccount(JuniorAccountRequest request, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Customer parent = customerRepository.findById(userDetails.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono rodzica"));

        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email jest już zajęty");
        }

        Customer child = new Customer();
        child.setEmail(request.getEmail());
        child.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        child.setFirstName(request.getFirstName());
        child.setLastName(request.getLastName());
        child.setDateOfBirth(request.getDateOfBirth());
        child.setPesel(request.getPesel());
        child.setPhone(request.getPhone());
        child.setAddressStreet(request.getAddressStreet());
        child.setAddressCity(request.getAddressCity());
        child.setRole("JUNIOR");
        child.setParent(parent);
        child.setAddressCountry(request.getAddressCountry() != null && !request.getAddressCountry().isEmpty() ? request.getAddressCountry() : parent.getAddressCountry());
        
        Customer savedChild = customerRepository.save(child);

        Account parentAccount = accountRepository.findByCustomerId(parent.getId()).stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Rodzic nie ma konta głównego"));

        Account childAccount = new Account();
        childAccount.setCustomer(savedChild);
        childAccount.setIban(generateIban(savedChild.getAddressCountry()));
        childAccount.setAccountType("JUNIOR");
        childAccount.setCurrency("EUR");
        childAccount.setBalance(BigDecimal.ZERO);
        childAccount.setAvailableBalance(BigDecimal.ZERO);
        childAccount.setParentAccount(parentAccount);
        
        accountRepository.save(childAccount);
    }

    @Transactional(readOnly = true)
    public Customer getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono klienta: " + email));
    }

    private String generateIban(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            countryCode = "DE";
        }
        long accountNumber = System.currentTimeMillis() % 1_000_000_000_000_000_000L;
        return String.format("%s89%018d", countryCode.toUpperCase(), accountNumber);
    }

    private AccountDto toDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .iban(account.getIban())
                .bic(account.getBic())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .dailyLimit(account.getDailyLimit())
                .isActive(account.getIsActive())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
