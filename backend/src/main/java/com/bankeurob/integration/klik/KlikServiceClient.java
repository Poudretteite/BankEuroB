package com.bankeurob.integration.klik;

import com.bankeurob.integration.klik.config.KlikConfig;
import com.bankeurob.integration.klik.dto.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Klient HTTP do komunikacji z systemem KLIK (płatności mobilne).
 * <p>
 * Moduły:
 * <ul>
 *   <li><b>C2B (Kody)</b> — generowanie kodów, potwierdzanie płatności</li>
 *   <li><b>P2P (Telefony)</b> — rejestracja aliasów, lookup, usuwanie</li>
 * </ul>
 * <p>
 * Dokumentacja: docs/c2b/integration/INFO.md oraz docs/p2p/integration/INFO.md
 *
 * @see <a href="https://github.com/your-org/KLIK-payments">KLIK Payments</a>
 */
@Service
@Slf4j
public class KlikServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final KlikConfig config;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public KlikServiceClient(KlikConfig config) {
        this.config = config;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeout());
        factory.setReadTimeout(config.getReadTimeout());
        factory.setBufferRequestBody(true);
        this.restTemplate.setRequestFactory(factory);
    }

    // ─────────────────────────────────────────────────
    // C2B — Generowanie kodu (POST /api/v1/codes/generate)
    // ─────────────────────────────────────────────────

    /**
     * Generuje 6-cyfrowy kod KLIK dla klienta banku.
     * Kod jest ważny 120 sekund, jednorazowy.
     *
     * @param userId wewnętrzny identyfikator klienta w BankEuroB
     * @param zone   strefa (PL, EU, UK, US)
     * @return odpowiedź z kodem
     */
    public KlikGenerateCodeResponse generateCode(String userId, String zone) {
        String url = config.getBaseUrl() + "/api/v1/codes/generate";
        log.info("Generowanie kodu KLIK: userId={}, zone={}", userId, zone);

        String requestBody = "{\"user_id\":\"" + userId + "\",\"zone\":\"" + zone + "\"}";

        try {
            String responseBody = sendPostRequest(url, requestBody);
            KlikGenerateCodeResponse body = objectMapper.readValue(responseBody, KlikGenerateCodeResponse.class);
            log.info("Kod KLIK wygenerowany: code={}, expiresIn={}s", body.getCode(), body.getExpiresIn());
            return body;
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            log.error("KLIK niedostępny: {}", e.getMessage());
            throw new RuntimeException("System KLIK jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("Błąd generowania kodu KLIK: {}", e.getMessage());
            throw new RuntimeException("Błąd generowania kodu KLIK: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // C2B — Potwierdzenie płatności (POST /api/v1/payments/confirm)
    // ─────────────────────────────────────────────────

    /**
     * Potwierdza lub odrzuca płatność C2B po autoryzacji klienta.
     *
     * @param transactionId  ID transakcji z KLIK
     * @param status         ACCEPTED lub REJECTED
     * @param rejectReason   powód odrzucenia (jeśli REJECTED)
     * @return odpowiedź KLIK
     */
    public KlikConfirmPaymentResponse confirmPayment(
            String transactionId, String status, String rejectReason) {
        String url = config.getBaseUrl() + "/api/v1/payments/confirm";
        log.info("Potwierdzanie płatności KLIK: transactionId={}, status={}", transactionId, status);

        String requestBody = "{\"transaction_id\":\"" + transactionId + "\",\"status\":\"" + status + "\"" +
                ("REJECTED".equals(status) && rejectReason != null ? ",\"reject_reason\":\"" + rejectReason + "\"" : "") +
                "}";

        try {
            String responseBody = sendPostRequest(url, requestBody);
            KlikConfirmPaymentResponse body = objectMapper.readValue(responseBody, KlikConfirmPaymentResponse.class);
            log.info("Płatność KLIK potwierdzona: transactionId={}, status={}",
                    body.getTransactionId(), body.getStatus());
            return body;
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            log.error("KLIK niedostępny: {}", e.getMessage());
            throw new RuntimeException("System KLIK jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("Błąd potwierdzania płatności KLIK: {}", e.getMessage());
            throw new RuntimeException("Błąd potwierdzania płatności KLIK: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // C2B — Status płatności (GET /api/v1/payments/status/{transactionId})
    // ─────────────────────────────────────────────────

    /**
     * Sprawdza status płatności C2B.
     *
     * @param transactionId ID transakcji
     * @return status płatności
     */
    public KlikPaymentStatusResponse getPaymentStatus(String transactionId) {
        String url = config.getBaseUrl() + "/api/v1/payments/status/" + transactionId;
        log.info("Sprawdzanie statusu płatności KLIK: transactionId={}", transactionId);

        try {
            ResponseEntity<KlikPaymentStatusResponse> response = restTemplate.getForEntity(
                    url, KlikPaymentStatusResponse.class);

            KlikPaymentStatusResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z KLIK");
            }

            log.info("Status płatności KLIK: transactionId={}, status={}",
                    body.getTransactionId(), body.getStatus());
            return body;

        } catch (ResourceAccessException e) {
            log.error("KLIK niedostępny: {}", e.getMessage());
            throw new RuntimeException("System KLIK jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("Błąd pobierania statusu płatności KLIK: {}", e.getMessage());
            throw new RuntimeException("Błąd pobierania statusu płatności KLIK: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // P2P — Rejestracja aliasu (POST /api/v1/aliases/register)
    // ─────────────────────────────────────────────────

    /**
     * Rejestruje alias P2P (numer telefonu → IBAN/konto).
     *
     * @param phone    numer telefonu w formacie E.164
     * @param iban     IBAN konta bankowego
     * @param zone     strefa (PL, EU, UK, US)
     * @return odpowiedź KLIK z aliasem
     */
    public KlikRegisterAliasResponse registerAlias(String phone, String iban, String zone) {
        String url = config.getBaseUrl() + "/api/v1/aliases/register";
        log.info("Rejestracja aliasu P2P: phone={}, iban={}, zone={}", phone, iban, zone);

        String requestBody = "{\"phone\":\"" + phone + "\",\"account_identifier\":{\"type\":\"iban\",\"value\":\"" + iban + "\"},\"zone\":\"" + zone + "\"}";

        try {
            String responseBody = sendPostRequest(url, requestBody);
            KlikRegisterAliasResponse body = objectMapper.readValue(responseBody, KlikRegisterAliasResponse.class);
            log.info("Alias P2P zarejestrowany: aliasId={}, phone={}", body.getAliasId(), body.getPhone());
            return body;
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            log.error("KLIK niedostępny: {}", e.getMessage());
            throw new RuntimeException("System KLIK jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("Błąd rejestracji aliasu P2P: {}", e.getMessage());
            throw new RuntimeException("Błąd rejestracji aliasu P2P: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // P2P — Lookup aliasu (GET /api/v1/aliases/lookup/{phone})
    // ─────────────────────────────────────────────────

    /**
     * Sprawdza alias P2P dla podanego numeru telefonu.
     * Każdy udany lookup jest płatny zgodnie z cennikiem P2P.
     *
     * @param phone numer telefonu w formacie E.164
     * @return dane aliasu (bank, IBAN)
     */
    public KlikLookupAliasResponse lookupAlias(String phone) {
        String url = config.getBaseUrl() + "/api/v1/aliases/lookup/" + phone;
        log.info("Lookup aliasu P2P: phone={}", phone);

        try {
            ResponseEntity<KlikLookupAliasResponse> response = restTemplate.getForEntity(
                    url, KlikLookupAliasResponse.class);

            KlikLookupAliasResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z KLIK");
            }

            log.info("Alias P2P znaleziony: phone={}, bankCode={}",
                    body.getPhone(), body.getBankCode());
            return body;

        } catch (ResourceAccessException e) {
            log.error("KLIK niedostępny: {}", e.getMessage());
            throw new RuntimeException("System KLIK jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("Błąd lookupu aliasu P2P: {}", e.getMessage());
            throw new RuntimeException("Błąd lookupu aliasu P2P: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // P2P — Usunięcie aliasu (DELETE /api/v1/aliases/{phone})
    // ─────────────────────────────────────────────────

    /**
     * Usuwa alias P2P dla podanego numeru telefonu.
     *
     * @param phone numer telefonu w formacie E.164
     */
    public void deleteAlias(String phone) {
        String url = config.getBaseUrl() + "/api/v1/aliases/" + phone;
        log.info("Usuwanie aliasu P2P: phone={}", phone);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            log.info("Alias P2P usunięty: phone={}", phone);

        } catch (ResourceAccessException e) {
            log.error("KLIK niedostępny: {}", e.getMessage());
            throw new RuntimeException("System KLIK jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("Błąd usuwania aliasu P2P: {}", e.getMessage());
            throw new RuntimeException("Błąd usuwania aliasu P2P: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────
    // Metody pomocnicze
    // ─────────────────────────────────────────────────

    /**
     * Buduje nagłówki autoryzacyjne dla KLIK API.
     * Wymagane: X-KLIK-Bank-Api-Key, Idempotency-Key (UUID), Content-Type.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-KLIK-Bank-Api-Key", config.getApiKey());
        headers.set("X-KLIK-Api-Key", config.getApiKey());
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
    }

    private String sendPostRequest(String url, String requestBody) throws java.io.IOException, InterruptedException {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(config.getConnectTimeout()))
                .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofMillis(config.getReadTimeout()))
                .header("Content-Type", "application/json")
                .header("X-KLIK-Bank-Api-Key", config.getApiKey())
                .header("X-KLIK-Api-Key", config.getApiKey())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException(response.statusCode() + " " + (response.statusCode() == 401 ? "Unauthorized" : response.body()));
        }
        return response.body();
    }
}
