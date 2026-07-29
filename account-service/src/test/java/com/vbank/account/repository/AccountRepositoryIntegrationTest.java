package com.vbank.account.repository;

import com.vbank.account.model.Account;
import com.vbank.account.model.AccountStatus;
import com.vbank.account.model.AccountType;
import com.vbank.account.repository.AccountRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountRepositoryIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void saveAndFindByUserId_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Account account = new Account(userId, "1000000001", AccountType.SAVINGS, new BigDecimal("100.00"));

        // Act — save into and read back from the real H2 database
        accountRepository.save(account);
        List<Account> found = accountRepository.findByUserId(userId);

        // Assert — the DB assigned an ID and the derived query returns our row
        Assertions.assertEquals(1, found.size());
        Assertions.assertNotNull(found.get(0).getAccountId());
        Assertions.assertEquals("1000000001", found.get(0).getAccountNumber());
    }

    @Test
    void inactivateStaleAccounts_FlipsOnlyStaleActive() {
        // Arrange
        Instant now = Instant.now();

        Account staleActive = new Account(UUID.randomUUID(), "2000000001", AccountType.SAVINGS, new BigDecimal("100.00"));
        staleActive.setLastTransactionAt(now.minus(2, ChronoUnit.DAYS));

        Account freshActive = new Account(UUID.randomUUID(), "2000000002", AccountType.SAVINGS, new BigDecimal("100.00"));
        freshActive.setLastTransactionAt(now);

        Account alreadyInactive = new Account(UUID.randomUUID(), "2000000003", AccountType.CHECKING, new BigDecimal("100.00"));
        alreadyInactive.setLastTransactionAt(now.minus(2, ChronoUnit.DAYS));
        alreadyInactive.setStatus(AccountStatus.INACTIVE);

        accountRepository.saveAll(List.of(staleActive, freshActive, alreadyInactive));

        // Act — the bulk @Modifying update needs an active transaction, so run
        // just this call in its own committed transaction (as the real job does).
        int updated = transactionTemplate.execute(status ->
                accountRepository.inactivateStaleAccounts(now.minus(1, ChronoUnit.DAYS)));

        // Assert — re-read each account by ID from the committed state
        Assertions.assertTrue(updated >= 1);
        Assertions.assertEquals(AccountStatus.INACTIVE,
                accountRepository.findById(staleActive.getAccountId()).orElseThrow().getStatus());
        Assertions.assertEquals(AccountStatus.ACTIVE,
                accountRepository.findById(freshActive.getAccountId()).orElseThrow().getStatus());
        Assertions.assertEquals(AccountStatus.INACTIVE,
                accountRepository.findById(alreadyInactive.getAccountId()).orElseThrow().getStatus());
    }
}