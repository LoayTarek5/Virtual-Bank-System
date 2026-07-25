package com.vbank.transaction.repository;

import com.vbank.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByFromAccountIdOrToAccountIdOrderByTimestampDesc(UUID fromAccountId, UUID toAccountId);
}