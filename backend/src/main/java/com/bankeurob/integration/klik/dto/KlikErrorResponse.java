package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź błędu z KLIK.
 * <p>
 * Wszystkie błędy mają jednolity format:
 * <pre>
 * {
 *   "error": {
 *     "code": "404_CODE_EXPIRED",
 *     "message": "Kod utracił ważność lub nie istnieje",
 *     "transaction_id": "uuid-if-applicable",
 *     "timestamp": "2026-04-23T14:00:00Z"
 *   }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikErrorResponse {
    @JsonProperty("error")
    private KlikErrorDetail error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KlikErrorDetail {
        @JsonProperty("code")
        private String code;

        @JsonProperty("message")
        private String message;

        @JsonProperty("transaction_id")
        private String transactionId;

        @JsonProperty("timestamp")
        private String timestamp;
    }
}

// missing JsonIgnoreProperties
