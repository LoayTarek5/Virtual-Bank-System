package com.vbank.account.repository;

import com.vbank.account.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserId(UUID userId);

    boolean existsByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockByAccountId(UUID accountId);

    @Modifying
    @Query("""
            update Account a
            set a.status = com.vbank.account.model.AccountStatus.INACTIVE
            where a.status = com.vbank.account.model.AccountStatus.ACTIVE
              and coalesce(a.lastTransactionAt, a.createdAt) < :cutoff
            """)
    int inactivateStaleAccounts(Instant cutoff);
}