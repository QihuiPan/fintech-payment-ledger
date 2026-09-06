package com.portfolio.ledger.service;

import java.util.UUID;

import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.repository.LedgerRepository;
import com.portfolio.ledger.repository.LedgerRepository.TransactionRow;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class WalletAccessService {
    private final LedgerRepository repository;

    public WalletAccessService(LedgerRepository repository) {
        this.repository = repository;
    }

    public void requireWalletAccess(UUID walletId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        String owner = repository.findWalletOwnerSubject(walletId)
                .orElseThrow(() -> DomainException.notFound(
                        "WALLET_NOT_FOUND", "Wallet does not exist"));
        requireOwner(owner, authentication, "WALLET_ACCESS_DENIED", "Wallet belongs to another API user");
    }

    public void requireQuoteAccess(UUID quoteId, Authentication authentication) {
        UUID walletId = repository.findQuoteWalletId(quoteId)
                .orElseThrow(() -> DomainException.notFound(
                        "FX_QUOTE_NOT_FOUND", "FX quote does not exist"));
        requireWalletAccess(walletId, authentication);
    }

    public void requireTransactionAccess(UUID transactionId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        TransactionRow transaction = repository.findTransaction(transactionId)
                .orElseThrow(() -> DomainException.notFound(
                        "TRANSACTION_NOT_FOUND", "Transaction does not exist"));
        requireOwner(
                transaction.initiatedBy(),
                authentication,
                "TRANSACTION_ACCESS_DENIED",
                "Transaction belongs to another API user");
    }

    private static void requireOwner(
            String owner,
            Authentication authentication,
            String code,
            String message) {
        if (authentication == null || !authentication.isAuthenticated()
                || !owner.equals(authentication.getName())) {
            throw DomainException.forbidden(code, message);
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
