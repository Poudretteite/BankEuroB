package com.bankeurob.integration.klik.dto;

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
public class KlikConfirmPaymentResponse {
    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("ledger_entries_count")
    private int ledgerEntriesCount;
}
