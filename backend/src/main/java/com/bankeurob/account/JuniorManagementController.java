package com.bankeurob.account;

import com.bankeurob.account.dto.JuniorAccountRequest;
import com.bankeurob.auth.LoginAttempt;
import com.bankeurob.auth.LoginAttemptRepository;
import com.bankeurob.security.CustomerUserDetails;
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
public class JuniorManagementController {

    private final AccountService accountService;
    private final LoginAttemptRepository loginAttemptRepository;
    private final com.bankeurob.transfer.TransferService transferService;
    private final TransactionRepository transactionRepository;

    @PostMapping("/account")
    public ResponseEntity<?> createJuniorAccount(@RequestBody JuniorAccountRequest request, Authentication authentication) {
        accountService.createJuniorAccount(request, authentication);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending-logins")
    @Transactional(readOnly = true)
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
    public ResponseEntity<?> approveTransfer(@PathVariable UUID transactionId, @RequestParam boolean approved, Authentication authentication) {
        transferService.approveJuniorTransaction(transactionId, approved, authentication);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending-transfers")
    @Transactional(readOnly = true)
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
