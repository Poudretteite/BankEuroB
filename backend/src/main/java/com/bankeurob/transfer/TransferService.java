package com.bankeurob.transfer;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.integration.sepa.batch.SepaBatchClient;
import com.bankeurob.integration.sepa.instant.SepaInstantClient;
import com.bankeurob.integration.swift.SwiftServiceClient;
import com.bankeurob.integration.swift.SwiftXmlGenerator;
import com.bankeurob.integration.swift.dto.SwiftMessageResponse;
import com.bankeurob.integration.target.TargetServiceClient;
import com.bankeurob.integration.target.dto.SettlementRequest;
import com.bankeurob.integration.target.dto.SettlementResponse;
import com.bankeurob.integration.xml.Pain001Generator;
import com.bankeurob.integration.klik.model.BlikTransaction;
import com.bankeurob.integration.klik.model.BlikTransactionRepository;
import com.bankeurob.security.CustomerUserDetails;
import com.bankeurob.transfer.dto.TargetIncomingWebhookDto;
import com.bankeurob.transfer.dto.TransactionDto;
import com.bankeurob.transfer.dto.TransferRequest;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TargetServiceClient targetClient;
    private final SepaBatchClient sepaBatchClient;
    private final SepaInstantClient sepaInstantClient;
    private final SwiftServiceClient swiftClient;
    private final SwiftXmlGenerator swiftXmlGenerator;
    private final Pain001Generator pain001Generator;
    private final com.bankeurob.transfer.aml.AmlService amlService;
    private final BlikTransactionRepository blikTransactionRepository;

    /**
     * Waliduje IBAN zgodnie ze standardem MOD-97 (ISO 7064).
     * Sprawdza długość, dozwolone znaki i sumę kontrolną.
     */
    private void validateIban(String iban, String fieldName) {
        if (iban == null || iban.isBlank()) {
            throw new IllegalArgumentException(fieldName + " jest wymagany");
        }

        String clean = iban.replaceAll("\\s+", "").toUpperCase();

        if (clean.length() < 15 || clean.length() > 34) {
            throw new IllegalArgumentException("Nieprawidłowa długość " + fieldName + ": " + clean.length() + " znaków (oczekiwano 15-34)");
        }

        if (!clean.matches("[A-Z0-9]+")) {
            throw new IllegalArgumentException(fieldName + " zawiera niedozwolone znaki. Dozwolone: A-Z, 0-9");
        }

        // Przeniesienie 4 pierwszych znaków na koniec
        String rearranged = clean.substring(4) + clean.substring(0, 4);

        // Zamiana liter na cyfry (A=10, B=11, ..., Z=35)
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(c - 55);
            } else {
                numeric.append(c);
            }
        }

        // Sprawdzenie MOD-97
        BigInteger ibanNumber = new BigInteger(numeric.toString());
        BigInteger mod97 = ibanNumber.mod(BigInteger.valueOf(97));

        if (!mod97.equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("Nieprawidłowa suma kontrolna " + fieldName + " (MOD-97)");
        }
    }

    @Transactional
    public TransactionDto createTransfer(TransferRequest request, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();

        // Walidacja IBAN nadawcy (MOD-97)
        validateIban(request.getSenderIban(), "IBAN nadawcy");

        // Walidacja IBAN odbiorcy (MOD-97)
        validateIban(request.getReceiverIban(), "IBAN odbiorcy");

        // Pobierz i zweryfikuj konto nadawcy (z blokadą pesymistyczną dla bezpieczeństwa współbieżności)
        Account senderAccount = accountRepository.findByIbanWithLock(request.getSenderIban())
                .orElseThrow(() -> new RuntimeException("Konto nadawcy nie znalezione: " + request.getSenderIban()));

        if (!senderAccount.getCustomer().getId().equals(userDetails.getCustomerId())) {
            throw new AccessDeniedException("Brak uprawnień do konta nadawcy");
        }

        if (!senderAccount.getIsActive()) {
            throw new IllegalStateException("Konto nadawcy jest nieaktywne");
        }

        // Zabezpieczenie przed przelewem na własne konto
        if (request.getSenderIban().equals(request.getReceiverIban())) {
            throw new IllegalArgumentException("Nie można wykonać przelewu na własne konto");
        }

        BigDecimal overdraftLimit = senderAccount.getOverdraftLimit() != null ? senderAccount.getOverdraftLimit() : BigDecimal.ZERO;
        BigDecimal availableFunds = senderAccount.getAvailableBalance().add(overdraftLimit);

        BigDecimal fee = getFee(request.getTransferType());
        BigDecimal totalAmount = request.getAmount().add(fee);

        BigDecimal overdraftFee = BigDecimal.ZERO;
        if (senderAccount.getAvailableBalance().compareTo(BigDecimal.ZERO) >= 0 &&
            senderAccount.getAvailableBalance().compareTo(totalAmount) < 0) {
            overdraftFee = new BigDecimal("2.00");
            totalAmount = totalAmount.add(overdraftFee);
        }

        if (availableFunds.compareTo(totalAmount) < 0) {
            throw new IllegalStateException(
                    "Niewystarczające środki (Kwota: " + request.getAmount() + " EUR, Opłaty: " + fee.add(overdraftFee) + " EUR). Dostępne saldo: " + senderAccount.getAvailableBalance() + " EUR, Limit debetowy: " + overdraftLimit + " EUR."
            );
        }

        // Sprawdzenie dziennego limitu (dailyLimit)
        if (senderAccount.getDailyLimit() != null && senderAccount.getDailyLimit().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal todayTotal = transactionRepository
                    .findTodayTotalBySenderAccountId(senderAccount.getId());
            BigDecimal afterTransfer = todayTotal.add(request.getAmount());
            if (afterTransfer.compareTo(senderAccount.getDailyLimit()) > 0) {
                throw new IllegalStateException(
                        "Przekroczono dzienny limit transakcji. Limit: " + senderAccount.getDailyLimit()
                        + " EUR, dotychczas wykorzystano: " + todayTotal + " EUR, próba: " + request.getAmount() + " EUR."
                );
            }
        }

        boolean isInternal = "INTERNAL".equals(request.getTransferType());
        if (isInternal) {
            Account receiverAccount = accountRepository.findByIban(request.getReceiverIban())
                    .orElseThrow(() -> new IllegalArgumentException("Dla przelewów wewnętrznych, konto odbiorcy musi należeć do BankEuroB."));

            // Sprawdzenie, czy konto odbiorcy jest aktywne
            if (!Boolean.TRUE.equals(receiverAccount.getIsActive())) {
                throw new IllegalStateException("Konto odbiorcy jest nieaktywne. Przelew wewnętrzny nie może zostać zrealizowany.");
            }
        }

        // Walidacja BIC dla przelewów międzybankowych
        if (!isInternal && (request.getReceiverBic() == null || request.getReceiverBic().isBlank())) {
            throw new IllegalArgumentException("Dla przelewów " + request.getTransferType() + " wymagane jest pole receiverBic (BIC banku odbiorcy).");
        }

        // Tworzenie rekordu transakcji
        Transaction transaction = new Transaction();
        transaction.setReferenceNumber(generateReferenceNumber());
        transaction.setTransactionType(request.getTransferType());

        boolean isJunior = "JUNIOR".equals(senderAccount.getAccountType());

        transaction.setTitle(request.getTitle());
        transaction.setRequestedAt(OffsetDateTime.now());

        if (amlService.isSuspicious(transaction)) {
            transaction.setStatus("AML_BLOCKED");
            transaction.setAmlStatus("BLOCKED");
        } else if (isJunior) {
            transaction.setStatus("PENDING");
        } else if ("SEPA_SCT".equals(request.getTransferType())) {
            transaction.setStatus("PROCESSING");
        } else {
            transaction.setStatus("COMPLETED");
            transaction.setCompletedAt(OffsetDateTime.now());
        }
        transaction.setSenderAccount(senderAccount);
        transaction.setSenderIban(senderAccount.getIban());
        transaction.setSenderName(
                senderAccount.getCustomer().getFirstName() + " " + senderAccount.getCustomer().getLastName()
        );
        transaction.setSenderBic(senderAccount.getBic());
        transaction.setReceiverIban(request.getReceiverIban());
        transaction.setReceiverName(request.getReceiverName());
        transaction.setReceiverBic(request.getReceiverBic());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(senderAccount.getCurrency());
        // Usunięto ustawianie tytułu i daty tutaj, bo przeniesiono wyżej przed sprawdzeniem AML

        if (!isJunior && !"AML_BLOCKED".equals(transaction.getStatus())) {
            // Zaktualizuj saldo nadawcy (kwota + prowizja)
            senderAccount.setBalance(senderAccount.getBalance().subtract(totalAmount));
            senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().subtract(totalAmount));
            accountRepository.save(senderAccount);

            // Dla przelewów wewnętrznych — zaktualizuj saldo odbiorcy natychmiast
            if (isInternal) {
                accountRepository.findByIban(request.getReceiverIban()).ifPresent(receiverAccount -> {
                    receiverAccount.setBalance(receiverAccount.getBalance().add(request.getAmount()));
                    receiverAccount.setAvailableBalance(receiverAccount.getAvailableBalance().add(request.getAmount()));
                    accountRepository.save(receiverAccount);
                    transaction.setReceiverName(
                            receiverAccount.getCustomer().getFirstName() + " " + receiverAccount.getCustomer().getLastName()
                    );
                });
            }

            // ─────────────────────────────────────────────────
            // Integracja z zewnętrznymi systemami rozliczeniowymi
            // ─────────────────────────────────────────────────

            try {
                if ("SEPA_SCT".equals(request.getTransferType())) {
                    // Przelew SEPA Batch
                    log.info("Inicjowanie SEPA Batch dla przelewu {}", transaction.getReferenceNumber());

                    String xml = pain001Generator.generate(request, senderAccount);
                    String responseXml = sepaBatchClient.submitTransferXml(xml);

                    transaction.setStatus("PROCESSING");
                    log.info("SEPA Batch przyjęty do kolejki dla {}", transaction.getReferenceNumber());
                } else if ("RTGS_TARGET2".equals(request.getTransferType())) {
                    // Rozliczenie przez TARGET (RTGS)
                    log.info("Inicjowanie settlement TARGET dla przelewu {} typu RTGS_TARGET2",
                            transaction.getReferenceNumber());

                    SettlementResponse settlement = targetClient.settlePayment(new SettlementRequest(
                            transaction.getId().toString(),
                            senderAccount.getBic(),
                            request.getReceiverBic(),
                            senderAccount.getIban(),
                            request.getReceiverIban(),
                            request.getAmount(),
                            senderAccount.getCurrency(),
                            request.getTitle(),
                            "customer"
                    ));

                    transaction.setExternalMessageId(settlement.getTransactionId());

                    if ("SETTLED".equalsIgnoreCase(settlement.getStatus()) ||
                        "COMPLETED".equalsIgnoreCase(settlement.getStatus())) {
                        transaction.setStatus("COMPLETED");
                        transaction.setCompletedAt(OffsetDateTime.now());
                        log.info("TARGET settlement udany dla {}", transaction.getReferenceNumber());
                    } else {
                        log.warn("TARGET settlement status: {} dla {}", settlement.getStatus(), transaction.getReferenceNumber());
                        transaction.setStatus("PROCESSING");
                    }

                } else if ("SWIFT".equals(request.getTransferType())) {
                    // Przelew SWIFT przez SWIFT Middleware (SWIFT-Aplikacje-Biznesowe)
                    log.info("Inicjowanie przelewu SWIFT przez SWIFT Middleware dla {}",
                            transaction.getReferenceNumber());

                    String xml = swiftXmlGenerator.generate(request, senderAccount);
                    SwiftMessageResponse swiftResponse = swiftClient.submitSwiftMessage(xml);

                    transaction.setExternalMessageId(swiftResponse.getUetr());

                    if ("accepted".equalsIgnoreCase(swiftResponse.getStatus())) {
                        transaction.setStatus("PROCESSING");
                        log.info("SWIFT komunikat przyjęty: UETR={}, trasa={}, ETA={}s",
                                swiftResponse.getUetr(),
                                swiftResponse.getRoute(),
                                swiftResponse.getEstimatedSeconds());
                    } else {
                        log.warn("SWIFT Middleware status: {} dla {}", swiftResponse.getStatus(),
                                transaction.getReferenceNumber());
                        transaction.setStatus("PROCESSING");
                    }

                } else if ("SEPA_INSTANT".equals(request.getTransferType())) {
                    // Przelew natychmiastowy przez SEPA Instant Service
                    log.info("Inicjowanie SEPA Instant dla przelewu {}", transaction.getReferenceNumber());

                    String xml = pain001Generator.generate(request, senderAccount);
                    String responseXml = sepaInstantClient.submitInstantTransferXml(xml);

                    transaction.setStatus("COMPLETED");
                    transaction.setCompletedAt(OffsetDateTime.now());
                    log.info("SEPA Instant udany dla {}", transaction.getReferenceNumber());
                }
            } catch (Exception e) {
                log.error("Błąd integracji dla przelewu {}: {}. Transakcja oznaczona jako FAILED, saldo przywrócone.",
                        transaction.getReferenceNumber(), e.getMessage());

                // Rollback: przywróć saldo nadawcy
                senderAccount.setBalance(senderAccount.getBalance().add(totalAmount));
                senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().add(totalAmount));
                accountRepository.save(senderAccount);

                transaction.setStatus("FAILED");
                transaction.setCompletedAt(OffsetDateTime.now());
            }
        } else if ("AML_BLOCKED".equals(transaction.getStatus())) {
            senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().subtract(totalAmount));
            accountRepository.save(senderAccount);
        } else if (isJunior) {
            senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().subtract(totalAmount));
            accountRepository.save(senderAccount);
        }

        Transaction saved = transactionRepository.save(transaction);

        // Logowanie audytowe dla przelewów wewnętrznych
        if (isInternal) {
            log.info("AUDYT: Przelew wewnętrzny | Ref: {} | Nadawca: {} ({}) | Odbiorca: {} | Kwota: {} {} | Status: {}",
                    saved.getReferenceNumber(),
                    saved.getSenderName(), saved.getSenderIban(),
                    saved.getReceiverIban(),
                    saved.getAmount(), saved.getCurrency(),
                    saved.getStatus());
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getMyTransactions(String iban, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();

        final String cleanIban = iban != null ? iban.replaceAll("\\s+", "") : null;

        Account account = accountRepository.findByIban(cleanIban)
                .orElseThrow(() -> new RuntimeException("Konto nie znalezione: " + cleanIban));

        if (!account.getCustomer().getId().equals(userDetails.getCustomerId())) {
            throw new AccessDeniedException("Brak uprawnień do tego konta");
        }

        // Pobierz standardowe transakcje
        List<TransactionDto> transferTxs = transactionRepository
                .findBySenderIbanOrReceiverIbanOrderByRequestedAtDesc(cleanIban, cleanIban)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        // Pobierz transakcje BLIK dla tego konta
        List<TransactionDto> blikTxs = blikTransactionRepository
                .findByAccountIdOrderByReceivedAtDesc(account.getId())
                .stream()
                .filter(bt -> !"PENDING_AUTHORIZATION".equals(bt.getStatus()))
                .map(bt -> blikToDto(bt, account))
                .collect(Collectors.toList());

        // Połącz i posortuj po dacie malejąco
        List<TransactionDto> all = new ArrayList<>(transferTxs.size() + blikTxs.size());
        all.addAll(transferTxs);
        all.addAll(blikTxs);
        all.sort(Comparator.comparing(TransactionDto::getRequestedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        return all;
    }

    @Transactional
    public void approveJuniorTransaction(UUID transactionId, boolean approved, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono przelewu"));

        Account senderAccount = transaction.getSenderAccount();
        if (!"JUNIOR".equals(senderAccount.getAccountType())) {
            throw new RuntimeException("Przelew nie pochodzi z konta JUNIOR");
        }
        if (!senderAccount.getParentAccount().getCustomer().getId().equals(userDetails.getCustomerId())) {
            throw new RuntimeException("Brak dostępu do zatwierdzenia tego przelewu");
        }

        if (!"PENDING".equals(transaction.getStatus())) {
            throw new RuntimeException("Przelew nie oczekuje na zatwierdzenie");
        }

        if (!approved) {
            transaction.setStatus("REJECTED");
            transaction.setCompletedAt(OffsetDateTime.now());
            transactionRepository.save(transaction);
            log.info("AUDYT: Przelew Juniora ODRZUCONY | Ref: {} | Kwota: {} {}",
                    transaction.getReferenceNumber(), transaction.getAmount(), transaction.getCurrency());
            return;
        }

        BigDecimal fee = getFee(transaction.getTransactionType());
        BigDecimal totalAmount = transaction.getAmount().add(fee);

        if (senderAccount.getAvailableBalance().compareTo(totalAmount) < 0) {
            transaction.setStatus("FAILED");
            transaction.setCompletedAt(OffsetDateTime.now());
            transactionRepository.save(transaction);
            throw new IllegalStateException("Niewystarczające saldo na koncie Junior");
        }

        // Process deduction
        if (!"AML_BLOCKED".equals(transaction.getStatus())) {
            senderAccount.setBalance(senderAccount.getBalance().subtract(totalAmount));
            senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().subtract(totalAmount));
            accountRepository.save(senderAccount);
        } else if ("AML_BLOCKED".equals(transaction.getStatus())) {
            senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().subtract(totalAmount));
            accountRepository.save(senderAccount);
        }

        Transaction savedTransaction = transactionRepository.save(transaction);

        if ("AML_BLOCKED".equals(savedTransaction.getStatus())) {
            return;
        }

        boolean isInternal = "INTERNAL".equals(transaction.getTransactionType());
        if (isInternal) {
            accountRepository.findByIban(transaction.getReceiverIban()).ifPresent(receiverAccount -> {
                receiverAccount.setBalance(receiverAccount.getBalance().add(transaction.getAmount()));
                receiverAccount.setAvailableBalance(receiverAccount.getAvailableBalance().add(transaction.getAmount()));
                accountRepository.save(receiverAccount);
            });
        }

        // Dla przelewów międzybankowych Juniora — wyślij do zewnętrznego systemu
        try {
            if ("SEPA_SCT".equals(transaction.getTransactionType())) {
                TransferRequest dummyRequest = new TransferRequest();
                dummyRequest.setSenderIban(transaction.getSenderIban());
                dummyRequest.setReceiverIban(transaction.getReceiverIban());
                dummyRequest.setReceiverName(transaction.getReceiverName());
                dummyRequest.setReceiverBic(transaction.getReceiverBic());
                dummyRequest.setAmount(transaction.getAmount());
                dummyRequest.setTitle(transaction.getTitle());
                dummyRequest.setTransferType(transaction.getTransactionType());

                String xml = pain001Generator.generate(dummyRequest, senderAccount);
                sepaBatchClient.submitTransferXml(xml);
            } else if ("RTGS_TARGET2".equals(transaction.getTransactionType())) {
                SettlementResponse settlement = targetClient.settlePayment(new SettlementRequest(
                        transaction.getId().toString(),
                        senderAccount.getBic(),
                        transaction.getReceiverBic(),
                        senderAccount.getIban(),
                        transaction.getReceiverIban(),
                        transaction.getAmount(),
                        senderAccount.getCurrency(),
                        transaction.getTitle(),
                        "customer"
                ));
                transaction.setExternalMessageId(settlement.getTransactionId());
            } else if ("SWIFT".equals(transaction.getTransactionType())) {
                // Przelew SWIFT Juniora przez SWIFT Middleware
                TransferRequest dummyRequest = new TransferRequest();
                dummyRequest.setSenderIban(transaction.getSenderIban());
                dummyRequest.setReceiverIban(transaction.getReceiverIban());
                dummyRequest.setReceiverName(transaction.getReceiverName());
                dummyRequest.setReceiverBic(transaction.getReceiverBic());
                dummyRequest.setAmount(transaction.getAmount());
                dummyRequest.setTitle(transaction.getTitle());
                dummyRequest.setTransferType(transaction.getTransactionType());

                String xml = swiftXmlGenerator.generate(dummyRequest, senderAccount);
                SwiftMessageResponse swiftResponse = swiftClient.submitSwiftMessage(xml);
                transaction.setExternalMessageId(swiftResponse.getUetr());
            } else if ("SEPA_INSTANT".equals(transaction.getTransactionType())) {
                // Dla Juniora tworzymy uproszczony transfer request
                TransferRequest dummyRequest = new TransferRequest();
                dummyRequest.setSenderIban(transaction.getSenderIban());
                dummyRequest.setReceiverIban(transaction.getReceiverIban());
                dummyRequest.setReceiverName(transaction.getReceiverName());
                dummyRequest.setReceiverBic(transaction.getReceiverBic());
                dummyRequest.setAmount(transaction.getAmount());
                dummyRequest.setTitle(transaction.getTitle());
                dummyRequest.setTransferType(transaction.getTransactionType());

                String xml = pain001Generator.generate(dummyRequest, senderAccount);
                sepaInstantClient.submitInstantTransferXml(xml);
            }
        } catch (Exception e) {
            log.error("Błąd integracji dla przelewu Juniora {}: {}", transaction.getReferenceNumber(), e.getMessage());
            // Rollback
            senderAccount.setBalance(senderAccount.getBalance().add(totalAmount));
            senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().add(totalAmount));
            accountRepository.save(senderAccount);
            transaction.setStatus("FAILED");
            transaction.setCompletedAt(OffsetDateTime.now());
            transactionRepository.save(transaction);
            throw new RuntimeException("Nie udało się przetworzyć przelewu przez system zewnętrzny: " + e.getMessage());
        }

        if ("SEPA_SCT".equals(transaction.getTransactionType()) || "SWIFT".equals(transaction.getTransactionType())) {
            transaction.setStatus("PROCESSING");
        } else {
            transaction.setStatus("COMPLETED");
            transaction.setCompletedAt(OffsetDateTime.now());
        }
        transactionRepository.save(transaction);

        // Logowanie audytowe dla zatwierdzonych przelewów Juniora
        log.info("AUDYT: Przelew Juniora ZATWIERDZONY | Ref: {} | Nadawca: {} | Odbiorca: {} | Kwota: {} {} | Typ: {}",
                transaction.getReferenceNumber(),
                transaction.getSenderIban(),
                transaction.getReceiverIban(),
                transaction.getAmount(), transaction.getCurrency(),
                transaction.getTransactionType());
    }

    private TransactionDto toDto(Transaction tx) {
        return TransactionDto.builder()
                .id(tx.getId())
                .referenceNumber(tx.getReferenceNumber())
                .transactionType(tx.getTransactionType())
                .status(tx.getStatus())
                .senderIban(tx.getSenderIban())
                .senderName(tx.getSenderName())
                .receiverIban(tx.getReceiverIban())
                .receiverName(tx.getReceiverName())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .title(tx.getTitle())
                .requestedAt(tx.getRequestedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }

    /**
     * Konwertuje transakcję BLIK na TransactionDto,
     * tak aby wyświetlała się w historii obok standardowych przelewów.
     */
    private TransactionDto blikToDto(BlikTransaction bt, Account account) {
        String status = bt.getStatus();
        // Mapowanie statusów BLIK na wspólne statusy historii
        if ("TIMEOUT".equals(status)) {
            status = "FAILED";
        } else if ("PENDING_AUTHORIZATION".equals(status) || "AUTHORIZED".equals(status)) {
            status = "PROCESSING";
        }

        return TransactionDto.builder()
                .id(bt.getId())
                .referenceNumber(bt.getReferenceNumber())
                .transactionType("BLIK")
                .status(status)
                .senderIban(account.getIban())
                .senderName(account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName())
                .receiverIban(null)
                .receiverName(bt.getMerchantName() != null ? bt.getMerchantName() : "Płatność BLIK")
                .amount(bt.getAmount())
                .currency(bt.getCurrency())
                .title("Płatność BLIK – " + (bt.getMerchantName() != null ? bt.getMerchantName() : "Sklep"))
                .requestedAt(bt.getReceivedAt())
                .completedAt(bt.getCompletedAt())
                .build();
    }

    private String generateReferenceNumber() {
        return "BEB" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private BigDecimal getFee(String transferType) {
        if ("SEPA_INSTANT".equals(transferType)) {
            return new BigDecimal("0.50");
        } else if ("RTGS_TARGET2".equals(transferType)) {
            return new BigDecimal("5.00");
        }
        return BigDecimal.ZERO;
    }

    @Transactional
    public void handleIncomingTargetWebhook(TargetIncomingWebhookDto dto) {
        // Znajdź konto odbiorcy w naszym banku
        final String cleanIban = dto.getReceiverIban() != null ? dto.getReceiverIban().replaceAll("\\s+", "") : null;
        
        Account receiverAccount = accountRepository.findByIban(cleanIban)
                .orElseThrow(() -> new RuntimeException("Konto odbiorcy nie znalezione w BankEuroB: " + cleanIban));

        // Księgowanie środków na koncie odbiorcy
        receiverAccount.setBalance(receiverAccount.getBalance().add(dto.getAmount()));
        receiverAccount.setAvailableBalance(receiverAccount.getAvailableBalance().add(dto.getAmount()));
        accountRepository.save(receiverAccount);

        // Zapisanie transakcji jako przychodzącej
        Transaction transaction = new Transaction();
        transaction.setReferenceNumber(generateReferenceNumber());
        
        String event = dto.getEvent();
        if ("payment.settled".equals(event)) {
            transaction.setTransactionType("INCOMING_SEPA");
        } else {
            transaction.setTransactionType("INCOMING_TARGET");
        }

        transaction.setStatus("COMPLETED");
        
        transaction.setSenderIban(dto.getSenderIban() != null ? dto.getSenderIban() : "EXTERNAL_BANK");
        transaction.setSenderName("Bank zewnętrzny (" + transaction.getTransactionType() + ")");
        transaction.setSenderBic(dto.getSenderBic());
        
        transaction.setReceiverIban(receiverAccount.getIban());
        transaction.setReceiverName(receiverAccount.getCustomer().getFirstName() + " " + receiverAccount.getCustomer().getLastName());
        
        transaction.setAmount(dto.getAmount());
        transaction.setCurrency(dto.getCurrency());
        transaction.setTitle(dto.getDescription() != null ? dto.getDescription() : "Przelew przychodzący");
        transaction.setExternalMessageId(dto.getTransferId());
        transaction.setCompletedAt(OffsetDateTime.now());

        transactionRepository.save(transaction);
        log.info("Pomyślnie zaksięgowano przychodzący przelew {} o ID {} na konto {}", transaction.getTransactionType(), dto.getTransferId(), receiverAccount.getIban());
    }

    @Transactional
    public void handleIncomingSwiftWebhook(String xmlMessage) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlMessage)));

            XPath xPath = XPathFactory.newInstance().newXPath();

            String receiverIban = xPath.compile("//CdtrAcct/Id/Othr/Id").evaluate(doc);
            if (receiverIban == null || receiverIban.isBlank()) {
                receiverIban = xPath.compile("//CdtrAcct/Id/IBAN").evaluate(doc); // Fallback
            }
            String amountStr = xPath.compile("//IntrBkSttlmAmt").evaluate(doc);
            String currency = xPath.compile("//IntrBkSttlmAmt/@Ccy").evaluate(doc);
            String senderBic = xPath.compile("//DbtrAgt/FinInstnId/BICFI").evaluate(doc);
            String title = xPath.compile("//RmtInf/Ustrd").evaluate(doc);
            String senderName = xPath.compile("//Dbtr/Nm").evaluate(doc);
            String transactionId = xPath.compile("//MsgId").evaluate(doc);

            if (receiverIban == null || receiverIban.isBlank()) {
                throw new IllegalArgumentException("Brak IBAN odbiorcy w komunikacie SWIFT XML");
            }

            final String cleanIban = receiverIban.replaceAll("\\s+", "");
            Account receiverAccount = accountRepository.findByIban(cleanIban)
                    .orElseThrow(() -> new RuntimeException("Konto odbiorcy nie znalezione w BankEuroB: " + cleanIban));

            BigDecimal amount = new BigDecimal(amountStr);

            // Księgowanie środków na koncie odbiorcy
            receiverAccount.setBalance(receiverAccount.getBalance().add(amount));
            receiverAccount.setAvailableBalance(receiverAccount.getAvailableBalance().add(amount));
            accountRepository.save(receiverAccount);

            // Zapisanie transakcji jako przychodzącej
            Transaction transaction = new Transaction();
            transaction.setReferenceNumber(generateReferenceNumber());
            transaction.setTransactionType("INCOMING_SWIFT");
            transaction.setStatus("COMPLETED");

            transaction.setSenderIban("EXTERNAL_BANK");
            transaction.setSenderName(senderName != null && !senderName.isBlank() ? senderName : "Bank zagraniczny (SWIFT)");
            transaction.setSenderBic(senderBic);

            transaction.setReceiverIban(receiverAccount.getIban());
            transaction.setReceiverName(receiverAccount.getCustomer().getFirstName() + " " + receiverAccount.getCustomer().getLastName());

            transaction.setAmount(amount);
            transaction.setCurrency(currency);
            transaction.setTitle(title != null && !title.isBlank() ? title : "Przelew przychodzący SWIFT");
            transaction.setExternalMessageId(transactionId);
            transaction.setCompletedAt(OffsetDateTime.now());

            transactionRepository.save(transaction);
            log.info("Pomyślnie zaksięgowano przychodzący przelew SWIFT o MsgId {} na konto {}", transactionId, receiverAccount.getIban());

        } catch (Exception e) {
            log.error("Błąd podczas przetwarzania przychodzącego webhooka SWIFT: {}", e.getMessage(), e);
            throw new RuntimeException("Nie udało się przetworzyć komunikatu SWIFT", e);
        }
    }

    public void processExternalRouting(Transaction transaction) {
        Account senderAccount = accountRepository.findById(transaction.getSenderAccount().getId())
                .orElseThrow(() -> new RuntimeException("Konto nie istnieje"));
        
        // Zdejmujemy w końcu kwotę z Salda głównego (dostępne saldo zostało już zablokowane wcześniej)
        BigDecimal fee = getFee(transaction.getTransactionType());
        BigDecimal totalAmount = transaction.getAmount().add(fee);
        senderAccount.setBalance(senderAccount.getBalance().subtract(totalAmount));
        accountRepository.save(senderAccount);

        TransferRequest dummyRequest = new TransferRequest();
        dummyRequest.setReceiverIban(transaction.getReceiverIban());
        dummyRequest.setReceiverName(transaction.getReceiverName());
        dummyRequest.setReceiverBic(transaction.getReceiverBic());
        dummyRequest.setAmount(transaction.getAmount());
        dummyRequest.setTitle(transaction.getTitle());
        dummyRequest.setTransferType(transaction.getTransactionType());

        if ("INTERNAL".equals(transaction.getTransactionType())) {
            accountRepository.findByIban(transaction.getReceiverIban()).ifPresent(receiverAccount -> {
                receiverAccount.setBalance(receiverAccount.getBalance().add(transaction.getAmount()));
                receiverAccount.setAvailableBalance(receiverAccount.getAvailableBalance().add(transaction.getAmount()));
                accountRepository.save(receiverAccount);
            });
            transaction.setStatus("COMPLETED");
            transaction.setCompletedAt(OffsetDateTime.now());
            transactionRepository.save(transaction);
        } else if ("SEPA_SCT".equals(transaction.getTransactionType())) {
            transaction.setStatus("PROCESSING");
            transactionRepository.save(transaction);
            
            org.springframework.scheduling.annotation.AsyncResult.forValue(null)
                .completable()
                .thenRun(() -> {
                    try {
                        String xml = pain001Generator.generate(dummyRequest, senderAccount);
                        sepaBatchClient.submitTransferXml(xml);
                    } catch (Exception e) {
                        log.error("Błąd wysyłki po odblokowaniu AML do SEPA Batch", e);
                    }
                });
        } else if ("SEPA_INSTANT".equals(transaction.getTransactionType())) {
            try {
                String xml = pain001Generator.generate(dummyRequest, senderAccount);
                sepaInstantClient.submitInstantTransferXml(xml);
                transaction.setStatus("COMPLETED");
                transaction.setCompletedAt(OffsetDateTime.now());
                transactionRepository.save(transaction);
            } catch (Exception e) {
                log.error("Błąd wysyłki po odblokowaniu AML do SEPA Instant", e);
            }
        } else if ("SWIFT".equals(transaction.getTransactionType())) {
            try {
                String xml = swiftXmlGenerator.generate(dummyRequest, senderAccount);
                com.bankeurob.integration.swift.dto.SwiftMessageResponse swiftResponse = swiftClient.submitSwiftMessage(xml);
                transaction.setExternalMessageId(swiftResponse.getUetr());
                transaction.setStatus("PROCESSING");
                transactionRepository.save(transaction);
            } catch (Exception e) {
                log.error("Błąd wysyłki po odblokowaniu AML do SWIFT", e);
            }
        } else if ("RTGS_TARGET2".equals(transaction.getTransactionType())) {
            transaction.setStatus("PROCESSING");
            transactionRepository.save(transaction);
            
            try {
                com.bankeurob.integration.target.dto.SettlementRequest req = new com.bankeurob.integration.target.dto.SettlementRequest(
                    transaction.getId().toString(),
                    senderAccount.getBic(),
                    transaction.getReceiverBic(),
                    senderAccount.getIban(),
                    transaction.getReceiverIban(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getTitle(),
                    "customer"
                );
                targetClient.settlePayment(req);
            } catch (Exception e) {
                log.error("Błąd wysyłki po odblokowaniu AML do TARGET2", e);
            }
        } else {
            transaction.setStatus("COMPLETED");
            transaction.setCompletedAt(OffsetDateTime.now());
            transactionRepository.save(transaction);
        }
    }

    public void refundTransaction(Transaction transaction) {
        Account senderAccount = accountRepository.findById(transaction.getSenderAccount().getId())
                .orElseThrow(() -> new RuntimeException("Nie znaleziono konta nadawcy"));
        
        BigDecimal fee = getFee(transaction.getTransactionType());
        BigDecimal totalAmount = transaction.getAmount().add(fee);
        
        senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().add(totalAmount));
        accountRepository.save(senderAccount);
    }
}
