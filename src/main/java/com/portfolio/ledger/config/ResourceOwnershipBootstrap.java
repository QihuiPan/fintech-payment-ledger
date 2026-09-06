package com.portfolio.ledger.config;

import com.portfolio.ledger.repository.LedgerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ResourceOwnershipBootstrap implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceOwnershipBootstrap.class);

    private final LedgerRepository repository;
    private final LedgerProperties properties;

    public ResourceOwnershipBootstrap(
            LedgerRepository repository,
            LedgerProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        int assigned = repository.assignLegacyOwnership(properties.security().userName());
        if (assigned > 0) {
            LOGGER.info("Assigned {} legacy resources to the configured wallet API subject", assigned);
        }
    }
}
