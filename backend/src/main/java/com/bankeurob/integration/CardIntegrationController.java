package com.bankeurob.integration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@RequestMapping("/api/cards")
@Slf4j
@Tag(name = "Card Integration", description = "Integracja z zewnętrznym systemem wydawania kart płatniczych (Payment Gateway)")
@SecurityRequirement(name = "bearerAuth")
public class CardIntegrationController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String PAYMENT_GATEWAY_URL = "http://localhost:8000/test-connection";

    @PostMapping("/integrate")
    @Operation(summary = "Zleć wydanie karty", description = "Wysyła żądanie do zewnętrznego systemu Card Provider (FastAPI na localhost:8000) w celu wydania nowej karty płatniczej.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Karta wydana pomyślnie przez system zewnętrzny",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"success\",\"message\":\"Karta została wydana pomyślnie\",\"cardId\":\"CARD-20260512-0001\"}"))),
        @ApiResponse(responseCode = "503", description = "Zewnętrzny system kart jest wyłączony",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Zespół od kart (Payment Gateway) nie ma uruchomionego serwera na localhost:8000.\"}"))),
        @ApiResponse(responseCode = "502", description = "Błąd odpowiedzi z zewnętrznego systemu kart",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Błąd zapytania do modułu kart: ...\"}"))),
        @ApiResponse(responseCode = "500", description = "Nieznany błąd wewnętrzny",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Błąd systemu: ...\"}")))
    })
    public ResponseEntity<?> requestCardFromExternalProvider() {
        log.info("Zlecanie wydania karty w zewnętrznym module (Card Provider Service)...");
        try {
            // Strzał POST do ich FastAPI
            ResponseEntity<Map> response = restTemplate.postForEntity(PAYMENT_GATEWAY_URL, null, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (ResourceAccessException e) {
            log.error("Moduł Kart Płatniczych jest wyłączony! " + e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "Zespół od kart (Payment Gateway) nie ma uruchomionego serwera na localhost:8000."));
        } catch (HttpClientErrorException e) {
            log.error("Błąd zapytania do modułu kart: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Nieznany błąd podczas łączenia z systemem zewnętrznym: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Błąd systemu: " + e.getMessage()));
        }
    }
}
