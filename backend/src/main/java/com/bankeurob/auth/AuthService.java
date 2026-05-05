package com.bankeurob.auth;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.account.Customer;
import com.bankeurob.account.CustomerRepository;
import com.bankeurob.auth.dto.AuthResponse;
import com.bankeurob.auth.dto.LoginRequest;
import com.bankeurob.auth.dto.RegisterRequest;
import com.bankeurob.security.CustomerUserDetails;
import com.bankeurob.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email jest już zajęty: " + request.getEmail());
        }

        Customer customer = new Customer();
        customer.setEmail(request.getEmail());
        customer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setPhone(request.getPhone());
        customer.setAddressStreet(request.getAddressStreet());
        customer.setAddressCity(request.getAddressCity());
        customer.setAddressCountry(request.getAddressCountry());
        customer.setPesel(request.getPesel());
        customer.setRole("CUSTOMER");

        Customer saved = customerRepository.save(customer);

        // Automatycznie utwórz konto w EUR
        Account account = new Account();
        account.setCustomer(saved);
        String countryCode = request.getAddressCountry() != null ? request.getAddressCountry() : "DE";
        account.setIban(generateIban(countryCode));
        account.setAccountType("STANDARD");
        account.setCurrency("EUR");
        account.setBalance(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);

        int age = java.time.Period.between(saved.getDateOfBirth(), java.time.LocalDate.now()).getYears();
        if (age >= 18) {
            account.setOverdraftLimit(new BigDecimal("500.00"));
        } else {
            account.setOverdraftLimit(BigDecimal.ZERO);
        }

        accountRepository.save(account);

        CustomerUserDetails userDetails = new CustomerUserDetails(saved);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .customerId(saved.getId())
                .email(saved.getEmail())
                .firstName(saved.getFirstName())
                .lastName(saved.getLastName())
                .role(saved.getRole())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            Customer customer = customerRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Klient nie znaleziony"));

            if ("JUNIOR".equals(customer.getRole())) {
                LoginAttempt attempt = new LoginAttempt();
                attempt.setCustomer(customer);
                attempt.setStatus("PENDING");
                attempt = loginAttemptRepository.save(attempt);

                return AuthResponse.builder()
                        .requiresParentApproval(true)
                        .loginAttemptId(attempt.getId())
                        .customerId(customer.getId())
                        .email(customer.getEmail())
                        .firstName(customer.getFirstName())
                        .lastName(customer.getLastName())
                        .role(customer.getRole())
                        .build();
            }

            CustomerUserDetails userDetails = new CustomerUserDetails(customer);
            String token = jwtService.generateToken(userDetails);

            return AuthResponse.builder()
                    .token(token)
                    .requiresParentApproval(false)
                    .customerId(customer.getId())
                    .email(customer.getEmail())
                    .firstName(customer.getFirstName())
                    .lastName(customer.getLastName())
                    .role(customer.getRole())
                    .build();
        } catch (Exception e) {
            System.err.println("BŁĄD LOGOWANIA: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Transactional
    public AuthResponse checkLoginStatus(UUID loginAttemptId) {
        LoginAttempt attempt = loginAttemptRepository.findById(loginAttemptId)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono próby logowania"));

        if ("REJECTED".equals(attempt.getStatus())) {
            throw new RuntimeException("Logowanie odrzucone przez rodzica");
        }

        if ("PENDING".equals(attempt.getStatus())) {
            return AuthResponse.builder()
                    .requiresParentApproval(true)
                    .loginAttemptId(attempt.getId())
                    .build();
        }

        if ("APPROVED".equals(attempt.getStatus())) {
            attempt.setStatus("CONSUMED");
            loginAttemptRepository.save(attempt);

            Customer customer = attempt.getCustomer();
            CustomerUserDetails userDetails = new CustomerUserDetails(customer);
            String token = jwtService.generateToken(userDetails);

            return AuthResponse.builder()
                    .token(token)
                    .requiresParentApproval(false)
                    .customerId(customer.getId())
                    .email(customer.getEmail())
                    .firstName(customer.getFirstName())
                    .lastName(customer.getLastName())
                    .role(customer.getRole())
                    .build();
        }

        throw new RuntimeException("Nieprawidłowy status logowania");
    }

    /**
     * Generuje uproszczony IBAN na podstawie wybranego kraju.
     * W produkcji należałoby użyć algorytmu ISO 13616.
     */
    private String generateIban(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            countryCode = "DE";
        }
        long accountNumber = System.currentTimeMillis() % 1_000_000_000_000_000_000L;
        return String.format("%s89%018d", countryCode.toUpperCase(), accountNumber);
    }
}
