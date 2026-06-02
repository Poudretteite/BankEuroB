package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Odpowiedź KLIK na zapytanie o alias P2P.
 * <p>
 * Endpoint: GET /api/v1/aliases/lookup/{phone}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikLookupAliasResponse {
    @JsonProperty("phone")
    private String phone;

    @JsonProperty("bank_id")
    private String bankId;

    @JsonProperty("bank_code")
    private String bankCode;

    @JsonProperty("account_identifier")
    private KlikRegisterAliasRequest.AccountIdentifier accountIdentifier;
}
