package com.bankeurob.config;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.account.Customer;
import com.bankeurob.account.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (customerRepository.count() > 0) {
            log.info("DataSeeder: baza już zawiera dane – pomijam inicjalizację.");
            return;
        }

        log.info("DataSeeder: ładuję dane testowe...");

        // ─── ADMIN ───────────────────────────────────────────
        Customer admin = createCustomer(
                "admin@bankeurob.eu",
                "admin123",
                "Admin",
                "BankEuroB",
                LocalDate.of(1980, 1, 1),
                "ADMIN"
        );
        createAccount(admin, "DE89370400440532013000", BigDecimal.ZERO);

        // ─── Klient testowy 1: Anna Kowalski ─────────────────
        Customer anna = createCustomer(
                "anna.kowalski@example.com",
                "password123",
                "Anna",
                "Kowalski",
                LocalDate.of(1990, 5, 15),
                "CUSTOMER"
        );
        createAccount(anna, "DE89370400440000100001", new BigDecimal("10000.00"));

        // ─── Klient testowy 2: Jan Nowak ─────────────────────
        Customer jan = createCustomer(
                "jan.nowak@example.com",
                "password123",
                "Jan",
                "Nowak",
                LocalDate.of(1985, 3, 22),
                "CUSTOMER"
        );
        createAccount(jan, "DE89370400440000200002", new BigDecimal("5000.00"));

        log.info("""
                DataSeeder: inicjalizacja zakończona!
                ╔══════════════════════════════════════════════╗
                ║           KONTA TESTOWE BANKEUROB            ║
                ╠══════════════════════════════════════════════╣
                ║ admin@bankeurob.eu       / admin123          ║
                ║ anna.kowalski@example.com / password123      ║
                ║ jan.nowak@example.com    / password123       ║
                ╚══════════════════════════════════════════════╝
                """);
    }

    private Customer createCustomer(String email, String password, String firstName,
                                    String lastName, LocalDate dob, String role) {
        Customer c = new Customer();
        c.setEmail(email);
        c.setPasswordHash(passwordEncoder.encode(password));
        c.setFirstName(firstName);
        c.setLastName(lastName);
        c.setDateOfBirth(dob);
        c.setRole(role);
        c.setAddressCountry("DE");
        return customerRepository.save(c);
    }

    private void createAccount(Customer customer, String iban, BigDecimal balance) {
        Account a = new Account();
        a.setCustomer(customer);
        a.setIban(iban);
        a.setAccountType("STANDARD");
        a.setCurrency("EUR");
        a.setBalance(balance);
        a.setAvailableBalance(balance);
        accountRepository.save(a);
    }
}
