package com.bankeurob.integration.cards;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.account.Customer;
import com.bankeurob.account.CustomerRepository;
import com.bankeurob.integration.cards.dto.CardWebhookRequest;
import com.bankeurob.integration.cards.dto.CardsIssueResponse;
import com.bankeurob.integration.cards.dto.IssueCardRequest;
import com.bankeurob.transfer.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bankeurob.security.CustomerUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentCardRepository paymentCardRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private CardsServiceClient cardsServiceClient;

    private Customer testCustomer;
    private Account testAccount;
    private final String mockToken = "tok_test123";

    @BeforeEach
    void setUp() {
        paymentCardRepository.findByCardToken(mockToken).ifPresent(paymentCardRepository::delete);
        
        var customers = customerRepository.findAll();
        if (customers.isEmpty()) {
            throw new IllegalStateException("Brak klientów w bazie do testów.");
        }
        testCustomer = customers.get(0);
        
        testAccount = accountRepository.findByCustomerId(testCustomer.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Brak konta do testów."));
        testAccount.setBalance(new BigDecimal("1000.00"));
        testAccount.setAvailableBalance(new BigDecimal("1000.00"));
        accountRepository.save(testAccount);

        CustomerUserDetails userDetails = new CustomerUserDetails(testCustomer);
        Authentication mockAuth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }

    @Test
    @DisplayName("Should successfully issue a card and save it to DB")
    void shouldIssueCard() throws Exception {
        IssueCardRequest request = new IssueCardRequest();
        request.setCardType("VIRTUAL");

        CardsIssueResponse mockResponse = new CardsIssueResponse();
        mockResponse.setCardToken(mockToken);
        mockResponse.setMaskedPan("4111********1111");
        mockResponse.setFullPan("4111111111111111");
        mockResponse.setCvv("123");
        mockResponse.setExpiryMonth(12);
        mockResponse.setExpiryYear(2026);
        mockResponse.setCardType("VIRTUAL");
        mockResponse.setBankId("BKEU");
        mockResponse.setMessage("Card issued successfully");
        mockResponse.setStatus("REQUESTED");

        when(cardsServiceClient.issueCard(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/cards/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.cardToken").value(mockToken));

        PaymentCard savedCard = paymentCardRepository.findByCardToken(mockToken).orElseThrow();
        assertEquals(testAccount.getId(), savedCard.getAccount().getId());
        assertEquals("VIRTUAL", savedCard.getCardType());
    }

    @Test
    @DisplayName("Should successfully process capture webhook and deduct balance")
    void shouldProcessCaptureWebhook() throws Exception {
        PaymentCard card = PaymentCard.builder()
                .account(testAccount)
                .cardToken(mockToken)
                .cardType("VIRTUAL")
                .status("ACTIVE")
                .build();
        paymentCardRepository.save(card);

        CardWebhookRequest webhook = new CardWebhookRequest();
        webhook.setTransactionId(UUID.randomUUID().toString());
        webhook.setAuthorizationCode("AUTH123");
        webhook.setAmount(new BigDecimal("150.00"));
        webhook.setCurrency("PLN");
        webhook.setMerchantId("Sklep ABC");
        webhook.setCardToken(mockToken);

        mockMvc.perform(post("/api/cards/webhook/capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("850.00").compareTo(updatedAccount.getBalance()));

        var transactions = transactionRepository.findAll().stream()
                .filter(t -> t.getSenderAccount() != null && testAccount.getId().equals(t.getSenderAccount().getId()))
                .toList();
        assertEquals(true, transactions.stream().anyMatch(t -> 
                new BigDecimal("150.00").compareTo(t.getAmount()) == 0 &&
                "Sklep ABC".equals(t.getReceiverName())
        ));
    }
}
