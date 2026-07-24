package com.vbank.transaction.service;

import com.vbank.transaction.client.AccountClient;
import com.vbank.transaction.client.AccountResponse;
import com.vbank.transaction.dto.ExecutionRequest;
import com.vbank.transaction.dto.InitiationRequest;
import com.vbank.transaction.dto.TransactionHistoryResponse;
import com.vbank.transaction.dto.TransactionResponse;
import com.vbank.transaction.exception.ApiException;
import com.vbank.transaction.model.Transaction;
import com.vbank.transaction.model.TransactionStatus;
import com.vbank.transaction.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final AccountClient accountClient;

    public TransactionService(TransactionRepository repository, AccountClient accountClient) {
        this.repository = repository;
        this.accountClient = accountClient;
    }

    public TransactionResponse initiate(InitiationRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot transfer to the same account.");
        }

        AccountResponse from = requireAccount(request.fromAccountId());
        requireAccount(request.toAccountId());

        if (from.balance().compareTo(request.amount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient funds.");
        }

        Transaction transaction = repository.save(new Transaction(
                request.fromAccountId(), request.toAccountId(), request.amount(), request.description()));
        return TransactionResponse.from(transaction);
    }

    public TransactionResponse execute(ExecutionRequest request) {
        Transaction transaction = repository.findById(request.transactionId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Transaction with ID " + request.transactionId() + " not found."));

        if (transaction.getStatus() != TransactionStatus.INITIATED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, transaction.getStatus() == TransactionStatus.SUCCESS
                    ? "Transaction has already been executed."
                    : "Transaction has already failed.");
        }

        Optional<String> rejection = accountClient.transfer(
                transaction.getFromAccountId(), transaction.getToAccountId(), transaction.getAmount());

        transaction.setStatus(rejection.isPresent() ? TransactionStatus.FAILED : TransactionStatus.SUCCESS);
        repository.save(transaction);

        if (rejection.isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, rejection.get());
        }
        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getHistory(UUID accountId) {
        List<Transaction> transactions =
                repository.findByFromAccountIdOrToAccountIdOrderByTimestampDesc(accountId, accountId);
        if (transactions.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No transactions found for account ID " + accountId + ".");
        }
        return transactions.stream().map(TransactionHistoryResponse::from).toList();
    }

    private AccountResponse requireAccount(UUID accountId) {
        return accountClient.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid 'from' or 'to' account ID."));
    }
}