package com.vbank.transaction.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ExecutionRequest(@NotNull UUID transactionId) {
}