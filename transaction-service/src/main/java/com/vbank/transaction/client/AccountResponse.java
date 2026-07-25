package com.vbank.transaction.client;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(UUID accountId, BigDecimal balance, String status) {
}