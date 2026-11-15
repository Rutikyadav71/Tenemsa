package com.tenemsa.platform.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.billing.model.BillingEvent;
import com.tenemsa.platform.billing.model.Plan;
import com.tenemsa.platform.billing.model.Subscription;
import com.tenemsa.platform.billing.repository.BillingEventRepository;
import com.tenemsa.platform.billing.repository.PlanRepository;
import com.tenemsa.platform.billing.repository.SubscriptionRepository;
import com.tenemsa.platform.company.service.CompanyLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Plans, subscriptions and payments. Razorpay holds the card and charges it; we only keep a mirror of
 * the subscription state and change the company's plan when Razorpay tells us a payment worked.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    /** Subscriptions that still own the company's paid plan. */
    static final Set<String> LIVE = Set.of("AUTHENTICATED", "ACTIVE", "PENDING");

    @Value("${billing.razorpay.webhook-secret:}") private String webhookSecret;
    @Value("${billing.grace-days:3}") private int graceDays;

    private final PlanRepository plans;
    private final SubscriptionRepository subs;
    private final BillingEventRepository events;
    private final RazorpayApi razorpay;
    private final CompanyLimits limits;
    private final AiBudget budget;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public BillingService(PlanRepository plans, SubscriptionRepository subs, BillingEventRepository events,
                          RazorpayApi razorpay, CompanyLimits limits, AiBudget budget, JdbcTemplate jdbc) {
        this.plans = plans; this.subs = subs; this.events = events; this.razorpay = razorpay;
        this.limits = limits; this.budget = budget; this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ read

    public Map<String, Object> overview(Long companyId) {
        CompanyLimits.Limits lim = limits.of(companyId);
        Long used = budget.tokensUsedThisMonth(companyId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paymentsEnabled", razorpay.isConfigured());
        m.put("currentPlan", lim.plan());

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("people", limits.userCount(companyId));
        usage.put("maxPeople", lim.maxUsers());
        usage.put("aiTokensUsed", used);
        usage.put("aiTokenBudget", lim.monthlyTokens());
        usage.put("aiResetsOn", AiBudget.nextReset().toString());
        m.put("usage", usage);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Plan p : plans.findAllByOrderBySortOrderAsc()) if (p.isActive()) list.add(planView(p));
        m.put("plans", list);

        Subscription sub = subs.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(s -> LIVE.contains(s.getStatus()) || "CANCELLED".equals(s.getStatus()) && s.getCurrentEnd() != null && s.getCurrentEnd().isAfter(LocalDateTime.now()))
                .findFirst().orElse(null);
        if (sub != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("planCode", sub.getPlanCode()); s.put("status", sub.getStatus());
            s.put("renewsOn", sub.getCurrentEnd() == null ? null : sub.getCurrentEnd().toLocalDate().toString());
            s.put("cancelAtPeriodEnd", sub.isCancelAtPeriodEnd() || "CANCELLED".equals(sub.getStatus()));
            m.put("subscription", s);
        } else {
            m.put("subscription", null);
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (BillingEvent e : events.findByCompanyIdAndEventTypeOrderByCreatedAtDesc(companyId, "subscription.charged", PageRequest.of(0, 12))) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("date", e.getCreatedAt().toLocalDate().toString());
            h.put("amountPaise", e.getAmountPaise());
            h.put("paymentId", e.getRazorpayPaymentId());
            h.put("status", e.getStatus());
            history.add(h);
        }
        m.put("payments", history);
        return m;
    }

    public static Map<String, Object> planView(Plan p) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("code", p.getCode()); v.put("name", p.getName()); v.put("description", p.getDescription());
        v.put("pricePaise", p.getPricePaise()); v.put("maxUsers", p.getMaxUsers());
        v.put("monthlyAiTokens", p.getMonthlyAiTokens()); v.put("maxConnections", p.getMaxConnections());
        v.put("contactOnly", p.isContactOnly());
        return v;
    }

    // ------------------------------------------------------------------ checkout

    /** Creates a Razorpay subscription for the plan and returns what the browser's checkout window needs. */
    @Transactional
    public Map<String, Object> startCheckout(Long companyId, String planCode) {
        if (!razorpay.isConfigured()) throw new BillingException(503, "Online payments are not set up yet. Ask the Tenemsa team to change your plan.");
        Plan plan = plans.findById(planCode == null ? "" : planCode.trim().toUpperCase())
                .filter(Plan::isActive).orElseThrow(() -> new BillingException(404, "That plan does not exist."));
        if (plan.isContactOnly()) throw new BillingException(400, "This plan is arranged with the Tenemsa team. Please contact us.");
        if (plan.getPricePaise() <= 0) throw new BillingException(400, "The Free plan needs no payment. Cancel your subscription to move to it.");
        if (subs.existsByCompanyIdAndStatusIn(companyId, LIVE)) {
            throw new BillingException(409, "You already have a subscription. Cancel it first, then pick the new plan.");
        }
        if (plan.getRazorpayPlanId() == null || plan.getRazorpayPlanId().isBlank()) {
            plan.setRazorpayPlanId(razorpay.createPlan(plan.getName(), plan.getDescription(), plan.getPricePaise()));
            plans.save(plan);
        }
        Map<String, Object> created = razorpay.createSubscription(plan.getRazorpayPlanId(),
                Map.of("company_id", String.valueOf(companyId), "plan_code", plan.getCode()));
        Subscription s = new Subscription();
        s.setCompanyId(companyId);
        s.setPlanCode(plan.getCode());
        s.setRazorpaySubscriptionId(String.valueOf(created.get("id")));
        subs.save(s);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("keyId", razorpay.keyId());
        out.put("subscriptionId", s.getRazorpaySubscriptionId());
        out.put("planName", plan.getName());
        out.put("amountPaise", plan.getPricePaise());
        return out;
    }

    /**
     * Called by the browser after the customer pays. Razorpay signs "payment_id|subscription_id" with our
     * secret; a valid signature proves the payment is real, so the plan switches on straight away. The webhook
     * later confirms and keeps renewals in step.
     */
    @Transactional
    public void verify(Long companyId, String paymentId, String subscriptionId, String signature) {
        if (paymentId == null || subscriptionId == null || signature == null) throw new BillingException(400, "The payment details are incomplete.");
        Subscription s = subs.findByRazorpaySubscriptionId(subscriptionId)
                .filter(x -> x.getCompanyId().equals(companyId))
                .orElseThrow(() -> new BillingException(404, "That subscription was not found."));
        String secret = razorpaySecretForCheckout();
        if (!constantTimeEquals(hmacHex(secret, paymentId + "|" + subscriptionId), signature)) {
            throw new BillingException(400, "The payment could not be verified.");
        }
        if ("CREATED".equals(s.getStatus()) || "AUTHENTICATED".equals(s.getStatus())) {
            s.setStatus("ACTIVE");
            s.setCurrentStart(LocalDateTime.now());
            s.setCurrentEnd(LocalDateTime.now().plusMonths(1));
            s.setPaidCount(Math.max(1, s.getPaidCount()));
            subs.save(s);
            applyPlan(companyId, s.getPlanCode());
            String eid = "verify:" + paymentId;
            if (!events.existsByRazorpayEventId(eid)) {
                BillingEvent e = new BillingEvent();
                e.setCompanyId(companyId); e.setRazorpayEventId(eid); e.setEventType("checkout.verified");
                e.setRazorpayPaymentId(paymentId); e.setStatus("OK");
                events.save(e);
            }
        }
    }

    /** Stops renewals. The plan stays until the end of the period already paid for. */
    @Transactional
    public void cancel(Long companyId) {
        Subscription s = subs.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(x -> LIVE.contains(x.getStatus())).findFirst()
                .orElseThrow(() -> new BillingException(404, "You do not have an active subscription."));
        razorpay.cancelSubscription(s.getRazorpaySubscriptionId(), true);
        s.setCancelAtPeriodEnd(true);
        subs.save(s);
    }

    // ------------------------------------------------------------------ webhook

    /** Razorpay -> us. Returns true when the call was genuine. Safe to deliver more than once. */
    @Transactional
    public boolean handleWebhook(String rawBody, String signature, String eventId) {
        if (webhookSecret == null || webhookSecret.isBlank()) throw new BillingException(503, "Webhooks are not set up.");
        if (signature == null || !constantTimeEquals(hmacHex(webhookSecret, rawBody), signature)) return false;
        String id = eventId != null && !eventId.isBlank() ? eventId : "body:" + sha256(rawBody);
        if (events.existsByRazorpayEventId(id)) return true;
        try {
            JsonNode root = json.readTree(rawBody);
            String type = root.path("event").asText("");
            JsonNode sn = root.path("payload").path("subscription").path("entity");
            JsonNode pn = root.path("payload").path("payment").path("entity");
            Subscription s = subs.findByRazorpaySubscriptionId(sn.path("id").asText("")).orElse(null);

            BillingEvent e = new BillingEvent();
            e.setRazorpayEventId(id); e.setEventType(type);
            e.setCompanyId(s == null ? null : s.getCompanyId());
            if (!pn.isMissingNode()) {
                e.setRazorpayPaymentId(pn.path("id").asText(null));
                if (pn.has("amount")) e.setAmountPaise(pn.path("amount").asLong());
                e.setStatus(pn.path("status").asText(null));
            }
            events.save(e);
            if (s == null) return true;

            if (sn.has("current_start") && !sn.path("current_start").isNull()) s.setCurrentStart(fromEpoch(sn.path("current_start").asLong()));
            if (sn.has("current_end") && !sn.path("current_end").isNull()) s.setCurrentEnd(fromEpoch(sn.path("current_end").asLong()));
            if (sn.has("paid_count")) s.setPaidCount(sn.path("paid_count").asInt());
            switch (type) {
                case "subscription.authenticated" -> { if ("CREATED".equals(s.getStatus())) s.setStatus("AUTHENTICATED"); }
                case "subscription.activated", "subscription.charged" -> { s.setStatus("ACTIVE"); applyPlan(s.getCompanyId(), s.getPlanCode()); }
                case "subscription.pending" -> s.setStatus("PENDING");
                case "subscription.halted" -> s.setStatus("HALTED");
                case "subscription.cancelled" -> s.setStatus("CANCELLED");
                case "subscription.completed" -> s.setStatus("COMPLETED");
                default -> { /* other events are recorded only */ }
            }
            subs.save(s);
        } catch (BillingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not process a Razorpay webhook: {}", e.toString());
        }
        return true;
    }

    // ------------------------------------------------------------------ plan changes

    void applyPlan(Long companyId, String planCode) {
        jdbc.update("UPDATE companies SET plan = ? WHERE id = ?", planCode, companyId);
    }

    /** Daily job: companies whose paid time has run out (plus the grace days) go back to Free. */
    @Transactional
    public int downgradeExpired() {
        int n = 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(graceDays);
        for (Subscription s : subs.findByStatusIn(List.of("CANCELLED", "HALTED", "COMPLETED"))) {
            if (s.getCurrentEnd() == null || s.getCurrentEnd().isAfter(cutoff)) continue;
            String current = jdbc.queryForObject("SELECT plan FROM companies WHERE id = ?", String.class, s.getCompanyId());
            if (s.getPlanCode().equals(current)) { applyPlan(s.getCompanyId(), "FREE"); n++; }
            s.setStatus("EXPIRED");
            subs.save(s);
        }
        return n;
    }

    // ------------------------------------------------------------------ crypto

    private String razorpaySecretForCheckout() {
        String secret = checkoutSecret;
        if (secret == null || secret.isBlank()) throw new BillingException(503, "Online payments are not set up yet.");
        return secret;
    }

    @Value("${billing.razorpay.key-secret:}") private String checkoutSecret;

    public static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static LocalDateTime fromEpoch(long seconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
    }
}
