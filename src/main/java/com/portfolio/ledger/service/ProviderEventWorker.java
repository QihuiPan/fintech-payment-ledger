package com.portfolio.ledger.service;

import com.portfolio.ledger.repository.LedgerRepository.ProviderEventRow;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProviderEventWorker {
    private final ProviderWebhookService webhookService;
    private final ProviderEventProcessor processor;
    private final Counter completed;
    private final Counter failed;
    private final Timer duration;

    public ProviderEventWorker(
            ProviderWebhookService webhookService,
            ProviderEventProcessor processor, MeterRegistry registry) {
        this.webhookService = webhookService;
        this.processor = processor;
        completed = Counter.builder("worker.requests").tags("service", "ledger-worker", "tenant", "team-payments", "status", "ok").register(registry);
        failed = Counter.builder("worker.requests").tags("service", "ledger-worker", "tenant", "team-payments", "status", "error").register(registry);
        duration = Timer.builder("worker.request.duration").tags("service", "ledger-worker", "tenant", "team-payments")
                .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(500), Duration.ofSeconds(1)).register(registry);
    }

    @Scheduled(fixedDelayString = "${ledger.provider.worker-delay-milliseconds:1000}")
    public void runOnce() {
        for (ProviderEventRow event : webhookService.pending(20)) {
            long started = System.nanoTime();
            try {
                processor.process(event);
                completed.increment();
            } catch (RuntimeException failure) {
                failed.increment();
                webhookService.markFailed(event.providerEventId(), failure);
            } finally {
                duration.record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        }
    }
}
