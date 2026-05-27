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
import com.bankeurob.security.CustomerUserDetails;
import com.bankeurob.transfer.dto.TargetIncomingWebhookDto;
import com.bankeurob.transfer.dto.TransactionDto;
import com.bankeurob.transfer.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Transactional
    public TransactionDto createTransfer(TransferRequest request, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();

        // Pobierz i zweryfikuj konto nadawcy
        Account senderAccount = accountRepository.findByIban(request.getSenderIban())
                .orElseThrow(() -> new RuntimeException("Konto nadawcy nie znalezione: " + request.getSenderIban()));

        if (!senderAccount.getCustomer().getId().equals(userDetails.getCustomerId())) {
            throw new AccessDeniedException("Brak uprawnień do konta nadawcy");
        }

        if (!senderAccount.getIsActive()) {
            throw new IllegalStateException("Konto nadawcy jest nieaktywne");
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

        boolean isInternal = "INTERNAL".equals(request.getTransferType());
        if (isInternal) {
            boolean receiverExists = accountRepository.findByIban(request.getReceiverIban()).isPresent();
            if (!receiverExists) {
                throw new IllegalArgumentException("Dla przelewów wewnętrznych, konto odbiorcy musi należeć do BankEuroB.");
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

        if (isJunior) {
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
        transaction.setTitle(request.getTitle());
        transaction.setRequestedAt(OffsetDateTime.now());

        if (!isJunior) {
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
                            transaction.getReferenceNumber(),
                            senderAccount.getBic(),
                            request.getReceiverBic(),
                            request.getAmount(),
                            senderAccount.getCurrency(),
                            request.getTitle(),
                            request.getTransferType()
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
        }

        Transaction saved = transactionRepository.save(transaction);

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

        return transactionRepository.findBySenderIbanOrReceiverIbanOrderByRequestedAtDesc(cleanIban, cleanIban)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
        senderAccount.setBalance(senderAccount.getBalance().subtract(totalAmount));
        senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().subtract(totalAmount));
        accountRepository.save(senderAccount);

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
                        transaction.getReferenceNumber(),
                        senderAccount.getBic(),
                        transaction.getReceiverBic(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getTitle(),
                        transaction.getTransactionType()
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
        transaction.setTransactionType("INCOMING_TARGET");
        transaction.setStatus("COMPLETED");
        
        transaction.setSenderIban("EXTERNAL_BANK");
        transaction.setSenderName("Bank zewnętrzny (TARGET)");
        transaction.setSenderBic(dto.getSenderBic());
        
        transaction.setReceiverIban(receiverAccount.getIban());
        transaction.setReceiverName(receiverAccount.getCustomer().getFirstName() + " " + receiverAccount.getCustomer().getLastName());
        
        transaction.setAmount(dto.getAmount());
        transaction.setCurrency(dto.getCurrency());
        transaction.setTitle(dto.getTitle() != null ? dto.getTitle() : "Przelew przychodzący TARGET");
        transaction.setExternalMessageId(dto.getTransactionId());
        transaction.setCompletedAt(OffsetDateTime.now());

        transactionRepository.save(transaction);
        log.info("Pomyślnie zaksięgowano przychodzący przelew TARGET o ID {} na konto {}", dto.getTransactionId(), receiverAccount.getIban());
    }
}
