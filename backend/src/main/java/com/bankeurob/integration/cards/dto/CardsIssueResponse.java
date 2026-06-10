package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź z Payment Gateway po wydaniu karty.
 * Zgodne z API: POST /api/v1/cards/issue
 */
@Data
@NoArgsConstructor
public class CardsIssueResponse {
    @JsonAlias("card_token")
    private String cardToken;

    @JsonAlias("masked_pan")
    private String maskedPan;

    @JsonAlias("full_pan")
    private String fullPan;

    private String cvv;

    @JsonAlias("expiry_month")
    private int expiryMonth;

    @JsonAlias("expiry_year")
    private int expiryYear;

    private String status;

    @JsonAlias("card_type")
    private String cardType;

    @JsonAlias("bank_id")
    private String bankId;

    private String message;
}
