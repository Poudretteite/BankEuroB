package com.bankeurob.integration.cards;

import com.bankeurob.account.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "card_token", nullable = false, unique = true)
    private String cardToken;

    @Column(name = "card_type", nullable = false)
    private String cardType;

    @Column(name = "status", nullable = false)
    private String status;

    @Builder.Default
    @Column(name = "daily_limit", nullable = false)
    private java.math.BigDecimal dailyLimit = java.math.BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "monthly_limit", nullable = false)
    private java.math.BigDecimal monthlyLimit = java.math.BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "daily_txn_limit", nullable = false)
    private int dailyTxnLimit = 0;

    @Builder.Default
    @Column(name = "monthly_txn_limit", nullable = false)
    private int monthlyTxnLimit = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "REQUESTED";
        }
    }
}
