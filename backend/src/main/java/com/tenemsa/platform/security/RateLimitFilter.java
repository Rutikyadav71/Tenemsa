package com.tenemsa.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiting for the PUBLIC endpoints that can be abused
 * without an account: sign-in (password guessing), company registration (spam),
 * workspace lookup (enumeration), password-reset and verification mail (mail
 * bombing) and invitation links (token guessing).
 *
 * Limits are per client IP and per rule group. State is in memory, so with
 * several backend instances each enforces its own count; that is still a real
 * brake on abuse, and a shared store (Redis) is the upgrade path if the app is
 * ever scaled out.
 *
 * Behind a reverse proxy every request arrives from the proxy's address, which
 * would put all users in one bucket. Set app.rate-limit.trust-proxy=true there
 * (RATE_LIMIT_TRUST_PROXY=true) so the address the proxy appended to
 * X-Forwarded-For is used. Leave it false when the app is reachable directly,
 * because a client can forge that header.
 */
@Component
// Just after Spring Security's chain (order -100) so its CORS headers are already
// on the response and the browser can read the 429 body.
@Order(-99)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String path, String group, int max, long windowMs) {}

    private static final long MINUTE = 60_000L;
    private static final long TEN_MINUTES = 10 * MINUTE;
    private static final long HOUR = 60 * MINUTE;

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", "login", 10, MINUTE),
            new Rule("POST", "/api/admin/login", "login", 10, MINUTE),
            new Rule("POST", "/api/companies/register", "register", 5, HOUR),
            new Rule("GET", "/api/companies/lookup", "lookup", 30, MINUTE),
            new Rule("POST", "/api/auth/forgot-password", "mail", 5, TEN_MINUTES),
            new Rule("POST", "/api/auth/resend-verification", "mail", 5, TEN_MINUTES),
            new Rule("POST", "/api/auth/reset-password", "reset", 10, TEN_MINUTES),
            new Rule("GET", "/api/invitations/preview", "invite", 20, MINUTE),
            new Rule("POST", "/api/invitations/accept", "invite", 20, MINUTE)
    );

    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final class Window {
        long start;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = enabled ? match(request) : null;
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        String key = rule.group() + "|" + clientIp(request);
        long[] outcome = new long[]{0}; // retry-after seconds when blocked, else 0

        windows.compute(key, (k, w) -> {
            if (w == null || now - w.start >= rule.windowMs()) {
                w = new Window();
                w.start = now;
            }
            if (w.count >= rule.max()) {
                outcome[0] = Math.max(1, (w.start + rule.windowMs() - now + 999) / 1000);
            } else {
                w.count++;
            }
            return w;
        });

        if (windows.size() > CLEANUP_THRESHOLD) evictExpired(now);

        if (outcome[0] > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(outcome[0]));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"message\":\"Too many requests. Please try again in "
                    + outcome[0] + " seconds.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static Rule match(HttpServletRequest request) {
        String path = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) path = path.substring(ctx.length());
        for (Rule r : RULES) {
            if (r.method().equals(request.getMethod()) && r.path().equals(path)) return r;
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                // The LAST entry is the one our own proxy added; earlier ones are client-supplied.
                return parts[parts.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void evictExpired(long now) {
        long longest = HOUR;
        windows.entrySet().removeIf(e -> now - e.getValue().start >= longest);
    }
}
