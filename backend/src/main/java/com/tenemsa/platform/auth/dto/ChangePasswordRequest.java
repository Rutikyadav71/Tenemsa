package com.tenemsa.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code refreshToken} is optional: when sent, that device stays signed in and every other device is signed out. */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password.") String currentPassword,
        @NotBlank(message = "Enter a new password.") @Size(max = 100, message = "Passwords can be up to 100 characters.") String newPassword,
        String refreshToken) {}
