package com.portfolio.ledger.api;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import com.portfolio.ledger.domain.LedgerModels.HealthInvariantView;
import com.portfolio.ledger.domain.LedgerModels.ReconciliationResult;
import com.portfolio.ledger.domain.LedgerModels.SettlementRow;
import com.portfolio.ledger.repository.LedgerRepository.AuditRow;
import com.portfolio.ledger.service.ReconciliationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final ReconciliationService reconciliationService;

    public AdminController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/reconciliation/run")
    ReconciliationResult run(
            @RequestHeader("X-Audit-Reason") String reason,
            @Valid @RequestBody ReconciliationRequest request,
            Principal principal) {
        return reconciliationService.run(
                request.businessDate(),
                request.settlements() == null ? List.of() : request.settlements(),
                principal.getName(),
                reason);
    }

    @GetMapping("/reconciliation/{businessDate}/report.csv")
    ResponseEntity<String> csv(@PathVariable LocalDate businessDate) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reconciliation-" + businessDate + ".csv\"")
                .body(reconciliationService.csvReport(businessDate));
    }

    @GetMapping("/invariants")
    HealthInvariantView invariants() {
        return reconciliationService.invariants();
    }

    @GetMapping("/audit-logs")
    List<AuditRow> auditLogs(@RequestParam(defaultValue = "100") int limit) {
        return reconciliationService.auditLogs(limit);
    }

    public record ReconciliationRequest(
            @NotNull LocalDate businessDate,
            List<SettlementRow> settlements) {
    }
}
