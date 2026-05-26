package com.bankeurob.integration;

import com.bankeurob.integration.sepa.batch.SepaBatchClient;
import com.bankeurob.integration.sepa.instant.SepaInstantClient;
import com.bankeurob.integration.sepa.instant.dto.TransferStatusResponse;
import com.bankeurob.integration.swift.SwiftServiceClient;
import com.bankeurob.integration.swift.dto.SwiftCancelResponse;
import com.bankeurob.integration.swift.dto.SwiftMessageResponse;
import com.bankeurob.integration.target.TargetServiceClient;
import com.bankeurob.integration.target.dto.*;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Kontroler administracyjny do zarządzania integracjami zewnętrznymi.
 * <p>
 * Endpointy:
 * <ul>
 *   <li>Rejestracja BankEuroB w TARGET (Central Bank RTGS)</li>
 *   <li>Podgląd banków zarejestrowanych w TARGET</li>
 *   <li>Zastrzyk płynności w TARGET</li>
 *   <li>Podgląd sesji SEPA Batch</li>
 *   <li>Status przelewów SEPA Instant</li>
 *   <li>Wysyłanie i anulowanie komunikatów SWIFT</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Integration", description = "Zarządzanie integracjami zewnętrznymi (TARGET RTGS, SEPA Batch, SEPA Instant, SWIFT)")
@SecurityRequirement(name = "bearerAuth")
public class AdminIntegrationController {

    private final TargetServiceClient targetClient;
    private final SepaBatchClient sepaBatchClient;
    private final SepaInstantClient sepaInstantClient;
    private final SwiftServiceClient swiftClient;

    // ─────────────────────────────────────────────────
    // TARGET – Banki
    // ─────────────────────────────────────────────────

