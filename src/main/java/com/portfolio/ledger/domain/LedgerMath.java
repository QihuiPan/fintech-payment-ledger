package com.portfolio.ledger.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LedgerMath {
    private LedgerMath() {
    }

    public static void requireBalanced(List<Posting> postings) {
        if (postings.size() < 2) {
            throw DomainException.badRequest(
                    "INSUFFICIENT_ENTRIES",
                    "A ledger transaction requires at least two entries");
        }
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Posting posting : postings) {
            if (posting.amountMinor() == 0) {
                throw DomainException.badRequest("ZERO_ENTRY", "Ledger entries cannot be zero");
            }
            String currency = normalizeCurrency(posting.currency());
            try {
                totals.merge(currency, posting.amountMinor(), Math::addExact);
            } catch (ArithmeticException overflow) {
                throw DomainException.badRequest("AMOUNT_OVERFLOW", "Entry totals exceed supported range");
            }
        }
        totals.forEach((currency, total) -> {
            if (total != 0) {
                throw DomainException.badRequest(
                        "UNBALANCED_TRANSACTION",
                        "Entries for " + currency + " must sum to zero");
            }
        });
    }

    public static String normalizeCurrency(String currency) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw DomainException.badRequest("INVALID_CURRENCY", "Currency must be a three-letter ISO code");
        }
        return normalized;
    }

    public record Posting(UUID accountId, String currency, long amountMinor) {
    }
}
