package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayIssueRequest {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("card_type")
    private String cardType;

    @JsonProperty("initial_balance")
    private double initialBalance;
}