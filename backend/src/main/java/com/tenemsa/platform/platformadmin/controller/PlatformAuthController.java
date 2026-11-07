package com.tenemsa.platform.platformadmin.controller;

import com.tenemsa.platform.platformadmin.service.PlatformAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public sign-in and the one-time registration of the two platform administrators. */
@RestController
@RequestMapping("/api/platform/auth")
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformAuthController {

    public record RegisterRequest(String name, String email, String password, String setupKey) {}
    public record LoginRequest(String email, String password, String code) {}

    private final PlatformAuthService auth;
    public PlatformAuthController(PlatformAuthService auth) { this.auth = auth; }

    static String ip(HttpServletRequest r) { return r.getRemoteAddr(); }

    @GetMapping("/status")
    public Map<String, Object> status() { return auth.status(); }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest r, HttpServletRequest req) {
        return ResponseEntity.status(201).body(auth.register(r.name(), r.email(), r.password(), r.setupKey(), ip(req)));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest r, HttpServletRequest req) {
        return auth.login(r.email(), r.password(), r.code(), ip(req));
    }

    @ExceptionHandler(com.tenemsa.platform.platformadmin.service.PlatformException.class)
    public ResponseEntity<?> handle(com.tenemsa.platform.platformadmin.service.PlatformException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    /** First sign-in: prove the authenticator app works. Turns two-factor on and signs in. */
    @PostMapping("/confirm-2fa")
    public Map<String, Object> confirm(@RequestBody LoginRequest r, HttpServletRequest req) {
        return auth.confirmTwoFactor(r.email(), r.password(), r.code(), ip(req));
    }
}
