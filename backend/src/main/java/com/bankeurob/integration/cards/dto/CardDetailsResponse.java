package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Szczegóły karty z Payment Gateway.
 * Zgodne z API: GET /api/v1/cards/{card_token}
 */
@Data
@NoArgsConstructor
public class CardDetailsResponse {
    @JsonAlias("card_token")
    private String cardToken;

    @JsonAlias("masked_pan")
    private String maskedPan;

    private String status;

    @JsonAlias("card_type")
    private String cardType;

    private double balance;

    @JsonAlias("daily_limit")
    private double dailyLimit;

    @JsonAlias("bank_id")
    private String bankId;
}
