package com.bankeurob.integration.sepa.batch;

import com.bankeurob.integration.sepa.batch.config.SepaBatchConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Klient HTTP/XML do komunikacji z SEPA Batch Service.
 * 
 * Endpointy:
 * - POST /transfers/xml – przesłanie pliku XML pain.001 z przelewami batch
 * - GET /sessions       – lista sesji rozliczeniowych
 * - GET /sessions/{id}  – szczegóły sesji
 * - POST /sessions/close/{id} – zamknięcie sesji (netting)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SepaBatchClient {

    private final RestTemplate restTemplate;
    private final SepaBatchConfig config;

    /**
     * Przesyła plik XML pain.001 do SEPA Batch Service.
     * 
     * @param xmlBody treść XML (pain.001)
     * @return odpowiedź XML z serwisu
     */
    public String submitTransferXml(String xmlBody) {
        String url = config.getBaseUrl() + "/transfers/xml";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(xmlBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            log.info("SEPA Batch XML submitted, response status: {}", response.getStatusCode());
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("SEPA Batch Service unavailable: {}", e.getMessage());
            throw new RuntimeException("SEPA Batch Service jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("SEPA Batch XML error: {}", e.getMessage());
            throw new RuntimeException("Błąd SEPA Batch: " + e.getMessage(), e);
        }
    }

    /**
     * Pobiera listę sesji rozliczeniowych SEPA Batch.
     *
     * @return odpowiedź JSON/XML z listą sesji
     */
    public String getSessions() {
        String url = config.getBaseUrl() + "/sessions";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.info("SEPA Batch sessions fetched, response status: {}", response.getStatusCode());
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("SEPA Batch Service unavailable: {}", e.getMessage());
            throw new RuntimeException("SEPA Batch Service jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("SEPA Batch sessions error: {}", e.getMessage());
            throw new RuntimeException("Błąd pobierania sesji SEPA Batch: " + e.getMessage(), e);
        }
    }

    /**
     * Pobiera szczegóły konkretnej sesji rozliczeniowej.
     *
     * @param sessionId ID sesji
     * @return odpowiedź JSON/XML ze szczegółami sesji
     */
    public String getSessionDetails(String sessionId) {
        String url = config.getBaseUrl() + "/sessions/" + sessionId;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.info("SEPA Batch session details fetched for {}", sessionId);
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("SEPA Batch Service unavailable: {}", e.getMessage());
            throw new RuntimeException("SEPA Batch Service jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("SEPA Batch session details error: {}", e.getMessage());
            throw new RuntimeException("Błąd pobierania sesji " + sessionId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Zamyka sesję rozliczeniową i wykonuje multilateral netting.
     *
     * @param sessionId ID sesji do zamknięcia
     * @return odpowiedź JSON/XML z wynikiem nettingu
     */
    public String closeSession(String sessionId) {
        String url = config.getBaseUrl() + "/sessions/close/" + sessionId;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            log.info("SEPA Batch session closed: {}", sessionId);
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("SEPA Batch Service unavailable: {}", e.getMessage());
            throw new RuntimeException("SEPA Batch Service jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("SEPA Batch close session error: {}", e.getMessage());
            throw new RuntimeException("Błąd zamykania sesji " + sessionId + ": " + e.getMessage(), e);
        }
    }
}
