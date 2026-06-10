package com.bankeurob.integration.swift;

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
class SwiftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Find an existing active account for testing, or assume one exists from migration
        List<Account> accounts = accountRepository.findAll();
        if (!accounts.isEmpty()) {
            testAccount = accounts.get(0);
        } else {
            throw new IllegalStateException("Brak kont w bazie do testów.");
        }
    }

    @Test
    @DisplayName("Powinien poprawnie przetworzyć przychodzący przelew SWIFT (XML) i zasilić konto")
    void shouldHandleIncomingSwiftWebhook() throws Exception {
        // given
        BigDecimal initialBalance = testAccount.getBalance();
        String receiverIban = testAccount.getIban();
        String amount = "1250.50";
        
        String xmlMessage = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>SWIFT-MSG-12345</MsgId>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <IntrBkSttlmAmt Ccy="EUR">%s</IntrBkSttlmAmt>
                      <Dbtr>
                        <Nm>John Doe Foreign</Nm>
                      </Dbtr>
                      <DbtrAgt>
                        <FinInstnId>
                          <BICFI>USBNKUS33</BICFI>
                        </FinInstnId>
                      </DbtrAgt>
                      <CdtrAcct>
                        <Id>
                          <Othr>
                            <Id>%s</Id>
                          </Othr>
                        </Id>
                      </CdtrAcct>
                      <RmtInf>
                        <Ustrd>Zaplata za fakture 123</Ustrd>
                      </RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """, amount, receiverIban);

        // when
        mockMvc.perform(post("/api/transfers/webhook/swift/receive")
                .contentType(MediaType.APPLICATION_XML)
                .content(xmlMessage))
                .andExpect(status().isOk());

        // then
        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertEquals(initialBalance.add(new BigDecimal(amount)), updatedAccount.getBalance(), "Saldo powinno urosnąć o kwotę przelewu");

        List<Transaction> transactions = transactionRepository.findBySenderIbanOrReceiverIbanOrderByRequestedAtDesc(receiverIban, receiverIban);
        Transaction lastTx = transactions.stream()
                .filter(tx -> "INCOMING_SWIFT".equals(tx.getTransactionType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nie zapisano transakcji INCOMING_SWIFT"));

        assertEquals(new BigDecimal(amount), lastTx.getAmount());
        assertEquals("SWIFT-MSG-12345", lastTx.getExternalMessageId());
        assertEquals("USBNKUS33", lastTx.getSenderBic());
        assertEquals("John Doe Foreign", lastTx.getSenderName());
        assertEquals("Zaplata za fakture 123", lastTx.getTitle());
        assertEquals("COMPLETED", lastTx.getStatus());
    }
}
