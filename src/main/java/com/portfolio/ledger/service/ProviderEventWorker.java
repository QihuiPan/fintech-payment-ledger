package com.portfolio.ledger.service;

import com.portfolio.ledger.repository.LedgerRepository.ProviderEventRow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProviderEventWorker {
    private final ProviderWebhookService webhookService;
    private final ProviderEventProcessor processor;

    public ProviderEventWorker(
            ProviderWebhookService webhookService,
            ProviderEventProcessor processor) {
        this.webhookService = webhookService;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${ledger.provider.worker-delay-milliseconds:1000}")
    public void runOnce() {
        for (ProviderEventRow event : webhookService.pending(20)) {
            try {
                processor.process(event);
            } catch (RuntimeException failure) {
                webhookService.markFailed(event.providerEventId(), failure);
            }
        }
    }
}
