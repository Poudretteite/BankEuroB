package com.bankeurob.integration.target.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LiquidityInjectionResponse {

    @JsonProperty("transfer_id")
    private String transferId;

    @JsonProperty("bank_bic")
    private String bankBic;

    private String amount;

    @JsonProperty("new_balance")
    private String newBalance;
}
