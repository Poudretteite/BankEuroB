package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO dla oczekującej transakcji BLIK widocznej w UI klienta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingTransactionDto {
    @JsonProperty("id")
    private String id;

    @JsonProperty("klik_transaction_id")
    private String klikTransactionId;

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("received_at")
    private OffsetDateTime receivedAt;

    @JsonProperty("expires_at")
    private OffsetDateTime expiresAt;

    @JsonProperty("seconds_left")
    private long secondsLeft;
}
