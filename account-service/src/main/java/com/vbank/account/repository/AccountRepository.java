package com.vbank.account.repository;

import com.vbank.account.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserId(UUID userId);
    boolean existsByAccountNumber(String accountNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockByAccountId(UUID accountId);
}