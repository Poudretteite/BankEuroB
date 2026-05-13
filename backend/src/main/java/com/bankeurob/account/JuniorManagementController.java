package com.bankeurob.account;

import com.bankeurob.account.dto.JuniorAccountRequest;
import com.bankeurob.auth.LoginAttempt;
import com.bankeurob.auth.LoginAttemptRepository;
import com.bankeurob.security.CustomerUserDetails;
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
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import com.bankeurob.transfer.TransactionRepository;
import com.bankeurob.transfer.dto.TransactionDto;
import com.bankeurob.transfer.Transaction;

@RestController
@RequestMapping("/api/junior")
@RequiredArgsConstructor
@Tag(name = "Junior Management", description = "Zarządzanie kontami dziecięcymi (Junior) – zakładanie, autoryzacja logowania i przelewów przez rodzica")
@SecurityRequirement(name = "bearerAuth")
public class JuniorManagementController {

    private final AccountService accountService;
    private final LoginAttemptRepository loginAttemptRepository;
    private final com.bankeurob.transfer.TransferService transferService;
    private final TransactionRepository transactionRepository;

    @PostMapping("/account")
    @Operation(summary = "Założ konto Junior", description = "Rodzic zakłada konto bankowe dla dziecka (Junior). Wymaga autoryzacji rodzica.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Konto Junior utworzone pomyślnie"),
        @ApiResponse(responseCode = "400", description = "Email dziecka jest już zajęty",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Email jest już zajęty\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/account\"}"))),
        @ApiResponse(responseCode = "404", description = "Rodzic nie znaleziony lub nie ma konta głównego",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Rodzic nie ma konta głównego\",\"status\":404,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/account\"}")))
    })
    public ResponseEntity<?> createJuniorAccount(@RequestBody JuniorAccountRequest request, Authentication authentication) {
        accountService.createJuniorAccount(request, authentication);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending-logins")
    @Transactional(readOnly = true)
    @Operation(summary = "Oczekujące logowania Junior", description = "Zwraca listę prób logowania dziecka oczekujących na zgodę rodzica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista oczekujących logowań (pusta jeśli brak)",
            content = @Content(examples = @ExampleObject(value = "[{\"id\":\"770e8400-e29b-41d4-a716-446655440002\",\"status\":\"PENDING\",\"createdAt\":\"2026-05-12T14:30:00Z\"}]"))),
        @ApiResponse(responseCode = "401", description = "Brak autoryzacji",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Unauthorized\",\"status\":401,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/pending-logins\"}")))
    })
    public ResponseEntity<List<java.util.Map<String, Object>>> getPendingLogins(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        
        UUID parentId;
        if (authentication.getPrincipal() instanceof CustomerUserDetails) {
            parentId = ((CustomerUserDetails) authentication.getPrincipal()).getCustomerId();
        } else {
            String email = authentication.getName();
            Customer parent = accountService.getCustomerByEmail(email);
            parentId = parent.getId();
        }
        
        List<LoginAttempt> attempts = loginAttemptRepository.findByCustomerParentIdAndStatus(parentId, "PENDING");
        List<java.util.Map<String, Object>> response = attempts.stream().map(a -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", a.getId());
            map.put("status", a.getStatus());
            map.put("createdAt", a.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/approve-login/{attemptId}")
    @Transactional
    @Operation(summary = "Zatwierdź/odrzuć logowanie Juniora", description = "Rodzic zatwierdza lub odrzuca próbę logowania dziecka. Wymaga parametru `approved=true|false`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Decyzja zapisana (APPROVED lub REJECTED)"),
        @ApiResponse(responseCode = "404", description = "Nie znaleziono próby logowania",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Nie znaleziono logowania\",\"status\":404,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/approve-login/...\"}"))),
        @ApiResponse(responseCode = "403", description = "Brak dostępu – to nie jest twoje dziecko",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Brak dostępu\",\"status\":403,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/approve-login/...\"}")))
    })
    public ResponseEntity<?> approveLogin(@PathVariable UUID attemptId, @RequestParam boolean approved, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        LoginAttempt attempt = loginAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono logowania"));
        
        if (!attempt.getCustomer().getParent().getId().equals(userDetails.getCustomerId())) {
            throw new RuntimeException("Brak dostępu");
        }

        attempt.setStatus(approved ? "APPROVED" : "REJECTED");
        loginAttemptRepository.save(attempt);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/approve-transfer/{transactionId}")
    @Operation(summary = "Zatwierdź/odrzuć przelew Juniora", description = "Rodzic zatwierdza lub odrzuca przelew dziecka. Wymaga parametru `approved=true|false`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Decyzja zapisana (przelew zrealizowany lub odrzucony)"),
        @ApiResponse(responseCode = "404", description = "Nie znaleziono przelewu",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Nie znaleziono przelewu\",\"status\":404,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/approve-transfer/...\"}"))),
        @ApiResponse(responseCode = "400", description = "Niewystarczające saldo na koncie Junior",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Niewystarczające saldo na koncie Junior\",\"status\":400,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/approve-transfer/...\"}"))),
        @ApiResponse(responseCode = "403", description = "Brak dostępu do zatwierdzenia tego przelewu",
            content = @Content(examples = @ExampleObject(value = "{\"error\":\"Brak dostępu do zatwierdzenia tego przelewu\",\"status\":403,\"timestamp\":\"2026-05-12T14:30:00Z\",\"path\":\"/api/junior/approve-transfer/...\"}")))
    })
    public ResponseEntity<?> approveTransfer(@PathVariable UUID transactionId, @RequestParam boolean approved, Authentication authentication) {
        transferService.approveJuniorTransaction(transactionId, approved, authentication);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending-transfers")
    @Transactional(readOnly = true)
    @Operation(summary = "Oczekujące przelewy Juniora", description = "Zwraca listę przelewów dziecka oczekujących na zgodę rodzica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista oczekujących przelewów (pusta jeśli brak)",
            content = @Content(examples = @ExampleObject(value = "[{\"id\":\"550e8400-e29b-41d4-a716-446655440003\",\"referenceNumber\":\"BEB-20260512-000001\",\"transactionType\":\"INTERNAL\",\"status\":\"PENDING\",\"senderIban\":\"DE89370400440532013000\",\"senderName\":\"Kacper Kowalski\",\"receiverIban\":\"FR1420041010050500013M02606\",\"receiverName\":\"Marie Curie S.A.\",\"amount\":50.00,\"currency\":\"EUR\",\"title\":\"Kieszonkowe\",\"requestedAt\":\"2026-05-12T14:30:00Z\"}]")))
    })
    public ResponseEntity<List<TransactionDto>> getPendingTransfers(Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        // find all transactions where sender is JUNIOR and sender's parent is current user and status is PENDING
        List<Transaction> pending = transactionRepository.findAll().stream()
            .filter(t -> "PENDING".equals(t.getStatus()))
            .filter(t -> "JUNIOR".equals(t.getSenderAccount().getAccountType()))
            .filter(t -> t.getSenderAccount().getParentAccount() != null && t.getSenderAccount().getParentAccount().getCustomer().getId().equals(userDetails.getCustomerId()))
            .collect(Collectors.toList());

        return ResponseEntity.ok(pending.stream().map(this::toTransactionDto).collect(Collectors.toList()));
    }

    private TransactionDto toTransactionDto(Transaction tx) {
        return TransactionDto.builder()
                .id(tx.getId())
                .referenceNumber(tx.getReferenceNumber())
                .transactionType(tx.getTransactionType())
                .status(tx.getStatus())
                .senderIban(tx.getSenderIban())
                .senderName(tx.getSenderName())
                .receiverIban(tx.getReceiverIban())
                .receiverName(tx.getReceiverName())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .title(tx.getTitle())
                .requestedAt(tx.getRequestedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
