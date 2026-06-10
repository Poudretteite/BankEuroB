package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CardWebhookRequest {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("authorization_code")
    private String authorizationCode;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("card_token")
    private String cardToken;
}
