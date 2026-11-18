package com.tenemsa.platform.billing.service;

import com.tenemsa.platform.billing.model.Plan;
import com.tenemsa.platform.billing.repository.PlanRepository;
import com.tenemsa.platform.billing.repository.SubscriptionRepository;
import com.tenemsa.platform.platformadmin.service.PlatformAuditService;
import com.tenemsa.platform.platformadmin.service.PlatformException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** What the system administrators can do with plans. Exists only when the platform console is switched on. */
@Service
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformBillingService {

    public record PlanUpdate(String name, String description, Integer pricePaise, Integer maxUsers, Long monthlyAiTokens,
                             Integer maxConnections, Boolean active, Integer trialDays) {}

    private final PlanRepository plans;
    private final SubscriptionRepository subs;
    private final RazorpayApi razorpay;
    private final PlatformAuditService audit;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public PlatformBillingService(PlanRepository plans, SubscriptionRepository subs, RazorpayApi razorpay, PlatformAuditService audit,
                                 org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.plans = plans; this.subs = subs; this.razorpay = razorpay; this.audit = audit; this.jdbc = jdbc;
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Plan p : plans.findAllByOrderBySortOrderAsc()) {
            Map<String, Object> v = new LinkedHashMap<>(BillingService.planView(p));
            v.put("active", p.isActive());
            v.put("razorpayPlanId", p.getRazorpayPlanId());
            v.put("activeSubscriptions", subs.countByPlanCodeAndStatus(p.getCode(), "ACTIVE"));
            list.add(v);
        }
        return Map.of("paymentsEnabled", razorpay.isConfigured(), "plans", list);
    }

    /**
     * Money view for the Tenemsa team. Monthly recurring revenue counts every subscription that is paying or is about to
     * (active, authenticated after a trial, or pending a retry) at its plan's price. Revenue this month adds up the
     * payments Razorpay reported (subscription charges and token top-ups).
     */
    public Map<String, Object> revenue() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> byPlan = jdbc.queryForList("SELECT p.code AS plan, p.name AS name, p.price_paise AS price_paise, COUNT(s.id) AS subscribers, "
                + "SUM(CASE WHEN s.trial = TRUE THEN 1 ELSE 0 END) AS on_trial FROM plans p LEFT JOIN subscriptions s ON s.plan_code = p.code "
                + "AND s.status IN ('ACTIVE','AUTHENTICATED','PENDING') WHERE p.price_paise > 0 GROUP BY p.code, p.name, p.price_paise, p.sort_order ORDER BY p.sort_order");
        long mrr = 0; long trials = 0; long paying = 0;
        for (Map<String, Object> r : byPlan) {
            long subscribers = ((Number) r.get("subscribers")).longValue();
            long onTrial = r.get("on_trial") == null ? 0 : ((Number) r.get("on_trial")).longValue();
            mrr += (subscribers - onTrial) * ((Number) r.get("price_paise")).longValue();
            trials += onTrial; paying += subscribers - onTrial;
        }
        java.time.LocalDateTime start = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Number charged = jdbc.queryForObject("SELECT COALESCE(SUM(amount_paise), 0) FROM billing_events WHERE event_type = 'subscription.charged' AND created_at >= ?", Number.class, start);
        Number topups = jdbc.queryForObject("SELECT COALESCE(SUM(amount_paise), 0) FROM billing_events WHERE event_type = 'topup.paid' AND created_at >= ?", Number.class, start);
        m.put("mrrPaise", mrr); m.put("payingCompanies", paying); m.put("trialCompanies", trials);
        m.put("chargedThisMonthPaise", charged == null ? 0 : charged.longValue());
        m.put("topupsThisMonthPaise", topups == null ? 0 : topups.longValue());
        m.put("byPlan", BillingExtrasService.lower(byPlan));
        m.put("recentSubscriptions", BillingExtrasService.lower(jdbc.queryForList("SELECT c.name AS company, s.plan_code AS plan, s.status AS status, s.trial AS trial, s.current_end AS renews, s.cancel_at_period_end AS ending "
                + "FROM subscriptions s JOIN companies c ON c.id = s.company_id WHERE s.status <> 'CREATED' ORDER BY s.created_at DESC LIMIT 20")));
        m.put("recentPayments", BillingExtrasService.lower(jdbc.queryForList("SELECT c.name AS company, e.event_type AS type, e.amount_paise AS amount_paise, e.created_at AS paid_at "
                + "FROM billing_events e JOIN companies c ON c.id = e.company_id WHERE e.event_type IN ('subscription.charged','topup.paid') ORDER BY e.created_at DESC LIMIT 20")));
        return m;
    }

    /** Edits a plan. A changed price clears the Razorpay plan so a new one (with the new price) is made at the next checkout. */
    @Transactional
    public Map<String, Object> update(String code, PlanUpdate u, Long adminId, String adminEmail, String ip) {
        Plan p = plans.findById(code.toUpperCase()).orElseThrow(() -> new PlatformException(404, "Plan not found."));
        if (u.name() != null) {
            if (u.name().isBlank() || u.name().length() > 60) throw new PlatformException(400, "The plan name must be 1 to 60 characters.");
            p.setName(u.name().trim());
        }
        if (u.description() != null) p.setDescription(u.description().length() > 250 ? u.description().substring(0, 250) : u.description());
        if (u.pricePaise() != null) {
            if (u.pricePaise() < 0 || u.pricePaise() > 100_000_000) throw new PlatformException(400, "That price is not valid.");
            if (u.pricePaise() != p.getPricePaise()) { p.setPricePaise(u.pricePaise()); p.setRazorpayPlanId(null); }
        }
        if (u.maxUsers() != null && u.maxUsers() < 1) throw new PlatformException(400, "The people limit must be at least 1.");
        if (u.monthlyAiTokens() != null && u.monthlyAiTokens() < 0) throw new PlatformException(400, "The AI allowance cannot be negative.");
        if (u.maxConnections() != null && u.maxConnections() < 0) throw new PlatformException(400, "The connections limit cannot be negative.");
        // For limits, an explicit 0 from the console means "no limit".
        if (u.maxUsers() != null) p.setMaxUsers(u.maxUsers());
        if (u.monthlyAiTokens() != null) p.setMonthlyAiTokens(u.monthlyAiTokens() == 0 ? null : u.monthlyAiTokens());
        if (u.maxConnections() != null) p.setMaxConnections(u.maxConnections());
        if (u.trialDays() != null) {
            if (u.trialDays() < 0 || u.trialDays() > 90) throw new PlatformException(400, "The trial must be between 0 and 90 days.");
            p.setTrialDays(u.trialDays());
        }
        if (u.active() != null) p.setActive(u.active());
        plans.save(p);
        audit.record(adminId, adminEmail, "PLAN_UPDATED", null, null, "Edited plan " + p.getCode(), ip);
        return overview();
    }

    /** Creates the matching plan in Razorpay now (it is otherwise made on the first checkout). */
    @Transactional
    public Map<String, Object> syncRazorpay(String code, Long adminId, String adminEmail, String ip) {
        Plan p = plans.findById(code.toUpperCase()).orElseThrow(() -> new PlatformException(404, "Plan not found."));
        if (!razorpay.isConfigured()) throw new PlatformException(503, "Razorpay keys are not set on the server.");
        if (p.isContactOnly() || p.getPricePaise() <= 0) throw new PlatformException(400, "Only paid plans exist in Razorpay.");
        try {
            p.setRazorpayPlanId(razorpay.createPlan(p.getName(), p.getDescription(), p.getPricePaise()));
        } catch (BillingException e) {
            throw new PlatformException(502, e.getMessage());
        }
        plans.save(p);
        audit.record(adminId, adminEmail, "RAZORPAY_PLAN_CREATED", null, null, "Created Razorpay plan for " + p.getCode(), ip);
        return overview();
    }
}
