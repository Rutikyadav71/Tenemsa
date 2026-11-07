package com.tenemsa.platform.platformadmin.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

/** Platform-only tokens and secret encryption, keyed by PLATFORM_JWT_SECRET (never the company JWT secret). */
@Component
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformCrypto {

    private final byte[] secret;
    private final long tokenMinutes;

    public PlatformCrypto(@Value("${platform.jwt-secret:}") String secret,
                          @Value("${platform.token-minutes:30}") long tokenMinutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("PLATFORM_ENABLED is true but PLATFORM_JWT_SECRET is missing or shorter than 32 characters. "
                    + "Generate one with: openssl rand -base64 48");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenMinutes = tokenMinutes;
    }

    public Date expiry() { return new Date(System.currentTimeMillis() + tokenMinutes * 60_000L); }

    public String issue(long adminId, Date expires) {
        return Jwts.builder().setSubject(String.valueOf(adminId)).claim("typ", "platform")
                .setIssuedAt(new Date()).setExpiration(expires)
                .signWith(Keys.hmacShaKeyFor(secret), SignatureAlgorithm.HS256).compact();
    }

    /** @return the admin id, or null if the token is not a valid, unexpired platform token. */
    public Long adminIdOf(String token) {
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret)).build().parseClaimsJws(token).getBody();
            if (!"platform".equals(c.get("typ", String.class)) || c.getExpiration().before(new Date())) return null;
            return Long.valueOf(c.getSubject());
        } catch (Exception e) { return null; }
    }

    private SecretKeySpec aesKey() throws Exception {
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret), "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public String decrypt(String enc) {
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Could not read the stored 2FA secret. Was PLATFORM_JWT_SECRET changed?", e); }
    }
}
