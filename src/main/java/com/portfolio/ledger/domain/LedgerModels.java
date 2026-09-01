package com.portfolio.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LedgerModels {
    private LedgerModels() {
    }

    public enum AccountType {
        USER,
        PLATFORM_CASH,
        FEE_REVENUE,
        FX_CLEARING,
        ROUNDING,
        SUSPENSE
    }

    public enum TransactionType {
        DEPOSIT,
        TRANSFER,
        FX_CONVERSION,
        REVERSAL
    }

    public record BalanceView(
            UUID accountId,
            String currency,
            long postedBalanceMinor,
            long availableBalanceMinor,
            long version) {
    }

    public record WalletView(
            UUID walletId,
            UUID userId,
            String email,
            String status,
            List<BalanceView> balances) {
    }

    public record EntryView(
            UUID entryId,
            UUID accountId,
            String accountCode,
            String currency,
            long amountMinor) {
    }

    public record TransactionView(
            UUID transactionId,
            TransactionType type,
            String status,
            String reference,
            UUID reversesTransactionId,
            Instant createdAt,
            List<EntryView> entries) {
    }

    public record StatementItem(
            UUID transactionId,
            TransactionType type,
            String reference,
            String currency,
            long amountMinor,
            long runningBalanceMinor,
            Instant createdAt) {
    }

    public record StatementPage(
            UUID walletId,
            String currency,
            List<StatementItem> items,
            String nextCursor) {
    }

    public record FxQuoteView(
            UUID quoteId,
            UUID walletId,
            String baseCurrency,
            String quoteCurrency,
            long baseAmountMinor,
            long quoteAmountMinor,
            long feeMinor,
            BigDecimal rate,
            Instant expiresAt,
            Instant consumedAt) {
    }

    public record SettlementRow(
            String providerReference,
            String currency,
            long amountMinor,
            String status,
            Instant settledAt) {
    }

    public record ReconciliationResult(
            UUID jobId,
            LocalDate businessDate,
            int matched,
            int amountMismatch,
            int missingLocally,
            int missingAtProvider,
            List<ReconciliationIssue> issues) {
    }

    public record ReconciliationIssue(
            String category,
            String providerReference,
            String currency,
            Long providerAmountMinor,
            Long localAmountMinor) {
    }

    public record HealthInvariantView(
            long unbalancedTransactionCount,
            long negativeUserBalanceCount,
            Map<String, Long> unpublishedOutboxByType) {
    }
}
