package com.portfolio.ledger.repository;

import static com.portfolio.ledger.domain.LedgerModels.AccountType;
import static com.portfolio.ledger.domain.LedgerModels.BalanceView;
import static com.portfolio.ledger.domain.LedgerModels.EntryView;
import static com.portfolio.ledger.domain.LedgerModels.FxQuoteView;
import static com.portfolio.ledger.domain.LedgerModels.StatementItem;
import static com.portfolio.ledger.domain.LedgerModels.TransactionType;
import static com.portfolio.ledger.domain.LedgerModels.TransactionView;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {
    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findUserByEmail(String email) {
        return optional(jdbc.query(
                "SELECT id, email, status, kyc_status, created_at FROM users WHERE email = ?",
                USER_MAPPER,
                email));
    }

    public void insertUser(UserRow user) {
        jdbc.update(
                "INSERT INTO users(id, email, status, kyc_status, created_at) VALUES (?, ?, ?, ?, ?)",
                user.id(), user.email(), user.status(), user.kycStatus(), dbTime(user.createdAt()));
    }

    public void insertWallet(WalletRow wallet) {
        jdbc.update(
                "INSERT INTO wallets(id, user_id, status, created_at) VALUES (?, ?, ?, ?)",
                wallet.id(), wallet.userId(), wallet.status(), dbTime(wallet.createdAt()));
    }

    public Optional<WalletRow> findWallet(UUID walletId) {
        return optional(jdbc.query(
                "SELECT id, user_id, status, created_at FROM wallets WHERE id = ?",
                WALLET_MAPPER,
                walletId));
    }

    public String findWalletEmail(UUID walletId) {
        return jdbc.queryForObject(
                "SELECT u.email FROM wallets w JOIN users u ON u.id = w.user_id WHERE w.id = ?",
                String.class,
                walletId);
    }

    public void insertAccount(AccountRow account) {
        jdbc.update("""
                INSERT INTO accounts(
                    id, wallet_id, code, currency, account_type,
                    posted_balance_minor, available_balance_minor, version, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                account.id(), account.walletId(), account.code(), account.currency(),
                account.type().name(), account.postedBalanceMinor(), account.availableBalanceMinor(),
                account.version(), dbTime(account.createdAt()));
    }

    public Optional<AccountRow> findAccount(UUID walletId, String currency) {
        return optional(jdbc.query("""
                SELECT id, wallet_id, code, currency, account_type,
                       posted_balance_minor, available_balance_minor, version, created_at
                  FROM accounts
                 WHERE wallet_id = ? AND currency = ? AND account_type = 'USER'
                """, ACCOUNT_MAPPER, walletId, currency));
    }

    public Optional<AccountRow> findAccountByCode(String code) {
        return optional(jdbc.query("""
                SELECT id, wallet_id, code, currency, account_type,
                       posted_balance_minor, available_balance_minor, version, created_at
                  FROM accounts WHERE code = ?
                """, ACCOUNT_MAPPER, code));
    }

    public List<BalanceView> findBalances(UUID walletId) {
        return jdbc.query("""
                SELECT id, currency, posted_balance_minor, available_balance_minor, version
                  FROM accounts
                 WHERE wallet_id = ? AND account_type = 'USER'
                 ORDER BY currency
                """, (rs, ignored) -> new BalanceView(
                rs.getObject("id", UUID.class),
                rs.getString("currency"),
                rs.getLong("posted_balance_minor"),
                rs.getLong("available_balance_minor"),
                rs.getLong("version")), walletId);
    }

    public List<AccountRow> lockAccounts(Collection<UUID> accountIds) {
        List<UUID> ordered = accountIds.stream().distinct().sorted().toList();
        if (ordered.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ordered.size(), "?"));
        String sql = """
                SELECT id, wallet_id, code, currency, account_type,
                       posted_balance_minor, available_balance_minor, version, created_at
                  FROM accounts
                 WHERE id IN (%s)
                 ORDER BY id
                 FOR UPDATE
                """.formatted(placeholders);
        return jdbc.query(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(sql);
                    for (int index = 0; index < ordered.size(); index++) {
                        statement.setObject(index + 1, ordered.get(index));
                    }
                    return statement;
                },
                ACCOUNT_MAPPER);
    }

    public AccountRow ensureSystemAccount(String code, String currency, AccountType type, Instant now) {
        Optional<AccountRow> existing = findAccountByCode(code);
        if (existing.isPresent()) {
            return existing.get();
        }
        AccountRow account = new AccountRow(
                UUID.randomUUID(), null, code, currency, type, 0, 0, 0, now);
        try {
            insertAccount(account);
            return account;
        } catch (DuplicateKeyException race) {
            return findAccountByCode(code).orElseThrow(() -> race);
        }
    }

    public Optional<TransactionRow> findTransactionByIdempotencyKey(String key) {
        return optional(jdbc.query("""
                SELECT id, transaction_type, status, reference, idempotency_key,
                       request_fingerprint, reverses_transaction_id, metadata_json, created_at
                  FROM ledger_transactions WHERE idempotency_key = ?
                """, TRANSACTION_MAPPER, key));
    }

    public Optional<TransactionRow> findTransaction(UUID transactionId) {
        return optional(jdbc.query("""
                SELECT id, transaction_type, status, reference, idempotency_key,
                       request_fingerprint, reverses_transaction_id, metadata_json, created_at
                  FROM ledger_transactions WHERE id = ?
                """, TRANSACTION_MAPPER, transactionId));
    }

    public Optional<TransactionRow> findTransactionByReference(String reference) {
        return optional(jdbc.query("""
                SELECT id, transaction_type, status, reference, idempotency_key,
                       request_fingerprint, reverses_transaction_id, metadata_json, created_at
                  FROM ledger_transactions WHERE reference = ?
                """, TRANSACTION_MAPPER, reference));
    }

    public Optional<TransactionRow> findReversalOf(UUID transactionId) {
        return optional(jdbc.query("""
                SELECT id, transaction_type, status, reference, idempotency_key,
                       request_fingerprint, reverses_transaction_id, metadata_json, created_at
                  FROM ledger_transactions WHERE reverses_transaction_id = ?
                """, TRANSACTION_MAPPER, transactionId));
    }

    public void insertTransaction(TransactionRow transaction) {
        jdbc.update("""
                INSERT INTO ledger_transactions(
                    id, transaction_type, status, reference, idempotency_key,
                    request_fingerprint, reverses_transaction_id, metadata_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                transaction.id(), transaction.type().name(), transaction.status(), transaction.reference(),
                transaction.idempotencyKey(), transaction.requestFingerprint(),
                transaction.reversesTransactionId(), transaction.metadataJson(),
                dbTime(transaction.createdAt()));
    }

    public void insertEntry(EntryRow entry) {
        jdbc.update("""
                INSERT INTO ledger_entries(
                    id, transaction_id, account_id, currency, amount_minor, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                entry.id(), entry.transactionId(), entry.accountId(), entry.currency(),
                entry.amountMinor(), dbTime(entry.createdAt()));
    }

    public void applyBalanceDelta(UUID accountId, long delta) {
        int changed = jdbc.update("""
                UPDATE accounts
                   SET posted_balance_minor = posted_balance_minor + ?,
                       available_balance_minor = available_balance_minor + ?,
                       version = version + 1
                 WHERE id = ?
                """, delta, delta, accountId);
        if (changed != 1) {
            throw new IllegalStateException("Expected exactly one account balance update");
        }
    }

    public List<EntryRow> findEntries(UUID transactionId) {
        return jdbc.query("""
                SELECT id, transaction_id, account_id, currency, amount_minor, created_at
                  FROM ledger_entries WHERE transaction_id = ? ORDER BY id
                """, ENTRY_MAPPER, transactionId);
    }

    public TransactionView transactionView(UUID transactionId) {
        TransactionRow transaction = findTransaction(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown transaction " + transactionId));
        List<EntryView> entries = jdbc.query("""
                SELECT e.id, e.account_id, a.code, e.currency, e.amount_minor
                  FROM ledger_entries e
                  JOIN accounts a ON a.id = e.account_id
                 WHERE e.transaction_id = ?
                 ORDER BY e.id
                """, (rs, ignored) -> new EntryView(
                rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class),
                rs.getString("code"),
                rs.getString("currency"),
                rs.getLong("amount_minor")), transactionId);
        return new TransactionView(
                transaction.id(),
                transaction.type(),
                transaction.status(),
                transaction.reference(),
                transaction.reversesTransactionId(),
                transaction.createdAt(),
                entries);
    }

    public void insertOutbox(OutboxRow event) {
        jdbc.update("""
                INSERT INTO outbox_events(
                    id, aggregate_id, event_type, payload_json, created_at, published_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, event.id(), event.aggregateId(), event.eventType(), event.payloadJson(),
                dbTime(event.createdAt()), event.publishedAt() == null ? null : dbTime(event.publishedAt()));
    }

    public List<StatementItem> statement(
            UUID accountId,
            Instant beforeCreatedAt,
            UUID beforeEntryId,
            int limit) {
        String cursorFilter = beforeCreatedAt == null
                ? ""
                : "WHERE (created_at < ? OR (created_at = ? AND entry_id < ?))";
        String sql = """
                SELECT * FROM (
                    SELECT e.id AS entry_id, t.id AS transaction_id, t.transaction_type,
                           t.reference, e.currency, e.amount_minor, e.created_at,
                           SUM(e.amount_minor) OVER (
                               ORDER BY e.created_at, e.id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                           ) AS running_balance_minor
                      FROM ledger_entries e
                      JOIN ledger_transactions t ON t.id = e.transaction_id
                     WHERE e.account_id = ?
                ) statement_rows
                %s
                ORDER BY created_at DESC, entry_id DESC
                LIMIT ?
                """.formatted(cursorFilter);
        List<Object> arguments = new ArrayList<>();
        arguments.add(accountId);
        if (beforeCreatedAt != null) {
            arguments.add(dbTime(beforeCreatedAt));
            arguments.add(dbTime(beforeCreatedAt));
            arguments.add(beforeEntryId);
        }
        arguments.add(limit);
        return jdbc.query(sql, STATEMENT_MAPPER, arguments.toArray());
    }

    public StatementCursorEntry lastStatementEntry(
            UUID accountId,
            Instant beforeCreatedAt,
            UUID beforeEntryId,
            int offset) {
        List<EntryRow> rows;
        if (beforeCreatedAt == null) {
            rows = jdbc.query("""
                    SELECT id, transaction_id, account_id, currency, amount_minor, created_at
                      FROM ledger_entries WHERE account_id = ?
                     ORDER BY created_at DESC, id DESC LIMIT 1 OFFSET ?
                    """, ENTRY_MAPPER, accountId, offset);
        } else {
            rows = jdbc.query("""
                    SELECT id, transaction_id, account_id, currency, amount_minor, created_at
                      FROM ledger_entries
                     WHERE account_id = ?
                       AND (created_at < ? OR (created_at = ? AND id < ?))
                     ORDER BY created_at DESC, id DESC LIMIT 1 OFFSET ?
                    """, ENTRY_MAPPER, accountId, dbTime(beforeCreatedAt), dbTime(beforeCreatedAt),
                    beforeEntryId, offset);
        }
        return rows.isEmpty() ? null : new StatementCursorEntry(rows.getFirst().createdAt(), rows.getFirst().id());
    }

    public void insertQuote(FxQuoteRow quote) {
        jdbc.update("""
                INSERT INTO fx_quotes(
                    id, wallet_id, base_currency, quote_currency, base_amount_minor,
                    quote_amount_minor, fee_minor, rate, expires_at, consumed_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, quote.id(), quote.walletId(), quote.baseCurrency(), quote.quoteCurrency(),
                quote.baseAmountMinor(), quote.quoteAmountMinor(), quote.feeMinor(), quote.rate(),
                dbTime(quote.expiresAt()), null, dbTime(quote.createdAt()));
    }

    public Optional<FxQuoteRow> lockQuote(UUID quoteId) {
        return optional(jdbc.query("""
                SELECT id, wallet_id, base_currency, quote_currency, base_amount_minor,
                       quote_amount_minor, fee_minor, rate, expires_at, consumed_at, created_at
                  FROM fx_quotes WHERE id = ? FOR UPDATE
                """, FX_QUOTE_MAPPER, quoteId));
    }

    public void markQuoteConsumed(UUID quoteId, Instant consumedAt) {
        jdbc.update("UPDATE fx_quotes SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL",
                dbTime(consumedAt), quoteId);
    }

    public FxQuoteView quoteView(FxQuoteRow quote) {
        return new FxQuoteView(
                quote.id(), quote.walletId(), quote.baseCurrency(), quote.quoteCurrency(),
                quote.baseAmountMinor(), quote.quoteAmountMinor(), quote.feeMinor(), quote.rate(),
                quote.expiresAt(), quote.consumedAt());
    }

    public boolean insertProviderEvent(ProviderEventRow event) {
        try {
            jdbc.update("""
                    INSERT INTO provider_events(
                        provider_event_id, payload_hash, payload_json, event_type, wallet_id,
                        currency, amount_minor, provider_reference, status, error_message,
                        received_at, processed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, event.providerEventId(), event.payloadHash(), event.payloadJson(),
                    event.eventType(), event.walletId(), event.currency(), event.amountMinor(),
                    event.providerReference(), event.status(), event.errorMessage(),
                    dbTime(event.receivedAt()), null);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public List<ProviderEventRow> findReceivedProviderEvents(int limit) {
        return jdbc.query("""
                SELECT provider_event_id, payload_hash, payload_json, event_type, wallet_id,
                       currency, amount_minor, provider_reference, status, error_message,
                       received_at, processed_at
                  FROM provider_events
                 WHERE status = 'RECEIVED'
                 ORDER BY received_at
                 LIMIT ?
                """, PROVIDER_EVENT_MAPPER, limit);
    }

    public void markProviderEvent(String eventId, String status, String error, Instant processedAt) {
        jdbc.update("""
                UPDATE provider_events
                   SET status = ?, error_message = ?, processed_at = ?
                 WHERE provider_event_id = ?
                """, status, error, dbTime(processedAt), eventId);
    }

    public Map<String, LocalDepositRow> findLocalDeposits(LocalDate businessDate) {
        Instant start = businessDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<LocalDepositRow> rows = jdbc.query("""
                SELECT t.reference, e.currency, SUM(e.amount_minor) AS amount_minor
                  FROM ledger_transactions t
                  JOIN ledger_entries e ON e.transaction_id = t.id
                  JOIN accounts a ON a.id = e.account_id
                 WHERE t.transaction_type = 'DEPOSIT'
                   AND a.account_type = 'USER'
                   AND t.created_at >= ? AND t.created_at < ?
                 GROUP BY t.reference, e.currency
                """, (rs, ignored) -> new LocalDepositRow(
                rs.getString("reference"),
                rs.getString("currency"),
                rs.getLong("amount_minor")), dbTime(start), dbTime(end));
        Map<String, LocalDepositRow> byReference = new LinkedHashMap<>();
        rows.forEach(row -> byReference.put(row.reference(), row));
        return byReference;
    }

    public void insertReconciliationException(ReconciliationExceptionRow issue) {
        try {
            jdbc.update("""
                    INSERT INTO reconciliation_exceptions(
                        id, business_date, category, provider_reference, currency,
                        provider_amount_minor, local_amount_minor, status, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, issue.id(), issue.businessDate(), issue.category(), issue.providerReference(),
                    issue.currency(), issue.providerAmountMinor(), issue.localAmountMinor(),
                    issue.status(), dbTime(issue.createdAt()));
        } catch (DuplicateKeyException duplicate) {
            // Re-running reconciliation is intentionally idempotent.
        }
    }

    public List<ReconciliationExceptionRow> findReconciliationExceptions(LocalDate businessDate) {
        return jdbc.query("""
                SELECT id, business_date, category, provider_reference, currency,
                       provider_amount_minor, local_amount_minor, status, created_at
                  FROM reconciliation_exceptions
                 WHERE business_date = ?
                 ORDER BY category, provider_reference
                """, RECONCILIATION_MAPPER, businessDate);
    }

    public void insertAudit(AuditRow audit) {
        jdbc.update("""
                INSERT INTO audit_logs(
                    id, actor, action, entity_type, entity_id, reason,
                    before_json, after_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, audit.id(), audit.actor(), audit.action(), audit.entityType(), audit.entityId(),
                audit.reason(), audit.beforeJson(), audit.afterJson(), dbTime(audit.createdAt()));
    }

    public List<AuditRow> findAuditLogs(int limit) {
        return jdbc.query("""
                SELECT id, actor, action, entity_type, entity_id, reason,
                       before_json, after_json, created_at
                  FROM audit_logs ORDER BY created_at DESC LIMIT ?
                """, AUDIT_MAPPER, limit);
    }

    public long countUnbalancedTransactions() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id, currency
                      FROM ledger_entries
                     GROUP BY transaction_id, currency
                    HAVING SUM(amount_minor) <> 0
                ) unbalanced
                """, Long.class);
        return count == null ? 0 : count;
    }

    public long countNegativeUserBalances() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM accounts
                 WHERE account_type = 'USER' AND available_balance_minor < 0
                """, Long.class);
        return count == null ? 0 : count;
    }

    public Map<String, Long> unpublishedOutboxByType() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("""
                SELECT event_type, COUNT(*) AS event_count
                  FROM outbox_events WHERE published_at IS NULL
                 GROUP BY event_type ORDER BY event_type
                """, rs -> {
            counts.put(rs.getString("event_type"), rs.getLong("event_count"));
        });
        return counts;
    }

    private static <T> Optional<T> optional(List<T> rows) {
        return rows.stream().findFirst();
    }

    private static OffsetDateTime dbTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private static final RowMapper<UserRow> USER_MAPPER = (rs, ignored) -> new UserRow(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getString("status"),
            rs.getString("kyc_status"),
            instant(rs, "created_at"));

    private static final RowMapper<WalletRow> WALLET_MAPPER = (rs, ignored) -> new WalletRow(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("status"),
            instant(rs, "created_at"));

    private static final RowMapper<AccountRow> ACCOUNT_MAPPER = (rs, ignored) -> new AccountRow(
            rs.getObject("id", UUID.class),
            nullableUuid(rs, "wallet_id"),
            rs.getString("code"),
            rs.getString("currency"),
            AccountType.valueOf(rs.getString("account_type")),
            rs.getLong("posted_balance_minor"),
            rs.getLong("available_balance_minor"),
            rs.getLong("version"),
            instant(rs, "created_at"));

    private static final RowMapper<TransactionRow> TRANSACTION_MAPPER = (rs, ignored) ->
            new TransactionRow(
                    rs.getObject("id", UUID.class),
                    TransactionType.valueOf(rs.getString("transaction_type")),
                    rs.getString("status"),
                    rs.getString("reference"),
                    rs.getString("idempotency_key"),
                    rs.getString("request_fingerprint"),
                    nullableUuid(rs, "reverses_transaction_id"),
                    rs.getString("metadata_json"),
                    instant(rs, "created_at"));

    private static final RowMapper<EntryRow> ENTRY_MAPPER = (rs, ignored) -> new EntryRow(
            rs.getObject("id", UUID.class),
            rs.getObject("transaction_id", UUID.class),
            rs.getObject("account_id", UUID.class),
            rs.getString("currency"),
            rs.getLong("amount_minor"),
            instant(rs, "created_at"));

    private static final RowMapper<StatementItem> STATEMENT_MAPPER = (rs, ignored) ->
            new StatementItem(
                    rs.getObject("transaction_id", UUID.class),
                    TransactionType.valueOf(rs.getString("transaction_type")),
                    rs.getString("reference"),
                    rs.getString("currency"),
                    rs.getLong("amount_minor"),
                    rs.getLong("running_balance_minor"),
                    instant(rs, "created_at"));

    private static final RowMapper<FxQuoteRow> FX_QUOTE_MAPPER = (rs, ignored) -> new FxQuoteRow(
            rs.getObject("id", UUID.class),
            rs.getObject("wallet_id", UUID.class),
            rs.getString("base_currency"),
            rs.getString("quote_currency"),
            rs.getLong("base_amount_minor"),
            rs.getLong("quote_amount_minor"),
            rs.getLong("fee_minor"),
            rs.getBigDecimal("rate"),
            instant(rs, "expires_at"),
            instant(rs, "consumed_at"),
            instant(rs, "created_at"));

    private static final RowMapper<ProviderEventRow> PROVIDER_EVENT_MAPPER = (rs, ignored) ->
            new ProviderEventRow(
                    rs.getString("provider_event_id"),
                    rs.getString("payload_hash"),
                    rs.getString("payload_json"),
                    rs.getString("event_type"),
                    nullableUuid(rs, "wallet_id"),
                    rs.getString("currency"),
                    (Long) rs.getObject("amount_minor"),
                    rs.getString("provider_reference"),
                    rs.getString("status"),
                    rs.getString("error_message"),
                    instant(rs, "received_at"),
                    instant(rs, "processed_at"));

    private static final RowMapper<ReconciliationExceptionRow> RECONCILIATION_MAPPER =
            (rs, ignored) -> new ReconciliationExceptionRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("business_date", LocalDate.class),
                    rs.getString("category"),
                    rs.getString("provider_reference"),
                    rs.getString("currency"),
                    (Long) rs.getObject("provider_amount_minor"),
                    (Long) rs.getObject("local_amount_minor"),
                    rs.getString("status"),
                    instant(rs, "created_at"));

    private static final RowMapper<AuditRow> AUDIT_MAPPER = (rs, ignored) -> new AuditRow(
            rs.getObject("id", UUID.class),
            rs.getString("actor"),
            rs.getString("action"),
            rs.getString("entity_type"),
            rs.getString("entity_id"),
            rs.getString("reason"),
            rs.getString("before_json"),
            rs.getString("after_json"),
            instant(rs, "created_at"));

    public record UserRow(
            UUID id,
            String email,
            String status,
            String kycStatus,
            Instant createdAt) {
    }

    public record WalletRow(UUID id, UUID userId, String status, Instant createdAt) {
    }

    public record AccountRow(
            UUID id,
            UUID walletId,
            String code,
            String currency,
            AccountType type,
            long postedBalanceMinor,
            long availableBalanceMinor,
            long version,
            Instant createdAt) {
    }

    public record TransactionRow(
            UUID id,
            TransactionType type,
            String status,
            String reference,
            String idempotencyKey,
            String requestFingerprint,
            UUID reversesTransactionId,
            String metadataJson,
            Instant createdAt) {
    }

    public record EntryRow(
            UUID id,
            UUID transactionId,
            UUID accountId,
            String currency,
            long amountMinor,
            Instant createdAt) {
    }

    public record OutboxRow(
            UUID id,
            UUID aggregateId,
            String eventType,
            String payloadJson,
            Instant createdAt,
            Instant publishedAt) {
    }

    public record FxQuoteRow(
            UUID id,
            UUID walletId,
            String baseCurrency,
            String quoteCurrency,
            long baseAmountMinor,
            long quoteAmountMinor,
            long feeMinor,
            BigDecimal rate,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt) {
    }

    public record ProviderEventRow(
            String providerEventId,
            String payloadHash,
            String payloadJson,
            String eventType,
            UUID walletId,
            String currency,
            Long amountMinor,
            String providerReference,
            String status,
            String errorMessage,
            Instant receivedAt,
            Instant processedAt) {
    }

    public record LocalDepositRow(String reference, String currency, long amountMinor) {
    }

    public record ReconciliationExceptionRow(
            UUID id,
            LocalDate businessDate,
            String category,
            String providerReference,
            String currency,
            Long providerAmountMinor,
            Long localAmountMinor,
            String status,
            Instant createdAt) {
    }

    public record AuditRow(
            UUID id,
            String actor,
            String action,
            String entityType,
            String entityId,
            String reason,
            String beforeJson,
            String afterJson,
            Instant createdAt) {
    }

    public record StatementCursorEntry(Instant createdAt, UUID entryId) {
    }
}
