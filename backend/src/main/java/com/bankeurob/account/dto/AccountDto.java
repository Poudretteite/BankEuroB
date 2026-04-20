package com.bankeurob.account.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AccountDto {
    private UUID id;
    private String iban;
    private String bic;
    private String accountType;
    private String currency;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal dailyLimit;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
