package com.portfolio.ledger.api;

import java.util.List;
import java.util.UUID;

import com.portfolio.ledger.domain.LedgerModels.StatementPage;
import com.portfolio.ledger.domain.LedgerModels.WalletView;
import com.portfolio.ledger.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WalletView create(@Valid @RequestBody CreateWalletRequest request) {
        return walletService.create(request.email(), request.currencies());
    }

    @GetMapping("/{walletId}/balances")
    WalletView balances(@PathVariable UUID walletId) {
        return walletService.get(walletId);
    }

    @GetMapping("/{walletId}/statement")
    StatementPage statement(
            @PathVariable UUID walletId,
            @RequestParam String currency,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return walletService.statement(walletId, currency, cursor, limit);
    }

    public record CreateWalletRequest(
            @NotNull @Email String email,
            @NotEmpty List<String> currencies) {
    }
}
