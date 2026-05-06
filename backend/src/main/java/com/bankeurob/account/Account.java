package com.bankeurob.account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "accounts",
    indexes = {
        // Pobieranie wszystkich kont danego klienta (dashboard)
        @Index(name = "idx_accounts_customer_id",
               columnList = "customer_id"),
        // Filtrowanie po typie konta (STANDARD, JUNIOR, itp.)
        @Index(name = "idx_accounts_type",
               columnList = "account_type"),
        // Wyszukiwanie aktywnych kont klienta (częste zapytanie)
        @Index(name = "idx_accounts_customer_active",
               columnList = "customer_id, is_active"),
        // Relacja konto junior -> konto rodzica
        @Index(name = "idx_accounts_parent_account",
               columnList = "parent_account_id")
    }
)
@Getter
@Setter
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, unique = true, length = 34)
    private String iban;

    @Column(nullable = false, length = 11)
    private String bic = "BKEUDEBBXXX";

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType = "STANDARD"; // STANDARD, SAVINGS, JUNIOR

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "overdraft_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal overdraftLimit = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private Account parentAccount;

    @Column(name = "daily_limit", precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
