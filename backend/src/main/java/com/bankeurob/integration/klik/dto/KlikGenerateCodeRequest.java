

package com.bankeurob.integration.klik.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Żądanie wygenerowania kodu KLIK (C2B).
 * <p>
 * Endpoint: POST /api/v1/codes/generate
 *
 * @param userId wewnętrzny identyfikator klienta w BankEuroB
 * @param zone   strefa walutowo-krajowa (PL, EU, UK, US)
 */
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikGenerateCodeRequest {
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("zone")
    private String zone;
}
