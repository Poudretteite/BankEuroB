package com.bankeurob.integration;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

/**
 * Kontroler integracji z systemem KLIK (płatności mobilne C2B i P2P).
 * <p>
 * Endpointy:
 * <ul>
 *   <li><b>C2B:</b> Generowanie kodu, potwierdzanie płatności, status płatności</li>
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

    // ─────────────────────────────────────────────────
    // C2B — Generowanie kodu KLIK
    // ─────────────────────────────────────────────────

    @PostMapping("/codes/generate")
    @Operation(summary = "Generuj kod KLIK",
               description = "Generuje 6-cyfrowy kod KLIK dla zalogowanego klienta. " +
                             "Kod ważny 120s, jednorazowy. Klient wpisuje go u agenta (sklepu) aby zainicjować płatność.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kod wygenerowany pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"code\":\"123456\",\"expires_in\":120,\"expires_at\":\"2026-06-02T14:00:00Z\"}"))),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> generateCode(@RequestParam(defaultValue = "PL") String zone) {
        log.info("Generowanie kodu KLIK: zone={}", zone);
        try {
            KlikGenerateCodeResponse response = klikServiceClient.generateCode("bankeurob_user", zone);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd generowania kodu KLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // C2B — Status płatności
    // ─────────────────────────────────────────────────

    @GetMapping("/payments/status/{transactionId}")
    @Operation(summary = "Status płatności KLIK",
               description = "Sprawdza status płatności C2B w systemie KLIK.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status płatności pobrany pomyślnie"),
        @ApiResponse(responseCode = "404", description = "Transakcja nie znaleziona"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> getPaymentStatus(@PathVariable String transactionId) {
        log.info("Sprawdzanie statusu płatności KLIK: transactionId={}", transactionId);
        try {
            KlikPaymentStatusResponse response = klikServiceClient.getPaymentStatus(transactionId);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd pobierania statusu płatności KLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // C2B — Potwierdzenie płatności (wywoływane po autoryzacji klienta)
    // ─────────────────────────────────────────────────

    @PostMapping("/payments/confirm")
    @Operation(summary = "Potwierdź/odrzuć płatność KLIK",
               description = "Potwierdza (ACCEPTED) lub odrzuca (REJECTED) płatność C2B " +
                             "po autoryzacji klienta w aplikacji bankowej.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Płatność potwierdzona pomyślnie"),
        @ApiResponse(responseCode = "409", description = "Przedwczesne potwierdzenie (brak autoryzacji)"),
        @ApiResponse(responseCode = "503", description = "System KLIK niedostępny")
    })
    public ResponseEntity<?> confirmPayment(
            @RequestParam String transactionId,
            @RequestParam String status,
            @RequestParam(required = false) String rejectReason) {
        log.info("Potwierdzanie płatności KLIK: transactionId={}, status={}", transactionId, status);
        try {
            KlikConfirmPaymentResponse response = klikServiceClient.confirmPayment(
                    transactionId, status, rejectReason);
            return ResponseEntity.ok(response);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(503).body(Map.of("error",
                "System KLIK jest wyłączony. Uruchom serwis na porcie 8000."));
        } catch (Exception e) {
            log.error("Błąd potwierdzania płatności KLIK: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // Webhook od KLIK — autoryzacja płatności
    // ─────────────────────────────────────────────────

    @PostMapping("/webhook/authorize")
    @Operation(summary = "Webhook autoryzacji od KLIK",
               description = "Odbiera żądanie autoryzacji płatności od KLIK. " +
                             "Bank musi pokazać klientowi push z prośbą o autoryzację PINem. " +
                             "Decyzja klienta jest przekazywana do KLIK przez POST /payments/confirm.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Żądanie autoryzacji przyjęte"),
        @ApiResponse(responseCode = "503", description = "Błąd przetwarzania")
    })
    public ResponseEntity<?> authorizePayment(@RequestBody KlikAuthorizeRequest request) {
        log.info("Webhook autoryzacji KLIK: transactionId={}, userId={}, amount={} {}",
                request.getTransactionId(), request.getUserId(),
                request.getAmount(), request.getCurrency());

        try {
            // TODO: Wysłać push notification do klienta z prośbą o autoryzację PINem
            log.info("Żądanie autoryzacji dla użytkownika {} na kwotę {} {} w sklepie {}",
                    request.getUserId(), request.getAmount(),
                    request.getCurrency(), request.getMerchantName());

            return ResponseEntity.ok(new KlikAuthorizeResponse(true, true));
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
            @RequestParam(defaultValue = "PL") String zone) {
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
