package com.tenemsa.platform.billing.controller;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.billing.service.BillingException;
import com.tenemsa.platform.billing.service.BillingService;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The company's own plan, usage and payments. Owners and admins may look; only owners may change the plan. */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    public record CheckoutRequest(String plan) {}
    public record VerifyRequest(String paymentId, String subscriptionId, String signature) {}

    private final BillingService billing;
    private final AuthContext auth;
    private final AuditService audit;

    public BillingController(BillingService billing, AuthContext auth, AuditService audit) {
        this.billing = billing; this.auth = auth; this.audit = audit;
    }

    @PreAuthorize("hasAuthority('billing:view')")
    @GetMapping("/overview")
    public Map<String, Object> overview() { return billing.overview(auth.currentCompanyId()); }

    @PreAuthorize("hasAuthority('subscription:manage')")
    @PostMapping("/checkout")
    public Map<String, Object> checkout(@RequestBody CheckoutRequest r) { return billing.startCheckout(auth.currentCompanyId(), r.plan()); }

    @PreAuthorize("hasAuthority('subscription:manage')")
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody VerifyRequest r) {
        billing.verify(auth.currentCompanyId(), r.paymentId(), r.subscriptionId(), r.signature());
        audit.recordCurrent(AuditAction.SUBSCRIPTION_STARTED, "SUBSCRIPTION", r.subscriptionId(), "Started a paid plan");
        return billing.overview(auth.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('subscription:manage')")
    @PostMapping("/cancel")
    public Map<String, Object> cancel() {
        billing.cancel(auth.currentCompanyId());
        audit.recordCurrent(AuditAction.SUBSCRIPTION_CANCELLED, "SUBSCRIPTION", "-", "Cancelled the subscription at the end of the paid period");
        return billing.overview(auth.currentCompanyId());
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<?> handle(BillingException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
