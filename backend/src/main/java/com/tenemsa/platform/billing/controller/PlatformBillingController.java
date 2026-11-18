package com.tenemsa.platform.billing.controller;

import com.tenemsa.platform.billing.service.PlatformBillingService;
import com.tenemsa.platform.platformadmin.security.PlatformJwtFilter;
import com.tenemsa.platform.platformadmin.service.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Plan management for the platform console. Exists only when the console is switched on. */
@RestController
@RequestMapping("/api/platform/billing")
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformBillingController {

    private final PlatformBillingService service;

    public PlatformBillingController(PlatformBillingService service) { this.service = service; }

    private static PlatformJwtFilter.Principal me() {
        return (PlatformJwtFilter.Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public Map<String, Object> overview() { return service.overview(); }

    @GetMapping("/revenue")
    public Map<String, Object> revenue() { return service.revenue(); }

    @PutMapping("/plans/{code}")
    public Map<String, Object> update(@PathVariable String code, @RequestBody PlatformBillingService.PlanUpdate u, HttpServletRequest req) {
        return service.update(code, u, me().id(), me().email(), req.getRemoteAddr());
    }

    @PostMapping("/plans/{code}/razorpay")
    public Map<String, Object> razorpay(@PathVariable String code, HttpServletRequest req) {
        return service.syncRazorpay(code, me().id(), me().email(), req.getRemoteAddr());
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<?> handle(PlatformException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
