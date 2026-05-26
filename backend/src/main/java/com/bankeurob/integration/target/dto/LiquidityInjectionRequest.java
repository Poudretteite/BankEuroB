package com.bankeurob.integration.target.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiquidityInjectionRequest {

    @JsonProperty("bank_bic")
    private String bankBic;

    private BigDecimal amount;

    private String currency = "EUR";
}
