package com.bankeurob.integration.target.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRequest {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("sender_bic")
    private String senderBic;

    @JsonProperty("receiver_bic")
    private String receiverBic;

    private BigDecimal amount;

    private String currency = "EUR";

    private String description;

    private String service;
}
