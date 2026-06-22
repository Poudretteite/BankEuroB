package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź KLIK z wygenerowanym kodem (C2B).
 * <p>
 * Kod jest 6-cyfrowy, ważny 120 sekund.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikGenerateCodeResponse {
    @JsonProperty("code")
    private String code;

    @JsonProperty("expires_in")
    private int expiresIn;

    @JsonProperty("expires_at")
    private String expiresAt;
}

// missing JsonIgnoreProperties
