package com.portfolio.ledger.config;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final int REQUESTS_PER_MINUTE = 120;
    private static final int WEBHOOK_REQUESTS_PER_MINUTE = 300;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMinute = new AtomicLong(-1);
    private final Clock clock = Clock.systemUTC();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long minute = Instant.now(clock).getEpochSecond() / 60;
        cleanupExpiredWindows(minute);
        boolean webhook = request.getRequestURI().equals("/api/provider/webhooks");
        String principal = request.getUserPrincipal() == null
                ? request.getRemoteAddr()
                : request.getUserPrincipal().getName();
        String client = (webhook ? "webhook:" : "api:") + principal;
        Window window = windows.compute(client, (ignored, current) -> {
            if (current == null || current.minute != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, current.count + 1);
        });

        int limit = webhook ? WEBHOOK_REQUESTS_PER_MINUTE : REQUESTS_PER_MINUTE;
        if (window.count > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void cleanupExpiredWindows(long minute) {
        long previous = lastCleanupMinute.get();
        if (previous == minute || !lastCleanupMinute.compareAndSet(previous, minute)) {
            return;
        }
        windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
    }

    private record Window(long minute, int count) {
    }
}
