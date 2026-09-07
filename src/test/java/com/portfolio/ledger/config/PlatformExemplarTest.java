package com.portfolio.ledger.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformExemplarTest {
    @Test
    void emitsCurrentRequestTraceIdInOpenMetrics() throws Exception {
        var bridge = new PlatformExemplarConfiguration().platformSpanContext();
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), Clock.SYSTEM, bridge);
        var filter = new SloMetricsFilter(registry, "fixture", "team");
        var context = io.opentelemetry.api.trace.SpanContext.create("12345678901234567890123456789012", "1234567890123456", TraceFlags.getSampled(), TraceState.getDefault());
        try (var scope = Span.wrap(context).makeCurrent()) {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/fixture"), new MockHttpServletResponse(), (request, response) -> {});
        }
        String metrics = registry.scrape("application/openmetrics-text;version=1.0.0");
        assertTrue(metrics.contains("trace_id=\"12345678901234567890123456789012\""), metrics);
        registry.close();
    }
}
