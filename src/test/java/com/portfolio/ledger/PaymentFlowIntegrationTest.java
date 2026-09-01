package com.portfolio.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.domain.LedgerModels.TransactionView;
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
class PaymentFlowIntegrationTest {
    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void completesDepositTransferFxReversalAndStatementFlow() {
        String suffix = UUID.randomUUID().toString();
        WalletView sender = walletService.create("sender-" + suffix + "@example.com", List.of("GBP", "EUR"));
        WalletView recipient = walletService.create("recipient-" + suffix + "@example.com", List.of("GBP"));

        TransactionView deposit = paymentService.deposit(
                sender.walletId(), "GBP", 2_000, "provider-" + suffix, "deposit-" + suffix);
        TransactionView replay = paymentService.deposit(
                sender.walletId(), "GBP", 2_000, "provider-" + suffix, "deposit-" + suffix);
        assertThat(replay.transactionId()).isEqualTo(deposit.transactionId());

        assertThatThrownBy(() -> paymentService.deposit(
                sender.walletId(), "GBP", 2_001, "provider-" + suffix, "deposit-" + suffix))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("different request payload");

        TransactionView transfer = paymentService.transfer(
                sender.walletId(), recipient.walletId(), "GBP", 1_000, "transfer-" + suffix);
        assertBalancedByCurrency(transfer);

        var quote = paymentService.quote(sender.walletId(), "GBP", "EUR", 500);
        TransactionView conversion = paymentService.convert(quote.quoteId(), "conversion-" + suffix);
        assertBalancedByCurrency(conversion);

        TransactionView reversal = paymentService.reverse(
                transfer.transactionId(), "Customer support approved refund", "reversal-" + suffix);
        assertThat(reversal.reversesTransactionId()).isEqualTo(transfer.transactionId());
        assertBalancedByCurrency(reversal);

        WalletView senderAfter = walletService.get(sender.walletId());
        WalletView recipientAfter = walletService.get(recipient.walletId());
        assertThat(balance(senderAfter, "GBP")).isEqualTo(1_499);
        assertThat(balance(senderAfter, "EUR")).isEqualTo(585);
        assertThat(balance(recipientAfter, "GBP")).isZero();

        var statement = walletService.statement(sender.walletId(), "GBP", null, 100);
        assertThat(statement.items()).hasSize(4);
        assertThat(statement.items().getFirst().runningBalanceMinor()).isEqualTo(1_499);

        var invariants = reconciliationService.invariants();
        assertThat(invariants.unbalancedTransactionCount()).isZero();
        assertThat(invariants.negativeUserBalanceCount()).isZero();
        assertThat(invariants.unpublishedOutboxByType()).containsEntry("ledger.transaction.posted", 4L);
    }

    private static long balance(WalletView wallet, String currency) {
        return wallet.balances().stream()
                .filter(balance -> balance.currency().equals(currency))
                .findFirst()
                .orElseThrow()
                .availableBalanceMinor();
    }

    private static void assertBalancedByCurrency(TransactionView transaction) {
        Map<String, Long> totals = transaction.entries().stream().collect(Collectors.groupingBy(
                entry -> entry.currency(),
                Collectors.summingLong(entry -> entry.amountMinor())));
        assertThat(totals.values()).allMatch(total -> total == 0);
    }
}
