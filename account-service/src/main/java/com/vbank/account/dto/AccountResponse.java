package com.vbank.account.dto;

import com.vbank.account.model.Account;
import com.vbank.account.model.AccountStatus;
import com.vbank.account.model.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID accountId, String accountNumber, AccountType accountType,
        BigDecimal balance, AccountStatus status) {

    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getAccountId(), a.getAccountNumber(),
                a.getAccountType(), a.getBalance(), a.getStatus());
    }
}