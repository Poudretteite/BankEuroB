package com.bankeurob.integration.target.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SettlementAccountResponse {
    private Integer id;

    @JsonProperty("bank_id")
    private Integer bankId;

    private String currency;
    private String balance;

    @JsonProperty("available_balance")
    private String availableBalance;

    @JsonProperty("limit_debt")
    private String limitDebt;
}
