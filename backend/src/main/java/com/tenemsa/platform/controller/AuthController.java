package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.*;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthContext authContext;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            User user = authService.login(request);
            AuthResponse resp = authService.issueAuthResponse(user, request.isRememberMe(), deviceLabel(httpRequest));
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    // Exchanges a valid refresh token for a new access token + rotated
    // refresh token. The frontend calls this transparently (via an axios
    // response interceptor, not user-initiated) whenever a request comes
    // back 401 with an expired access token — see frontend axiosInstance.ts.
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
        } catch (RuntimeException e) {
            return errorResponse(e);
        }
    }

    // Logs out the current device only — revokes just the refresh token
    // presented, leaving other devices' sessions untouched.
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // Logs out every device — revokes all refresh tokens for the
    // authenticated caller. Requires a valid access token (unlike /logout,
    // which only needs the refresh token itself) since this is a more
    // consequential action tied to "who is asking", not just "which token".
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAllDevices() {
        User user = userRepository.findByEmail(authContext.currentEmail())
                .orElseThrow(() -> new RuntimeException("Not authenticated"));
        authService.logoutAllDevices(user.getId());
        return ResponseEntity.ok(Map.of("message", "Signed out of all devices"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        // Same response regardless of whether the email exists — see
        // AuthService.requestPasswordReset for why.
        return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password updated. Please sign in again."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody VerifyEmailRequest request) {
        try {
            authService.verifyEmail(request.getToken());
            return ResponseEntity.ok(Map.of("message", "Email verified."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If that account needs verification, a new link has been sent."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        User user = userRepository.findByEmail(authContext.currentEmail())
                .orElseThrow(() -> new RuntimeException("Not authenticated"));
        return ResponseEntity.ok(authService.getCurrentUserProfile(user));
    }

    @GetMapping("/password-policy")
    public ResponseEntity<?> passwordPolicy() {
        return ResponseEntity.ok(Map.of(
                "minLength", 8,
                "requiresUppercase", true,
                "requiresLowercase", true,
                "requiresDigit", true
        ));
    }

    private ResponseEntity<?> errorResponse(RuntimeException e) {
        String msg = e.getMessage();
        if (msg != null && msg.startsWith("ACCOUNT_FROZEN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(msg);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(msg);
    }

    private String deviceLabel(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
