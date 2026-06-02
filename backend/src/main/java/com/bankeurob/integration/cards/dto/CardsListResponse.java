package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Odpowiedź z listą kart z Payment Gateway.
 * Zgodne z API: GET /api/v1/cards
 */
@Data
@NoArgsConstructor
public class CardsListResponse {
    private List<CardSummary> cards;

    @Data
    @NoArgsConstructor
    public static class CardSummary {
        @JsonProperty("card_token")
        private String cardToken;

        @JsonProperty("masked_pan")
        private String maskedPan;

        private String status;

        @JsonProperty("card_type")
        private String cardType;

        private double balance;

        @JsonProperty("daily_limit")
        private double dailyLimit;

        @JsonProperty("bank_id")
        private String bankId;
    }
}
