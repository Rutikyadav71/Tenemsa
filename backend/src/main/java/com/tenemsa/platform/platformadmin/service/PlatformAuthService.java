package com.tenemsa.platform.platformadmin.service;

import com.tenemsa.platform.platformadmin.model.PlatformAdmin;
import com.tenemsa.platform.platformadmin.repository.PlatformAdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Registration (only ever two accounts, only with the setup key) and sign-in (password + authenticator code,
 * lockout after repeated failures) for platform administrators.
 */
@Service
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformAuthService {

    static final int MAX_ADMINS = 2, MAX_FAILURES = 5, LOCK_MINUTES = 15;
    private static final String ISSUER = "Tenemsa Platform";

    private final PlatformAdminRepository admins;
    private final PlatformCrypto crypto;
    private final PlatformAuditService audit;
    private final PasswordEncoder encoder;
    private final String setupKey;
    private final String dummyHash;

    public PlatformAuthService(PlatformAdminRepository admins, PlatformCrypto crypto, PlatformAuditService audit,
                               PasswordEncoder encoder, @Value("${platform.setup-key:}") String setupKey) {
        this.admins = admins; this.crypto = crypto; this.audit = audit; this.encoder = encoder;
        this.setupKey = setupKey == null ? "" : setupKey;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public Map<String, Object> status() {
        return Map.of("registrationOpen", registrationOpen());
    }

    private boolean registrationOpen() { return !setupKey.isBlank() && admins.count() < MAX_ADMINS; }

    /** Password rules for the platform: stricter than for companies (12+ characters). */
    static String passwordProblem(String p) {
        if (p == null || p.length() < 12) return "Use at least 12 characters.";
        if (p.length() > 128) return "That password is too long.";
        if (!p.matches(".*[a-z].*") || !p.matches(".*[A-Z].*") || !p.matches(".*\\d.*")) return "Use upper case, lower case and a number.";
        return null;
    }

    /** Not one big transaction on purpose: when the database refuses a duplicate or a third account, that failure
     *  must reach the catch below as a clean error, not leave an outer transaction marked rollback-only. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Map<String, Object> register(String name, String email, String password, String key, String ip) {
        if (!registrationOpen()) throw new PlatformException(403, "Registration is closed.");
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), setupKey.getBytes(StandardCharsets.UTF_8))) {
            audit.record(null, email, "REGISTER_REFUSED", null, null, "Wrong setup key", ip);
            throw new PlatformException(403, "The setup key is not correct.");
        }
        String n = name == null ? "" : name.trim();
        String e = email == null ? "" : email.trim().toLowerCase();
        if (n.isEmpty() || n.length() > 150) throw new PlatformException(400, "Enter your name.");
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || e.length() > 255) throw new PlatformException(400, "Enter a valid email address.");
        String problem = passwordProblem(password);
        if (problem != null) throw new PlatformException(400, problem);

        if (admins.findByEmailIgnoreCase(e).isPresent()) throw new PlatformException(403, "Registration is closed or that email is already registered.");

        Set<Integer> used = new HashSet<>();
        for (PlatformAdmin a : admins.findAll()) used.add(a.getSlot());
        int slot = !used.contains(1) ? 1 : !used.contains(2) ? 2 : 0;
        if (slot == 0) throw new PlatformException(403, "Registration is closed.");

        String secret = TotpService.newSecret();
        PlatformAdmin a = new PlatformAdmin();
        a.setSlot(slot); a.setName(n); a.setEmail(e);
        a.setPasswordHash(encoder.encode(password));
        a.setTotpSecretEnc(crypto.encrypt(secret));
        try {
            admins.saveAndFlush(a);   // the unique slot (1 or 2) and email constraints are the last line of defence
        } catch (DataIntegrityViolationException ex) {
            throw new PlatformException(403, "Registration is closed or that email is already registered.");
        }
        audit.record(a.getId(), e, "REGISTERED", null, null, "Platform administrator " + slot + " registered", ip);
        return setupInfo(a, secret);
    }

    private Map<String, Object> setupInfo(PlatformAdmin a, String secret) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "TOTP_SETUP");
        m.put("secret", secret);
        m.put("otpauthUri", TotpService.uri(ISSUER, a.getEmail(), secret));
        return m;
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public Map<String, Object> login(String email, String password, String code, String ip) {
        PlatformAdmin a = checkPassword(email, password, ip);
        if (!a.isTotpEnabled()) return setupInfo(a, crypto.decrypt(a.getTotpSecretEnc()));
        if (code == null || code.isBlank()) return Map.of("status", "CODE_REQUIRED");
        return finishWithCode(a, code, ip);
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public Map<String, Object> confirmTwoFactor(String email, String password, String code, String ip) {
        PlatformAdmin a = checkPassword(email, password, ip);
        if (a.isTotpEnabled()) throw new PlatformException(400, "Two-factor sign-in is already set up. Sign in with your code.");
        Map<String, Object> out = finishWithCode(a, code, ip);
        a.setTotpEnabled(true);
        admins.save(a);
        audit.record(a.getId(), a.getEmail(), "TWO_FACTOR_ENABLED", null, null, "Authenticator app confirmed", ip);
        return out;
    }

    private PlatformAdmin checkPassword(String email, String password, String ip) {
        String e = email == null ? "" : email.trim().toLowerCase();
        Optional<PlatformAdmin> found = admins.findByEmailIgnoreCase(e);
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, dummyHash);   // same time as a real check
            audit.record(null, e, "LOGIN_FAILED", null, null, "Unknown email", ip);
            throw new PlatformException(401, "Invalid email, password or code.");
        }
        PlatformAdmin a = found.get();
        if (a.getLockedUntil() != null && a.getLockedUntil().isAfter(LocalDateTime.now())) {
            long mins = Math.max(1, java.time.Duration.between(LocalDateTime.now(), a.getLockedUntil()).toMinutes() + 1);
            throw new PlatformException(429, "Too many failed attempts. Try again in " + mins + " minute" + (mins == 1 ? "" : "s") + ".");
        }
        if (password == null || !encoder.matches(password, a.getPasswordHash())) {
            fail(a, "Wrong password", ip);
            throw new PlatformException(401, "Invalid email, password or code.");
        }
        return a;
    }

    private void fail(PlatformAdmin a, String why, String ip) {
        a.setFailedAttempts(a.getFailedAttempts() + 1);
        if (a.getFailedAttempts() >= MAX_FAILURES) {
            a.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            a.setFailedAttempts(0);
            audit.record(a.getId(), a.getEmail(), "ACCOUNT_LOCKED", null, null, "Locked for " + LOCK_MINUTES + " minutes after repeated failures", ip);
        }
        admins.save(a);
        audit.record(a.getId(), a.getEmail(), "LOGIN_FAILED", null, null, why, ip);
    }

    private Map<String, Object> finishWithCode(PlatformAdmin a, String code, String ip) {
        Long step = TotpService.verify(crypto.decrypt(a.getTotpSecretEnc()), code, a.getLastTotpStep());
        if (step == null) {
            fail(a, "Wrong authenticator code", ip);
            throw new PlatformException(401, "Invalid email, password or code.");
        }
        a.setLastTotpStep(step);
        a.setFailedAttempts(0);
        a.setLockedUntil(null);
        a.setLastLoginAt(LocalDateTime.now());
        admins.save(a);
        audit.record(a.getId(), a.getEmail(), "LOGIN", null, null, "Signed in", ip);
        return tokenFor(a);
    }

    public Map<String, Object> tokenFor(PlatformAdmin a) {
        Date exp = crypto.expiry();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "OK");
        m.put("token", crypto.issue(a.getId(), exp));
        m.put("expiresAt", exp.toInstant().toString());
        m.put("admin", adminView(a));
        return m;
    }

    public static Map<String, Object> adminView(PlatformAdmin a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId()); m.put("name", a.getName()); m.put("email", a.getEmail());
        m.put("slot", a.getSlot()); m.put("twoFactor", a.isTotpEnabled());
        m.put("lastLoginAt", a.getLastLoginAt()); m.put("createdAt", a.getCreatedAt());
        return m;
    }

    public Optional<PlatformAdmin> find(Long id) { return id == null ? Optional.empty() : admins.findById(id); }
    public List<PlatformAdmin> all() { return admins.findAll(); }
}
