

package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Żądanie rejestracji aliasu P2P (przelew na numer telefonu).
 * <p>
 * Endpoint: POST /api/v1/aliases/register
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikRegisterAliasRequest {
    @JsonProperty("phone")
    private String phone;

    @JsonProperty("account_identifier")
    private AccountIdentifier accountIdentifier;

    @JsonProperty("zone")
    private String zone;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountIdentifier {
        @JsonProperty("type")
        private String type;      // "iban" | "us_routing"

        @JsonProperty("value")
        private String value;     // dla IBAN

        @JsonProperty("routing_number")
        private String routingNumber;  // dla US

        @JsonProperty("account_number")
        private String accountNumber;  // dla US
    }
}
