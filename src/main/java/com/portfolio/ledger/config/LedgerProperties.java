package com.portfolio.ledger.config;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(
        Security security,
        Provider provider,
        Fx fx) {

    public record Security(
            String userName,
            String userPassword,
            String adminName,
            String adminPassword) {
    }

    public record Provider(
            String webhookSecret,
            long timestampToleranceSeconds) {
    }

    public record Fx(
            long quoteTtlSeconds,
            int feeBasisPoints,
            Map<String, BigDecimal> rates) {
    }
}
