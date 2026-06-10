package com.bankeurob.integration.cards.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CardLimitsUpdateRequest {
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private Integer dailyTxnLimit;
    private Integer monthlyTxnLimit;
}
