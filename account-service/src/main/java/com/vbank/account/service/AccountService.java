package com.vbank.account.service;

import com.vbank.account.dto.*;
import com.vbank.account.exception.ApiException;
import com.vbank.account.model.Account;
import com.vbank.account.model.AccountStatus;
import com.vbank.account.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
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

    @Transactional
    public MessageResponse transfer(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot transfer to the same account.");
        }

        // Lock both rows in a consistent order so two opposite transfers can't deadlock.
        Account from;
        Account to;
        if (request.fromAccountId().compareTo(request.toAccountId()) < 0) {
            from = findLockedOrThrow(request.fromAccountId());
            to = findLockedOrThrow(request.toAccountId());
        } else {
            to = findLockedOrThrow(request.toAccountId());
            from = findLockedOrThrow(request.fromAccountId());
        }

        requireActive(from);
        requireActive(to);

        if (from.getBalance().compareTo(request.amount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient funds.");
        }

        Instant now = Instant.now();
        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));
        from.setLastTransactionAt(now);
        to.setLastTransactionAt(now);

        return new MessageResponse("Account updated successfully.");
    }

    private Account findLockedOrThrow(UUID accountId) {
        return repository.findWithLockByAccountId(accountId).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "Invalid 'from' or 'to' account ID."));
    }

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Account " + account.getAccountId() + " is not active.");
        }
    }
}