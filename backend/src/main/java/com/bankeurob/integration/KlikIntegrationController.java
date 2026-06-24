package com.bankeurob.integration;

import com.bankeurob.integration.klik.BlikService;
import com.bankeurob.integration.klik.KlikServiceClient;
import com.bankeurob.integration.klik.config.KlikConfig;
import com.bankeurob.integration.klik.dto.*;
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

import java.util.List;
import java.util.Map;

/**
 * Kontroler integracji z systemem KLIK (płatności mobilne C2B i P2P).
 * <p>
 * Endpointy:
 * <ul>
 *   <li><b>C2B:</b> Generowanie kodu, oczekujące transakcje, autoryzacja PIN-em, odrzucenie</li>
 *   <li><b>P2P:</b> Rejestracja aliasu, lookup aliasu, usunięcie aliasu</li>
 *   <li><b>Webhook:</b> Odbieranie żądań autoryzacji od KLIK</li>
 * </ul>
 *
 * @see <a href="https://github.com/your-org/KLIK-payments">KLIK Payments</a>
 */
@RestController
@RequestMapping("/api/klik")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KLIK Integration", description = "Integracja z systemem płatności mobilnych KLIK (C2B – kody, P2P – telefony)")
@SecurityRequirement(name = "bearerAuth")
public class KlikIntegrationController {

    private final KlikServiceClient klikServiceClient;
    private final KlikConfig klikConfig;
    private final BlikService blikService;

    // ─────────────────────────────────────────────────
    // C2B — Generowanie kodu BLIK
    // ─────────────────────────────────────────────────

