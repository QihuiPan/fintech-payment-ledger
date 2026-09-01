package com.portfolio.ledger.service;

import static com.portfolio.ledger.domain.LedgerModels.AccountType;
import static com.portfolio.ledger.domain.LedgerModels.StatementPage;
import static com.portfolio.ledger.domain.LedgerModels.WalletView;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.domain.LedgerMath;
import com.portfolio.ledger.repository.LedgerRepository;
import com.portfolio.ledger.repository.LedgerRepository.AccountRow;
import com.portfolio.ledger.repository.LedgerRepository.StatementCursorEntry;
import com.portfolio.ledger.repository.LedgerRepository.UserRow;
import com.portfolio.ledger.repository.LedgerRepository.WalletRow;
import com.portfolio.ledger.util.CursorCodec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final LedgerRepository repository;
    private final Clock clock = Clock.systemUTC();

    public WalletService(LedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WalletView create(String email, List<String> requestedCurrencies) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        requestedCurrencies.forEach(currency -> currencies.add(LedgerMath.normalizeCurrency(currency)));
        if (currencies.isEmpty()) {
            throw DomainException.badRequest("CURRENCY_REQUIRED", "At least one currency is required");
        }

        Instant now = Instant.now(clock);
        UserRow user = repository.findUserByEmail(normalizedEmail).orElseGet(() -> {
            UserRow created = new UserRow(
                    UUID.randomUUID(), normalizedEmail, "ACTIVE", "APPROVED", now);
            repository.insertUser(created);
            return created;
        });
        if (!user.status().equals("ACTIVE") || !user.kycStatus().equals("APPROVED")) {
            throw DomainException.forbidden(
                    "USER_NOT_ELIGIBLE",
                    "The user must be active with approved KYC status");
        }

        WalletRow wallet = new WalletRow(UUID.randomUUID(), user.id(), "ACTIVE", now);
        repository.insertWallet(wallet);
        for (String currency : currencies) {
            repository.insertAccount(new AccountRow(
                    UUID.randomUUID(),
                    wallet.id(),
                    "wallet:" + wallet.id() + ":" + currency,
                    currency,
                    AccountType.USER,
                    0,
                    0,
                    0,
                    now));
        }
        return get(wallet.id());
    }

    public WalletView get(UUID walletId) {
        WalletRow wallet = requireWallet(walletId);
        return new WalletView(
                wallet.id(),
                wallet.userId(),
                repository.findWalletEmail(wallet.id()),
                wallet.status(),
                repository.findBalances(wallet.id()));
    }

    public StatementPage statement(UUID walletId, String currency, String cursor, int requestedLimit) {
        requireWallet(walletId);
        String normalizedCurrency = LedgerMath.normalizeCurrency(currency);
        AccountRow account = repository.findAccount(walletId, normalizedCurrency)
                .orElseThrow(() -> DomainException.notFound(
                        "ACCOUNT_NOT_FOUND",
                        "Wallet does not have an account for the requested currency"));
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
        var fetched = repository.statement(
                account.id(), decoded.createdAt(), decoded.entryId(), limit + 1);
        boolean hasMore = fetched.size() > limit;
        var items = hasMore ? List.copyOf(fetched.subList(0, limit)) : fetched;
        String nextCursor = null;
        if (hasMore) {
            StatementCursorEntry last = repository.lastStatementEntry(
                    account.id(), decoded.createdAt(), decoded.entryId(), limit - 1);
            nextCursor = CursorCodec.encode(last.createdAt(), last.entryId());
        }
        return new StatementPage(walletId, normalizedCurrency, items, nextCursor);
    }

    public AccountRow requireAccount(UUID walletId, String currency) {
        String normalizedCurrency = LedgerMath.normalizeCurrency(currency);
        return repository.findAccount(walletId, normalizedCurrency)
                .orElseThrow(() -> DomainException.notFound(
                        "ACCOUNT_NOT_FOUND",
                        "Wallet does not have an account for " + normalizedCurrency));
    }

    public WalletRow requireWallet(UUID walletId) {
        return repository.findWallet(walletId)
                .orElseThrow(() -> DomainException.notFound("WALLET_NOT_FOUND", "Wallet does not exist"));
    }
}
