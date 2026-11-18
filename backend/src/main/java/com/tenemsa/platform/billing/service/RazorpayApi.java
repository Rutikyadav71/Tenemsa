package com.tenemsa.platform.billing.service;

import java.util.Map;

/** The few Razorpay calls Tenemsa needs. An interface so tests can replace the network. */
public interface RazorpayApi {
    boolean isConfigured();
    /** The public key id, safe to give to the browser. */
    String keyId();
    /** Returns the new subscription's id and status. */
    /** {@code startAtEpochSeconds} delays the first charge (a free trial); null starts billing straight away. */
    Map<String, Object> createSubscription(String razorpayPlanId, Map<String, String> notes, Long startAtEpochSeconds);
    /** Moves a subscription to another plan, at the end of the current period when {@code atCycleEnd}. */
    void updateSubscription(String razorpaySubscriptionId, String newRazorpayPlanId, boolean atCycleEnd);
    /** A one-off payment request (a token top-up). Returns the order id. */
    String createOrder(long amountPaise, String receipt, Map<String, String> notes);
    /** The subscription as Razorpay sees it: status, plan_id, current_start, current_end (epoch seconds) and paid_count. */
    Map<String, Object> fetchSubscription(String razorpaySubscriptionId);
    void cancelSubscription(String razorpaySubscriptionId, boolean atCycleEnd);
    /** Creates a monthly plan and returns its id. */
    String createPlan(String name, String description, long amountPaise);
}
