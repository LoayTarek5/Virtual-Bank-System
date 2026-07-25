package com.vbank.transaction.client;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
}