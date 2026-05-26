package com.bankeurob.integration.swift;

import com.bankeurob.integration.swift.config.SwiftServiceConfig;
import com.bankeurob.integration.swift.dto.SwiftCancelResponse;
import com.bankeurob.integration.swift.dto.SwiftMessageResponse;
import com.bankeurob.integration.swift.dto.SwiftTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Klient HTTP do komunikacji z SWIFT Middleware (SWIFT-Aplikacje-Biznesowe).
 * <p>
 * SWIFT Middleware działa na porcie 3000 i udostępnia:
 * <ul>
 *   <li>POST /auth/token – uzyskanie tokena OAuth2 (client_credentials)</li>
 *   <li>POST /swift/message – wysłanie komunikatu pacs.008 XML</li>
 *   <li>POST /swift/cancel/{uetr} – anulowanie oczekującego przelewu</li>
 * </ul>
 * <p>
 * Klient automatycznie zarządza tokenem OAuth2 (cache'uje go i odświeża po wygaśnięciu).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwiftServiceClient {

    private final RestTemplate restTemplate;
    private final SwiftServiceConfig config;

    // Cache tokena w pamięci
    private final ConcurrentHashMap<String, TokenCache> tokenCache = new ConcurrentHashMap<>();
    private static final String TOKEN_CACHE_KEY = "swift_token";

    /**
     * Wysyła komunikat SWIFT (pacs.008 XML) do SWIFT Middleware.
     *
     * @param xmlMessage komunikat XML w formacie pacs.008.001.08
     * @return odpowiedź z SWIFT Middleware
     * @throws RuntimeException gdy SWIFT Middleware jest niedostępny lub zwróci błąd
     */
    public SwiftMessageResponse submitSwiftMessage(String xmlMessage) {
        String token = getAccessToken();
        String url = config.getBaseUrl() + "/swift/message";

        log.info("Wysyłanie komunikatu SWIFT do {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(xmlMessage, headers);

        try {
            ResponseEntity<SwiftMessageResponse> response = restTemplate.postForEntity(
                    url, entity, SwiftMessageResponse.class);

            SwiftMessageResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z SWIFT Middleware");
            }

            if (body.getError() != null) {
                log.error("SWIFT Middleware zwrócił błąd: {}", body.getError());
                throw new RuntimeException("SWIFT Middleware błąd: " + body.getError());
            }

            log.info("SWIFT komunikat przyjęty: status={}, uetr={}, messageId={}",
                    body.getStatus(), body.getUetr(), body.getMessageId());

            return body;

        } catch (ResourceAccessException e) {
            log.error("SWIFT Middleware niedostępny: {}", e.getMessage());
            throw new RuntimeException("SWIFT Middleware jest wyłączony na " + config.getBaseUrl(), e);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("SWIFT Middleware błąd")) {
                throw e;
            }
            log.error("Błąd komunikacji z SWIFT Middleware: {}", e.getMessage());
            throw new RuntimeException("Błąd wysyłania komunikatu SWIFT: " + e.getMessage(), e);
        }
    }

    /**
     * Anuluje oczekujący przelew SWIFT po UETR.
     *
     * @param uetr unikalny identyfikator transakcji (UETR)
     * @return odpowiedź z SWIFT Middleware
     * @throws RuntimeException gdy SWIFT Middleware jest niedostępny
     */
    public SwiftCancelResponse cancelSwiftMessage(String uetr) {
        String token = getAccessToken();
        String url = config.getBaseUrl() + "/swift/cancel/" + uetr;

        log.info("Anulowanie komunikatu SWIFT: UETR={}", uetr);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<SwiftCancelResponse> response = restTemplate.postForEntity(
                    url, entity, SwiftCancelResponse.class);

            SwiftCancelResponse body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Pusta odpowiedź z SWIFT Middleware przy anulowaniu");
            }

            if (body.getError() != null) {
                log.warn("Nie można anulować przelewu {}: {}", uetr, body.getError());
                throw new RuntimeException("Nie można anulować przelewu SWIFT: " + body.getError());
            }

            log.info("SWIFT komunikat anulowany: UETR={}, status={}", uetr, body.getStatus());
            return body;

        } catch (ResourceAccessException e) {
            log.error("SWIFT Middleware niedostępny przy anulowaniu: {}", e.getMessage());
            throw new RuntimeException("SWIFT Middleware jest wyłączony na " + config.getBaseUrl(), e);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Nie można anulować")) {
                throw e;
            }
            log.error("Błąd anulowania komunikatu SWIFT: {}", e.getMessage());
            throw new RuntimeException("Błąd anulowania przelewu SWIFT: " + e.getMessage(), e);
        }
    }

    /**
     * Pobiera (lub odświeża) token OAuth2 z SWIFT Middleware.
     * Token jest cache'owany w pamięci do momentu wygaśnięcia.
     *
     * @return ważny access_token
     */
    private String getAccessToken() {
        TokenCache cached = tokenCache.get(TOKEN_CACHE_KEY);
        if (cached != null && cached.isValid()) {
            return cached.token;
        }

        return refreshToken();
    }

    /**
     * Odświeża token OAuth2 przez wywołanie POST /auth/token.
     */
    private synchronized String refreshToken() {
        // Podwójne sprawdzenie – inny wątek mógł już odświeżyć
        TokenCache cached = tokenCache.get(TOKEN_CACHE_KEY);
        if (cached != null && cached.isValid()) {
            return cached.token;
        }

        String url = config.getBaseUrl() + "/auth/token";
        log.info("Pobieranie tokena OAuth2 z SWIFT Middleware: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", config.getClientId());
        body.add("client_secret", config.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<SwiftTokenResponse> response = restTemplate.postForEntity(
                    url, entity, SwiftTokenResponse.class);

            SwiftTokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new RuntimeException("Nie udało się uzyskać tokena OAuth2 z SWIFT Middleware");
            }

            // Cache'uj token z uwzględnieniem marginesu bezpieczeństwa (30s przed wygaśnięciem)
            int expiresIn = tokenResponse.getExpiresIn() != null ? tokenResponse.getExpiresIn() : 3600;
            TokenCache newCache = new TokenCache(
                    tokenResponse.getAccessToken(),
                    Instant.now().plusSeconds(expiresIn - 30)
            );
            tokenCache.put(TOKEN_CACHE_KEY, newCache);

            log.info("Token OAuth2 pobrany, ważny przez {}s", expiresIn);
            return newCache.token;

        } catch (ResourceAccessException e) {
            log.error("SWIFT Middleware niedostępny przy pobieraniu tokena: {}", e.getMessage());
            throw new RuntimeException("SWIFT Middleware (auth) jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("Błąd pobierania tokena OAuth2: {}", e.getMessage());
            throw new RuntimeException("Błąd autoryzacji w SWIFT Middleware: " + e.getMessage(), e);
        }
    }

    /**
     * Wewnętrzna klasa do cache'owania tokena z czasem wygaśnięcia.
     */
    private static class TokenCache {
        private final String token;
        private final Instant expiresAt;

        TokenCache(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
