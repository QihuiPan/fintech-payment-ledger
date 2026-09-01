package com.portfolio.ledger.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.portfolio.ledger.config.LedgerProperties;
import com.portfolio.ledger.domain.DomainException;
import com.portfolio.ledger.repository.LedgerRepository;
import com.portfolio.ledger.repository.LedgerRepository.ProviderEventRow;
import com.portfolio.ledger.util.CryptoSupport;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderWebhookService {
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    private final LedgerRepository repository;
    private final LedgerProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock = Clock.systemUTC();

    public ProviderWebhookService(
            LedgerRepository repository,
            LedgerProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public WebhookReceipt receive(
            String providerEventId,
            String timestampHeader,
            String signatureHeader,
            String rawPayload) {
        String eventId = requireEventId(providerEventId);
        if (rawPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw DomainException.badRequest("PAYLOAD_TOO_LARGE", "Webhook payload exceeds 64 KiB");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (RuntimeException invalid) {
            throw DomainException.badRequest("INVALID_WEBHOOK_TIMESTAMP", "Webhook timestamp is invalid");
        }
        long nowEpoch = Instant.now(clock).getEpochSecond();
        if (Math.abs(nowEpoch - timestamp) > properties.provider().timestampToleranceSeconds()) {
            throw DomainException.forbidden("STALE_WEBHOOK", "Webhook timestamp is outside the allowed window");
        }
        String supplied = signatureHeader == null ? "" : signatureHeader.replaceFirst("^sha256=", "");
        String expected = CryptoSupport.hmacSha256(
                properties.provider().webhookSecret(),
                timestampHeader + "." + rawPayload);
        if (!CryptoSupport.constantTimeEquals(expected, supplied)) {
            throw DomainException.forbidden("INVALID_WEBHOOK_SIGNATURE", "Webhook signature is invalid");
        }

        ProviderPayload payload = parse(rawPayload);
        validatePayload(payload);
        Instant receivedAt = Instant.now(clock);
        boolean inserted = repository.insertProviderEvent(new ProviderEventRow(
                eventId,
                CryptoSupport.sha256(rawPayload),
                rawPayload,
                payload.type(),
                payload.walletId(),
                payload.currency(),
                payload.amountMinor(),
                payload.providerReference(),
                "RECEIVED",
                null,
                receivedAt,
                null));
        if (!inserted) {
            meterRegistry.counter("webhook_duplicate_total").increment();
            return new WebhookReceipt(eventId, "DUPLICATE");
        }
        return new WebhookReceipt(eventId, "RECEIVED");
    }

    public List<ProviderEventRow> pending(int limit) {
        return repository.findReceivedProviderEvents(limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId, Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        repository.markProviderEvent(
                eventId,
                "FAILED",
                message.substring(0, Math.min(500, message.length())),
                Instant.now(clock));
    }

    private ProviderPayload parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, ProviderPayload.class);
        } catch (JacksonException invalid) {
            throw DomainException.badRequest("INVALID_WEBHOOK_PAYLOAD", "Webhook payload is not valid JSON");
        }
    }

    private static void validatePayload(ProviderPayload payload) {
        if (!"deposit.succeeded".equals(payload.type())) {
            throw DomainException.badRequest("UNSUPPORTED_PROVIDER_EVENT", "Unsupported provider event type");
        }
        if (payload.walletId() == null || payload.amountMinor() == null || payload.amountMinor() <= 0) {
            throw DomainException.badRequest("INVALID_WEBHOOK_PAYLOAD", "Deposit fields are incomplete");
        }
        com.portfolio.ledger.domain.LedgerMath.normalizeCurrency(payload.currency());
        if (payload.providerReference() == null || payload.providerReference().isBlank()) {
            throw DomainException.badRequest("INVALID_WEBHOOK_PAYLOAD", "Provider reference is required");
        }
    }

    private static String requireEventId(String eventId) {
        String normalized = eventId == null ? "" : eventId.trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw DomainException.badRequest("INVALID_PROVIDER_EVENT_ID", "Provider event ID is invalid");
        }
        return normalized;
    }

    public record ProviderPayload(
            String type,
            UUID walletId,
            String currency,
            Long amountMinor,
            String providerReference) {
    }

    public record WebhookReceipt(String providerEventId, String status) {
    }
}
