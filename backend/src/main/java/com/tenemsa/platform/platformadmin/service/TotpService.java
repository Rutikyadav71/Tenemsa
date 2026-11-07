package com.tenemsa.platform.platformadmin.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Time-based one-time passwords (RFC 6238: SHA-1, 6 digits, 30 seconds), as used by authenticator apps. */
public final class TotpService {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final long STEP_SECONDS = 30;

    private TotpService() {}

    public static String newSecret() {
        byte[] b = new byte[20];
        new SecureRandom().nextBytes(b);
        return base32(b);
    }

    public static long currentStep() { return System.currentTimeMillis() / 1000 / STEP_SECONDS; }

    /** The code an authenticator app shows for this secret during the given 30-second step. */
    public static String code(String secret, long step) {
        try {
            byte[] key = decode32(secret);
            byte[] msg = new byte[8];
            for (int i = 7, s = 0; i >= 0; i--, s += 8) msg[i] = (byte) (step >>> s);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(msg);
            int o = h[h.length - 1] & 0x0F;
            int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /**
     * Accepts the current step and one either side (clock drift). A step that was already used
     * (lastUsedStep) is refused, so a code cannot be replayed. Returns the matched step, or null.
     */
    public static Long verify(String secret, String code, Long lastUsedStep) {
        if (code == null) return null;
        String c = code.replaceAll("\\s", "");
        if (!c.matches("\\d{6}")) return null;
        long now = currentStep();
        for (long s = now - 1; s <= now + 1; s++) {
            if (lastUsedStep != null && s <= lastUsedStep) continue;
            if (java.security.MessageDigest.isEqual(code(secret, s).getBytes(StandardCharsets.UTF_8), c.getBytes(StandardCharsets.UTF_8))) return s;
        }
        return null;
    }

    public static String uri(String issuer, String account, String secret) {
        String i = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String a = URLEncoder.encode(account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + i + ":" + a + "?secret=" + secret + "&issuer=" + i + "&algorithm=SHA1&digits=6&period=30";
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) { sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] decode32(String s) {
        String in = s.replace("=", "").replace(" ", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char ch : in.toCharArray()) {
            int v = ALPHABET.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("Bad secret");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) { out.write((buffer >> (bits - 8)) & 0xFF); bits -= 8; }
        }
        return out.toByteArray();
    }
}
