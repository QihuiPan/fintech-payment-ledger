package com.portfolio.ledger.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SloMetricsFilterTest {
    @Test
    void countsOnlyValidTrafficAndKeepsBoundedLabels() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new SloMetricsFilter(registry, "service", "team");
        for (int status : new int[] {200, 400, 503}) {
            var request = new MockHttpServletRequest("GET", "/api/example");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(status));
        }
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(), (req, res) -> {});
        assertEquals(1.0, registry.get("http.requests").tag("status", "ok").counter().count());
        assertEquals(1.0, registry.get("http.requests").tag("status", "error").counter().count());
        assertEquals(2, registry.get("http.request.duration").timer().count());
        assertEquals(3, registry.get("http.requests").tag("status", "ok").counter().getId().getTags().size());
    }
}
