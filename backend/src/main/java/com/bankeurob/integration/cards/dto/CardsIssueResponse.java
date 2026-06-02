package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź z Payment Gateway po wydaniu karty.
 * Zgodne z API: POST /api/v1/cards/issue
 */
@Data
@NoArgsConstructor
public class CardsIssueResponse {
    @JsonProperty("card_token")
    private String cardToken;

    @JsonProperty("masked_pan")
    private String maskedPan;

    @JsonProperty("full_pan")
    private String fullPan;

    private String cvv;

    @JsonProperty("expiry_month")
    private int expiryMonth;

    @JsonProperty("expiry_year")
    private int expiryYear;

    private String status;

    @JsonProperty("card_type")
    private String cardType;

    @JsonProperty("bank_id")
    private String bankId;

    private String message;
}
