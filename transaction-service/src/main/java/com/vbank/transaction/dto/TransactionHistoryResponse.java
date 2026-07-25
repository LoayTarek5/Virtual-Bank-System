package com.vbank.transaction.dto;

import com.vbank.transaction.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryResponse(
        UUID transactionId, UUID fromAccountId, UUID toAccountId,
        BigDecimal amount, String description, Instant timestamp, String deliveryStatus) {

    public static TransactionHistoryResponse from(Transaction t) {
        return new TransactionHistoryResponse(t.getTransactionId(), t.getFromAccountId(), t.getToAccountId(),
                t.getAmount(), t.getDescription(), t.getTimestamp(), "SENT");
    }
}