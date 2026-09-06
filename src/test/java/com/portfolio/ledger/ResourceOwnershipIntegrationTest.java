package com.portfolio.ledger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.service.PaymentService;
import com.portfolio.ledger.service.WalletAccessService;
import com.portfolio.ledger.service.WalletService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResourceOwnershipIntegrationTest {
    @Autowired
    private WalletService walletService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WalletAccessService walletAccessService;

    @Test
    void isolatesWalletsAndTransactionsByAuthenticatedSubject() {
        String suffix = UUID.randomUUID().toString();
        var wallet = walletService.create(
                "alice-" + suffix + "@example.com", List.of("GBP"), "alice");
        var deposit = paymentService.deposit(
                wallet.walletId(),
                "GBP",
                1_000,
                "ownership-" + suffix,
                "ownership-" + suffix,
                "alice");

        var alice = authentication("alice", "ROLE_USER");
        var bob = authentication("bob", "ROLE_USER");
        var admin = authentication("admin", "ROLE_ADMIN");

        assertThatCode(() -> walletAccessService.requireWalletAccess(wallet.walletId(), alice))
                .doesNotThrowAnyException();
        assertThatCode(() -> walletAccessService.requireTransactionAccess(
                deposit.transactionId(), alice)).doesNotThrowAnyException();
        assertThatCode(() -> walletAccessService.requireWalletAccess(wallet.walletId(), admin))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> walletAccessService.requireWalletAccess(wallet.walletId(), bob))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("another API user");
        assertThatThrownBy(() -> walletAccessService.requireTransactionAccess(
                deposit.transactionId(), bob))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("another API user");
    }

    private static UsernamePasswordAuthenticationToken authentication(
            String subject,
            String authority) {
        return UsernamePasswordAuthenticationToken.authenticated(
                subject,
                "not-used",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
