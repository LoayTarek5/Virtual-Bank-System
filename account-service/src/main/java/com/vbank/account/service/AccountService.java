package com.vbank.account.service;

import com.vbank.account.dto.*;
import com.vbank.account.exception.ApiException;
import com.vbank.account.model.Account;
import com.vbank.account.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreateAccountResponse create(CreateAccountRequest request) {
        Account account = repository.save(new Account(
                request.userId(), generateAccountNumber(),
                request.accountType(), request.initialBalance()));
        return new CreateAccountResponse(account.getAccountId(), account.getAccountNumber(),
                "Account created successfully.");
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(UUID accountId) {
        return AccountResponse.from(findOrThrow(accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getByUserId(UUID userId) {
        List<Account> accounts = repository.findByUserId(userId);
        if (accounts.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No accounts found for user ID " + userId + ".");
        }
        return accounts.stream().map(AccountResponse::from).toList();
    }

    Account findOrThrow(UUID accountId) {
        return repository.findById(accountId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Account with ID " + accountId + " not found."));
    }

    private String generateAccountNumber() {
        String number;
        do {
            number = String.valueOf(RANDOM.nextLong(1_000_000_000L, 10_000_000_000L));
        } while (repository.existsByAccountNumber(number));
        return number;
    }
}