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

    public com.bankeurob.integration.cards.dto.CardsListResponse listUserCards(String userEmail) {
        Customer customer = customerRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono klienta: " + userEmail));
        
        java.util.List<PaymentCard> userCardsEntities = accountRepository.findAll().stream()
                .filter(a -> a.getCustomer().getId().equals(customer.getId()))
                .flatMap(a -> paymentCardRepository.findByAccountId(a.getId()).stream())
                .toList();

        com.bankeurob.integration.cards.dto.CardsListResponse allCards = cardsServiceClient.listCards();
        com.bankeurob.integration.cards.dto.CardsListResponse userCards = new com.bankeurob.integration.cards.dto.CardsListResponse();
        
        if (allCards != null && allCards.getCards() != null) {
            java.util.List<com.bankeurob.integration.cards.dto.CardsListResponse.CardSummary> enrichedCards = allCards.getCards().stream()
                .filter(c -> userCardsEntities.stream().anyMatch(e -> e.getCardToken().equals(c.getCardToken())))
                .map(c -> {
                    PaymentCard localCard = userCardsEntities.stream()
                            .filter(e -> e.getCardToken().equals(c.getCardToken()))
                            .findFirst().orElseThrow();
                    c.setDailyLimit(localCard.getDailyLimit().doubleValue());
                    c.setMonthlyLimit(localCard.getMonthlyLimit().doubleValue());
                    c.setDailyTxnLimit(localCard.getDailyTxnLimit());
                    c.setMonthlyTxnLimit(localCard.getMonthlyTxnLimit());
                    return c;
                })
                .toList();
            userCards.setCards(enrichedCards);
        } else {
            userCards.setCards(java.util.List.of());
        }
        return userCards;
    }

    @Transactional
    public void processCaptureWebhook(CardWebhookRequest webhookRequest) {
        log.info("Otrzymano webhook obciążenia z Payment Gateway: {}", webhookRequest);

        PaymentCard card = paymentCardRepository.findByCardToken(webhookRequest.getCardToken())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono karty dla podanego tokenu"));

        Account account = card.getAccount();
        BigDecimal amount = webhookRequest.getAmount();
        
        // Weryfikacja limitów karty
        String cardTitle = "Płatność kartą " + card.getCardToken();
        BigDecimal todayTotal = transactionRepository.findTodayTotalByCardTitle(cardTitle);
        BigDecimal monthlyTotal = transactionRepository.findMonthlyTotalByCardTitle(cardTitle);
        Integer todayCount = transactionRepository.countTodayTransactionsByCardTitle(cardTitle);
        Integer monthlyCount = transactionRepository.countMonthlyTransactionsByCardTitle(cardTitle);
        
        if (card.getDailyLimit().compareTo(BigDecimal.ZERO) > 0 && todayTotal.add(amount).compareTo(card.getDailyLimit()) > 0) {
            log.error("Przekroczono dzienny limit kwotowy dla karty {}", card.getCardToken());
            throw new IllegalStateException("Przekroczono dzienny limit karty");
        }
        if (card.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0 && monthlyTotal.add(amount).compareTo(card.getMonthlyLimit()) > 0) {
            log.error("Przekroczono miesięczny limit kwotowy dla karty {}", card.getCardToken());
            throw new IllegalStateException("Przekroczono miesięczny limit karty");
        }
        if (card.getDailyTxnLimit() > 0 && (todayCount + 1) > card.getDailyTxnLimit()) {
            log.error("Przekroczono dzienny limit ilościowy dla karty {}", card.getCardToken());
            throw new IllegalStateException("Przekroczono dzienny limit transakcji karty");
        }
        if (card.getMonthlyTxnLimit() > 0 && (monthlyCount + 1) > card.getMonthlyTxnLimit()) {
            log.error("Przekroczono miesięczny limit ilościowy dla karty {}", card.getCardToken());
            throw new IllegalStateException("Przekroczono miesięczny limit transakcji karty");
        }

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
        transaction.setTitle(cardTitle);
        transaction.setCompletedAt(java.time.OffsetDateTime.now());
        transactionRepository.save(transaction);

        log.info("Pomyślnie obciążono rachunek {} kwotą {} dla autoryzacji {}",
                account.getIban(), amount, webhookRequest.getAuthorizationCode());
    }

    @Transactional
    public void updateCardLimits(String cardToken, com.bankeurob.integration.cards.dto.CardLimitsUpdateRequest request) {
        PaymentCard card = paymentCardRepository.findByCardToken(cardToken)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono karty dla podanego tokenu"));
        
        if (request.getDailyLimit() != null) card.setDailyLimit(request.getDailyLimit());
        if (request.getMonthlyLimit() != null) card.setMonthlyLimit(request.getMonthlyLimit());
        if (request.getDailyTxnLimit() != null) card.setDailyTxnLimit(request.getDailyTxnLimit());
        if (request.getMonthlyTxnLimit() != null) card.setMonthlyTxnLimit(request.getMonthlyTxnLimit());
        
        paymentCardRepository.save(card);
    }
}
