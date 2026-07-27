package com.vbank.account.job;

import com.vbank.account.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class StaleAccountJob {

    private static final Logger log = LoggerFactory.getLogger(StaleAccountJob.class);
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    private final AccountRepository repository;

    public StaleAccountJob(AccountRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void inactivateStaleAccounts() {
        int count = repository.inactivateStaleAccounts(Instant.now().minus(STALE_AFTER));
        if (count > 0) {
            log.info("Marked {} stale account(s) inactive", count);
        }
    }
}