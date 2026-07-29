package com.vbank.transaction.service;

import com.vbank.transaction.client.AccountClient;
import com.vbank.transaction.client.AccountResponse;
import com.vbank.transaction.dto.ExecutionRequest;
import com.vbank.transaction.dto.InitiationRequest;
import com.vbank.transaction.dto.TransactionResponse;
import com.vbank.transaction.exception.ApiException;
import com.vbank.transaction.model.Transaction;
import com.vbank.transaction.model.TransactionStatus;
import com.vbank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final UUID FROM = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TO = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private TransactionRepository repository;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private TransactionService transactionService;

    private AccountResponse account(BigDecimal balance) {
        return new AccountResponse(UUID.randomUUID(), balance, "ACTIVE");
    }

    @Test
    void initiate_Success_SavesInitiatedTransaction() {
        // Arrange
        InitiationRequest request = new InitiationRequest(FROM, TO, new BigDecimal("30.00"), "rent");
        when(accountClient.findById(FROM)).thenReturn(Optional.of(account(new BigDecimal("100.00"))));
        when(accountClient.findById(TO)).thenReturn(Optional.of(account(new BigDecimal("50.00"))));
        // return the same entity that was passed to save(), so we can read its fields back
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        TransactionResponse response = transactionService.initiate(request);

        // a brand-new transaction is persisted in the INITIATED state
        assertEquals("Initiated", response.status()); // label() title-cases the enum
        verify(repository, times(1)).save(any(Transaction.class));
    }

    @Test
    void initiate_ThrowsBadRequest_WhenInsufficientFunds() {
        // Arrange
        InitiationRequest request = new InitiationRequest(FROM, TO, new BigDecimal("100.00"), "rent");
        when(accountClient.findById(FROM)).thenReturn(Optional.of(account(new BigDecimal("10.00"))));
        when(accountClient.findById(TO)).thenReturn(Optional.of(account(new BigDecimal("50.00"))));

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> transactionService.initiate(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(repository, never()).save(any(Transaction.class)); // nothing persisted on rejection
    }

    @Test
    void execute_Success_FlipsToSuccess() {
        // an INITIATED transaction already in the DB
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction(FROM, TO, new BigDecimal("30.00"), "rent");
        when(repository.findById(txId)).thenReturn(Optional.of(tx));
        // empty Optional == account service accepted the transfer
        when(accountClient.transfer(FROM, TO, new BigDecimal("30.00"))).thenReturn(Optional.empty());

        // Act
        TransactionResponse response = transactionService.execute(new ExecutionRequest(txId));

        // Assert
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
        assertEquals("Success", response.status());
        verify(repository).save(tx);
    }

    @Test
    void execute_Rejected_FlipsToFailedAndThrows() {
        // Arrange
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction(FROM, TO, new BigDecimal("30.00"), "rent");
        when(repository.findById(txId)).thenReturn(Optional.of(tx));
        // present Optional == account service rejected; the string is the reason
        when(accountClient.transfer(FROM, TO, new BigDecimal("30.00")))
                .thenReturn(Optional.of("Insufficient funds."));

        // the transaction is still marked FAILED before the exception propagates
        ApiException exception = assertThrows(ApiException.class,
                () -> transactionService.execute(new ExecutionRequest(txId)));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Insufficient funds.", exception.getMessage());
        assertEquals(TransactionStatus.FAILED, tx.getStatus());
        verify(repository).save(tx); // the FAILED state is persisted, not lost
    }
}