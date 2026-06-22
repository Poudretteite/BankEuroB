package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź BankEuroB na webhook autoryzacyjny KLIK.
 * <p>
 * Bank potwierdza że przyjął żądanie do procesowania.
 * Decyzja klienta idzie osobnym kanałem przez POST /api/v1/payments/confirm.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikAuthorizeResponse {
    @JsonProperty("received")
    private boolean received;

    @JsonProperty("will_prompt_user")
    private boolean willPromptUser;
}

// missing JsonIgnoreProperties