    @PostMapping("/register-bank")
    @Operation(summary = "Rejestracja BankEuroB w TARGET",
               description = "Rejestruje BankEuroB (lub inny bank) w centralnym systemie TARGET RTGS. " +
                             "Wymaga podania BIC i nazwy banku.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank zarejestrowany pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"id\":1,\"bic\":\"BKEBPLPW\",\"name\":\"BankEuroB\",\"is_blocked\":false,\"created_at\":\"2026-05-26T12:00:00Z\"}"))),
        @ApiResponse(responseCode = "409", description = "Bank o podanym BIC już istnieje",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Bank o BIC BKEBPLPW już istnieje w systemie TARGET\"}"))),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyłączony",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"TARGET Service (RTGS) jest wyłączony na http://localhost:8001\"}")))
    })
    public ResponseEntity<?> registerBank(@RequestBody BankCreateRequest request) {
        log.info("Rejestracja banku w TARGET: BIC={}, name={}", request.getBic(), request.getName());
        try {
            BankResponse response = targetClient.createBank(request);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            log.error("TARGET Service unavailable: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyłączony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("Błąd rejestracji banku w TARGET: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/target/banks")
    @Operation(summary = "Lista banków w TARGET",
               description = "Pobiera listę wszystkich banków zarejestrowanych w systemie TARGET RTGS.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista banków pobrana pomyślnie"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyłączony")
    })
    public ResponseEntity<?> getTargetBanks() {
        log.info("Pobieranie listy banków z TARGET");
        try {
            List<BankResponse> banks = targetClient.getBanks();
            return ResponseEntity.ok(banks);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyłączony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("Błąd pobierania banków z TARGET: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/target/banks/{bic}")
    @Operation(summary = "Szczegóły banku w TARGET",
               description = "Pobiera szczegółowe informacje o banku zarejestrowanym w TARGET, " +
                             "w tym listę kont settlement.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły banku pobrane pomyślnie"),
        @ApiResponse(responseCode = "404", description = "Bank nie znaleziony"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyłączony")
    })
    public ResponseEntity<?> getTargetBankDetails(@PathVariable String bic) {
        log.info("Pobieranie szczegółów banku z TARGET: BIC={}", bic);
        try {
            BankDetailResponse bank = targetClient.getBank(bic);
            return ResponseEntity.ok(bank);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyłączony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("Błąd pobierania banku {} z TARGET: {}", bic, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/target/banks/{bic}/block")
    @Operation(summary = "Blokada banku w TARGET",
               description = "Blokuje bank w systemie TARGET, uniemożliwiając mu dokonywanie rozliczeń.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank zablokowany pomyślnie"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyłączony")
    })
    public ResponseEntity<?> blockTargetBank(@PathVariable String bic) {
        log.info("Blokowanie banku w TARGET: BIC={}", bic);
        try {
            targetClient.blockBank(bic);
            return ResponseEntity.ok(Map.of("message", "Bank " + bic + " został zablokowany w systemie TARGET"));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyłączony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("Błąd blokowania banku {} w TARGET: {}", bic, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/target/banks/{bic}/unblock")
    @Operation(summary = "Odblokowanie banku w TARGET",
               description = "Odblokowuje bank w systemie TARGET, przywracając mu możliwość rozliczeń.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank odblokowany pomyślnie"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyłączony")
    })
    public ResponseEntity<?> unblockTargetBank(@PathVariable String bic) {
        log.info("Odblokowywanie banku w TARGET: BIC={}", bic);
        try {
            targetClient.unblockBank(bic);
            return ResponseEntity.ok(Map.of("message", "Bank " + bic + " został odblokowany w systemie TARGET"));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyłączony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("Błąd odblokowywania banku {} w TARGET: {}", bic, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // TARGET – Płynność
    // ─────────────────────────────────────────────────

    @PostMapping("/target/liquidity")
    @Operation(summary = "Zastrzyk płynności w TARGET",
               description = "Wpłaca środki na konto settlement banku w systemie TARGET RTGS " +
                             "w celu zwiększenia płynności międzybankowej.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zastrzyk płynności wykonany pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"transfer_id\":\"LIQ-20260526-0001\",\"bank_bic\":\"BKEBPLPW\",\"amount\":1000000.00,\"new_balance\":2500000.00}"))),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyłączony")
    })
    public ResponseEntity<?> injectLiquidity(@RequestBody LiquidityInjectionRequest request) {
        log.info("Zastrzyk płynności w TARGET: BIC={}, amount={} {}", request.getBankBic(), request.getAmount(), request.getCurrency());
        try {
            LiquidityInjectionResponse response = targetClient.injectLiquidity(request);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyłączony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("Błąd zastrzyku płynności w TARGET: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // SEPA Batch – Sesje
    // ─────────────────────────────────────────────────

    @GetMapping("/sepa/sessions")
    @Operation(summary = "Lista sesji SEPA Batch",
               description = "Pobiera listę sesji rozliczeniowych SEPA Batch (clearing sessions).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista sesji pobrana pomyślnie"),
        @ApiResponse(responseCode = "503", description = "SEPA Batch Service wyłączony")
    })
    public ResponseEntity<?> getSepaBatchSessions() {
        log.info("Pobieranie sesji SEPA Batch");
        try {
            // SepaBatchClient nie ma jeszcze metody getSessions – wywołujemy GET /sessions przez RestTemplate
            String response = sepaBatchClient.getSessions();
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Batch Service jest wyłączony. Uruchom serwis na porcie 8002."));
        } catch (Exception e) {
            log.error("Błąd pobierania sesji SEPA Batch: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sepa/sessions/{sessionId}")
    @Operation(summary = "Szczegóły sesji SEPA Batch",
               description = "Pobiera szczegółowe informacje o konkretnej sesji rozliczeniowej SEPA Batch.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły sesji pobrane pomyślnie"),
        @ApiResponse(responseCode = "404", description = "Sesja nie znaleziona"),
        @ApiResponse(responseCode = "503", description = "SEPA Batch Service wyłączony")
    })
    public ResponseEntity<?> getSepaBatchSessionDetails(@PathVariable String sessionId) {
        log.info("Pobieranie szczegółów sesji SEPA Batch: {}", sessionId);
        try {
            String response = sepaBatchClient.getSessionDetails(sessionId);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Batch Service jest wyłączony. Uruchom serwis na porcie 8002."));
        } catch (Exception e) {
            log.error("Błąd pobierania sesji {} SEPA Batch: {}", sessionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sepa/sessions/{sessionId}/close")
    @Operation(summary = "Zamknięcie sesji SEPA Batch (netting)",
               description = "Zamyka sesję rozliczeniową i wykonuje multilateral netting.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesja zamknięta, netting wykonany"),
        @ApiResponse(responseCode = "503", description = "SEPA Batch Service wyłączony")
    })
    public ResponseEntity<?> closeSepaBatchSession(@PathVariable String sessionId) {
        log.info("Zamykanie sesji SEPA Batch: {}", sessionId);
        try {
            String response = sepaBatchClient.closeSession(sessionId);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Batch Service jest wyłączony. Uruchom serwis na porcie 8002."));
        } catch (Exception e) {
            log.error("Błąd zamykania sesji {} SEPA Batch: {}", sessionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // SEPA Instant – Status przelewów
    // ─────────────────────────────────────────────────

    @GetMapping("/sepa/instant/{transferId}")
    @Operation(summary = "Status przelewu SEPA Instant",
               description = "Sprawdza status przelewu natychmiastowego w SEPA Instant Service.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status przelewu pobrany pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"transfer_id\":\"INST-20260526-0001\",\"status\":\"COMPLETED\",\"processed_at\":\"2026-05-26T12:00:00Z\",\"error_message\":null}"))),
        @ApiResponse(responseCode = "404", description = "Przelew nie znaleziony"),
        @ApiResponse(responseCode = "503", description = "SEPA Instant Service wyłączony")
    })
    public ResponseEntity<?> getInstantTransferStatus(@PathVariable String transferId) {
        log.info("Sprawdzanie statusu SEPA Instant: {}", transferId);
        try {
            TransferStatusResponse status = sepaInstantClient.getTransferStatus(transferId);
            return ResponseEntity.ok(status);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Instant Service jest wyłączony. Uruchom serwis na porcie 8003."));
        } catch (Exception e) {
            log.error("Błąd pobierania statusu SEPA Instant {}: {}", transferId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sepa/instant")
    @Operation(summary = "Lista przelewów SEPA Instant",
               description = "Pobiera listę wszystkich przelewów natychmiastowych z SEPA Instant Service.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista przelewów pobrana pomyślnie"),
        @ApiResponse(responseCode = "503", description = "SEPA Instant Service wyłączony")
    })
    public ResponseEntity<?> getInstantTransfers() {
        log.info("Pobieranie listy przelewów SEPA Instant");
        try {
            String response = sepaInstantClient.getTransfers();
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Instant Service jest wyłączony. Uruchom serwis na porcie 8003."));
        } catch (Exception e) {
            log.error("Błąd pobierania listy SEPA Instant: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // SWIFT Middleware – Komunikaty
    // ─────────────────────────────────────────────────

    @PostMapping("/swift/message")
    @Operation(summary = "Wysłanie komunikatu SWIFT (XML)",
               description = "Wysyła komunikat XML w formacie pacs.008.001.08 do SWIFT Middleware. " +
                             "Wymaga podania treści XML w body żądania (Content-Type: application/xml).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Komunikat SWIFT przyjęty przez middleware",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"accepted\",\"message_id\":\"MSG-1001\",\"uetr\":\"11111111-1111-4111-8111-111111111111\",\"receiver_bank\":\"Bank UK 1\",\"route\":[\"PLBKPL01XXX\",\"UKBKGB01XXX\"],\"estimated_seconds\":1.0,\"cancel_window_seconds\":5}"))),
        @ApiResponse(responseCode = "503", description = "SWIFT Middleware wyłączony")
    })
    public ResponseEntity<?> sendSwiftMessage(@RequestBody String xmlMessage) {
        log.info("Wysyłanie komunikatu SWIFT do middleware");
        try {
            SwiftMessageResponse response = swiftClient.submitSwiftMessage(xmlMessage);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SWIFT Middleware jest wyłączony. Uruchom serwis na porcie 3000."));
        } catch (Exception e) {
            log.error("Błąd wysyłania komunikatu SWIFT: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/swift/cancel/{uetr}")
    @Operation(summary = "Anulowanie komunikatu SWIFT",
               description = "Anuluje oczekujący komunikat SWIFT w middleware po UETR. " +
                             "Możliwe tylko w oknie anulowania (domyślnie 5 sekund).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Komunikat anulowany",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"cancelled\",\"uetr\":\"11111111-1111-4111-8111-111111111111\"}"))),
        @ApiResponse(responseCode = "404", description = "Nie znaleziono lub okno anulowania zamknięte"),
        @ApiResponse(responseCode = "503", description = "SWIFT Middleware wyłączony")
    })
    public ResponseEntity<?> cancelSwiftMessage(@PathVariable String uetr) {
        log.info("Anulowanie komunikatu SWIFT: UETR={}", uetr);
        try {
            SwiftCancelResponse response = swiftClient.cancelSwiftMessage(uetr);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SWIFT Middleware jest wyłączony. Uruchom serwis na porcie 3000."));
        } catch (Exception e) {
            log.error("Błąd anulowania komunikatu SWIFT {}: {}", uetr, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
