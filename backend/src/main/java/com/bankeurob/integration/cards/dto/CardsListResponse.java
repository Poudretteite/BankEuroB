package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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

        private double monthlyLimit;
        private int dailyTxnLimit;
        private int monthlyTxnLimit;

        @JsonAlias("bank_id")
        private String bankId;
    }
}
