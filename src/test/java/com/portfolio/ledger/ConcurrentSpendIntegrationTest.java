package com.portfolio.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.portfolio.ledger.domain.LedgerModels.WalletView;
import com.portfolio.ledger.service.PaymentService;
import com.portfolio.ledger.service.ReconciliationService;
import com.portfolio.ledger.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentSpendIntegrationTest {
    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void allowsAtMostOneConcurrentSpendAgainstTheSameFunds() throws Exception {
        String suffix = UUID.randomUUID().toString();
        WalletView source = walletService.create("source-" + suffix + "@example.com", List.of("GBP"));
        WalletView firstRecipient = walletService.create("first-" + suffix + "@example.com", List.of("GBP"));
        WalletView secondRecipient = walletService.create("second-" + suffix + "@example.com", List.of("GBP"));
        paymentService.deposit(source.walletId(), "GBP", 1_000, "fund-" + suffix, "fund-" + suffix);

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptTransfer(
                    start, source.walletId(), firstRecipient.walletId(), "spend-a-" + suffix));
            Future<Boolean> second = executor.submit(() -> attemptTransfer(
                    start, source.walletId(), secondRecipient.walletId(), "spend-b-" + suffix));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }

        assertThat(balance(source)).isEqualTo(200);
        assertThat(balance(firstRecipient) + balance(secondRecipient)).isEqualTo(800);
        assertThat(reconciliationService.invariants().negativeUserBalanceCount()).isZero();
    }

    private boolean attemptTransfer(
            CountDownLatch start,
            UUID source,
            UUID recipient,
            String key) throws InterruptedException {
        start.await();
        try {
            paymentService.transfer(source, recipient, "GBP", 800, key);
            return true;
        } catch (RuntimeException expected) {
            return false;
        }
    }

    private long balance(WalletView wallet) {
        return walletService.get(wallet.walletId()).balances().getFirst().availableBalanceMinor();
    }
}
