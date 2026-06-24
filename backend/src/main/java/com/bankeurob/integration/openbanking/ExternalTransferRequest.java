package com.bankeurob.integration.openbanking;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ExternalTransferRequest {
    private UUID linkedBankId;
    private String fromAccountId;
    private String toAccountNumber;
    private String bic;
    private BigDecimal amount;
    private String currency;
    private String description;
}
