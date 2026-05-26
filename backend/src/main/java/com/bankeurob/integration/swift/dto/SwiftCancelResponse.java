package com.bankeurob.integration.swift.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Odpowiedź z SWIFT Middleware API po anulowaniu przelewu.
 * <pre>
 * { "status": "cancelled", "uetr": "11111111-1111-4111-8111-111111111111" }
 * </pre>
 */
@Data
public class SwiftCancelResponse {
    private String status;
    private String uetr;
    private String error;
}
