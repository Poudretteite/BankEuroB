package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź KLIK po rejestracji aliasu P2P.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikRegisterAliasResponse {
    @JsonProperty("alias_id")
    private String aliasId;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("registered_at")
    private String registeredAt;
}
