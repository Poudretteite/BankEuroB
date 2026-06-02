

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
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikGenerateCodeRequest {
    private String userId;
    private String zone;
}
