package com.vbank.account.service;

import com.vbank.account.dto.MessageResponse;
import com.vbank.account.dto.TransferRequest;
import com.vbank.account.exception.ApiException;
import com.vbank.account.model.Account;
import com.vbank.account.model.AccountType;
import com.vbank.account.repository.AccountRepository;
import com.vbank.account.service.AccountService;
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
class AccountServiceTest {

    // Fixed, ordered IDs: ACC_A < ACC_B, so transfer() always locks A first.
    // That makes the "not found" test deterministic instead of depending on
    // the ordering of two random UUIDs.
    private static final UUID ACC_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ACC_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    private AccountRepository repository;

    @InjectMocks
    private AccountService accountService;

    private Account activeAccount(BigDecimal balance) {
        // status defaults to ACTIVE via the entity's field initializer
        return new Account(UUID.randomUUID(), "1234567890", AccountType.SAVINGS, balance);
    }

    @Test
    void transfer_Success() {
        // Arrange
        TransferRequest request = new TransferRequest(ACC_A, ACC_B, new BigDecimal("30.00"));
        Account from = activeAccount(new BigDecimal("100.00"));
        Account to = activeAccount(new BigDecimal("50.00"));
        when(repository.findWithLockByAccountId(ACC_A)).thenReturn(Optional.of(from));
        when(repository.findWithLockByAccountId(ACC_B)).thenReturn(Optional.of(to));

        // Act
        MessageResponse response = accountService.transfer(request);

        // Assert
        assertEquals("Account updated successfully.", response.message());
        // compareTo, not equals: BigDecimal equals is scale-sensitive (70.00 != 70.0)
        assertEquals(0, new BigDecimal("70.00").compareTo(from.getBalance()));
        assertEquals(0, new BigDecimal("80.00").compareTo(to.getBalance()));
        assertNotNull(from.getLastTransactionAt());
        assertNotNull(to.getLastTransactionAt());
    }

    @Test
    void transfer_ThrowsBadRequest_WhenInsufficientFunds() {
        // Arrange
        TransferRequest request = new TransferRequest(ACC_A, ACC_B, new BigDecimal("100.00"));
        Account from = activeAccount(new BigDecimal("10.00"));
        Account to = activeAccount(new BigDecimal("50.00"));
        when(repository.findWithLockByAccountId(ACC_A)).thenReturn(Optional.of(from));
        when(repository.findWithLockByAccountId(ACC_B)).thenReturn(Optional.of(to));

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> accountService.transfer(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(0, new BigDecimal("10.00").compareTo(from.getBalance())); // unchanged
    }

    @Test
    void transfer_ThrowsBadRequest_WhenSameAccount() {
        // Arrange
        TransferRequest request = new TransferRequest(ACC_A, ACC_A, new BigDecimal("30.00"));

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> accountService.transfer(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(repository, never()).findWithLockByAccountId(any()); // rejected before any lookup
    }

    @Test
    void transfer_ThrowsBadRequest_WhenAccountNotFound() {
        // Arrange — ACC_A is locked first (A < B), so an empty A short-circuits before B is touched
        TransferRequest request = new TransferRequest(ACC_A, ACC_B, new BigDecimal("30.00"));
        when(repository.findWithLockByAccountId(ACC_A)).thenReturn(Optional.empty());

        // Act & Assert
        ApiException exception = assertThrows(ApiException.class, () -> accountService.transfer(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }
}