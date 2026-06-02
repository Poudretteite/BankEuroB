
package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Żądanie potwierdzenia/odrzucenia płatności przez bank (C2B).
 * <p>
 * Endpoint: POST /api/v1/payments/confirm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikConfirmPaymentRequest {
    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("status")
    private String status;       // ACCEPTED | REJECTED

    @JsonProperty("authorization_timestamp")
    private String authorizationTimestamp;

    @JsonProperty("reject_reason")
    private String rejectReason;
}
