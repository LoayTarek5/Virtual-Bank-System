package com.vbank.transaction.repository;

import com.vbank.transaction.model.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TransactionRepositoryIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void findByAccount_ReturnsBothDirections_NewestFirst() {
        // 'account' appears as sender in one tx and receiver in another;
        // a third, unrelated tx must not come back.
        UUID account = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID unrelatedA = UUID.randomUUID();
        UUID unrelatedB = UUID.randomUUID();

        Transaction outgoing = new Transaction(account, other, new BigDecimal("30.00"), "outgoing");
        Transaction incoming = new Transaction(other, account, new BigDecimal("50.00"), "incoming");
        Transaction unrelated = new Transaction(unrelatedA, unrelatedB, new BigDecimal("10.00"), "unrelated");

        // save incoming last so it has the latest timestamp -> should sort first
        transactionRepository.save(outgoing);
        transactionRepository.save(unrelated);
        transactionRepository.save(incoming);

        // Act
        List<Transaction> history =
                transactionRepository.findByFromAccountIdOrToAccountIdOrderByTimestampDesc(account, account);

        // exactly the two rows touching 'account', unrelated excluded
        assertEquals(2, history.size());
        assertTrue(history.stream().noneMatch(t -> t.getDescription().equals("unrelated")));
        // newest-first: incoming was saved last
        assertEquals("incoming", history.get(0).getDescription());
        assertEquals("outgoing", history.get(1).getDescription());
    }

    @Test
    void findByAccount_ReturnsEmpty_WhenNoTransactions() {
        // an account with no transactions
        List<Transaction> history = transactionRepository
                .findByFromAccountIdOrToAccountIdOrderByTimestampDesc(UUID.randomUUID(), UUID.randomUUID());

        // the repository returns an empty list (the service turns this into a 404)
        assertTrue(history.isEmpty());
    }
}