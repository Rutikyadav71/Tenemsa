package com.tenemsa.platform.connections.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Encrypts saved sign-in tokens (AES-256-GCM) and signs the short "state" value used while signing in to an app.
 * Keyed by CONNECTIONS_SECRET. Without it the connected-apps feature stays off, so tokens are never stored in the clear.
 */
@Component
public class ConnectionCrypto {

    private static final String PREFIX = "v2.";

    private final byte[] secret;
    private final String keyId;
    /** Older secrets, still accepted for reading so a key change does not strand saved sign-ins. */
    private final java.util.Map<String, byte[]> older = new java.util.HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public ConnectionCrypto(@Value("${connections.secret:}") String secret,
                            @Value("${connections.previous-secrets:}") String previous) {
        this.secret = secret == null || secret.length() < 32 ? null : secret.getBytes(StandardCharsets.UTF_8);
        this.keyId = this.secret == null ? null : idOf(this.secret);
        if (previous != null) {
            for (String p : previous.split(",")) {
                String t = p.strip();
                if (t.length() >= 32) { byte[] b = t.getBytes(StandardCharsets.UTF_8); older.put(idOf(b), b); }
            }
        }
    }

    private static String idOf(byte[] key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key)).substring(0, 8); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static byte[] aesKey(byte[] secret) {
        try { return MessageDigest.getInstance("SHA-256").digest(secret); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** True when a stored value was written with an older secret (or before keys had ids) and should be re-saved. */
    public boolean needsRotation(String enc) { return secret != null && enc != null && !enc.startsWith(PREFIX + keyId + "."); }

    /** PKCE (RFC 7636): the verifier is derived from the signed state, so nothing has to be stored between the two steps. */
    public String pkceVerifier(String state) {
        need();
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(("pkce|" + state).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public String pkceChallenge(String verifier) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public boolean isReady() { return secret != null; }

    private void need() { if (secret == null) throw new ConnectionException(503, "Connected apps are not set up on this server yet."); }

    public String encrypt(String plain) {
        need();
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey(secret), "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + keyId + "." + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) { throw new IllegalStateException("Could not encrypt.", e); }
    }

    public String decrypt(String enc) {
        need();
        try {
            byte[] key = secret;
            String body = enc;
            if (enc.startsWith(PREFIX)) {
                int dot = enc.indexOf('.', PREFIX.length());
                String id = enc.substring(PREFIX.length(), dot);
                body = enc.substring(dot + 1);
                key = id.equals(keyId) ? secret : older.get(id);
                if (key == null) throw new IllegalStateException("unknown key");
            }
            byte[] all = Base64.getDecoder().decode(body);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey(key), "AES"), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new ConnectionException(409, "The saved sign-in could not be read. Please reconnect the app."); }
    }

    /** payload + "." + signature, so a forged or edited value is rejected. */
    public String sign(String payload) {
        need();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + mac(payload);
    }

    /** @return the payload, or null if the signature is wrong. */
    public String verify(String signed) {
        if (secret == null || signed == null || !signed.contains(".")) return null;
        try {
            int i = signed.lastIndexOf('.');
            String payload = new String(Base64.getUrlDecoder().decode(signed.substring(0, i)), StandardCharsets.UTF_8);
            return MessageDigest.isEqual(mac(payload).getBytes(StandardCharsets.UTF_8), signed.substring(i + 1).getBytes(StandardCharsets.UTF_8)) ? payload : null;
        } catch (Exception e) { return null; }
    }

    private String mac(String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(m.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