    @PostMapping("/codes/generate")
    @Operation(summary = "Generuj kod BLIK",
               description = "Generuje 6-cyfrowy kod BLIK dla zalogowanego klienta. " +
                             "Kod ważny 120s, jednorazowy. Klient wpisuje go u agenta (sklepu) aby zainicjować płatność.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kod wygenerowany pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"code\":\"123456\",\"expires_in\":120,\"expires_at\":\"2026-06-02T14:00:00Z\"}"))),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> generateCode(Authentication authentication) {
        log.info("Generowanie kodu BLIK dla zalogowanego klienta");
        try {
            KlikGenerateCodeResponse response = blikService.generateCode(authentication);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd generowania kodu BLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // C2B — Oczekujące transakcje (polling z frontendu)
    // ─────────────────────────────────────────────────

    @GetMapping("/pending-transactions")
    @Operation(summary = "Oczekujące transakcje BLIK",
               description = "Zwraca listę transakcji BLIK oczekujących na autoryzację PIN-em " +
                             "dla zalogowanego klienta. Frontend polluje ten endpoint co kilka sekund.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista oczekujących transakcji")
    })
    public ResponseEntity<List<PendingTransactionDto>> getPendingTransactions(Authentication authentication) {
        List<PendingTransactionDto> pending = blikService.getPendingTransactions(authentication);
        return ResponseEntity.ok(pending);
    }

    // ─────────────────────────────────────────────────
    // C2B — Autoryzacja PIN-em (zatwierdzenie płatności)
    // ─────────────────────────────────────────────────

    @PostMapping("/payments/authorize")
    @Operation(summary = "Autoryzuj płatność BLIK PIN-em",
               description = "Weryfikuje PIN klienta i autoryzuje transakcję BLIK. " +
                             "W przypadku sukcesu: odpisuje środki z konta i wysyła ACCEPTED do KLIK. " +
                             "W przypadku błędu: zwraca błąd bez odpisania środków.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Wynik autoryzacji (success=true/false)"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> authorizePayment(
            @RequestParam String klikTransactionId,
            @RequestParam String pin,
            Authentication authentication) {
        log.info("Autoryzacja płatności BLIK: klikTransactionId={}", klikTransactionId);
        try {
            BlikConfirmResult result = blikService.authorizeTransaction(klikTransactionId, pin, authentication);
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            log.error("Błąd autoryzacji płatności BLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // C2B — Odrzucenie transakcji przez klienta
    // ─────────────────────────────────────────────────

    @PostMapping("/payments/reject")
    @Operation(summary = "Odrzuć płatność BLIK",
               description = "Odrzuca transakcję BLIK bez autoryzacji PIN-em. " +
                             "Wysyła REJECTED do KLIK z powodem USER_DECLINED.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transakcja odrzucona"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> rejectPayment(
            @RequestParam String klikTransactionId,
            Authentication authentication) {
        log.info("Odrzucenie płatności BLIK: klikTransactionId={}", klikTransactionId);
        try {
            BlikConfirmResult result = blikService.rejectTransaction(klikTransactionId, authentication);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Błąd odrzucania płatności BLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // C2B — Status płatności (z KLIK)
    // ─────────────────────────────────────────────────

    @GetMapping("/payments/status/{transactionId}")
    @Operation(summary = "Status płatności BLIK",
               description = "Sprawdza status płatności C2B w systemie KLIK.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status płatności pobrany pomyślnie"),
        @ApiResponse(responseCode = "404", description = "Transakcja nie znaleziona"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> getPaymentStatus(@PathVariable String transactionId) {
        log.info("Sprawdzanie statusu płatności BLIK: transactionId={}", transactionId);
        try {
            KlikPaymentStatusResponse response = klikServiceClient.getPaymentStatus(transactionId);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd pobierania statusu płatności BLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Webhook od KLIK — autoryzacja płatności
    // ─────────────────────────────────────────────────

    @PostMapping("/webhook/authorize")
    @Operation(summary = "Webhook autoryzacji od KLIK",
               description = "Odbiera żądanie autoryzacji płatności od KLIK. " +
                             "Zapisuje transakcję w DB. Klient zobaczy ją na liście oczekujących transakcji " +
                             "i będzie mógł autoryzować PIN-em lub odrzucić.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Żądanie autoryzacji przyjęte"),
        @ApiResponse(responseCode = "503", description = "Błąd przetwarzania")
    })
    public ResponseEntity<?> authorizePayment(@RequestBody KlikAuthorizeRequest request) {
        log.info("Webhook autoryzacji KLIK: transactionId={}, userId={}, amount={} {}",
                request.getTransactionId(), request.getUserId(),
                request.getAmount(), request.getCurrency());

        try {
            KlikAuthorizeResponse response = blikService.handleAuthorizeWebhook(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Błąd przetwarzania webhooka autoryzacji KLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // P2P — Rejestracja aliasu
    // ─────────────────────────────────────────────────

    @PostMapping("/aliases/register")
    @Operation(summary = "Rejestracja aliasu P2P",
               description = "Rejestruje numer telefonu klienta jako alias P2P w systemie KLIK. " +
                             "Umożliwia innym użytkownikom przelewanie środków na numer telefonu.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alias zarejestrowany pomyślnie"),
        @ApiResponse(responseCode = "409", description = "Alias już istnieje"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> registerAlias(
            @RequestParam String phone,
            @RequestParam String iban,
            @RequestParam(defaultValue = "EU") String zone) {
        log.info("Rejestracja aliasu P2P: phone={}, iban={}, zone={}", phone, iban, zone);
        try {
            KlikRegisterAliasResponse response = klikServiceClient.registerAlias(phone, iban, zone);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd rejestracji aliasu P2P: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // P2P — Lookup aliasu
    // ─────────────────────────────────────────────────

    @GetMapping("/aliases/lookup/{phone}")
    @Operation(summary = "Sprawdź alias P2P",
               description = "Sprawdza alias P2P dla podanego numeru telefonu. " +
                             "Każdy udany lookup jest płatny zgodnie z cennikiem KLIK P2P.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alias znaleziony"),
        @ApiResponse(responseCode = "404", description = "Alias nie znaleziony (KLIK zwrócił 404)"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> lookupAlias(@PathVariable String phone) {
        log.info("Lookup aliasu P2P: phone={}", phone);
        try {
            KlikLookupAliasResponse response = klikServiceClient.lookupAlias(phone);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd lookupu aliasu P2P: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // P2P — Usunięcie aliasu
    // ─────────────────────────────────────────────────

    @DeleteMapping("/aliases/{phone}")
    @Operation(summary = "Usuń alias P2P",
               description = "Usuwa alias P2P dla podanego numeru telefonu.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alias usunięty pomyślnie"),
        @ApiResponse(responseCode = "404", description = "Alias nie znaleziony (KLIK zwrócił 404)"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> deleteAlias(@PathVariable String phone) {
        log.info("Usuwanie aliasu P2P: phone={}", phone);
        try {
            klikServiceClient.deleteAlias(phone);
            return ResponseEntity.ok(Map.of("message", "Alias " + phone + " został usunięty"));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd usuwania aliasu P2P: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Status integracji
    // ─────────────────────────────────────────────────

    @GetMapping("/status")
    @Operation(summary = "Status połączenia z KLIK",
               description = "Sprawdza czy system KLIK jest dostępny. Endpoint publiczny (bez autoryzacji).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status połączenia",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"connected\",\"service\":\"KLIK Payments\",\"url\":\"http://web:8000\"}")))
    })
    public ResponseEntity<?> getIntegrationStatus() {
        try {
            klikServiceClient.generateCode("healthcheck", "PL");
            return ResponseEntity.ok(Map.of(
                "status", "connected",
                "service", "KLIK Payments",
                "url", configBaseUrl()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "status", "disconnected",
                "service", "KLIK Payments",
                "error", e.getMessage()
            ));
        }
    }

    private String configBaseUrl() {
        return klikConfig.getBaseUrl();
    }
}
