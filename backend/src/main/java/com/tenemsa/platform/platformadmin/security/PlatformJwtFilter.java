package com.tenemsa.platform.platformadmin.security;

import com.tenemsa.platform.platformadmin.model.PlatformAdmin;
import com.tenemsa.platform.platformadmin.service.PlatformAuthService;
import com.tenemsa.platform.platformadmin.service.PlatformCrypto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Only for /api/platform/**. Drops whatever a company token set up and accepts nothing but a valid platform token,
 * so a company Owner can never reach the console and a platform admin is never treated as a company user.
 * Optionally answers only requests that come from this machine (PLATFORM_LOCALHOST_ONLY=true).
 */
@Component
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformJwtFilter extends OncePerRequestFilter {

    public record Principal(Long id, String email, String name) {}

    private final PlatformCrypto crypto;
    private final PlatformAuthService auth;
    private final boolean localhostOnly;

    public PlatformJwtFilter(PlatformCrypto crypto, PlatformAuthService auth,
                             @Value("${platform.localhost-only:false}") boolean localhostOnly) {
        this.crypto = crypto; this.auth = auth; this.localhostOnly = localhostOnly;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/platform/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (localhostOnly && !isLoopback(req.getRemoteAddr())) {
            res.setStatus(403);
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"The platform console only answers from the server itself.\"}");
            return;
        }
        SecurityContextHolder.clearContext();
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            Long id = crypto.adminIdOf(h.substring(7));
            PlatformAdmin a = id == null ? null : auth.find(id).orElse(null);
            boolean locked = a != null && a.getLockedUntil() != null && a.getLockedUntil().isAfter(LocalDateTime.now());
            if (a != null && a.isTotpEnabled() && !locked) {
                UsernamePasswordAuthenticationToken t = new UsernamePasswordAuthenticationToken(
                        new Principal(a.getId(), a.getEmail(), a.getName()), null, List.of(new SimpleGrantedAuthority("PLATFORM_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(t);
            }
        }
        chain.doFilter(req, res);
    }

    private static boolean isLoopback(String addr) {
        return "127.0.0.1".equals(addr) || "0:0:0:0:0:0:0:1".equals(addr) || "::1".equals(addr);
    }
}
