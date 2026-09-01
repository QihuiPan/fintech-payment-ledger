package com.portfolio.ledger.service;

import static com.portfolio.ledger.domain.LedgerMath.Posting;
import static com.portfolio.ledger.domain.LedgerModels.AccountType;
import static com.portfolio.ledger.domain.LedgerModels.FxQuoteView;
import static com.portfolio.ledger.domain.LedgerModels.TransactionType;
import static com.portfolio.ledger.domain.LedgerModels.TransactionView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.portfolio.ledger.config.LedgerProperties;
import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.domain.LedgerMath;
import com.portfolio.ledger.repository.LedgerRepository;
import com.portfolio.ledger.repository.LedgerRepository.AccountRow;
import com.portfolio.ledger.repository.LedgerRepository.FxQuoteRow;
import com.portfolio.ledger.repository.LedgerRepository.TransactionRow;
import com.portfolio.ledger.service.LedgerService.PostCommand;
import com.portfolio.ledger.util.CryptoSupport;
import com.portfolio.ledger.util.IdempotencyLocks;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final LedgerRepository repository;
    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final LedgerProperties properties;
    private final IdempotencyLocks idempotencyLocks;
    private final MeterRegistry meterRegistry;
    private final Clock clock = Clock.systemUTC();

    public PaymentService(
            LedgerRepository repository,
            LedgerService ledgerService,
            WalletService walletService,
            LedgerProperties properties,
            IdempotencyLocks idempotencyLocks,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.ledgerService = ledgerService;
        this.walletService = walletService;
        this.properties = properties;
        this.idempotencyLocks = idempotencyLocks;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public TransactionView deposit(
            UUID walletId,
            String currency,
            long amountMinor,
            String providerReference,
            String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        return idempotencyLocks.withLock(key, () -> {
            requirePositive(amountMinor);
            String normalizedCurrency = LedgerMath.normalizeCurrency(currency);
            String reference = requireReference(providerReference);
            String fingerprint = CryptoSupport.fingerprint(
                    "DEPOSIT", walletId, normalizedCurrency, amountMinor, reference);
            Optional<TransactionView> replay = replay(key, fingerprint);
            if (replay.isPresent()) {
                return replay.get();
            }
            AccountRow userAccount = walletService.requireAccount(walletId, normalizedCurrency);
            AccountRow platformCash = repository.ensureSystemAccount(
                    "platform:cash:" + normalizedCurrency,
                    normalizedCurrency,
                    AccountType.PLATFORM_CASH,
                    Instant.now(clock));
            return ledgerService.post(new PostCommand(
                    TransactionType.DEPOSIT,
                    reference,
                    key,
                    fingerprint,
                    null,
                    Map.of("walletId", walletId, "providerReference", reference),
                    List.of(
                            new Posting(userAccount.id(), normalizedCurrency, amountMinor),
                            new Posting(platformCash.id(), normalizedCurrency, -amountMinor))));
        });
    }

    @Transactional
    public TransactionView transfer(
            UUID senderWalletId,
            UUID recipientWalletId,
            String currency,
            long amountMinor,
            String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        return idempotencyLocks.withLock(key, () -> {
            requirePositive(amountMinor);
            if (senderWalletId.equals(recipientWalletId)) {
                throw DomainException.badRequest(
                        "SAME_WALLET_TRANSFER",
                        "Sender and recipient wallets must be different");
            }
            String normalizedCurrency = LedgerMath.normalizeCurrency(currency);
            String fingerprint = CryptoSupport.fingerprint(
                    "TRANSFER", senderWalletId, recipientWalletId, normalizedCurrency, amountMinor);
            Optional<TransactionView> replay = replay(key, fingerprint);
            if (replay.isPresent()) {
                return replay.get();
            }
            AccountRow sender = walletService.requireAccount(senderWalletId, normalizedCurrency);
            AccountRow recipient = walletService.requireAccount(recipientWalletId, normalizedCurrency);
            return ledgerService.post(new PostCommand(
                    TransactionType.TRANSFER,
                    "transfer:" + UUID.randomUUID(),
                    key,
                    fingerprint,
                    null,
                    Map.of("senderWalletId", senderWalletId, "recipientWalletId", recipientWalletId),
                    List.of(
                            new Posting(sender.id(), normalizedCurrency, -amountMinor),
                            new Posting(recipient.id(), normalizedCurrency, amountMinor))));
        });
    }

    @Transactional
    public FxQuoteView quote(
            UUID walletId,
            String baseCurrency,
            String quoteCurrency,
            long baseAmountMinor) {
        requirePositive(baseAmountMinor);
        walletService.requireWallet(walletId);
        String base = LedgerMath.normalizeCurrency(baseCurrency);
        String quote = LedgerMath.normalizeCurrency(quoteCurrency);
        if (base.equals(quote)) {
            throw DomainException.badRequest("SAME_CURRENCY", "FX currencies must be different");
        }
        walletService.requireAccount(walletId, base);
        walletService.requireAccount(walletId, quote);
        BigDecimal rate = properties.fx().rates().get(base + "-" + quote);
        if (rate == null || rate.signum() <= 0) {
            throw DomainException.badRequest("FX_PAIR_UNAVAILABLE", "The requested FX pair is unavailable");
        }
        long quoteAmount = BigDecimal.valueOf(baseAmountMinor)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_EVEN)
                .longValueExact();
        long fee = BigDecimal.valueOf(baseAmountMinor)
                .multiply(BigDecimal.valueOf(properties.fx().feeBasisPoints()))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
        fee = Math.max(1, fee);
        Instant now = Instant.now(clock);
        FxQuoteRow row = new FxQuoteRow(
                UUID.randomUUID(),
                walletId,
                base,
                quote,
                baseAmountMinor,
                quoteAmount,
                fee,
                rate,
                now.plusSeconds(properties.fx().quoteTtlSeconds()),
                null,
                now);
        repository.insertQuote(row);
        return repository.quoteView(row);
    }

    @Transactional
    public TransactionView convert(UUID quoteId, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        return idempotencyLocks.withLock(key, () -> {
            String fingerprint = CryptoSupport.fingerprint("FX_CONVERSION", quoteId);
            Optional<TransactionView> replay = replay(key, fingerprint);
            if (replay.isPresent()) {
                return replay.get();
            }
            FxQuoteRow quote = repository.lockQuote(quoteId)
                    .orElseThrow(() -> DomainException.notFound("FX_QUOTE_NOT_FOUND", "FX quote does not exist"));
            Instant now = Instant.now(clock);
            if (quote.consumedAt() != null) {
                throw DomainException.conflict("FX_QUOTE_CONSUMED", "FX quote was already consumed");
            }
            if (!quote.expiresAt().isAfter(now)) {
                throw DomainException.conflict("FX_QUOTE_EXPIRED", "FX quote has expired; request a new quote");
            }

            AccountRow baseUser = walletService.requireAccount(quote.walletId(), quote.baseCurrency());
            AccountRow quoteUser = walletService.requireAccount(quote.walletId(), quote.quoteCurrency());
            AccountRow baseClearing = repository.ensureSystemAccount(
                    "platform:fx:" + quote.baseCurrency(),
                    quote.baseCurrency(),
                    AccountType.FX_CLEARING,
                    now);
            AccountRow quoteClearing = repository.ensureSystemAccount(
                    "platform:fx:" + quote.quoteCurrency(),
                    quote.quoteCurrency(),
                    AccountType.FX_CLEARING,
                    now);
            AccountRow feeRevenue = repository.ensureSystemAccount(
                    "platform:fees:" + quote.baseCurrency(),
                    quote.baseCurrency(),
                    AccountType.FEE_REVENUE,
                    now);
            long totalDebit;
            try {
                totalDebit = Math.addExact(quote.baseAmountMinor(), quote.feeMinor());
            } catch (ArithmeticException overflow) {
                throw DomainException.badRequest("AMOUNT_OVERFLOW", "FX debit exceeds supported range");
            }
            repository.markQuoteConsumed(quote.id(), now);
            return ledgerService.post(new PostCommand(
                    TransactionType.FX_CONVERSION,
                    "fx:" + quote.id(),
                    key,
                    fingerprint,
                    null,
                    Map.of("quoteId", quote.id(), "rate", quote.rate()),
                    List.of(
                            new Posting(baseUser.id(), quote.baseCurrency(), -totalDebit),
                            new Posting(baseClearing.id(), quote.baseCurrency(), quote.baseAmountMinor()),
                            new Posting(feeRevenue.id(), quote.baseCurrency(), quote.feeMinor()),
                            new Posting(quoteClearing.id(), quote.quoteCurrency(), -quote.quoteAmountMinor()),
                            new Posting(quoteUser.id(), quote.quoteCurrency(), quote.quoteAmountMinor()))));
        });
    }

    @Transactional
    public TransactionView reverse(UUID originalTransactionId, String reason, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        return idempotencyLocks.withLock(key, () -> {
            String normalizedReason = requireReason(reason);
            String fingerprint = CryptoSupport.fingerprint(
                    "REVERSAL", originalTransactionId, normalizedReason);
            Optional<TransactionView> replay = replay(key, fingerprint);
            if (replay.isPresent()) {
                return replay.get();
            }
            TransactionRow original = repository.findTransaction(originalTransactionId)
                    .orElseThrow(() -> DomainException.notFound(
                            "TRANSACTION_NOT_FOUND",
                            "Original transaction does not exist"));
            if (original.type() == TransactionType.REVERSAL) {
                throw DomainException.badRequest(
                        "REVERSAL_OF_REVERSAL",
                        "A reversal transaction cannot be reversed directly");
            }
            if (repository.findReversalOf(original.id()).isPresent()) {
                throw DomainException.conflict(
                        "ALREADY_REVERSED",
                        "The transaction already has a full reversal");
            }
            List<Posting> opposite = new ArrayList<>();
            repository.findEntries(original.id()).forEach(entry -> opposite.add(
                    new Posting(entry.accountId(), entry.currency(), Math.negateExact(entry.amountMinor()))));
            return ledgerService.post(new PostCommand(
                    TransactionType.REVERSAL,
                    "reversal:" + original.id(),
                    key,
                    fingerprint,
                    original.id(),
                    Map.of("reason", normalizedReason, "originalReference", original.reference()),
                    opposite));
        });
    }

    public TransactionView getTransaction(UUID transactionId) {
        repository.findTransaction(transactionId)
                .orElseThrow(() -> DomainException.notFound(
                        "TRANSACTION_NOT_FOUND",
                        "Transaction does not exist"));
        return repository.transactionView(transactionId);
    }

    private Optional<TransactionView> replay(String key, String fingerprint) {
        Optional<TransactionRow> existing = repository.findTransactionByIdempotencyKey(key);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        if (!existing.get().requestFingerprint().equals(fingerprint)) {
            throw DomainException.conflict(
                    "IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used with a different request payload");
        }
        meterRegistry.counter("idempotency_replay_total").increment();
        return Optional.of(repository.transactionView(existing.get().id()));
    }

    private static String requireIdempotencyKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw DomainException.badRequest(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain between 1 and 128 characters");
        }
        return normalized;
    }

    private static String requireReference(String reference) {
        String normalized = reference == null ? "" : reference.trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw DomainException.badRequest(
                    "INVALID_REFERENCE",
                    "Provider reference must contain between 1 and 160 characters");
        }
        return normalized;
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 3 || normalized.length() > 500) {
            throw DomainException.badRequest(
                    "INVALID_REASON",
                    "Reason must contain between 3 and 500 characters");
        }
        return normalized;
    }

    private static void requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw DomainException.badRequest("INVALID_AMOUNT", "Amount must be greater than zero");
        }
    }
}
