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
    private final com.bankeurob.transfer.TransactionRepository transactionRepository;
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

        // ─── Klient testowy 1: Anna Kowalska ─────────────────
        Customer anna = createCustomer(
                "anna.kowalska@example.com",
                "password123",
                "Anna",
                "Kowalska",
                LocalDate.of(1990, 5, 15),
                "CUSTOMER"
        );
        createAccount(anna, "DE36370400440000100001", new BigDecimal("10000.00"));

        // ─── Klient testowy 2: Jan Nowak ─────────────────────
        Customer jan = createCustomer(
                "jan.nowak@example.com",
                "password123",
                "Jan",
                "Nowak",
                LocalDate.of(1985, 3, 22),
                "CUSTOMER"
        );
        createAccount(jan, "DE04370400440000200002", new BigDecimal("5000.00"));

        // ─── Klient testowy 3: Kamil Kowalski (JUNIOR) ────────
        Customer kamil = createJunior(
                "junior@example.com",
                "password123",
                "Kamil",
                "Kowalski",
                LocalDate.of(2015, 1, 1),
                anna
        );
        createJuniorAccount(kamil, "DE69370400440000300003", new BigDecimal("150.00"), anna);

        // ─── Przelewy testowe (Historia transakcji) ───────────
        Account annaAccount = accountRepository.findByIban("DE36370400440000100001").orElseThrow();
        Account janAccount = accountRepository.findByIban("DE04370400440000200002").orElseThrow();

        createTransaction(annaAccount, "DE04370400440000200002", "Jan Nowak", new BigDecimal("250.00"), "Za zakupy");
        createTransaction(janAccount, "DE36370400440000100001", "Anna Kowalska", new BigDecimal("50.00"), "Zwrot za kino");
        createTransaction(annaAccount, "PL123456789012345678901234", "Zewnętrzny Sklep", new BigDecimal("120.50"), "Zamówienie nr 12345");

        log.info("""
                DataSeeder: inicjalizacja zakończona!
                ╔══════════════════════════════════════════════╗
                ║           KONTA TESTOWE BANKEUROB            ║
                ╠══════════════════════════════════════════════╣
                ║ admin@bankeurob.eu       / admin123          ║
                ║ anna.kowalska@example.com / password123      ║
                ║ jan.nowak@example.com    / password123       ║
                ║ junior@example.com       / password123       ║
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

    private Customer createJunior(String email, String password, String firstName,
                                  String lastName, LocalDate dob, Customer parent) {
        Customer c = new Customer();
        c.setEmail(email);
        c.setPasswordHash(passwordEncoder.encode(password));
        c.setFirstName(firstName);
        c.setLastName(lastName);
        c.setDateOfBirth(dob);
        c.setRole("JUNIOR");
        c.setParent(parent);
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
        a.setOverdraftLimit(new BigDecimal("500.00"));
        accountRepository.save(a);
    }

    private void createJuniorAccount(Customer customer, String iban, BigDecimal balance, Customer parent) {
        // Znajdź konto rodzica
        Account parentAccount = accountRepository.findByCustomerId(parent.getId()).stream().findFirst().orElse(null);

        Account a = new Account();
        a.setCustomer(customer);
        a.setIban(iban);
        a.setAccountType("JUNIOR");
        a.setCurrency("EUR");
        a.setBalance(balance);
        a.setAvailableBalance(balance);
        a.setParentAccount(parentAccount);
        a.setDailyLimit(new BigDecimal("50.00"));
        accountRepository.save(a);
    }

    private void createTransaction(Account senderAccount, String receiverIban, String receiverName, BigDecimal amount, String title) {
        com.bankeurob.transfer.Transaction t = new com.bankeurob.transfer.Transaction();
        t.setReferenceNumber(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        t.setTransactionType("INTERNAL");
        t.setStatus("COMPLETED");
        
        t.setSenderAccount(senderAccount);
        t.setSenderIban(senderAccount.getIban());
        t.setSenderName(senderAccount.getCustomer().getFirstName() + " " + senderAccount.getCustomer().getLastName());
        
        t.setReceiverIban(receiverIban);
        t.setReceiverName(receiverName);
        
        t.setAmount(amount);
        t.setCurrency("EUR");
        t.setTitle(title);
        
        t.setRequestedAt(java.time.OffsetDateTime.now().minusDays(new java.util.Random().nextInt(30)));
        t.setCompletedAt(t.getRequestedAt().plusMinutes(5));
        t.setValueDate(t.getRequestedAt().toLocalDate());
        
        transactionRepository.save(t);
    }
}
