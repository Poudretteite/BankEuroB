package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status płatności C2B w KLIK.
 * <p>
 * Endpoint: GET /api/v1/payments/status/{transaction_id}
 * <p>
 * Możliwe statusy: PENDING, AUTHORIZED, COMPLETED, REJECTED, TIMEOUT
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikPaymentStatusResponse {
    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount_gross")
    private String amountGross;

    @JsonProperty("merchant_net")
    private String merchantNet;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("completed_at")
    private String completedAt;
}

// missing JsonIgnoreProperties
