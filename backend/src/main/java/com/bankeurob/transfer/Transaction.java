package com.bankeurob.transfer;

import com.bankeurob.account.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "transactions",
    indexes = {
        // Historia transakcji dla danego konta nadawcy (najczęstsze zapytanie dashboardu)
        @Index(name = "idx_transactions_sender_account_date",
               columnList = "sender_account_id, requested_at DESC"),
        // Wyszukiwanie po IBAN nadawcy
        @Index(name = "idx_transactions_sender_iban",
               columnList = "sender_iban"),
        // Wyszukiwanie po IBAN odbiorcy
        @Index(name = "idx_transactions_receiver_iban",
               columnList = "receiver_iban"),
        // Filtrowanie po statusie (np. PENDING, COMPLETED)
        @Index(name = "idx_transactions_status",
               columnList = "status"),
        // Filtrowanie po typie transakcji (INTERNAL, SEPA, itp.)
        @Index(name = "idx_transactions_type",
               columnList = "transaction_type"),
        // Sortowanie / filtrowanie po dacie żądania
        @Index(name = "idx_transactions_requested_at",
               columnList = "requested_at DESC")
    }
)
@Getter
@Setter
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber; // BankEuroB unikalny numer transakcji

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType; // INTERNAL, SEPA_SCT, SEPA_INSTANT, RTGS_TARGET2, SWIFT, CARD_PAYMENT

    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, COMPLETED, REJECTED, FAILED, PROCESSING

    // --- SENDER ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id")
    private Account senderAccount;

    @Column(name = "sender_iban", nullable = false, length = 34)
    private String senderIban;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_bic", length = 11)
    private String senderBic;

    // --- RECEIVER ---
    @Column(name = "receiver_iban", nullable = false, length = 34)
    private String receiverIban;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_bic", length = 11)
    private String receiverBic;

    @Column(name = "receiver_bank_name")
    private String receiverBankName;

    // --- AMOUNTS & DATES ---
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(length = 140)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "external_message_id", length = 100)
    private String externalMessageId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
