package com.bankeurob.integration.klik;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.account.Customer;
import com.bankeurob.account.CustomerRepository;
import com.bankeurob.integration.klik.dto.*;
import com.bankeurob.integration.klik.model.BlikTransaction;
import com.bankeurob.integration.klik.model.BlikTransactionRepository;
import com.bankeurob.security.CustomerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serwis biznesowy dla płatności BLIK (C2B) po stronie BankEuroB.
 * <p>
 * Odpowiada za:
 * - Generowanie kodu BLIK przez KLIK
 * - Odbieranie webhooka autoryzacyjnego i zapis transakcji
 * - Weryfikację PIN-u klienta
 * - Potwierdzanie/odrzucanie płatności do KLIK
 * - Rzeczywisty odpis środków z konta
 * - Obsługę TIMEOUT i REJECTED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlikService {

    private final KlikServiceClient klikServiceClient;
    private final BlikTransactionRepository blikTransactionRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    // ─────────────────────────────────────────────────
    // Generowanie kodu BLIK
    // ─────────────────────────────────────────────────

    /**
     * Generuje kod BLIK dla zalogowanego klienta.
     * Wysyła żądanie do KLIK z userId = UUID klienta.
     */
    public KlikGenerateCodeResponse generateCode(Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Customer customer = customerRepository.findById(userDetails.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono klienta"));

        String zone = resolveZone(customer);
        String userId = customer.getId().toString();

        log.info("Generowanie kodu BLIK dla klienta {} (userId={}, zone={})",
                customer.getEmail(), userId, zone);

        return klikServiceClient.generateCode(userId, zone);
    }

    // ─────────────────────────────────────────────────
    // Webhook autoryzacyjny od KLIK
    // ─────────────────────────────────────────────────

    /**
     * Odbiera webhook autoryzacyjny od KLIK.
     * Zapisuje transakcję w DB i zwraca potwierdzenie.
     * <p>
     * Klient zostanie powiadomiony o oczekującej transakcji przez polling
     * endpointu GET /api/klik/pending-transactions.
     */
    @Transactional
    public KlikAuthorizeResponse handleAuthorizeWebhook(KlikAuthorizeRequest request) {
        log.info("Webhook autoryzacji KLIK: transactionId={}, userId={}, amount={} {}, merchant={}",
                request.getTransactionId(), request.getUserId(),
                request.getAmount(), request.getCurrency(), request.getMerchantName());

        // Sprawdź czy już istnieje (idempotentność)
        if (blikTransactionRepository.findByKlikTransactionId(request.getTransactionId()).isPresent()) {
            log.info("Transakcja {} już istnieje – pomijam (idempotentność)", request.getTransactionId());
            return new KlikAuthorizeResponse(true, true);
        }

        // Znajdź klienta po userId (UUID przekazane do KLIK przy generowaniu kodu)
        Customer customer = customerRepository.findById(UUID.fromString(request.getUserId()))
                .orElseThrow(() -> {
                    log.error("Nie znaleziono klienta o userId={}", request.getUserId());
                    return new RuntimeException("Nie znaleziono klienta: " + request.getUserId());
                });

        // Znajdź pierwsze aktywne konto klienta
        Account account = accountRepository.findByCustomerId(customer.getId())
                .stream()
                .filter(Account::getIsActive)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Klient {} nie ma aktywnego konta", customer.getId());
                    return new RuntimeException("Brak aktywnego konta dla klienta: " + customer.getId());
                });

        // Zapisz transakcję
        BlikTransaction tx = new BlikTransaction();
        tx.setKlikTransactionId(request.getTransactionId());
        tx.setUserId(request.getUserId());
        tx.setCustomer(customer);
        tx.setAccount(account);
        tx.setAmount(new BigDecimal(request.getAmount()));
        tx.setCurrency(request.getCurrency());
        tx.setMerchantName(request.getMerchantName());
        tx.setOnUs(request.isOnUs());
        tx.setZone(request.getZone() != null ? request.getZone() : resolveZone(customer));
        tx.setExpiryTime(request.getExpiryTime() != null
                ? OffsetDateTime.parse(request.getExpiryTime().replace("Z", "+00:00"))
                : OffsetDateTime.now().plusSeconds(120));
        tx.setStatus("PENDING_AUTHORIZATION");
        tx.setReceivedAt(OffsetDateTime.now());

        blikTransactionRepository.save(tx);

        log.info("Zapisano transakcję BLIK {} dla klienta {} (konto {})",
                tx.getKlikTransactionId(), customer.getEmail(), account.getIban());

        return new KlikAuthorizeResponse(true, true);
    }

    // ─────────────────────────────────────────────────
    // Pobieranie oczekujących transakcji
    // ─────────────────────────────────────────────────

    /**
     * Zwraca listę oczekujących na autoryzację transakcji BLIK
     * dla zalogowanego klienta.
     */
    @Transactional(readOnly = true)
    public List<PendingTransactionDto> getPendingTransactions(Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        UUID customerId = userDetails.getCustomerId();

        // Najpierw oznacz wygasłe jako TIMEOUT
        expireTimedOutTransactions();

        List<BlikTransaction> pending = blikTransactionRepository
                .findByCustomerIdAndStatusOrderByReceivedAtDesc(customerId, "PENDING_AUTHORIZATION");

        return pending.stream()
                .map(this::toPendingDto)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────
    // Weryfikacja PIN-u i autoryzacja płatności
    // ─────────────────────────────────────────────────

    /**
     * Weryfikuje PIN klienta i autoryzuje transakcję BLIK.
     * <p>
     * 1. Weryfikuje PIN
     * 2. Sprawdza czy transakcja istnieje i nie wygasła
     * 3. Sprawdza czy klient ma wystarczające środki
     * 4. Odbpisuje środki z konta
     * 5. Wysyła confirm ACCEPTED do KLIK
     * 6. Zapisuje wynik
     */
    @Transactional
    public BlikConfirmResult authorizeTransaction(
            String klikTransactionId, String pin, Authentication authentication) {

        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Customer customer = customerRepository.findById(userDetails.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono klienta"));

        // 1. Weryfikacja PIN-u
        if (customer.getBlikPin() == null || !customer.getBlikPin().equals(pin)) {
            log.warn("Nieprawidłowy PIN dla klienta {}", customer.getEmail());
            return BlikConfirmResult.failure("Nieprawidłowy PIN");
        }

        // 2. Znajdź transakcję
        BlikTransaction tx = blikTransactionRepository.findByKlikTransactionId(klikTransactionId)
                .orElse(null);

        if (tx == null) {
            log.warn("Transakcja {} nie istnieje", klikTransactionId);
            return BlikConfirmResult.failure("Transakcja nie istnieje");
        }

        // Sprawdź czy transakcja należy do tego klienta
        if (!tx.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Transakcja nie należy do tego klienta");
        }

        // Sprawdź status
        if (!"PENDING_AUTHORIZATION".equals(tx.getStatus())) {
            log.warn("Transakcja {} ma status {} – oczekiwano PENDING_AUTHORIZATION",
                    klikTransactionId, tx.getStatus());
            return BlikConfirmResult.failure("Transakcja została już przetworzona");
        }

        // Sprawdź czy nie wygasła
        if (tx.getExpiryTime() != null && tx.getExpiryTime().isBefore(OffsetDateTime.now())) {
            tx.setStatus("TIMEOUT");
            blikTransactionRepository.save(tx);
            log.warn("Transakcja {} wygasła – oznaczono TIMEOUT", klikTransactionId);
            return BlikConfirmResult.failure("Kod BLIK wygasł – wygeneruj nowy");
        }

        // 3. Sprawdź środki
        Account account = tx.getAccount();
        BigDecimal amount = tx.getAmount();
        BigDecimal overdraftLimit = account.getOverdraftLimit() != null
                ? account.getOverdraftLimit() : BigDecimal.ZERO;
        BigDecimal availableFunds = account.getAvailableBalance().add(overdraftLimit);

        if (availableFunds.compareTo(amount) < 0) {
            log.warn("Niewystarczające środki dla transakcji {}: dostępne {}, wymagane {}",
                    klikTransactionId, availableFunds, amount);

            // Wyślij REJECTED do KLIK z powodu braku środków
            try {
                klikServiceClient.confirmPayment(klikTransactionId, "REJECTED", "INSUFFICIENT_FUNDS");
            } catch (Exception e) {
                log.error("Błąd wysyłania REJECTED do KLIK dla {}: {}", klikTransactionId, e.getMessage());
            }

            tx.setStatus("REJECTED");
            tx.setCompletedAt(OffsetDateTime.now());
            blikTransactionRepository.save(tx);

            return BlikConfirmResult.failure("Niewystarczające środki na koncie");
        }

        // 4. Odpis środków z konta (blokada)
        account.setBalance(account.getBalance().subtract(amount));
        account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
        accountRepository.save(account);

        // 5. Wyślij confirm ACCEPTED do KLIK
        KlikConfirmPaymentResponse klikResponse;
        try {
            klikResponse = klikServiceClient.confirmPayment(klikTransactionId, "ACCEPTED", null);
        } catch (Exception e) {
            log.error("Błąd wysyłania ACCEPTED do KLIK dla {}: {}. Przywracam saldo.",
                    klikTransactionId, e.getMessage());

            // Rollback: przywróć saldo
            account.setBalance(account.getBalance().add(amount));
            account.setAvailableBalance(account.getAvailableBalance().add(amount));
            accountRepository.save(account);

            tx.setStatus("REJECTED");
            tx.setCompletedAt(OffsetDateTime.now());
            blikTransactionRepository.save(tx);

            return BlikConfirmResult.failure("Błąd komunikacji z systemem KLIK: " + e.getMessage());
        }

        // 6. Zaktualizuj transakcję
        tx.setStatus("COMPLETED");
        tx.setAuthorizedAt(OffsetDateTime.now());
        tx.setCompletedAt(OffsetDateTime.now());
        tx.setReferenceNumber("BLK" + System.currentTimeMillis());
        tx.setMerchantNet(new BigDecimal(klikResponse.getMerchantNet() != null
                ? klikResponse.getMerchantNet() : "0"));
        tx.setKlikFee(new BigDecimal(klikResponse.getKlikFee() != null
                ? klikResponse.getKlikFee() : "0"));
        tx.setAgentFee(new BigDecimal(klikResponse.getAgentFee() != null
                ? klikResponse.getAgentFee() : "0"));
        blikTransactionRepository.save(tx);

        log.info("Płatność BLIK {} zakończona sukcesem dla klienta {}. Kwota: {} {}",
                klikTransactionId, customer.getEmail(), amount, tx.getCurrency());

        return BlikConfirmResult.success(
                tx.getReferenceNumber(),
                amount,
                tx.getCurrency(),
                tx.getMerchantName());
    }

    // ─────────────────────────────────────────────────
    // Odrzucenie transakcji przez klienta
    // ─────────────────────────────────────────────────

    /**
     * Odrzuca transakcję BLIK bez autoryzacji PIN-em.
     */
    @Transactional
    public BlikConfirmResult rejectTransaction(String klikTransactionId, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Customer customer = customerRepository.findById(userDetails.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono klienta"));

        BlikTransaction tx = blikTransactionRepository.findByKlikTransactionId(klikTransactionId)
                .orElse(null);

        if (tx == null) {
            return BlikConfirmResult.failure("Transakcja nie istnieje");
        }

        if (!tx.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Transakcja nie należy do tego klienta");
        }

        if (!"PENDING_AUTHORIZATION".equals(tx.getStatus())) {
            return BlikConfirmResult.failure("Transakcja została już przetworzona");
        }

        // Wyślij REJECTED do KLIK
        try {
            klikServiceClient.confirmPayment(klikTransactionId, "REJECTED", "USER_DECLINED");
        } catch (Exception e) {
            log.error("Błąd wysyłania REJECTED do KLIK dla {}: {}", klikTransactionId, e.getMessage());
        }

        tx.setStatus("REJECTED");
        tx.setCompletedAt(OffsetDateTime.now());
        blikTransactionRepository.save(tx);

        log.info("Transakcja BLIK {} odrzucona przez klienta {}", klikTransactionId, customer.getEmail());

        return BlikConfirmResult.failure("Transakcja odrzucona");
    }

    // ─────────────────────────────────────────────────
    // Scheduled task: oznaczanie wygasłych transakcji
    // ─────────────────────────────────────────────────

    /**
     * Co 30 sekund sprawdza i oznacza wygasłe transakcje jako TIMEOUT.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void expireTimedOutTransactions() {
        List<BlikTransaction> expired = blikTransactionRepository
                .findByStatusAndExpiryTimeBefore("PENDING_AUTHORIZATION", OffsetDateTime.now());

        for (BlikTransaction tx : expired) {
            tx.setStatus("TIMEOUT");
            tx.setCompletedAt(OffsetDateTime.now());
            blikTransactionRepository.save(tx);

            log.info("Transakcja BLIK {} wygasła – oznaczono TIMEOUT", tx.getKlikTransactionId());
        }

        if (!expired.isEmpty()) {
            log.info("Oznaczono {} wygasłych transakcji BLIK jako TIMEOUT", expired.size());
        }
    }

    // ─────────────────────────────────────────────────
    // Pomocnicze
    // ─────────────────────────────────────────────────

    private PendingTransactionDto toPendingDto(BlikTransaction tx) {
        long secondsLeft = tx.getExpiryTime() != null
                ? Math.max(0, java.time.Duration.between(OffsetDateTime.now(), tx.getExpiryTime()).getSeconds())
                : 0;

        return PendingTransactionDto.builder()
                .id(tx.getId().toString())
                .klikTransactionId(tx.getKlikTransactionId())
                .merchantName(tx.getMerchantName())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .receivedAt(tx.getReceivedAt())
                .expiresAt(tx.getExpiryTime())
                .secondsLeft(secondsLeft)
                .build();
    }

    private String resolveZone(Customer customer) {
        String country = customer.getAddressCountry();
        if ("PL".equals(country)) return "PL";
        if ("GB".equals(country) || "UK".equals(country)) return "UK";
        if ("US".equals(country)) return "US";
        return "EU"; // default dla DE, FR, IT, ES itp.
    }
}
