package com.bankeurob.integration.swift.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Odpowiedź OAuth2 z endpointu /auth/token SWIFT Middleware.
 * <pre>
 * { "access_token": "uuid", "token_type": "Bearer", "expires_in": 3600 }
 * </pre>
 */
@Data
public class SwiftTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("token_type")
    private String tokenType;
    @JsonProperty("expires_in")
    private Integer expiresIn;
    private String error;
}
