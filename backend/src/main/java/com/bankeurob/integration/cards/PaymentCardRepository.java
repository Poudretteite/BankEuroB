package com.bankeurob.integration.cards;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID> {
    Optional<PaymentCard> findByCardToken(String cardToken);
    List<PaymentCard> findByAccountId(UUID accountId);
}
