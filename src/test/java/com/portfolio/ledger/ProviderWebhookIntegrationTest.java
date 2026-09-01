package com.portfolio.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.portfolio.ledger.service.ProviderEventWorker;
import com.portfolio.ledger.service.ProviderWebhookService;
import com.portfolio.ledger.service.WalletService;
import com.portfolio.ledger.util.CryptoSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "ledger.provider.worker-delay-milliseconds=600000")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProviderWebhookIntegrationTest {
    @Autowired
    private WalletService walletService;

    @Autowired
    private ProviderWebhookService webhookService;

    @Autowired
    private ProviderEventWorker worker;

    @Test
    void verifiesSignatureDeduplicatesAndPostsDeposit() {
        String suffix = UUID.randomUUID().toString();
        var wallet = walletService.create("webhook-" + suffix + "@example.com", List.of("GBP"));
        String eventId = "evt-" + suffix;
        String payload = """
                {"type":"deposit.succeeded","walletId":"%s","currency":"GBP","amountMinor":750,"providerReference":"pay-%s"}
                """.formatted(wallet.walletId(), suffix).strip();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = CryptoSupport.hmacSha256("local-demo-secret", timestamp + "." + payload);

        var first = webhookService.receive(eventId, timestamp, signature, payload);
        var duplicate = webhookService.receive(eventId, timestamp, signature, payload);
        assertThat(first.status()).isEqualTo("RECEIVED");
        assertThat(duplicate.status()).isEqualTo("DUPLICATE");

        worker.runOnce();
        assertThat(walletService.get(wallet.walletId()).balances().getFirst().availableBalanceMinor())
                .isEqualTo(750);
    }
}
