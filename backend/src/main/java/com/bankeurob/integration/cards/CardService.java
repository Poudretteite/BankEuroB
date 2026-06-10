package com.bankeurob.integration.cards;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.account.Customer;
import com.bankeurob.account.CustomerRepository;
import com.bankeurob.integration.cards.dto.CardWebhookRequest;
import com.bankeurob.integration.cards.dto.CardsIssueResponse;
import com.bankeurob.integration.cards.dto.IssueCardRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private final CardsServiceClient cardsServiceClient;
    private final PaymentCardRepository paymentCardRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final com.bankeurob.transfer.TransactionRepository transactionRepository;

    @Transactional
    public CardsIssueResponse issueCardForUser(String userEmail, IssueCardRequest request) {
        Customer customer = customerRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono klienta: " + userEmail));

        Account account;
        if (request.getAccountId() != null && !request.getAccountId().isEmpty()) {
            account = accountRepository.findById(UUID.fromString(request.getAccountId()))
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono rachunku"));
            if (!account.getCustomer().getId().equals(customer.getId())) {
                throw new IllegalArgumentException("Rachunek nie należy do tego klienta");
            }
        } else {
            account = accountRepository.findAll().stream()
                    .filter(a -> a.getCustomer().getId().equals(customer.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Klient nie posiada żadnego rachunku"));
        }

        // Przygotowanie danych dla Payment Gateway
        request.setUserId(customer.getId().toString());
        request.setAccountId(account.getIban()); // Gateway oczekuje unikalnego identyfikatora np. IBAN

        // Wywołanie zewnętrznego serwisu
        CardsIssueResponse response = cardsServiceClient.issueCard(request);

        // Zapis w naszej bazie
        PaymentCard paymentCard = PaymentCard.builder()
                .account(account)
                .cardToken(response.getCardToken())
                .cardType(request.getCardType())
                .status("REQUESTED")
                .build();
        paymentCardRepository.save(paymentCard);

        return response;
    }

    @Transactional
    public void processCaptureWebhook(CardWebhookRequest webhookRequest) {
        log.info("Otrzymano webhook obciążenia z Payment Gateway: {}", webhookRequest);

        PaymentCard card = paymentCardRepository.findByCardToken(webhookRequest.getCardToken())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono karty dla podanego tokenu"));

        Account account = card.getAccount();
        BigDecimal amount = webhookRequest.getAmount();

        if (account.getBalance().compareTo(amount) < 0) {
            log.error("Brak środków na rachunku {} dla transakcji {}", account.getIban(), webhookRequest.getTransactionId());
            throw new IllegalStateException("Niewystarczające środki na koncie");
        }

        // Zmniejszenie salda
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        com.bankeurob.transfer.Transaction transaction = new com.bankeurob.transfer.Transaction();
        transaction.setReferenceNumber("CARD-" + System.currentTimeMillis());
        transaction.setTransactionType("CARD_PAYMENT");
        transaction.setStatus("COMPLETED");
        transaction.setSenderAccount(account);
        transaction.setSenderIban(account.getIban());
        transaction.setSenderName(account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName());
        transaction.setReceiverIban("N/A");
        transaction.setReceiverName(webhookRequest.getMerchantId());
        transaction.setAmount(amount);
        transaction.setCurrency(webhookRequest.getCurrency());
        transaction.setTitle("Płatność kartą " + card.getCardToken());
        transaction.setCompletedAt(java.time.OffsetDateTime.now());
        transactionRepository.save(transaction);

        log.info("Pomyślnie obciążono rachunek {} kwotą {} dla autoryzacji {}",
                account.getIban(), amount, webhookRequest.getAuthorizationCode());
    }
}
