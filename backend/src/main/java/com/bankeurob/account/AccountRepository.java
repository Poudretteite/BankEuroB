package com.bankeurob.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByIban(String iban);
    List<Account> findByCustomerId(UUID customerId);
    boolean existsByIban(String iban);

    /**
     * Znajduje konto po IBAN z blokadą pesymistyczną (PESSIMISTIC_WRITE),
     * aby zapobiec race condition przy równoczesnych przelewach.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.iban = :iban")
    Optional<Account> findByIbanWithLock(@Param("iban") String iban);
}
