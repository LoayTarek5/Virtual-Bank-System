package com.vbank.account.dto;

import java.util.UUID;

public record CreateAccountResponse(UUID accountId, String accountNumber, String message) {
}