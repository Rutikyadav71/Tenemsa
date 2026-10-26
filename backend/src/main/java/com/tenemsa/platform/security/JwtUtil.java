package com.tenemsa.platform.security;

import com.tenemsa.platform.employee.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // Loaded from JWT_SECRET env var (see application.properties / .env.example).
    // Never hardcode this — a secret committed to source control means anyone
    // with repo access (or a public GitHub history) can forge valid tokens for
    // any user, including admins.
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:3600000}") // default 1 hour if not set
    private long expirationMs;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ✅ Generate JWT. companyId is embedded as a claim so every authenticated
    // request carries its tenant context for free — the alternative (looking
    // the user up by email on every request just to find their company) is a
    // DB round-trip per request purely to answer "which tenant is this?",
    // which doesn't scale once there are many companies hitting the API
    // concurrently. JwtFilter reads this claim once per request instead.
    public String generateToken(String email, Role role, Long companyId) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role.name())
                .claim("companyId", companyId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ✅ Extract email (username)
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // ✅ Validate token expiration
    public boolean isTokenValid(String token) {
        return !extractClaims(token)
                .getExpiration()
                .before(new Date());
    }

    // 🔒 Central claim extraction
    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    // jjwt/Jackson may deserialize the numeric claim as Integer or Long
    // depending on the value size, so this normalizes to Long defensively
    // rather than risking a ClassCastException on large IDs.
    public Long extractCompanyId(String token) {
        Object raw = extractClaims(token).get("companyId");
        if (raw == null) return null;
        return ((Number) raw).longValue();
    }

}
