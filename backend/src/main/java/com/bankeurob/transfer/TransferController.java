package com.bankeurob.transfer;

import com.bankeurob.transfer.dto.TransactionDto;
import com.bankeurob.transfer.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Zlecanie i historia przelewów bankowych")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @Operation(
            summary = "Zlecenie przelewu",
            description = "Tworzy przelew INTERNAL (wewnętrzny), SEPA_SCT, SEPA_INSTANT lub SWIFT"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Przelew utworzony pomyślnie",
            content = @Content(examples = @ExampleObject(value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440003\",\"referenceNumber\":\"BEB-20260512-000001\",\"transactionType\":\"SEPA_SCT\",\"status\":\"COMPLETED\",\"senderIban\":\"DE89370400440532013000\",\"senderName\":\"Jan Kowalski\",\"receiverIban\":\"FR1420041010050500013M02606\",\"receiverName\":\"Marie Curie S.A.\",\"amount\":2500.00,\"currency\":\"EUR\",\"title\":\"Faktura nr 123/2026\",\"requestedAt\":\"2026-05-12T14:30:00Z\",\"completedAt\":\"2026-05-12T14:30:05Z\"}"))),
        @ApiResponse(responseCode = "400", description = "Błąd walidacji – nieprawidłowe dane przelewu",
            content = @Content(examples = {
                @ExampleObject(name = "Niewystarczające środki", value = "{\"error\":\"Niewystarczające środki (Kwota: 100000.00 EUR, Opłaty: 0.00 EUR). Dostępne saldo: 5000.00 EUR, Limit debetowy: 500.00 EUR.\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/transfers\"}"),
                @ExampleObject(name = "Konto nieaktywne", value = "{\"error\":\"Konto nadawcy jest nieaktywne\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/transfers\"}"),
                @ExampleObject(name = "Nie znaleziono konta", value = "{\"error\":\"Konto nadawcy nie znalezione: DE00000000000000000000\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/transfers\"}")
            })),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień do konta nadawcy",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Brak uprawnień do konta nadawcy\",\"status\":403,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/transfers\"}"))),
        @ApiResponse(responseCode = "422", description = "Błąd walidacji pól – np. IBAN, kwota",
            content = @Content(examples = @ExampleObject(value = "{\"senderIban\":\"IBAN nadawcy jest wymagany\",\"amount\":\"Kwota musi być dodatnia\"}")))
    })
    public ResponseEntity<TransactionDto> createTransfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.createTransfer(request, authentication));
    }

    @PostMapping("/webhook/target")
    @Operation(
            summary = "Webhook dla systemu TARGET",
            description = "Endpoint do odbierania powiadomień z systemu TARGET o przychodzącym przelewie"
    )
    public ResponseEntity<Void> handleTargetWebhook(
            @Valid @RequestBody com.bankeurob.transfer.dto.TargetIncomingWebhookDto request
    ) {
        transferService.handleIncomingTargetWebhook(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(
            summary = "Historia transakcji",
            description = "Zwraca historię transakcji dla podanego IBAN (parametr ?iban=...)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista transakcji dla danego IBAN",
            content = @Content(examples = @ExampleObject(value = "[{\"id\":\"550e8400-e29b-41d4-a716-446655440003\",\"referenceNumber\":\"BEB-20260512-000001\",\"transactionType\":\"SEPA_SCT\",\"status\":\"COMPLETED\",\"senderIban\":\"DE89370400440532013000\",\"senderName\":\"Jan Kowalski\",\"receiverIban\":\"FR1420041010050500013M02606\",\"receiverName\":\"Marie Curie S.A.\",\"amount\":2500.00,\"currency\":\"EUR\",\"title\":\"Faktura nr 123/2026\",\"requestedAt\":\"2026-05-12T14:30:00Z\",\"completedAt\":\"2026-05-12T14:30:05Z\"}]"))),
        @ApiResponse(responseCode = "404", description = "Konto nie znalezione",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Konto nie znalezione: DE00000000000000000000\",\"status\":404,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/transfers\"}"))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień do tego konta",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Brak uprawnień do tego konta\",\"status\":403,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/transfers\"}")))
    })
    public ResponseEntity<List<TransactionDto>> getTransactions(
            @RequestParam String iban,
            Authentication authentication
    ) {
        return ResponseEntity.ok(transferService.getMyTransactions(iban, authentication));
    }
}
