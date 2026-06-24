package com.bankeurob.integration.openbanking;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/open-banking")
@SecurityRequirement(name = "bearerAuth")
public class OpenBankingController {

    private final OpenBankingService openBankingService;

    public OpenBankingController(OpenBankingService openBankingService) {
        this.openBankingService = openBankingService;
    }

    @PostMapping("/link")
    public ResponseEntity<Void> linkBank(@RequestBody LinkBankRequest request, Authentication authentication) {
        openBankingService.linkBank(authentication.getName(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getExternalAccounts(Authentication authentication) {
        return ResponseEntity.ok(openBankingService.getExternalAccounts(authentication.getName()));
    }

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> executeTransfer(@RequestBody ExternalTransferRequest request, Authentication authentication) {
        return ResponseEntity.ok(openBankingService.executeExternalTransfer(authentication.getName(), request));
    }

    @DeleteMapping("/link/{linkedBankId}")
    public ResponseEntity<Void> unlinkBank(@PathVariable java.util.UUID linkedBankId, Authentication authentication) {
        openBankingService.unlinkBank(authentication.getName(), linkedBankId);
        return ResponseEntity.ok().build();
    }
}
