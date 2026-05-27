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

        // Walidacja kodu kraju
        String countryCode = request.getAddressCountry();
        if (countryCode != null && !countryCode.isEmpty() && (countryCode.length() != 2 || !countryCode.matches("[A-Za-z]{2}"))) {
            throw new IllegalArgumentException("Kod kraju musi być 2-znakowym kodem ISO (np. DE, PL, FR). Podano: " + countryCode);
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
        child.setAddressCountry(countryCode != null && !countryCode.isEmpty() ? countryCode.toUpperCase() : parent.getAddressCountry());
        
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
        countryCode = countryCode.toUpperCase();
        
        long accountNumber = System.currentTimeMillis() % 1_000_000_000_000_000L;
        String bban;
        
        if ("PL".equals(countryCode)) {
            bban = String.format("11402004%016d", accountNumber); // PL bban ma 24 znaki
        } else {
            bban = String.format("37040044%010d", accountNumber); // DE bban ma 18 znaków
        }
        
        // Obliczanie sumy kontrolnej wg MOD-97
        int char1 = countryCode.charAt(0) - 55;
        int char2 = countryCode.charAt(1) - 55;
        String toCheck = bban + char1 + char2 + "00";
        java.math.BigInteger bigInt = new java.math.BigInteger(toCheck);
        int mod = bigInt.remainder(new java.math.BigInteger("97")).intValue();
        int checksum = 98 - mod;
        
        return String.format("%s%02d%s", countryCode, checksum, bban);
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
