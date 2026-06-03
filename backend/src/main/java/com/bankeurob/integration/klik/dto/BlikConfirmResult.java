package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Wynik autoryzacji transakcji BLIK.
 * <p>
 * Zwracany do frontendu po weryfikacji PIN-u.
 */
@Data
@Builder
@AllArgsConstructor
public class BlikConfirmResult {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("reference_number")
    private String referenceNumber;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("message")
    private String message;

    public static BlikConfirmResult success(String ref, BigDecimal amount, String currency, String merchant) {
        return BlikConfirmResult.builder()
                .success(true)
                .referenceNumber(ref)
                .amount(amount)
                .currency(currency)
                .merchantName(merchant)
                .message("Płatność zrealizowana pomyślnie")
                .build();
    }

    public static BlikConfirmResult failure(String message) {
        return BlikConfirmResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
