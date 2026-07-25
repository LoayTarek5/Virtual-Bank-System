package com.vbank.transaction.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_from", columnList = "from_account_id"),
        @Index(name = "idx_tx_to", columnList = "to_account_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {
    @Id
    @GeneratedValue
    private UUID transactionId;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status = TransactionStatus.INITIATED;

    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    public Transaction(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String description) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.description = description;
    }
}