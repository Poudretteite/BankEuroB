package com.bankeurob.integration.cards.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Żądanie zmiany statusu karty.
 * Zgodne z API: PATCH /api/v1/cards/{card_token}/status
 */
@Data
@NoArgsConstructor
public class StatusChangeRequest {
    private String status;   // BLOCKED lub ACTIVE
    private String reason;   // tylko dla BLOCKED
}
