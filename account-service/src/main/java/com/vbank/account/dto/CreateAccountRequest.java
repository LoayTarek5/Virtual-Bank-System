package com.vbank.account.dto;

import com.vbank.account.model.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(
        @NotNull UUID userId,
        @NotNull AccountType accountType,
        @NotNull @PositiveOrZero BigDecimal initialBalance) {
}