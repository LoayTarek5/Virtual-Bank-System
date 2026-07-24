package com.vbank.transaction.dto;

import com.vbank.transaction.model.Transaction;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID transactionId, String status, Instant timestamp) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getTransactionId(), t.getStatus().label(), t.getTimestamp());
    }
}