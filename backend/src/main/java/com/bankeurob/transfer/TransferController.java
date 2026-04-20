package com.bankeurob.transfer;

import com.bankeurob.transfer.dto.TransactionDto;
import com.bankeurob.transfer.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
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
    public ResponseEntity<TransactionDto> createTransfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.createTransfer(request, authentication));
    }

    @GetMapping
    @Operation(
            summary = "Historia transakcji",
            description = "Zwraca historię transakcji dla podanego IBAN (parametr ?iban=...)"
    )
    public ResponseEntity<List<TransactionDto>> getTransactions(
            @RequestParam String iban,
            Authentication authentication
    ) {
        return ResponseEntity.ok(transferService.getMyTransactions(iban, authentication));
    }
}
