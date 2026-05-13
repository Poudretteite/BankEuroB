package com.bankeurob.account;

import com.bankeurob.account.dto.AccountDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Zarządzanie kontami bankowymi")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "Lista kont", description = "Zwraca wszystkie konta zalogowanego klienta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista kont klienta",
            content = @Content(examples = @ExampleObject(value = "[{\"id\":\"550e8400-e29b-41d4-a716-446655440002\",\"iban\":\"DE89370400440532013000\",\"bic\":\"BKEUDEBBXXX\",\"accountType\":\"STANDARD\",\"currency\":\"EUR\",\"balance\":12500.50,\"availableBalance\":12000.00,\"dailyLimit\":5000.00,\"isActive\":true,\"createdAt\":\"2026-01-15T10:30:00Z\"}]"))),
        @ApiResponse(responseCode = "401", description = "Brak autoryzacji – token JWT wymagany",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Unauthorized\",\"status\":401,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/accounts\"}")))
    })
    public ResponseEntity<List<AccountDto>> getMyAccounts(Authentication authentication) {
        return ResponseEntity.ok(accountService.getMyAccounts(authentication));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły konta", description = "Zwraca szczegóły wybranego konta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły konta",
            content = @Content(examples = @ExampleObject(value = "{\"id\":\"550e8400-e29b-41d4-a716-446655440002\",\"iban\":\"DE89370400440532013000\",\"bic\":\"BKEUDEBBXXX\",\"accountType\":\"STANDARD\",\"currency\":\"EUR\",\"balance\":12500.50,\"availableBalance\":12000.00,\"dailyLimit\":5000.00,\"isActive\":true,\"createdAt\":\"2026-01-15T10:30:00Z\"}"))),
        @ApiResponse(responseCode = "404", description = "Konto nie znalezione",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Konto nie znalezione: 550e8400-e29b-41d4-a716-446655440099\",\"status\":404,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/accounts/...\"}"))),
        @ApiResponse(responseCode = "403", description = "Brak dostępu do tego konta",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Brak dostępu do tego konta\",\"status\":403,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/accounts/...\"}")))
    })
    public ResponseEntity<AccountDto> getAccount(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(accountService.getAccountById(id, authentication));
    }
}
