package com.portfolio.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.portfolio.ledger.domain.LedgerModels.SettlementRow;
import com.portfolio.ledger.service.PaymentService;
import com.portfolio.ledger.service.ReconciliationService;
import com.portfolio.ledger.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReconciliationIntegrationTest {
    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    void classifiesMismatchMissingLocalAndMissingProviderWithoutAutoCorrection() {
        String suffix = UUID.randomUUID().toString();
        var wallet = walletService.create("recon-" + suffix + "@example.com", List.of("GBP"));
        String mismatchReference = "recon-mismatch-" + suffix;
        String absentReference = "recon-absent-" + suffix;
        paymentService.deposit(wallet.walletId(), "GBP", 500, mismatchReference, "idem-a-" + suffix);
        paymentService.deposit(wallet.walletId(), "GBP", 300, absentReference, "idem-b-" + suffix);
        LocalDate date = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();

        var result = reconciliationService.run(
                date,
                List.of(
                        new SettlementRow(mismatchReference, "GBP", 550, "SETTLED", Instant.now()),
                        new SettlementRow("provider-only-" + suffix, "GBP", 100, "SETTLED", Instant.now())),
                "ledger-admin",
                "Daily provider settlement verification");

        assertThat(result.amountMismatch()).isEqualTo(1);
        assertThat(result.missingLocally()).isEqualTo(1);
        assertThat(result.missingAtProvider()).isEqualTo(1);
        assertThat(result.issues()).hasSize(3);
        assertThat(reconciliationService.csvReport(date))
                .contains("AMOUNT_MISMATCH", "MISSING_LOCALLY", "MISSING_AT_PROVIDER");
        assertThat(walletService.get(wallet.walletId()).balances().getFirst().availableBalanceMinor())
                .isEqualTo(800);
    }
}
