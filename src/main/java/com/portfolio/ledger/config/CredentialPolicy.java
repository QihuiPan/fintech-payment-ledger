package com.portfolio.ledger.config;

import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CredentialPolicy implements ApplicationRunner {
    private static final Set<String> DEMO_VALUES = Set.of(
            "wallet-demo",
            "admin-demo",
            "local-demo-secret",
            "ledger",
            "ledger-local-password",
            "replace-with-a-long-random-password",
            "replace-with-a-long-random-secret");

    private final LedgerProperties properties;
    private final Environment environment;

    public CredentialPolicy(LedgerProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.security().requireStrongCredentials()) {
            return;
        }
        if (properties.security().userName().equals(properties.security().adminName())) {
            throw new IllegalStateException("LEDGER_USER_NAME and LEDGER_ADMIN_NAME must be different");
        }
        requireSecret("DATABASE_PASSWORD", environment.getProperty("spring.datasource.password"), 12);
        requireSecret("LEDGER_USER_PASSWORD", properties.security().userPassword(), 12);
        requireSecret("LEDGER_ADMIN_PASSWORD", properties.security().adminPassword(), 12);
        requireSecret("PROVIDER_WEBHOOK_SECRET", properties.provider().webhookSecret(), 24);
    }

    private static void requireSecret(String name, String value, int minimumLength) {
        if (value == null || value.length() < minimumLength || DEMO_VALUES.contains(value)) {
            throw new IllegalStateException(
                    name + " must be replaced with a non-demo value of at least "
                            + minimumLength + " characters");
        }
    }
}
