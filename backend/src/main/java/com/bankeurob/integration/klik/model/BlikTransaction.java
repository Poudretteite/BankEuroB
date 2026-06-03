package com.bankeurob.integration.klik.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Transakcja BLIK otrzymana z webhooka autoryzacyjnego KLIK.
 * <p>
 * Przechowuje stan transakcji C2B po stronie banku:
 * - PENDING_AUTHORIZATION — oczekuje na autoryzację PIN-em klienta
 * - AUTHORIZED — klient zatwierdził PIN-em, wysłano confirm do KLIK
 * - COMPLETED — KLIK potwierdził zakończenie transakcji
 * - REJECTED — klient odrzucił lub KLIK odrzucił
 * - TIMEOUT — minął czas na autoryzację
 */
@Entity
@Table(name = "blik_transactions", indexes = {
    @Index(name = "idx_blik_tx_klik_id", columnList = "klik_transaction_id", unique = true),
    @Index(name = "idx_blik_tx_customer", columnList = "customer_id"),
    @Index(name = "idx_blik_tx_status", columnList = "status"),
})
@Getter
@Setter
public class BlikTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** ID transakcji nadane przez KLIK (uuid). */
    @Column(name = "klik_transaction_id", nullable = false, unique = true, length = 36)
    private String klikTransactionId;

    /** ID użytkownika w BankEuroB (przekazane do KLIK przy generowaniu kodu). */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** Klient BankEuroB, do którego należy transakcja. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private com.bankeurob.account.Customer customer;

    /** Konto, z którego zostaną pobrane środki. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private com.bankeurob.account.Account account;

    /** Kwota brutto transakcji. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Waluta (PLN, EUR, GBP, USD). */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Nazwa sklepu/merchanta. */
    @Column(name = "merchant_name", length = 200)
    private String merchantName;

    /** Czy nadawca i merchant są w tym samym banku. */
    @Column(name = "is_on_us")
    private boolean isOnUs;

    /** Strefa (PL, EU, UK, US). */
    @Column(length = 2)
    private String zone;

    /** Czas wygaśnięcia autoryzacji (z KLIK). */
    @Column(name = "expiry_time")
    private OffsetDateTime expiryTime;

    /** Status transakcji po stronie banku. */
    @Column(nullable = false, length = 30)
    private String status = "PENDING_AUTHORIZATION"; // PENDING_AUTHORIZATION, AUTHORIZED, COMPLETED, REJECTED, TIMEOUT

    /** Kwota netto dla merchanta (z odpowiedzi KLIK). */
    @Column(name = "merchant_net", precision = 19, scale = 4)
    private BigDecimal merchantNet;

    /** Prowizja KLIK (z odpowiedzi KLIK). */
    @Column(name = "klik_fee", precision = 19, scale = 4)
    private BigDecimal klikFee;

    /** Prowizja agenta (z odpowiedzi KLIK). */
    @Column(name = "agent_fee", precision = 19, scale = 4)
    private BigDecimal agentFee;

    /** Numer referencyjny transakcji w BankEuroB. */
    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    /** Czas otrzymania webhooka. */
    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    /** Czas autoryzacji PIN-em. */
    @Column(name = "authorized_at")
    private OffsetDateTime authorizedAt;

    /** Czas zakończenia. */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
