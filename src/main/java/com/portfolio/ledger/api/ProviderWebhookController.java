package com.portfolio.ledger.api;

import com.portfolio.ledger.service.ProviderWebhookService;
import com.portfolio.ledger.service.ProviderWebhookService.WebhookReceipt;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
public class ProviderWebhookController {
    private final ProviderWebhookService webhookService;

    public ProviderWebhookController(ProviderWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhooks")
    ResponseEntity<WebhookReceipt> receive(
            @RequestHeader("X-Provider-Event-Id") String eventId,
            @RequestHeader("X-Provider-Timestamp") String timestamp,
            @RequestHeader("X-Provider-Signature") String signature,
            @RequestBody String rawPayload) {
        return ResponseEntity.accepted().body(
                webhookService.receive(eventId, timestamp, signature, rawPayload));
    }
}
