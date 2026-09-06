package com.portfolio.ledger.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CredentialPolicyTest {
    @Test
    void acceptsIndependentStrongCredentials() {
        CredentialPolicy policy = policy(
                "database-secret-4827",
                "wallet-secret-5938",
                "admin-secret-6049",
                "provider-webhook-secret-7150");

        assertDoesNotThrow(() -> policy.run(null));
    }

    @Test
    void rejectsDocumentedPlaceholderCredentials() {
        CredentialPolicy policy = policy(
                "replace-with-a-long-random-password",
                "wallet-secret-5938",
                "admin-secret-6049",
                "replace-with-a-long-random-secret");

        assertThrows(IllegalStateException.class, () -> policy.run(null));
    }

    private static CredentialPolicy policy(
            String databasePassword,
            String userPassword,
            String adminPassword,
            String webhookSecret) {
        LedgerProperties properties = new LedgerProperties(
                new LedgerProperties.Security(
                        "wallet-user", userPassword, "ledger-admin", adminPassword, true),
                new LedgerProperties.Provider(webhookSecret, 300),
                new LedgerProperties.Fx(60, 20, Map.of("GBP-EUR", BigDecimal.ONE)));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", databasePassword);
        return new CredentialPolicy(properties, environment);
    }
}
