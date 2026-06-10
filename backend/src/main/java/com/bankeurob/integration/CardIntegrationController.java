package com.bankeurob.integration;

import com.bankeurob.integration.cards.CardsServiceClient;
import com.bankeurob.integration.cards.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

/**
 * Kontroler integracji z systemem kart płatniczych (Payment Gateway).
 * <p>
 * Endpointy:
 * <ul>
 *   <li>Wydanie nowej karty (VIRTUAL, PHYSICAL, PREPAID)</li>
 *   <li>Lista wszystkich kart</li>
 *   <li>Szczegóły karty po tokenie</li>
 *   <li>Blokowanie i odblokowanie karty</li>
 *   <li>Status integracji z Payment Gateway</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cards Integration", description = "Integracja z systemem wydawania kart płatniczych (Payment Gateway)")
@SecurityRequirement(name = "bearerAuth")
public class CardIntegrationController {

    private final CardsServiceClient cardsServiceClient;
    private final com.bankeurob.integration.cards.CardService cardService;

    // ─────────────────────────────────────────────────
    // Wydawanie kart
    // ─────────────────────────────────────────────────

    @PostMapping("/issue")
    @Operation(summary = "Wydaj nową kartę",
               description = "Wydaje nową kartę płatniczą za pośrednictwem Payment Gateway. " +
                             "Wymaga podpisu HMAC-SHA256. Obsługiwane typy: VIRTUAL, PHYSICAL, PREPAID. " +
                             "Kwota początkowa (initialBalance) dotyczy tylko PREPAID.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Karta wydana pomyślnie"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane żądania"),
        @ApiResponse(responseCode = "503", description = "Payment Gateway niedostępny")
    })
    public ResponseEntity<?> issueCard(
            @RequestBody IssueCardRequest request,
            Authentication auth) {
        log.info("Wydawanie karty: type={}, user={}", request.getCardType(), auth != null ? auth.getName() : "anonymous");

        try {
            String userEmail = auth != null ? auth.getName() : "bankeurob_user";
            CardsIssueResponse response = cardService.issueCardForUser(userEmail, request);

            log.info("Karta wydana: token={}, maskedPan={}", response.getCardToken(), response.getMaskedPan());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "cardToken", response.getCardToken(),
                "maskedPan", response.getMaskedPan(),
                "fullPan", response.getFullPan(),
                "cvv", response.getCvv(),
                "expiryMonth", response.getExpiryMonth(),
                "expiryYear", response.getExpiryYear(),
                "cardType", response.getCardType(),
                "bankId", response.getBankId(),
                "message", response.getMessage()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "Payment Gateway jest wyłączony. Uruchom serwis na porcie 8072."));
        } catch (Exception e) {
            log.error("Błąd wydawania karty: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Webhook - Obciążenie (Capture)
    // ─────────────────────────────────────────────────

    @PostMapping("/webhook/capture")
    @Operation(summary = "Webhook obciążeniowy",
               description = "Endpoint dla Payment Gateway, który pobiera środki z konta po zapłaceniu kartą.")
    public ResponseEntity<?> processCaptureWebhook(@RequestBody CardWebhookRequest request) {
        try {
            cardService.processCaptureWebhook(request);
            return ResponseEntity.ok(Map.of("success", true, "message", "Obciążono rachunek"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(402).body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Błąd webhooka: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Lista kart
    // ─────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Lista wszystkich kart",
               description = "Pobiera listę wszystkich kart zarejestrowanych w Payment Gateway. " +
                             "Wymaga klucza X-Admin-Key.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista kart pobrana pomyślnie"),
        @ApiResponse(responseCode = "503", description = "Payment Gateway niedostępny")
    })
    public ResponseEntity<?> listCards() {
        log.info("Pobieranie listy kart z Payment Gateway");
        try {
            CardsListResponse cards = cardsServiceClient.listCards();
            return ResponseEntity.ok(cards);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "Payment Gateway jest wyłączony. Uruchom serwis na porcie 8072."));
        } catch (Exception e) {
            log.error("Błąd pobierania listy kart: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Szczegóły karty
    // ─────────────────────────────────────────────────

    @GetMapping("/{cardToken}")
    @Operation(summary = "Szczegóły karty",
               description = "Pobiera szczegółowe informacje o karcie na podstawie jej tokenu.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły karty pobrane pomyślnie"),
        @ApiResponse(responseCode = "404", description = "Karta nie znaleziona"),
        @ApiResponse(responseCode = "503", description = "Payment Gateway niedostępny")
    })
    public ResponseEntity<?> getCardDetails(@PathVariable String cardToken) {
        log.info("Pobieranie szczegółów karty: token={}", cardToken);
        try {
            CardDetailsResponse card = cardsServiceClient.getCardDetails(cardToken);
            return ResponseEntity.ok(card);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "Payment Gateway jest wyłączony. Uruchom serwis na porcie 8072."));
        } catch (Exception e) {
            log.error("Błąd pobierania szczegółów karty {}: {}", cardToken, e.getMessage());
            return ResponseEntity.status(404).body(Map.of("error", "Karta nie znaleziona: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Blokowanie / odblokowanie karty
    // ─────────────────────────────────────────────────

    @PatchMapping("/{cardToken}/status")
    @Operation(summary = "Zmień status karty",
               description = "Zastrzega (BLOCKED) lub odblokowuje (ACTIVE) kartę. " +
                             "Wymaga podania nowego statusu i opcjonalnego powodu.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status karty zmieniony pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"success\":true,\"card_token\":\"tok_abc123\",\"status\":\"BLOCKED\",\"message\":\"Karta została zablokowana\"}"))),
        @ApiResponse(responseCode = "503", description = "Payment Gateway niedostępny")
    })
    public ResponseEntity<?> changeCardStatus(
            @PathVariable String cardToken,
            @RequestBody StatusChangeRequest request) {
        log.info("Zmiana statusu karty: token={}, newStatus={}, reason={}", cardToken, request.getStatus(), request.getReason());
        try {
            StatusChangeResponse response = cardsServiceClient.changeCardStatus(
                    cardToken, request.getStatus(), request.getReason());
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "Payment Gateway jest wyłączony. Uruchom serwis na porcie 8072."));
        } catch (Exception e) {
            log.error("Błąd zmiany statusu karty {}: {}", cardToken, e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Status integracji
    // ─────────────────────────────────────────────────

    @GetMapping("/status")
    @Operation(summary = "Status połączenia z Payment Gateway",
               description = "Sprawdza czy Payment Gateway jest dostępny. " +
                             "Endpoint nie wymaga autoryzacji (publiczny).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status połączenia",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"connected\",\"service\":\"Payment Gateway\",\"url\":\"http://payment-gateway:8000\"}")))
    })
    public ResponseEntity<?> getIntegrationStatus() {
        try {
            cardsServiceClient.listCards();
            return ResponseEntity.ok(Map.of(
                "status", "connected",
                "service", "Payment Gateway",
                "url", "http://payment-gateway:8000"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "status", "disconnected",
                "service", "Payment Gateway",
                "error", e.getMessage()
            ));
        }
    }
}