package com.tenemsa.platform.auth.service;

import com.tenemsa.platform.auth.model.RefreshToken;
import com.tenemsa.platform.auth.model.VerificationToken;
import com.tenemsa.platform.auth.repository.RefreshTokenRepository;
import com.tenemsa.platform.auth.repository.VerificationTokenRepository;
import com.tenemsa.platform.employee.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and validates the opaque (non-JWT) tokens used for refresh and
 * email-verification/password-reset flows. Only SHA-256 hashes are ever
 * persisted — same principle as password hashing: even a full DB leak
 * doesn't hand out usable tokens, since the raw value is never stored
 * anywhere after the response that issued it.
 */
@Service
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.refresh-token.expiration-days}")
    private int refreshTokenExpirationDays;

    @Value("${app.refresh-token.remember-me-expiration-days}")
    private int rememberMeExpirationDays;

    @Value("${app.verification-token.expiration-minutes}")
    private int verificationTokenExpirationMinutes;

    public TokenService(RefreshTokenRepository refreshTokenRepository,
                         VerificationTokenRepository verificationTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
    }

    // ---- Refresh tokens ----------------------------------------------

    public String issueRefreshToken(User user, String deviceLabel, boolean rememberMe) {
        String raw = randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(hash(raw));
        rt.setDeviceLabel(deviceLabel);
        int days = rememberMe ? rememberMeExpirationDays : refreshTokenExpirationDays;
        rt.setExpiresAt(LocalDateTime.now().plusDays(days));
        refreshTokenRepository.save(rt);
        return raw;
    }

    /** Validates a raw refresh token and returns the associated user, or empty if invalid/expired/revoked. */
    public Optional<RefreshToken> validateRefreshToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isValid);
    }

    /** Token rotation: the presented token is revoked and a new one issued, so a stolen-and-reused
     *  old token is a detectable signal (not implemented as an alarm yet, but the rotation itself
     *  already prevents silent indefinite reuse of a leaked token). */
    public String rotateRefreshToken(RefreshToken old) {
        old.setRevoked(true);
        refreshTokenRepository.save(old);
        return issueRefreshToken(old.getUser(), old.getDeviceLabel(), false);
    }

    public void revokeRefreshToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    /** Devices that can still refresh: not revoked and not expired. Newest first. */
    public java.util.List<RefreshToken> activeSessions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId).stream()
                .filter(t -> t.getExpiresAt().isAfter(now))
                .sorted(java.util.Comparator.comparing(RefreshToken::getCreatedAt).reversed()).toList();
    }

    /** Signs out one device. Returns false when that session is not this user's (or is already gone). */
    public boolean revokeSession(Long userId, Long sessionId) {
        return refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getUser().getId().equals(userId) && !t.isRevoked())
                .map(t -> { revokeRefreshToken(t); return true; }).orElse(false);
    }

    /** Signs out every device except the one whose raw token is given (null keeps none). */
    public void revokeAllExcept(Long userId, String keepRawToken) {
        String keep = keepRawToken == null || keepRawToken.isBlank() ? null : hash(keepRawToken);
        for (RefreshToken t : refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId)) {
            if (keep == null || !keep.equals(t.getTokenHash())) revokeRefreshToken(t);
        }
    }

    public void revokeAllRefreshTokensForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    // ---- Verification / reset tokens ----------------------------------

    public String issueVerificationToken(User user, VerificationToken.Purpose purpose) {
        String raw = randomToken();
        VerificationToken vt = new VerificationToken();
        vt.setUser(user);
        vt.setTokenHash(hash(raw));
        vt.setPurpose(purpose);
        vt.setExpiresAt(LocalDateTime.now().plusMinutes(verificationTokenExpirationMinutes));
        verificationTokenRepository.save(vt);
        return raw;
    }

    public Optional<VerificationToken> validateVerificationToken(String rawToken, VerificationToken.Purpose purpose) {
        return verificationTokenRepository.findByTokenHash(hash(rawToken))
                .filter(VerificationToken::isValid)
                .filter(vt -> vt.getPurpose() == purpose);
    }

    public void consumeVerificationToken(VerificationToken token) {
        token.setUsed(true);
        verificationTokenRepository.save(token);
    }

    // ---- Shared helpers (invitations reuse the same opaque-token scheme) --

    /** A fresh random token; only its hash is ever stored. */
    public String newRawToken() { return randomToken(); }

    public String hashToken(String raw) { return hash(raw); }

    // ---- Internals ------------------------------------------------------

    private String randomToken() {
        byte[] bytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e); // never happens on any real JVM
        }
    }
}
