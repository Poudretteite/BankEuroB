package com.bankeurob.integration.klik.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Webhook od KLIK — żądanie autoryzacji płatności.
 * <p>
 * KLIK wywołuje POST {bank_webhook_url}/authorize po inicjalizacji płatności przez agenta.
 * Bank musi pokazać klientowi push z prośbą o autoryzację PINem.
 *
 * @see <a href="https://github.com/your-org/KLIK-payments/docs/c2b/integration/INFO.md">Dokumentacja C2B</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlikAuthorizeRequest {
    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("is_on_us")
    private boolean isOnUs;

    @JsonProperty("expiry_time")
    private String expiryTime;

    @JsonProperty("zone")
    private String zone;
}
