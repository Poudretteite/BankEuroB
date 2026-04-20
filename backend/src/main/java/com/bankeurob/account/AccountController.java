package com.bankeurob.account;

import com.bankeurob.account.dto.AccountDto;
import io.swagger.v3.oas.annotations.Operation;
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
    public ResponseEntity<List<AccountDto>> getMyAccounts(Authentication authentication) {
        return ResponseEntity.ok(accountService.getMyAccounts(authentication));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły konta", description = "Zwraca szczegóły wybranego konta")
    public ResponseEntity<AccountDto> getAccount(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(accountService.getAccountById(id, authentication));
    }
}
