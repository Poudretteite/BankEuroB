package com.bankeurob.account.dto;

import lombok.Data;

/**
 * Żądanie weryfikacji PIN-u BLIK.
 * <p>
 * Dedykowany endpoint do weryfikacji PIN-u, bez zmiany PIN-u.
 * Używany podczas autoryzacji płatności BLIK.
 */
@Data
public class VerifyBlikPinRequest {
    private String pin;
}
