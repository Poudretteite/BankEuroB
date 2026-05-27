package com.bankeurob.integration.xml;

import com.bankeurob.account.Account;
import com.bankeurob.transfer.dto.TransferRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generator dokumentów XML w standardzie ISO 20022 pain.001.001.09
 * dla przelewów SEPA (Batch i Instant).
 */
@Slf4j
@Component
public class Pain001Generator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Generuje XML pain.001.001.09 dla przelewu SEPA.
     *
     * @param request       dane przelewu
     * @param senderAccount konto nadawcy (z BIC i IBAN)
     * @return sformatowany XML
     */
    public String generate(TransferRequest request, Account senderAccount) {
        String msgId = "MSG-" + System.currentTimeMillis();
        String now = LocalDateTime.now().format(FORMATTER);

        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                    <CstmrCdtTrfInitn>
                        <GrpHdr>
                            <MsgId>%s</MsgId>
                            <CreDtTm>%s</CreDtTm>
                            <NbOfTxs>1</NbOfTxs>
                            <CtrlSum>%s</CtrlSum>
                            <InitgPty>
                                <Nm>%s</Nm>
                            </InitgPty>
                        </GrpHdr>
                        <PmtInf>
                            <PmtInfId>PI-%s</PmtInfId>
                            <PmtMtd>TRF</PmtMtd>
                            <BtchBookg>false</BtchBookg>
                            <NbOfTxs>1</NbOfTxs>
                            <CtrlSum>%s</CtrlSum>
                            <PmtTpInf>
                                <SvcLvl>
                                    <Cd>SEPA</Cd>
                                </SvcLvl>
                            </PmtTpInf>
                            <ReqdExctnDt>%s</ReqdExctnDt>
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
                                    <BIC>%s</BIC>
                                </FinInstnId>
                            </DbtrAgt>
                            <CdtTrfTxInf>
                                <PmtId>
                                    <EndToEndId>%s</EndToEndId>
                                </PmtId>
                                <Amt>
                                    <InstdAmt Ccy="EUR">%s</InstdAmt>
                                </Amt>
                                <Cdtr>
                                    <Nm>%s</Nm>
                                </Cdtr>
                                <CdtrAcct>
                                    <Id>
                                        <IBAN>%s</IBAN>
                                    </Id>
                                </CdtrAcct>
                                <CdtrAgt>
                                    <FinInstnId>
                                        <BIC>%s</BIC>
                                    </FinInstnId>
                                </CdtrAgt>
                                <RmtInf>
                                    <Ustrd>%s</Ustrd>
                                </RmtInf>
                            </CdtTrfTxInf>
                        </PmtInf>
                    </CstmrCdtTrfInitn>
                </Document>
                """,
                escapeXml(msgId),
                escapeXml(now),
                request.getAmount(),
                escapeXml(senderAccount.getCustomer().getFirstName() + " " + senderAccount.getCustomer().getLastName()),
                escapeXml(msgId),
                request.getAmount(),
                escapeXml(now.substring(0, 10)),
                escapeXml(senderAccount.getCustomer().getFirstName() + " " + senderAccount.getCustomer().getLastName()),
                escapeXml(senderAccount.getIban()),
                escapeXml(senderAccount.getBic()),
                escapeXml(msgId),
                request.getAmount(),
                escapeXml(request.getReceiverName() != null ? request.getReceiverName() : ""),
                escapeXml(request.getReceiverIban()),
                escapeXml(request.getReceiverBic() != null ? request.getReceiverBic() : ""),
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
