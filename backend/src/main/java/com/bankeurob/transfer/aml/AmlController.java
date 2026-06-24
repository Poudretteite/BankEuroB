package com.bankeurob.transfer.aml;

import com.bankeurob.transfer.Transaction;
import com.bankeurob.transfer.TransactionRepository;
import com.bankeurob.transfer.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/aml")
@RequiredArgsConstructor
@Slf4j
public class AmlController {

    private final TransactionRepository transactionRepository;
    private final TransferService transferService;

    @GetMapping("/blocked")
    public ResponseEntity<List<Transaction>> getBlockedTransactions() {
        // Mock: Zwraca wszystkie transakcje o statusie AML_BLOCKED
        List<Transaction> blocked = transactionRepository.findByStatus("AML_BLOCKED");
        return ResponseEntity.ok(blocked);
    }

    @PostMapping("/explain/{transactionId}")
    public ResponseEntity<?> submitExplanation(@PathVariable UUID transactionId, @RequestBody Map<String, String> request) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transakcja nie istnieje"));

        if (!"AML_BLOCKED".equals(transaction.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Transakcja nie jest zablokowana przez AML"));
        }

        String explanation = request.get("explanation");
        transaction.setAmlExplanation(explanation);
        transaction.setAmlStatus("EXPLANATION_SUBMITTED");
        transactionRepository.save(transaction);

        return ResponseEntity.ok(Map.of("message", "Wyjaśnienie zostało przesłane"));
    }

    @PostMapping("/admin/resolve/{transactionId}")
    public ResponseEntity<?> resolveAml(@PathVariable UUID transactionId, @RequestBody Map<String, String> request) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transakcja nie istnieje"));

        if (!"AML_BLOCKED".equals(transaction.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Transakcja nie jest zablokowana przez AML"));
        }

        String decision = request.getOrDefault("decision", "APPROVE"); // APPROVE lub REJECT

        if ("APPROVE".equalsIgnoreCase(decision)) {
            transaction.setStatus("PENDING"); // Wraca do standardowego przetwarzania
            transaction.setAmlStatus("RESOLVED");
            transactionRepository.save(transaction);
            
            // Wypuszczamy w świat (Target, SWIFT, SEPA itp.)
            transferService.processExternalRouting(transaction);
            
            return ResponseEntity.ok(Map.of("message", "Transakcja odblokowana i wysłana dalej."));
        } else {
            transaction.setStatus("REJECTED");
            transaction.setAmlStatus("REJECTED");
            
            // Zwrot środków
            transferService.refundTransaction(transaction);
            transactionRepository.save(transaction);
            
            return ResponseEntity.ok(Map.of("message", "Transakcja odrzucona przez AML, środki zwrócone."));
        }
    }
}
