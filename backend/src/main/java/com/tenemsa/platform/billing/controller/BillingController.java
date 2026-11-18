package com.tenemsa.platform.billing.controller;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.billing.service.BillingException;
import com.tenemsa.platform.billing.service.BillingExtrasService;
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
    public record PlanChangeRequest(String plan) {}
    public record TopupRequest(String pack) {}
    public record TopupVerifyRequest(String orderId, String paymentId, String signature) {}

    private final BillingService billing;
    private final BillingExtrasService extras;
    private final AuthContext auth;
    private final AuditService audit;

    public BillingController(BillingService billing, BillingExtrasService extras, AuthContext auth, AuditService audit) {
        this.billing = billing; this.extras = extras; this.auth = auth; this.audit = audit;
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

    @PreAuthorize("hasAuthority('subscription:manage')")
    @PostMapping("/plan-change")
    public Map<String, Object> planChange(@RequestBody PlanChangeRequest r) {
        billing.changePlan(auth.currentCompanyId(), r.plan());
        audit.recordCurrent(AuditAction.PLAN_CHANGE_SCHEDULED, "SUBSCRIPTION", String.valueOf(r.plan()), "Scheduled a plan change for the next renewal");
        return billing.overview(auth.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('subscription:manage')")
    @PostMapping("/topup/checkout")
    public Map<String, Object> topupCheckout(@RequestBody TopupRequest r) { return extras.startTopup(auth.currentCompanyId(), r.pack()); }

    @PreAuthorize("hasAuthority('subscription:manage')")
    @PostMapping("/topup/verify")
    public Map<String, Object> topupVerify(@RequestBody TopupVerifyRequest r) {
        extras.verifyTopup(auth.currentCompanyId(), r.orderId(), r.paymentId(), r.signature());
        audit.recordCurrent(AuditAction.TOKEN_TOPUP_BOUGHT, "TOPUP", r.orderId(), "Bought extra AI tokens");
        return billing.overview(auth.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('billing:view')")
    @GetMapping("/details")
    public Map<String, Object> details() { return extras.getDetails(auth.currentCompanyId()); }

    @PreAuthorize("hasAuthority('subscription:manage')")
    @PutMapping("/details")
    public Map<String, Object> saveDetails(@RequestBody Map<String, String> body) {
        extras.saveDetails(auth.currentCompanyId(), body);
        audit.recordCurrent(AuditAction.BILLING_DETAILS_UPDATED, "BILLING", "-", "Updated the billing details");
        return extras.getDetails(auth.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('billing:view')")
    @GetMapping("/usage")
    public Map<String, Object> usage() { return extras.usage(auth.currentCompanyId()); }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<?> handle(BillingException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
