package com.bankeurob.integration.swift;

import com.bankeurob.account.Account;
import com.bankeurob.transfer.dto.TransferRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Generator dokumentów XML w standardzie ISO 20022 pacs.008.001.08
 * dla przelewów SWIFT (międzynarodowych) wysyłanych do SWIFT Middleware.
 * <p>
 * Format zgodny z oczekiwaniami projektu SWIFT-Aplikacje-Biznesowe:
 * <ul>
 *   <li>Namespace: urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08</li>
 *   <li>Element główny: FIToFICstmrCdtTrf</li>
 *   <li>Konto odbiorcy w elemencie CdtrAcct/Id/Othr/Id (zgodnie z ISO 20022)</li>
 *   <li>Pole ChrgBr (Charge Bearer): DEBT, CRED, SHAR lub SLEV</li>
 * </ul>
 */
@Slf4j
@Component
public class SwiftXmlGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /**
     * Generuje XML pacs.008.001.08 dla przelewu SWIFT.
     *
     * @param request       dane przelewu
     * @param senderAccount konto nadawcy (z BIC i IBAN)
     * @return sformatowany XML pacs.008
     */
    public String generate(TransferRequest request, Account senderAccount) {
        String msgId = "MSG-" + System.currentTimeMillis();
        String instrId = "INST-" + System.currentTimeMillis();
        String uetr = UUID.randomUUID().toString();
        String now = LocalDateTime.now().format(FORMATTER);
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String chargeBearer = request.getChargeBearer() != null ? request.getChargeBearer() : "SHAR";

        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr>
                      <MsgId>%s</MsgId>
                      <CreDtTm>%s</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                      <SttlmInf>
                        <SttlmMtd>INDA</SttlmMtd>
                      </SttlmInf>
                    </GrpHdr>
                    <CdtTrfTxInf>
                      <PmtId>
                        <InstrId>%s</InstrId>
                        <EndToEndId>NOTPROVIDED</EndToEndId>
                        <UETR>%s</UETR>
                      </PmtId>
                      <IntrBkSttlmAmt Ccy="%s">%s</IntrBkSttlmAmt>
                      <IntrBkSttlmDt>%s</IntrBkSttlmDt>
                      <InstdAmt Ccy="%s">%s</InstdAmt>
                      <ChrgBr>%s</ChrgBr>
                      <Dbtr>
                        <Nm>%s</Nm>
                      </Dbtr>
                      <DbtrAcct>
                        <Id>
                          <IBAN>%s</IBAN>
                        </Id>
                      </DbtrAcct>
                      <DbtrAgt>
                        <FinInstnId>
                          <BICFI>%s</BICFI>
                        </FinInstnId>
                      </DbtrAgt>
                      <Cdtr>
                        <Nm>%s</Nm>
                      </Cdtr>
                      <CdtrAgt>
                        <FinInstnId>
                          <BICFI>%s</BICFI>
                        </FinInstnId>
                      </CdtrAgt>
                      <CdtrAcct>
                        <Id>
                          <Othr>
                            <Id>%s</Id>
                          </Othr>
                        </Id>
                      </CdtrAcct>
                      <RmtInf>
                        <Ustrd>%s</Ustrd>
                      </RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """,
                escapeXml(msgId),
                escapeXml(now),
                escapeXml(instrId),
                escapeXml(uetr),
                escapeXml(senderAccount.getCurrency()),
                request.getAmount(),
                escapeXml(today),
                escapeXml(senderAccount.getCurrency()),
                request.getAmount(),
                escapeXml(chargeBearer),
                escapeXml(senderAccount.getCustomer().getFirstName() + " " + senderAccount.getCustomer().getLastName()),
                escapeXml(senderAccount.getIban()),
                escapeXml(senderAccount.getBic()),
                escapeXml(request.getReceiverName() != null ? request.getReceiverName() : ""),
                escapeXml(request.getReceiverBic() != null ? request.getReceiverBic() : ""),
                escapeXml(request.getReceiverIban()),
                escapeXml(request.getTitle() != null ? request.getTitle() : "")
        );
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&")
                .replace("<", "<")
                .replace(">", ">")
                .replace("\"", "&#34;")
                .replace("'", "'");
    }
}
