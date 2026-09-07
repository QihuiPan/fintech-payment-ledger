package com.portfolio.ledger.config;

import io.opentelemetry.api.trace.Span;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Connects Prometheus exemplars to the active Java-agent span. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "observe.exemplars.enabled", havingValue = "true")
public class PlatformExemplarConfiguration {
    @Bean
    @Primary
    SpanContext platformSpanContext() {
        return new SpanContext() {
            public String getCurrentTraceId() {
                var context = Span.current().getSpanContext();
                return context.isValid() ? context.getTraceId() : null;
            }
            public String getCurrentSpanId() {
                var context = Span.current().getSpanContext();
                return context.isValid() ? context.getSpanId() : null;
            }
            public boolean isCurrentSpanSampled() {
                return Span.current().getSpanContext().isSampled();
            }
            public void markCurrentSpanAsExemplar() {
                Span.current().setAttribute("prometheus.exemplar", true);
            }
        };
    }
}
