package com.tenemsa.platform.billing.service;

import java.util.Map;

/** The few Razorpay calls Tenemsa needs. An interface so tests can replace the network. */
public interface RazorpayApi {
    boolean isConfigured();
    /** The public key id, safe to give to the browser. */
    String keyId();
    /** Returns the new subscription's id and status. */
    Map<String, Object> createSubscription(String razorpayPlanId, Map<String, String> notes);
    void cancelSubscription(String razorpaySubscriptionId, boolean atCycleEnd);
    /** Creates a monthly plan and returns its id. */
    String createPlan(String name, String description, long amountPaise);
}
