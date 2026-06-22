package com.bankeurob.integration.openbanking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LinkedBankRepository extends JpaRepository<LinkedBank, UUID> {
    List<LinkedBank> findByCustomerId(UUID customerId);
    boolean existsByCustomerIdAndBankUrl(UUID customerId, String bankUrl);
}
