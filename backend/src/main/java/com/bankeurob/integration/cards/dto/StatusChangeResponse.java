package com.bankeurob.integration.cards.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź z Payment Gateway po zmianie statusu karty.
 * Zgodne z API: PATCH /api/v1/cards/{card_token}/status
 */
@Data
@NoArgsConstructor
public class StatusChangeResponse {
    private boolean success;
    private String message;
}
