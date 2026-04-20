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

        if (senderAccount.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException(
                    "Niewystarczające saldo. Dostępne: " + senderAccount.getAvailableBalance() + " EUR"
            );
        }

        // Tworzenie rekordu transakcji
        Transaction transaction = new Transaction();
        transaction.setReferenceNumber(generateReferenceNumber());
        transaction.setTransactionType(request.getTransferType());
        transaction.setStatus("PROCESSING");
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

        // Zaktualizuj saldo nadawcy
        senderAccount.setBalance(senderAccount.getBalance().subtract(request.getAmount()));
        senderAccount.setAvailableBalance(senderAccount.getAvailableBalance().subtract(request.getAmount()));
        accountRepository.save(senderAccount);

        // Dla przelewów wewnętrznych — zaktualizuj saldo odbiorcy natychmiast
        boolean isInternal = "INTERNAL".equals(request.getTransferType());
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

        transaction.setStatus("COMPLETED");
        transaction.setCompletedAt(OffsetDateTime.now());
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
}
