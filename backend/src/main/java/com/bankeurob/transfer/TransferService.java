package com.bankeurob.transfer;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.security.CustomerUserDetails;
import com.bankeurob.transfer.dto.TransactionDto;
import com.bankeurob.transfer.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
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
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

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
        }

        Transaction saved = transactionRepository.save(transaction);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getMyTransactions(String iban, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();

        Account account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new RuntimeException("Konto nie znalezione: " + iban));

        if (!account.getCustomer().getId().equals(userDetails.getCustomerId())) {
            throw new AccessDeniedException("Brak uprawnień do tego konta");
        }

        return transactionRepository.findBySenderIbanOrReceiverIban(iban, iban)
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

        if ("SEPA_SCT".equals(transaction.getTransactionType())) {
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
}
