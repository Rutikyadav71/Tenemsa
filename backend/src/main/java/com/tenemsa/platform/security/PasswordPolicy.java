package com.tenemsa.platform.security;

import java.util.regex.Pattern;

/**
 * Centralized password strength rule so registration, admin-creation, and
 * password-reset all enforce the same policy instead of each hand-rolling
 * (or forgetting) their own check — the pre-Phase-2 code only checked
 * password length inconsistently, and only on the frontend in some forms,
 * meaning the backend itself never actually enforced anything.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    private PasswordPolicy() {}

    /** Returns null if the password is acceptable, or a human-readable reason if not. */
    public static String validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters.";
        }
        if (!UPPERCASE.matcher(password).find()) {
            return "Password must include at least one uppercase letter.";
        }
        if (!LOWERCASE.matcher(password).find()) {
            return "Password must include at least one lowercase letter.";
        }
        if (!DIGIT.matcher(password).find()) {
            return "Password must include at least one number.";
        }
        return null;
    }

    public static boolean isValid(String password) {
        return validate(password) == null;
    }
}
