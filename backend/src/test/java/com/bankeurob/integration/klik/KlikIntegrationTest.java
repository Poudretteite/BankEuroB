package com.bankeurob.integration.klik;

import com.bankeurob.account.Account;
import com.bankeurob.account.AccountRepository;
import com.bankeurob.account.Customer;
import com.bankeurob.account.CustomerRepository;
import com.bankeurob.integration.klik.dto.KlikAuthorizeRequest;
import com.bankeurob.integration.klik.dto.KlikConfirmPaymentResponse;
import com.bankeurob.integration.klik.model.BlikTransaction;
import com.bankeurob.integration.klik.model.BlikTransactionRepository;
import com.bankeurob.security.CustomerUserDetails;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class KlikIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BlikTransactionRepository blikTransactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KlikServiceClient klikServiceClient;

    private Customer testCustomer;
    private Account testAccount;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        // Prepare Customer with BLIK PIN
        List<Customer> customers = customerRepository.findAll();
        if (customers.isEmpty()) {
            throw new IllegalStateException("Brak klientów w bazie do testów.");
        }
        testCustomer = customers.get(0);
        testCustomer.setBlikPin("1234");
        customerRepository.save(testCustomer);

        // Prepare Account
        testAccount = accountRepository.findByCustomerId(testCustomer.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Brak konta do testów."));
        testAccount.setBalance(new BigDecimal("1000.00"));
        testAccount.setAvailableBalance(new BigDecimal("1000.00"));
        accountRepository.save(testAccount);

        // Set up Spring Security Context
        CustomerUserDetails userDetails = new CustomerUserDetails(testCustomer);
        mockAuth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }

    @Test
    @DisplayName("Should successfully handle C2B authorize webhook and create pending transaction")
    void shouldHandleAuthorizeWebhook() throws Exception {
        String klikTransactionId = UUID.randomUUID().toString();
        String expiryTime = OffsetDateTime.now().plusMinutes(2).atZoneSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

        KlikAuthorizeRequest request = new KlikAuthorizeRequest(
                klikTransactionId,
                testCustomer.getId().toString(),
                "25.50",
                "PLN",
                "Sklep Testowy",
                false,
                expiryTime,
                "PL"
        );

        mockMvc.perform(post("/api/klik/webhook/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        BlikTransaction savedTx = blikTransactionRepository.findByKlikTransactionId(klikTransactionId).orElseThrow();
        assertEquals(testCustomer.getId(), savedTx.getCustomer().getId());
        assertEquals("PENDING_AUTHORIZATION", savedTx.getStatus());
        assertEquals(new BigDecimal("25.50"), savedTx.getAmount());
    }

    @Test
    @DisplayName("Should successfully authorize pending transaction using PIN")
    void shouldAuthorizePendingTransaction() throws Exception {
        // Given a PENDING transaction
        String klikTransactionId = UUID.randomUUID().toString();
        BlikTransaction tx = new BlikTransaction();
        tx.setKlikTransactionId(klikTransactionId);
        tx.setUserId(testCustomer.getId().toString());
        tx.setCustomer(testCustomer);
        tx.setAccount(testAccount);
        tx.setAmount(new BigDecimal("50.00"));
        tx.setCurrency("PLN");
        tx.setMerchantName("Sklep Testowy 2");
        tx.setOnUs(false);
        tx.setZone("PL");
        tx.setExpiryTime(OffsetDateTime.now().plusMinutes(2));
        tx.setStatus("PENDING_AUTHORIZATION");
        tx.setReceivedAt(OffsetDateTime.now());
        blikTransactionRepository.save(tx);

        BigDecimal initialBalance = testAccount.getAvailableBalance();

        // Mock outbound confirm to KLIK
        KlikConfirmPaymentResponse mockKlikResponse = new KlikConfirmPaymentResponse(
                klikTransactionId,
                "ACCEPTED",
                2,
                "50.00",
                "0.50",
                "0.10"
        );
        when(klikServiceClient.confirmPayment(eq(klikTransactionId), eq("ACCEPTED"), any()))
                .thenReturn(mockKlikResponse);

        // When user authorizes via API
        mockMvc.perform(post("/api/klik/payments/authorize")
                        .param("klikTransactionId", klikTransactionId)
                        .param("pin", "1234")
                        .with(authentication(mockAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then transaction is completed
        BlikTransaction completedTx = blikTransactionRepository.findByKlikTransactionId(klikTransactionId).orElseThrow();
        assertEquals("COMPLETED", completedTx.getStatus());
        assertNotNull(completedTx.getReferenceNumber());

        // And balance is deducted
        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(initialBalance.subtract(new BigDecimal("50.00")), updatedAccount.getAvailableBalance());
    }

    @Test
    @DisplayName("Should reject pending transaction if PIN is invalid")
    void shouldRejectWhenInvalidPin() throws Exception {
        // Given a PENDING transaction
        String klikTransactionId = UUID.randomUUID().toString();
        BlikTransaction tx = new BlikTransaction();
        tx.setKlikTransactionId(klikTransactionId);
        tx.setUserId(testCustomer.getId().toString());
        tx.setCustomer(testCustomer);
        tx.setAccount(testAccount);
        tx.setAmount(new BigDecimal("50.00"));
        tx.setCurrency("PLN");
        tx.setStatus("PENDING_AUTHORIZATION");
        tx.setExpiryTime(OffsetDateTime.now().plusMinutes(2));
        blikTransactionRepository.save(tx);

        BigDecimal initialBalance = testAccount.getAvailableBalance();

        // When user authorizes with INVALID PIN
        mockMvc.perform(post("/api/klik/payments/authorize")
                        .param("klikTransactionId", klikTransactionId)
                        .param("pin", "9999") // Invalid PIN
                        .with(authentication(mockAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Nieprawidłowy PIN"));

        // Then status is still PENDING
        BlikTransaction pendingTx = blikTransactionRepository.findByKlikTransactionId(klikTransactionId).orElseThrow();
        assertEquals("PENDING_AUTHORIZATION", pendingTx.getStatus());

        // And balance is unchanged
        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(initialBalance, updatedAccount.getAvailableBalance());
    }
}
