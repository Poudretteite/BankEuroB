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
 * Kontroler administracyjny do zarzÄ…dzania integracjami zewnÄ™trznymi.
 * <p>
 * Endpointy:
 * <ul>
 *   <li>Rejestracja BankEuroB w TARGET (Central Bank RTGS)</li>
 *   <li>PodglÄ…d bankĂłw zarejestrowanych w TARGET</li>
 *   <li>Zastrzyk pĹ‚ynnoĹ›ci w TARGET</li>
 *   <li>PodglÄ…d sesji SEPA Batch</li>
 *   <li>Status przelewĂłw SEPA Instant</li>
 *   <li>WysyĹ‚anie i anulowanie komunikatĂłw SWIFT</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Integration", description = "ZarzÄ…dzanie integracjami zewnÄ™trznymi (TARGET RTGS, SEPA Batch, SEPA Instant, SWIFT)")
@SecurityRequirement(name = "bearerAuth")
public class AdminIntegrationController {

    private final TargetServiceClient targetClient;
    private final SepaBatchClient sepaBatchClient;
    private final SepaInstantClient sepaInstantClient;
    private final SwiftServiceClient swiftClient;

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // TARGET â€“ Banki
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/register-bank")
    @Operation(summary = "Rejestracja BankEuroB w TARGET",
               description = "Rejestruje BankEuroB (lub inny bank) w centralnym systemie TARGET RTGS. " +
                             "Wymaga podania BIC i nazwy banku.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank zarejestrowany pomyĹ›lnie",
            content = @Content(examples = @ExampleObject(value = "{\"id\":1,\"bic\":\"BKEBPLPW\",\"name\":\"BankEuroB\",\"is_blocked\":false,\"created_at\":\"2026-05-26T12:00:00Z\"}"))),
        @ApiResponse(responseCode = "409", description = "Bank o podanym BIC juĹĽ istnieje",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Bank o BIC BKEBPLPW juĹĽ istnieje w systemie TARGET\"}"))),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyĹ‚Ä…czony",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"TARGET Service (RTGS) jest wyĹ‚Ä…czony na http://localhost:8001\"}")))
    })
    public ResponseEntity<?> registerBank(@RequestBody BankCreateRequest request) {
        log.info("Rejestracja banku w TARGET: BIC={}, name={}", request.getBic(), request.getName());
        try {
            BankResponse response = targetClient.createBank(request);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            log.error("TARGET Service unavailable: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d rejestracji banku w TARGET: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/target/banks")
    @Operation(summary = "Lista bankĂłw w TARGET",
               description = "Pobiera listÄ™ wszystkich bankĂłw zarejestrowanych w systemie TARGET RTGS.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista bankĂłw pobrana pomyĹ›lnie"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> getTargetBanks() {
        log.info("Pobieranie listy bankĂłw z TARGET");
        try {
            List<BankResponse> banks = targetClient.getBanks();
            return ResponseEntity.ok(banks);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d pobierania bankĂłw z TARGET: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/target/banks/{bic}")
    @Operation(summary = "SzczegĂłĹ‚y banku w TARGET",
               description = "Pobiera szczegĂłĹ‚owe informacje o banku zarejestrowanym w TARGET, " +
                             "w tym listÄ™ kont settlement.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "SzczegĂłĹ‚y banku pobrane pomyĹ›lnie"),
        @ApiResponse(responseCode = "404", description = "Bank nie znaleziony"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> getTargetBankDetails(@PathVariable String bic) {
        log.info("Pobieranie szczegĂłĹ‚Ăłw banku z TARGET: BIC={}", bic);
        try {
            BankDetailResponse bank = targetClient.getBank(bic);
            return ResponseEntity.ok(bank);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d pobierania banku {} z TARGET: {}", bic, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/target/banks/{bic}/block")
    @Operation(summary = "Blokada banku w TARGET",
               description = "Blokuje bank w systemie TARGET, uniemoĹĽliwiajÄ…c mu dokonywanie rozliczeĹ„.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank zablokowany pomyĹ›lnie"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> blockTargetBank(@PathVariable String bic) {
        log.info("Blokowanie banku w TARGET: BIC={}", bic);
        try {
            targetClient.blockBank(bic);
            return ResponseEntity.ok(Map.of("message", "Bank " + bic + " zostaĹ‚ zablokowany w systemie TARGET"));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d blokowania banku {} w TARGET: {}", bic, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/target/banks/{bic}/unblock")
    @Operation(summary = "Odblokowanie banku w TARGET",
               description = "Odblokowuje bank w systemie TARGET, przywracajÄ…c mu moĹĽliwoĹ›Ä‡ rozliczeĹ„.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bank odblokowany pomyĹ›lnie"),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> unblockTargetBank(@PathVariable String bic) {
        log.info("Odblokowywanie banku w TARGET: BIC={}", bic);
        try {
            targetClient.unblockBank(bic);
            return ResponseEntity.ok(Map.of("message", "Bank " + bic + " zostaĹ‚ odblokowany w systemie TARGET"));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d odblokowywania banku {} w TARGET: {}", bic, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // TARGET â€“ PĹ‚ynnoĹ›Ä‡
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/target/liquidity")
    @Operation(summary = "Zastrzyk pĹ‚ynnoĹ›ci w TARGET",
               description = "WpĹ‚aca Ĺ›rodki na konto settlement banku w systemie TARGET RTGS " +
                             "w celu zwiÄ™kszenia pĹ‚ynnoĹ›ci miÄ™dzybankowej.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Zastrzyk pĹ‚ynnoĹ›ci wykonany pomyĹ›lnie",
            content = @Content(examples = @ExampleObject(value = "{\"transfer_id\":\"LIQ-20260526-0001\",\"bank_bic\":\"BKEBPLPW\",\"amount\":1000000.00,\"new_balance\":2500000.00}"))),
        @ApiResponse(responseCode = "503", description = "TARGET Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> injectLiquidity(@RequestBody LiquidityInjectionRequest request) {
        log.info("Zastrzyk pĹ‚ynnoĹ›ci w TARGET: BIC={}, amount={} {}", request.getBankBic(), request.getAmount(), request.getCurrency());
        try {
            LiquidityInjectionResponse response = targetClient.injectLiquidity(request);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "TARGET Service (RTGS) jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8001."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d zastrzyku pĹ‚ynnoĹ›ci w TARGET: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // SEPA Batch â€“ Sesje
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/sepa/sessions")
    @Operation(summary = "Lista sesji SEPA Batch",
               description = "Pobiera listÄ™ sesji rozliczeniowych SEPA Batch (clearing sessions).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista sesji pobrana pomyĹ›lnie"),
        @ApiResponse(responseCode = "503", description = "SEPA Batch Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> getSepaBatchSessions() {
        log.info("Pobieranie sesji SEPA Batch");
        try {
            // SepaBatchClient nie ma jeszcze metody getSessions â€“ wywoĹ‚ujemy GET /sessions przez RestTemplate
            String response = sepaBatchClient.getSessions();
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Batch Service jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8002."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d pobierania sesji SEPA Batch: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sepa/sessions/{sessionId}")
    @Operation(summary = "SzczegĂłĹ‚y sesji SEPA Batch",
               description = "Pobiera szczegĂłĹ‚owe informacje o konkretnej sesji rozliczeniowej SEPA Batch.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "SzczegĂłĹ‚y sesji pobrane pomyĹ›lnie"),
        @ApiResponse(responseCode = "404", description = "Sesja nie znaleziona"),
        @ApiResponse(responseCode = "503", description = "SEPA Batch Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> getSepaBatchSessionDetails(@PathVariable String sessionId) {
        log.info("Pobieranie szczegĂłĹ‚Ăłw sesji SEPA Batch: {}", sessionId);
        try {
            String response = sepaBatchClient.getSessionDetails(sessionId);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Batch Service jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8002."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d pobierania sesji {} SEPA Batch: {}", sessionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sepa/sessions/{sessionId}/close")
    @Operation(summary = "ZamkniÄ™cie sesji SEPA Batch (netting)",
               description = "Zamyka sesjÄ™ rozliczeniowÄ… i wykonuje multilateral netting.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesja zamkniÄ™ta, netting wykonany"),
        @ApiResponse(responseCode = "503", description = "SEPA Batch Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> closeSepaBatchSession(@PathVariable String sessionId) {
        log.info("Zamykanie sesji SEPA Batch: {}", sessionId);
        try {
            String response = sepaBatchClient.closeSession(sessionId);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Batch Service jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8002."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d zamykania sesji {} SEPA Batch: {}", sessionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // SEPA Instant â€“ Status przelewĂłw
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @GetMapping("/sepa/instant/{transferId}")
    @Operation(summary = "Status przelewu SEPA Instant",
               description = "Sprawdza status przelewu natychmiastowego w SEPA Instant Service.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status przelewu pobrany pomyĹ›lnie",
            content = @Content(examples = @ExampleObject(value = "{\"transfer_id\":\"INST-20260526-0001\",\"status\":\"COMPLETED\",\"processed_at\":\"2026-05-26T12:00:00Z\",\"error_message\":null}"))),
        @ApiResponse(responseCode = "404", description = "Przelew nie znaleziony"),
        @ApiResponse(responseCode = "503", description = "SEPA Instant Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> getInstantTransferStatus(@PathVariable String transferId) {
        log.info("Sprawdzanie statusu SEPA Instant: {}", transferId);
        try {
            TransferStatusResponse status = sepaInstantClient.getTransferStatus(transferId);
            return ResponseEntity.ok(status);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Instant Service jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8003."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d pobierania statusu SEPA Instant {}: {}", transferId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sepa/instant")
    @Operation(summary = "Lista przelewĂłw SEPA Instant",
               description = "Pobiera listÄ™ wszystkich przelewĂłw natychmiastowych z SEPA Instant Service.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista przelewĂłw pobrana pomyĹ›lnie"),
        @ApiResponse(responseCode = "503", description = "SEPA Instant Service wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> getInstantTransfers() {
        log.info("Pobieranie listy przelewĂłw SEPA Instant");
        try {
            String response = sepaInstantClient.getTransfers();
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SEPA Instant Service jest wyĹ‚Ä…czony. Uruchom serwis na porcie 8003."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d pobierania listy SEPA Instant: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // SWIFT Middleware â€“ Komunikaty
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostMapping("/swift/message")
    @Operation(summary = "WysĹ‚anie komunikatu SWIFT (XML)",
               description = "WysyĹ‚a komunikat XML w formacie pacs.008.001.08 do SWIFT Middleware. " +
                             "Wymaga podania treĹ›ci XML w body ĹĽÄ…dania (Content-Type: application/xml).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Komunikat SWIFT przyjÄ™ty przez middleware",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"accepted\",\"message_id\":\"MSG-1001\",\"uetr\":\"11111111-1111-4111-8111-111111111111\",\"receiver_bank\":\"Bank UK 1\",\"route\":[\"PLBKPL01XXX\",\"UKBKGB01XXX\"],\"estimated_seconds\":1.0,\"cancel_window_seconds\":5}"))),
        @ApiResponse(responseCode = "503", description = "SWIFT Middleware wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> sendSwiftMessage(@RequestBody String xmlMessage) {
        log.info("WysyĹ‚anie komunikatu SWIFT do middleware");
        try {
            SwiftMessageResponse response = swiftClient.submitSwiftMessage(xmlMessage);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SWIFT Middleware jest wyĹ‚Ä…czony. Uruchom serwis na porcie 3000."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d wysyĹ‚ania komunikatu SWIFT: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/swift/cancel/{uetr}")
    @Operation(summary = "Anulowanie komunikatu SWIFT",
               description = "Anuluje oczekujÄ…cy komunikat SWIFT w middleware po UETR. " +
                             "MoĹĽliwe tylko w oknie anulowania (domyĹ›lnie 5 sekund).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Komunikat anulowany",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"cancelled\",\"uetr\":\"11111111-1111-4111-8111-111111111111\"}"))),
        @ApiResponse(responseCode = "404", description = "Nie znaleziono lub okno anulowania zamkniÄ™te"),
        @ApiResponse(responseCode = "503", description = "SWIFT Middleware wyĹ‚Ä…czony")
    })
    public ResponseEntity<?> cancelSwiftMessage(@PathVariable String uetr) {
        log.info("Anulowanie komunikatu SWIFT: UETR={}", uetr);
        try {
            SwiftCancelResponse response = swiftClient.cancelSwiftMessage(uetr);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "SWIFT Middleware jest wyĹ‚Ä…czony. Uruchom serwis na porcie 3000."));
        } catch (Exception e) {
            log.error("BĹ‚Ä…d anulowania komunikatu SWIFT {}: {}", uetr, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
