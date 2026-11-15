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
                             Integer maxConnections, Boolean active) {}

    private final PlanRepository plans;
    private final SubscriptionRepository subs;
    private final RazorpayApi razorpay;
    private final PlatformAuditService audit;

    public PlatformBillingService(PlanRepository plans, SubscriptionRepository subs, RazorpayApi razorpay, PlatformAuditService audit) {
        this.plans = plans; this.subs = subs; this.razorpay = razorpay; this.audit = audit;
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
