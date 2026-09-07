package com.portfolio.ledger.config;

import java.io.IOException;
import java.time.Duration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exposes bounded SLO metrics for completed valid HTTP requests. */
@Component
public class SloMetricsFilter extends OncePerRequestFilter {
    private final Counter good;
    private final Counter bad;
    private final Timer latency;

    public SloMetricsFilter(MeterRegistry registry,
            @Value("${observe.service:ledger-worker}") String service,
            @Value("${observe.tenant:team-payments}") String tenant) {
        good = Counter.builder("http.requests").tags("service", service, "tenant", tenant, "status", "ok").register(registry);
        bad = Counter.builder("http.requests").tags("service", service, "tenant", tenant, "status", "error").register(registry);
        latency = Timer.builder("http.request.duration").tags("service", service, "tenant", tenant)
                .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(500), Duration.ofSeconds(1))
                .register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator") || request.getRequestURI().startsWith("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException failure) {
            failed = true;
            throw failure;
        } finally {
            int status = failed ? 500 : response.getStatus();
            if (status < 400 || status >= 500) {
                (status >= 500 ? bad : good).increment();
                latency.record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
        }
    }
}
