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

    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public ConnectionCrypto(@Value("${connections.secret:}") String secret) {
        this.secret = secret == null || secret.length() < 32 ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isReady() { return secret != null; }

    private void need() { if (secret == null) throw new ConnectionException(503, "Connected apps are not set up on this server yet."); }

    public String encrypt(String plain) {
        need();
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret), "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) { throw new IllegalStateException("Could not encrypt.", e); }
    }

    public String decrypt(String enc) {
        need();
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret), "AES"), new GCMParameterSpec(128, all, 0, 12));
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
