package com.tenemsa.platform.billing.controller;

import com.tenemsa.platform.billing.service.BillingException;
import com.tenemsa.platform.billing.service.BillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Razorpay calls this when a payment or subscription changes. Open to the internet; the signature is the lock. */
@RestController
@RequestMapping("/api/billing/webhook")
public class BillingWebhookController {

    private final BillingService billing;

    public BillingWebhookController(BillingService billing) { this.billing = billing; }

    @PostMapping("/razorpay")
    public ResponseEntity<?> razorpay(@RequestBody String rawBody,
                                      @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                      @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        if (!billing.handleWebhook(rawBody, signature, eventId)) return ResponseEntity.status(400).body(Map.of("message", "Bad signature."));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<?> handle(BillingException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
