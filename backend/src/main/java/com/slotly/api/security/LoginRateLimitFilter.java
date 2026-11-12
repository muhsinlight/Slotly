package com.slotly.api.security;

import com.slotly.api.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final int MAX_CLIENTS = 10_000;

    private final Map<String, AttemptWindow> clients = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxAttempts;
    private final long windowMillis;

    public LoginRateLimitFilter(
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${slotly.auth.rate-limit.max-attempts:10}") int maxAttempts,
        @Value("${slotly.auth.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMillis = Math.max(1, windowSeconds) * 1000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
        }

        return !LOGIN_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        long now = clock.millis();
        String clientAddress = request.getRemoteAddr();
        AttemptWindow window = clients.compute(clientAddress, (key, current) -> {
            if (current == null || now - current.startedAt() >= windowMillis) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(current.startedAt(), current.attempts() + 1);
        });

        boolean clientCapacityExceeded = false;
        if (clients.size() > MAX_CLIENTS) {
            clients.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= windowMillis);
            if (clients.size() > MAX_CLIENTS) {
                clients.remove(clientAddress);
                clientCapacityExceeded = true;
            }
        }

        if (window.attempts() > maxAttempts || clientCapacityExceeded) {
            long retrySeconds = Math.max(1, (window.startedAt() + windowMillis - now + 999) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", Long.toString(retrySeconds));
            objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS, "Login rate limit reached. Try again later.")
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private record AttemptWindow(long startedAt, int attempts) {
    }
}
