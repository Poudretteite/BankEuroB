package com.bankeurob.integration.sepa.instant;

import com.bankeurob.integration.sepa.instant.config.SepaInstantConfig;
import com.bankeurob.integration.sepa.instant.dto.TransferStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Klient HTTP/XML do komunikacji z SEPA Instant Service.
 * 
 * Endpointy:
 * - POST /transfers/xml          – przesłanie przelewu natychmiastowego XML
 * - GET  /transfers/{transfer_id} – status przelewu instant
 * - GET  /transfers              – lista przelewów instant
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SepaInstantClient {

    private final RestTemplate restTemplate;
    private final SepaInstantConfig config;

    /**
     * Przesyła przelew natychmiastowy XML do SEPA Instant Service.
     * 
     * @param xmlBody treść XML (pain.001)
     * @return odpowiedź XML z serwisu
     */
    public String submitInstantTransferXml(String xmlBody) {
        String url = config.getBaseUrl() + "/transfers/xml";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(xmlBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            log.info("SEPA Instant XML submitted, response status: {}", response.getStatusCode());
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("SEPA Instant Service unavailable: {}", e.getMessage());
            throw new RuntimeException("SEPA Instant Service jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("SEPA Instant XML error: {}", e.getMessage());
            throw new RuntimeException("Błąd SEPA Instant: " + e.getMessage(), e);
        }
    }

    /**
     * Sprawdza status przelewu instant.
     * 
     * @param transferId ID przelewu
     * @return status przelewu
     */
    public TransferStatusResponse getTransferStatus(String transferId) {
        String url = config.getBaseUrl() + "/transfers/" + transferId;
        try {
            ResponseEntity<TransferStatusResponse> response = restTemplate.getForEntity(url, TransferStatusResponse.class);
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("SEPA Instant Service unavailable: {}", e.getMessage());
            throw new RuntimeException("SEPA Instant Service jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("SEPA Instant status error: {}", e.getMessage());
            throw new RuntimeException("Błąd pobierania statusu SEPA Instant: " + e.getMessage(), e);
        }
    }

    /**
     * Pobiera listę wszystkich przelewów natychmiastowych.
     *
     * @return odpowiedź JSON/XML z listą przelewów
     */
    public String getTransfers() {
        String url = config.getBaseUrl() + "/transfers";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.info("SEPA Instant transfers list fetched, response status: {}", response.getStatusCode());
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("SEPA Instant Service unavailable: {}", e.getMessage());
            throw new RuntimeException("SEPA Instant Service jest wyłączony na " + config.getBaseUrl(), e);
        } catch (Exception e) {
            log.error("SEPA Instant transfers list error: {}", e.getMessage());
            throw new RuntimeException("Błąd pobierania listy SEPA Instant: " + e.getMessage(), e);
        }
    }
}
