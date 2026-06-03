package com.bankeurob.integration.klik.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlikTransactionRepository extends JpaRepository<BlikTransaction, UUID> {
    Optional<BlikTransaction> findByKlikTransactionId(String klikTransactionId);
    List<BlikTransaction> findByCustomerIdAndStatusOrderByReceivedAtDesc(UUID customerId, String status);
    List<BlikTransaction> findByCustomerIdOrderByReceivedAtDesc(UUID customerId);
    List<BlikTransaction> findByStatusAndExpiryTimeBefore(String status, java.time.OffsetDateTime now);
}
