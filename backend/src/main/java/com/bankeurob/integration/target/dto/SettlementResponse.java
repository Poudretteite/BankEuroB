package com.bankeurob.integration.target.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SettlementResponse {

    @JsonProperty("transaction_id")
    private String transactionId;

    private String status;

    @JsonProperty("settled_at")
    private LocalDateTime settledAt;

    @JsonProperty("sender_balance")
    private String senderBalance;

    @JsonProperty("receiver_balance")
    private String receiverBalance;
}
