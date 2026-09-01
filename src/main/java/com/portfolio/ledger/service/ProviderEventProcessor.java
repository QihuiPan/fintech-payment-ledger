package com.portfolio.ledger.service;

import java.time.Clock;
import java.time.Instant;

import com.portfolio.ledger.repository.LedgerRepository;
import com.portfolio.ledger.repository.LedgerRepository.ProviderEventRow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderEventProcessor {
    private final PaymentService paymentService;
    private final LedgerRepository repository;
    private final Clock clock = Clock.systemUTC();

    public ProviderEventProcessor(PaymentService paymentService, LedgerRepository repository) {
        this.paymentService = paymentService;
        this.repository = repository;
    }

    @Transactional
    public void process(ProviderEventRow event) {
        paymentService.deposit(
                event.walletId(),
                event.currency(),
                event.amountMinor(),
                event.providerReference(),
                "provider:" + event.providerEventId());
        repository.markProviderEvent(event.providerEventId(), "PROCESSED", null, Instant.now(clock));
    }
}
