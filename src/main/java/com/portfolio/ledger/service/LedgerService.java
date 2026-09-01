package com.portfolio.ledger.service;

import static com.portfolio.ledger.domain.LedgerMath.Posting;
import static com.portfolio.ledger.domain.LedgerModels.AccountType;
import static com.portfolio.ledger.domain.LedgerModels.TransactionType;
import static com.portfolio.ledger.domain.LedgerModels.TransactionView;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.domain.LedgerMath;
import com.portfolio.ledger.repository.LedgerRepository;
import com.portfolio.ledger.repository.LedgerRepository.AccountRow;
import com.portfolio.ledger.repository.LedgerRepository.EntryRow;
import com.portfolio.ledger.repository.LedgerRepository.OutboxRow;
import com.portfolio.ledger.repository.LedgerRepository.TransactionRow;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final LedgerRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock = Clock.systemUTC();

    public LedgerService(
            LedgerRepository repository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public TransactionView post(PostCommand command) {
        LedgerMath.requireBalanced(command.postings());
        List<AccountRow> locked = repository.lockAccounts(
                command.postings().stream().map(Posting::accountId).toList());
        Map<UUID, AccountRow> accounts = locked.stream()
                .collect(Collectors.toMap(AccountRow::id, Function.identity()));
        if (accounts.size() != command.postings().stream().map(Posting::accountId).distinct().count()) {
            throw DomainException.notFound("ACCOUNT_NOT_FOUND", "One or more ledger accounts do not exist");
        }

        Map<UUID, Long> deltas = new LinkedHashMap<>();
        for (Posting posting : command.postings()) {
            AccountRow account = accounts.get(posting.accountId());
            String currency = LedgerMath.normalizeCurrency(posting.currency());
            if (!account.currency().equals(currency)) {
                throw DomainException.badRequest(
                        "ACCOUNT_CURRENCY_MISMATCH",
                        "Entry currency does not match its account");
            }
            try {
                deltas.merge(account.id(), posting.amountMinor(), Math::addExact);
            } catch (ArithmeticException overflow) {
                throw DomainException.badRequest("AMOUNT_OVERFLOW", "Account delta exceeds supported range");
            }
        }

        deltas.forEach((accountId, delta) -> {
            AccountRow account = accounts.get(accountId);
            if (account.type() == AccountType.USER) {
                long resultingBalance;
                try {
                    resultingBalance = Math.addExact(account.availableBalanceMinor(), delta);
                } catch (ArithmeticException overflow) {
                    throw DomainException.badRequest("AMOUNT_OVERFLOW", "Balance exceeds supported range");
                }
                if (resultingBalance < 0) {
                    meterRegistry.counter("transfer_failure_total", "reason", "insufficient_funds")
                            .increment();
                    throw DomainException.conflict(
                            "INSUFFICIENT_FUNDS",
                            "The account does not have enough available balance");
                }
            }
        });

        Instant now = Instant.now(clock);
        UUID transactionId = UUID.randomUUID();
        repository.insertTransaction(new TransactionRow(
                transactionId,
                command.type(),
                "POSTED",
                command.reference(),
                command.idempotencyKey(),
                command.requestFingerprint(),
                command.reversesTransactionId(),
                json(command.metadata()),
                now));

        for (Posting posting : command.postings()) {
            repository.insertEntry(new EntryRow(
                    UUID.randomUUID(),
                    transactionId,
                    posting.accountId(),
                    LedgerMath.normalizeCurrency(posting.currency()),
                    posting.amountMinor(),
                    now));
        }
        deltas.forEach(repository::applyBalanceDelta);

        repository.insertOutbox(new OutboxRow(
                UUID.randomUUID(),
                transactionId,
                "ledger.transaction.posted",
                json(Map.of(
                        "transactionId", transactionId,
                        "type", command.type().name(),
                        "reference", command.reference())),
                now,
                null));

        meterRegistry.counter(
                "ledger_transaction_total",
                "type", command.type().name(),
                "status", "POSTED")
                .increment();
        return repository.transactionView(transactionId);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize ledger metadata", exception);
        }
    }

    public record PostCommand(
            TransactionType type,
            String reference,
            String idempotencyKey,
            String requestFingerprint,
            UUID reversesTransactionId,
            Map<String, Object> metadata,
            List<Posting> postings) {
    }
}
