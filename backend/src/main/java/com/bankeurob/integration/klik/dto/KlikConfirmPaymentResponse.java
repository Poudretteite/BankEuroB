package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź KLIK na potwierdzenie płatności (C2B).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KlikConfirmPaymentResponse {
    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("ledger_entries_count")
    private int ledgerEntriesCount;

    @JsonProperty("merchant_net")
    private String merchantNet;

    @JsonProperty("klik_fee")
    private String klikFee;

    @JsonProperty("agent_fee")
    private String agentFee;

    @JsonProperty("amount_gross")
    private String amountGross;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("reject_reason")
    private String rejectReason;

    @JsonProperty("completed_at")
    private String completedAt;
}

// missing JsonIgnoreProperties
