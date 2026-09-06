package com.portfolio.ledger.api;

import java.util.UUID;

import com.portfolio.ledger.domain.LedgerModels.FxQuoteView;
import com.portfolio.ledger.domain.LedgerModels.TransactionView;
import com.portfolio.ledger.service.PaymentService;
import com.portfolio.ledger.service.WalletAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentService paymentService;
    private final WalletAccessService walletAccessService;

    public PaymentController(PaymentService paymentService, WalletAccessService walletAccessService) {
        this.paymentService = paymentService;
        this.walletAccessService = walletAccessService;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionView deposit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositRequest request,
            Authentication authentication) {
        walletAccessService.requireWalletAccess(request.walletId(), authentication);
        return paymentService.deposit(
                request.walletId(),
                request.currency(),
                request.amountMinor(),
                request.providerReference(),
                idempotencyKey,
                authentication.getName());
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionView transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        walletAccessService.requireWalletAccess(request.senderWalletId(), authentication);
        return paymentService.transfer(
                request.senderWalletId(),
                request.recipientWalletId(),
                request.currency(),
                request.amountMinor(),
                idempotencyKey,
                authentication.getName());
    }

    @PostMapping("/fx/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    FxQuoteView quote(@Valid @RequestBody FxQuoteRequest request, Authentication authentication) {
        walletAccessService.requireWalletAccess(request.walletId(), authentication);
        return paymentService.quote(
                request.walletId(),
                request.baseCurrency(),
                request.quoteCurrency(),
                request.baseAmountMinor());
    }

    @PostMapping("/conversions")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionView convert(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConversionRequest request,
            Authentication authentication) {
        walletAccessService.requireQuoteAccess(request.quoteId(), authentication);
        return paymentService.convert(request.quoteId(), idempotencyKey, authentication.getName());
    }

    @PostMapping("/transactions/{transactionId}/reversals")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionView reverse(
            @PathVariable UUID transactionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReversalRequest request,
            Authentication authentication) {
        walletAccessService.requireTransactionAccess(transactionId, authentication);
        return paymentService.reverse(
                transactionId, request.reason(), idempotencyKey, authentication.getName());
    }

    @GetMapping("/transactions/{transactionId}")
    TransactionView transaction(@PathVariable UUID transactionId, Authentication authentication) {
        walletAccessService.requireTransactionAccess(transactionId, authentication);
        return paymentService.getTransaction(transactionId);
    }

    public record DepositRequest(
            @NotNull UUID walletId,
            @NotBlank String currency,
            @Positive long amountMinor,
            @NotBlank String providerReference) {
    }

    public record TransferRequest(
            @NotNull UUID senderWalletId,
            @NotNull UUID recipientWalletId,
            @NotBlank String currency,
            @Positive long amountMinor) {
    }

    public record FxQuoteRequest(
            @NotNull UUID walletId,
            @NotBlank String baseCurrency,
            @NotBlank String quoteCurrency,
            @Positive long baseAmountMinor) {
    }

    public record ConversionRequest(@NotNull UUID quoteId) {
    }

    public record ReversalRequest(@NotBlank String reason) {
    }
}
