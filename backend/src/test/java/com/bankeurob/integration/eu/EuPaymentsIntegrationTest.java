package com.bankeurob.integration.eu;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.transfer.Transaction;
import com.bankeurob.transfer.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EuPaymentsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        List<Account> accounts = accountRepository.findAll();
        if (!accounts.isEmpty()) {
            testAccount = accounts.get(0);
        } else {
            throw new IllegalStateException("Brak kont w bazie do testów.");
        }
    }

    @Test
    @DisplayName("Powinien poprawnie przetworzyć przychodzący przelew SEPA z webhooka")
    void shouldHandleIncomingSepaWebhook() throws Exception {
        // given
        BigDecimal initialBalance = testAccount.getBalance();
        String receiverIban = testAccount.getIban();
        String amount = "550.75";
        
        String jsonPayload = String.format("""
                {
                    "event": "payment.settled",
                    "transfer_id": "SEPA-MSG-555",
                    "sender_bic": "DEBNKDEFF",
                    "receiver_bic": "PLBNKPLXX",
                    "sender_iban": "DE1234567890",
                    "receiver_iban": "%s",
                    "amount": %s,
                    "currency": "EUR",
                    "description": "Zaplata za fakture SEPA",
                    "settled_at": "2026-06-10T10:00:00Z",
                    "signature": "dummy_signature"
                }
                """, receiverIban, amount);

        // when
        mockMvc.perform(post("/api/transfers/webhook/target")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk());

        // then
        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(initialBalance.add(new BigDecimal(amount)), updatedAccount.getBalance(), "Saldo powinno urosnąć o kwotę przelewu");

        List<Transaction> transactions = transactionRepository.findBySenderIbanOrReceiverIbanOrderByRequestedAtDesc(receiverIban, receiverIban);
        Transaction lastTx = transactions.stream()
                .filter(tx -> "INCOMING_SEPA".equals(tx.getTransactionType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nie zapisano transakcji INCOMING_SEPA"));

        assertEquals(new BigDecimal(amount), lastTx.getAmount());
        assertEquals("SEPA-MSG-555", lastTx.getExternalMessageId());
        assertEquals("DEBNKDEFF", lastTx.getSenderBic());
        assertEquals("DE1234567890", lastTx.getSenderIban());
        assertEquals("Zaplata za fakture SEPA", lastTx.getTitle());
        assertEquals("COMPLETED", lastTx.getStatus());
    }

    @Test
    @DisplayName("Powinien poprawnie przetworzyć przychodzący przelew TARGET z webhooka")
    void shouldHandleIncomingTargetWebhook() throws Exception {
        // given
        BigDecimal initialBalance = testAccount.getBalance();
        String receiverIban = testAccount.getIban();
        String amount = "99000.00";
        
        String jsonPayload = String.format("""
                {
                    "event": "transfer.settled",
                    "transfer_id": "TARGET-MSG-999",
                    "sender_bic": "FRBNKFRXX",
                    "receiver_bic": "PLBNKPLXX",
                    "sender_iban": "FR0987654321",
                    "receiver_iban": "%s",
                    "amount": %s,
                    "currency": "EUR",
                    "description": "Rozliczenie TARGET RTGS",
                    "settled_at": "2026-06-10T10:05:00Z",
                    "signature": "dummy_signature"
                }
                """, receiverIban, amount);

        // when
        mockMvc.perform(post("/api/transfers/webhook/target")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk());

        // then
        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(initialBalance.add(new BigDecimal(amount)), updatedAccount.getBalance(), "Saldo powinno urosnąć o kwotę przelewu");

        List<Transaction> transactions = transactionRepository.findBySenderIbanOrReceiverIbanOrderByRequestedAtDesc(receiverIban, receiverIban);
        Transaction lastTx = transactions.stream()
                .filter(tx -> "INCOMING_TARGET".equals(tx.getTransactionType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nie zapisano transakcji INCOMING_TARGET"));

        assertEquals(new BigDecimal(amount), lastTx.getAmount());
        assertEquals("TARGET-MSG-999", lastTx.getExternalMessageId());
        assertEquals("FRBNKFRXX", lastTx.getSenderBic());
        assertEquals("FR0987654321", lastTx.getSenderIban());
        assertEquals("Rozliczenie TARGET RTGS", lastTx.getTitle());
        assertEquals("COMPLETED", lastTx.getStatus());
    }
}
