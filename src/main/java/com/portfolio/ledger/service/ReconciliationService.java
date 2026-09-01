package com.portfolio.ledger.service;

import static com.portfolio.ledger.domain.LedgerModels.HealthInvariantView;
import static com.portfolio.ledger.domain.LedgerModels.ReconciliationIssue;
import static com.portfolio.ledger.domain.LedgerModels.ReconciliationResult;
import static com.portfolio.ledger.domain.LedgerModels.SettlementRow;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.domain.LedgerMath;
import com.portfolio.ledger.repository.LedgerRepository;
import com.portfolio.ledger.repository.LedgerRepository.AuditRow;
import com.portfolio.ledger.repository.LedgerRepository.LocalDepositRow;
import com.portfolio.ledger.repository.LedgerRepository.ReconciliationExceptionRow;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final LedgerRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock = Clock.systemUTC();

    public ReconciliationService(
            LedgerRepository repository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ReconciliationResult run(
            LocalDate businessDate,
            List<SettlementRow> settlementRows,
            String actor,
            String reason) {
        if (businessDate == null) {
            throw DomainException.badRequest("BUSINESS_DATE_REQUIRED", "Business date is required");
        }
        String auditReason = requireReason(reason);
        Map<String, LocalDepositRow> local = repository.findLocalDeposits(businessDate);
        Set<String> providerReferences = new HashSet<>();
        List<ReconciliationIssue> issues = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        int missingLocally = 0;

        for (SettlementRow row : settlementRows) {
            validateSettlement(row);
            if (!providerReferences.add(row.providerReference())) {
                throw DomainException.badRequest(
                        "DUPLICATE_PROVIDER_REFERENCE",
                        "Settlement input contains a duplicate provider reference");
            }
            LocalDepositRow localRow = local.get(row.providerReference());
            if (localRow == null) {
                missingLocally++;
                issues.add(issue("MISSING_LOCALLY", row, null));
            } else if (localRow.amountMinor() != row.amountMinor()
                    || !localRow.currency().equals(LedgerMath.normalizeCurrency(row.currency()))) {
                mismatched++;
                issues.add(issue("AMOUNT_MISMATCH", row, localRow));
            } else {
                matched++;
            }
        }

        int missingAtProvider = 0;
        for (LocalDepositRow localRow : local.values()) {
            if (!providerReferences.contains(localRow.reference())) {
                missingAtProvider++;
                issues.add(new ReconciliationIssue(
                        "MISSING_AT_PROVIDER",
                        localRow.reference(),
                        localRow.currency(),
                        null,
                        localRow.amountMinor()));
            }
        }

        Instant now = Instant.now(clock);
        for (ReconciliationIssue issue : issues) {
            repository.insertReconciliationException(new ReconciliationExceptionRow(
                    UUID.randomUUID(),
                    businessDate,
                    issue.category(),
                    issue.providerReference(),
                    issue.currency(),
                    issue.providerAmountMinor(),
                    issue.localAmountMinor(),
                    "OPEN",
                    now));
        }

        UUID jobId = UUID.randomUUID();
        ReconciliationResult result = new ReconciliationResult(
                jobId,
                businessDate,
                matched,
                mismatched,
                missingLocally,
                missingAtProvider,
                issues);
        repository.insertAudit(new AuditRow(
                UUID.randomUUID(),
                actor,
                "RECONCILIATION_RUN",
                "RECONCILIATION_JOB",
                jobId.toString(),
                auditReason,
                null,
                json(result),
                now));
        meterRegistry.counter("reconciliation_run_total").increment();
        meterRegistry.summary("reconciliation_unmatched_total")
                .record(issues.size());
        return result;
    }

    public String csvReport(LocalDate businessDate) {
        StringBuilder csv = new StringBuilder(
                "business_date,category,provider_reference,currency,provider_amount_minor,local_amount_minor,status\n");
        for (ReconciliationExceptionRow row : repository.findReconciliationExceptions(businessDate)) {
            csv.append(row.businessDate()).append(',')
                    .append(csvValue(row.category())).append(',')
                    .append(csvValue(row.providerReference())).append(',')
                    .append(csvValue(row.currency())).append(',')
                    .append(row.providerAmountMinor() == null ? "" : row.providerAmountMinor()).append(',')
                    .append(row.localAmountMinor() == null ? "" : row.localAmountMinor()).append(',')
                    .append(csvValue(row.status())).append('\n');
        }
        return csv.toString();
    }

    public HealthInvariantView invariants() {
        return new HealthInvariantView(
                repository.countUnbalancedTransactions(),
                repository.countNegativeUserBalances(),
                repository.unpublishedOutboxByType());
    }

    public List<AuditRow> auditLogs(int requestedLimit) {
        return repository.findAuditLogs(Math.max(1, Math.min(requestedLimit, 200)));
    }

    private static void validateSettlement(SettlementRow row) {
        if (row.providerReference() == null || row.providerReference().isBlank()) {
            throw DomainException.badRequest("INVALID_SETTLEMENT", "Provider reference is required");
        }
        LedgerMath.normalizeCurrency(row.currency());
        if (row.amountMinor() <= 0) {
            throw DomainException.badRequest("INVALID_SETTLEMENT", "Settlement amount must be positive");
        }
    }

    private static ReconciliationIssue issue(
            String category,
            SettlementRow provider,
            LocalDepositRow local) {
        return new ReconciliationIssue(
                category,
                provider.providerReference(),
                LedgerMath.normalizeCurrency(provider.currency()),
                provider.amountMinor(),
                local == null ? null : local.amountMinor());
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 3 || normalized.length() > 500) {
            throw DomainException.badRequest(
                    "AUDIT_REASON_REQUIRED",
                    "X-Audit-Reason must contain between 3 and 500 characters");
        }
        return normalized;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize audit data", exception);
        }
    }

    private static String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}
