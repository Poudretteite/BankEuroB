package com.bankeurob.integration.cards;

import com.bankeurob.integration.cards.config.CardsGatewayConfig;
import com.bankeurob.integration.cards.dto.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Klient HTTP do komunikacji z Payment Gateway (system kart płatniczych).
 * <p>
 * Endpointy:
 * <ul>
 *   <li>POST /api/v1/cards/issue          – wydanie karty (HMAC-SHA256)</li>
 *   <li>GET  /api/v1/cards                – lista wszystkich kart (X-Admin-Key)</li>
 *   <li>GET  /api/v1/cards/{token}        – szczegóły karty</li>
 *   <li>PATCH /api/v1/cards/{token}/status – blokuj/odblokuj kartę (X-API-Key)</li>
 * </ul>
 * <p>
 * Autoryzacja banku: X-API-Key + X-Signature (HMAC-SHA256) + X-Timestamp (max 30s)
 */
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardsServiceClient {

    private final RestTemplate restTemplate;
    private final CardsGatewayConfig config;
    private final ObjectMapper objectMapper;

    private static final String API_KEY = "bank-key-eu-b";
    private static final String HMAC_SECRET = "secret-eu-b-hmac";

    // ─────────────────────────────────────────────────
    // Wydanie karty (POST /api/v1/cards/issue)
    // ─────────────────────────────────────────────────

    /**
     * Wydaje nową kartę w Payment Gateway.
     * Żądanie jest podpisywane HMAC-SHA256 zgodnie z dokumentacją API Kart Płatniczych.
     *
     * @param request dane karty (userId, accountId, cardType, initialBalance)
     * @return odpowiedź z pełnym PAN i CVV (jednorazowo)
     */
    public CardsIssueResponse issueCard(IssueCardRequest request) {
        String url = config.getBaseUrl() + "/api/v1/cards/issue";
        log.info("Wydawanie karty: type={}, userId={}, accountId={}",
                request.getCardType(), request.getUserId(), request.getAccountId());

        // Budowa JSON w formacie Python: json.dumps(body, separators=(',', ':'), sort_keys=True)
        String bodyJson = buildPaymentGatewayJson(
            request.getUserId(),
            request.getAccountId(),
            request.getCardType(),
            request.getInitialBalance()
        );

        HttpHeaders headers = buildSignedHeaders(bodyJson);
        HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);

        try {
            ResponseEntity<CardsIssueResponse> response = restTemplate.postForEntity(
                    url, entity, CardsIssueResponse.class);

            CardsIssueResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z Payment Gateway");
            }

            log.info("Karta wydana: token={}, maskedPan={}, status={}",
                    body.getCardToken(), body.getMaskedPan(), body.getStatus());
            return body;

        } catch (ResourceAccessException e) {
            log.error("Payment Gateway niedostępny: {}", e.getMessage());
            throw new ResourceAccessException("Payment Gateway jest wyłączony na " + config.getBaseUrl());
        } catch (Exception e) {
            log.error("Błąd wydawania karty: {}", e.getMessage());
            throw new RuntimeException("Błąd wydawania karty: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Lista kart (GET /api/v1/cards)
    // ─────────────────────────────────────────────────

    /**
     * Pobiera listę wszystkich kart z Payment Gateway.
     * Wymaga klucza administracyjnego (X-Admin-Key).
     */
    public CardsListResponse listCards() {
        String url = config.getBaseUrl() + "/api/v1/cards";
        log.info("Pobieranie listy kart z Payment Gateway");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Key", "admin-secret-key-2026");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<CardsListResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, CardsListResponse.class);

            CardsListResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z Payment Gateway");
            }

            log.info("Pobrano {} kart", body.getCards() != null ? body.getCards().size() : 0);
            return body;

        } catch (ResourceAccessException e) {
            log.error("Payment Gateway niedostępny: {}", e.getMessage());
            throw new ResourceAccessException("Payment Gateway jest wyłączony na " + config.getBaseUrl());
        } catch (Exception e) {
            log.error("Błąd pobierania listy kart: {}", e.getMessage());
            throw new RuntimeException("Błąd pobierania listy kart: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Szczegóły karty (GET /api/v1/cards/{token})
    // ─────────────────────────────────────────────────

    /**
     * Pobiera szczegółowe informacje o karcie na podstawie tokenu.
     */
    public CardDetailsResponse getCardDetails(String cardToken) {
        String url = config.getBaseUrl() + "/api/v1/cards/" + cardToken;
        log.info("Pobieranie szczegółów karty: token={}", cardToken);

        try {
            ResponseEntity<CardDetailsResponse> response = restTemplate.getForEntity(
                    url, CardDetailsResponse.class);

            CardDetailsResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z Payment Gateway");
            }

            log.info("Szczegóły karty: maskedPan={}, status={}, type={}",
                    body.getMaskedPan(), body.getStatus(), body.getCardType());
            return body;

        } catch (ResourceAccessException e) {
            log.error("Payment Gateway niedostępny: {}", e.getMessage());
            throw new ResourceAccessException("Payment Gateway jest wyłączony na " + config.getBaseUrl());
        } catch (Exception e) {
            log.error("Błąd pobierania szczegółów karty {}: {}", cardToken, e.getMessage());
            throw new RuntimeException("Błąd pobierania szczegółów karty: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Zmiana statusu karty (PATCH /api/v1/cards/{token}/status)
    // ─────────────────────────────────────────────────

    /**
     * Zmienia status karty (BLOCKED / ACTIVE).
     *
     * @param cardToken token karty
     * @param newStatus nowy status (BLOCKED, ACTIVE)
     * @param reason    powód zmiany (opcjonalny)
     */
    public StatusChangeResponse changeCardStatus(String cardToken, String newStatus, String reason) {
        String url = config.getBaseUrl() + "/api/v1/cards/" + cardToken + "/status";
        log.info("Zmiana statusu karty: token={}, newStatus={}", cardToken, newStatus);

        String bodyJson = String.format(
            "{\"status\":\"%s\",\"reason\":\"%s\"}",
            escapeJson(newStatus), escapeJson(reason != null ? reason : "")
        );

        HttpHeaders headers = buildSignedHeaders(bodyJson);

        HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);

        try {
            ResponseEntity<StatusChangeResponse> response = restTemplate.exchange(
                    url, HttpMethod.PATCH, entity, StatusChangeResponse.class);

            StatusChangeResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z Payment Gateway");
            }

            log.info("Status karty zmieniony: token={}, success={}", cardToken, body.isSuccess());
            return body;

        } catch (ResourceAccessException e) {
            log.error("Payment Gateway niedostępny: {}", e.getMessage());
            throw new ResourceAccessException("Payment Gateway jest wyłączony na " + config.getBaseUrl());
        } catch (Exception e) {
            log.error("Błąd zmiany statusu karty {}: {}", cardToken, e.getMessage());
            throw new RuntimeException("Błąd zmiany statusu karty: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Metody pomocnicze
    // ─────────────────────────────────────────────────

    /**
     * Buduje JSON requestu do Payment Gateway w formacie:
     * {"account_id":"...","card_type":"...","initial_balance":0.0,"user_id":"..."}
     * <p>
     * Odpowiednik Python: json.dumps(body, separators=(',', ':'), sort_keys=True)
     * Klucze są posortowane alfabetycznie: account_id, card_type, initial_balance, user_id
     */
    private String buildPaymentGatewayJson(String userId, String accountId, String cardType, double initialBalance) {
        return String.format(
            "{\"account_id\":\"%s\",\"card_type\":\"%s\",\"initial_balance\":%s,\"user_id\":\"%s\"}",
            escapeJson(accountId),
            escapeJson(cardType),
            formatBalance(initialBalance),
            escapeJson(userId)
        );
    }

    private String formatBalance(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return ((long) value) + ".0";
        }
        return String.valueOf(value);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ─────────────────────────────────────────────────
    // Doładowanie karty prepaid (POST /api/v1/cards/{token}/topup)
    // ─────────────────────────────────────────────────

    /**
     * Doładowuje kartę prepaid podaną kwotą.
     */
    public void topupCard(String cardToken, java.math.BigDecimal amount) {
        String url = config.getBaseUrl() + "/api/v1/cards/" + cardToken + "/topup";
        log.info("Wysyłanie doładowania karty: token={}, kwota={}", cardToken, amount);

        try {
            TopUpRequest requestBody = new TopUpRequest();
            requestBody.setAmount(amount);

            String bodyJson = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = buildSignedHeaders(bodyJson);
            HttpEntity<String> request = new HttpEntity<>(bodyJson, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Błąd doładowania. Status: " + response.getStatusCode());
            }

            log.info("Karta {} doładowana pomyślnie.", cardToken);

        } catch (ResourceAccessException e) {
            log.error("Payment Gateway niedostępny: {}", e.getMessage());
            throw new ResourceAccessException("Payment Gateway jest wyłączony na " + config.getBaseUrl());
        } catch (Exception e) {
            log.error("Błąd doładowania karty {}: {}", cardToken, e.getMessage());
            throw new RuntimeException("Błąd doładowania karty: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Aktywacja karty (POST /api/v1/cards/{token}/activate)
    // ─────────────────────────────────────────────────

    /**
     * Aktywuje kartę przez klienta.
     * Karta musi być w statusie SHIPPED.
     */
    public void activateCard(String cardToken) {
        String url = config.getBaseUrl() + "/api/v1/cards/" + cardToken + "/activate";
        log.info("Wysyłanie aktywacji karty: token={}", cardToken);

        try {
            ActivateCardBody requestBody = new ActivateCardBody();
            requestBody.setActivated_by("customer");

            String bodyJson = objectMapper.writeValueAsString(requestBody);
            HttpHeaders headers = buildSignedHeaders(bodyJson);
            HttpEntity<String> request = new HttpEntity<>(bodyJson, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Błąd aktywacji. Status: " + response.getStatusCode());
            }

            log.info("Karta {}aktywowana pomyślnie.", cardToken);

        } catch (ResourceAccessException e) {
            log.error("Payment Gateway niedostępny: {}", e.getMessage());
            throw new ResourceAccessException("Payment Gateway jest wyłączony na " + config.getBaseUrl());
        } catch (Exception e) {
            log.error("Błąd aktywacji karty {}: {}", cardToken, e.getMessage());
            throw new RuntimeException("Błąd aktywacji karty: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Podpisywanie żądań (HMAC)
    // ─────────────────────────────────────────────────

    @Data
    private static class ActivateCardBody {
        private String activated_by;
    }

    @Data
    private static class TopUpRequest {
        private java.math.BigDecimal amount;
    }

    /**
     * Buduje nagłówki z podpisem HMAC.
     * Algorytm (Payment Gateway wymaga):
     * 1. timestamp = unix_timestamp_in_seconds
     * 2. body_json = json.dumps(body, separators=(',', ':'), sort_keys=True)
     * 3. payload = timestamp + body_json
     * 4. signature = hmac.new(secret, payload, hashlib.sha256).hexdigest()
     */
    private HttpHeaders buildSignedHeaders(String bodyJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = generateHmacSignature(timestamp, bodyJson);

        log.debug("HMAC signing: ts={}, sig={}, body={}", timestamp, signature.substring(0, 16) + "...", bodyJson);

        headers.set("X-API-Key", API_KEY);
        headers.set("X-Signature", signature);
        headers.set("X-Timestamp", timestamp);

        return headers;
    }

    private String generateHmacSignature(String timestamp, String bodyJson) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            String payload = timestamp + bodyJson;
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hmacBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Błąd generowania podpisu HMAC", e);
        }
    }
}