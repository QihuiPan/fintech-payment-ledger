package com.portfolio.ledger.domain;

import static com.portfolio.ledger.domain.LedgerMath.Posting;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LedgerMathTest {

    @Test
    void acceptsRandomBalancedTransactions() {
        Random random = new Random(412_2026L);
        for (int iteration = 0; iteration < 1_000; iteration++) {
            long first = random.nextLong(1, 1_000_000);
            long second = random.nextLong(1, 1_000_000);
            List<Posting> postings = List.of(
                    new Posting(UUID.randomUUID(), "GBP", first),
                    new Posting(UUID.randomUUID(), "GBP", second),
                    new Posting(UUID.randomUUID(), "GBP", -(first + second)));
            assertThatCode(() -> LedgerMath.requireBalanced(postings)).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsAnUnbalancedCurrencyLeg() {
        List<Posting> postings = List.of(
                new Posting(UUID.randomUUID(), "GBP", -1_000),
                new Posting(UUID.randomUUID(), "GBP", 999));

        assertThatThrownBy(() -> LedgerMath.requireBalanced(postings))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must sum to zero");
    }

    @Test
    void balancesEachCurrencyIndependentlyForFx() {
        List<Posting> postings = List.of(
                new Posting(UUID.randomUUID(), "GBP", -1_000),
                new Posting(UUID.randomUUID(), "GBP", 1_000),
                new Posting(UUID.randomUUID(), "EUR", -1_170),
                new Posting(UUID.randomUUID(), "EUR", 1_170));

        assertThatCode(() -> LedgerMath.requireBalanced(postings)).doesNotThrowAnyException();
    }
}
