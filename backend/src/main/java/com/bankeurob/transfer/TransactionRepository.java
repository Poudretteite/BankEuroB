package com.bankeurob.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReferenceNumber(String referenceNumber);
    List<Transaction> findBySenderAccountIdOrReceiverIban(UUID accountId, String recipientIban);
    List<Transaction> findBySenderIbanOrReceiverIbanOrderByRequestedAtDesc(String senderIban, String receiverIban);

    /**
     * Oblicza sumę kwot transakcji dla danego konta nadawcy z dzisiejszego dnia.
     * Używane do sprawdzania dziennego limitu (dailyLimit).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.senderAccount.id = :accountId " +
           "AND t.status NOT IN ('FAILED', 'REJECTED') " +
           "AND CAST(t.requestedAt AS date) = CURRENT_DATE")
    BigDecimal findTodayTotalBySenderAccountId(@Param("accountId") UUID accountId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.title = :cardTitle " +
           "AND t.status NOT IN ('FAILED', 'REJECTED') " +
           "AND CAST(t.completedAt AS date) = CURRENT_DATE")
    BigDecimal findTodayTotalByCardTitle(@Param("cardTitle") String cardTitle);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.title = :cardTitle " +
           "AND t.status NOT IN ('FAILED', 'REJECTED') " +
           "AND EXTRACT(MONTH FROM t.completedAt) = EXTRACT(MONTH FROM CURRENT_DATE) " +
           "AND EXTRACT(YEAR FROM t.completedAt) = EXTRACT(YEAR FROM CURRENT_DATE)")
    BigDecimal findMonthlyTotalByCardTitle(@Param("cardTitle") String cardTitle);

    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.title = :cardTitle " +
           "AND t.status NOT IN ('FAILED', 'REJECTED') " +
           "AND CAST(t.completedAt AS date) = CURRENT_DATE")
    Integer countTodayTransactionsByCardTitle(@Param("cardTitle") String cardTitle);

    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.title = :cardTitle " +
           "AND t.status NOT IN ('FAILED', 'REJECTED') " +
           "AND EXTRACT(MONTH FROM t.completedAt) = EXTRACT(MONTH FROM CURRENT_DATE) " +
           "AND EXTRACT(YEAR FROM t.completedAt) = EXTRACT(YEAR FROM CURRENT_DATE)")
    Integer countMonthlyTransactionsByCardTitle(@Param("cardTitle") String cardTitle);
}
