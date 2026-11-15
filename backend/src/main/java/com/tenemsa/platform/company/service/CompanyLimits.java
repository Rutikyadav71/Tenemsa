package com.tenemsa.platform.company.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Per-company limits set from the platform console. A missing limit means unlimited. Fails open if the lookup fails. */
@Service
public class CompanyLimits {

    private static final Logger log = LoggerFactory.getLogger(CompanyLimits.class);

    /** Effective limits: a company's own override if set, otherwise the plan's value. null = unlimited. */
    public record Limits(String plan, Integer maxUsers, Integer aiDailyLimit, Long monthlyTokens, Integer maxConnections) {}

    private final JdbcTemplate jdbc;

    public CompanyLimits(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Limits of(Long companyId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT c.plan AS plan, c.max_users AS cu, p.max_users AS pu, c.ai_daily_limit AS ad, c.ai_monthly_tokens AS ct, "
                  + "p.monthly_ai_tokens AS pt, p.max_connections AS pc FROM companies c LEFT JOIN plans p ON p.code = c.plan WHERE c.id = ?", companyId);
            if (rows.isEmpty()) return new Limits("FREE", null, null, null, null);
            Map<String, Object> r = rows.get(0);
            Integer users = r.get("cu") != null ? num(r.get("cu")) : num(r.get("pu"));
            Long tokens = r.get("ct") != null ? lng(r.get("ct")) : lng(r.get("pt"));
            return new Limits(String.valueOf(r.get("plan")), users, num(r.get("ad")), tokens, num(r.get("pc")));
        } catch (Exception e) {
            log.warn("Could not read plan limits for company {}: {}", companyId, e.toString());
            return new Limits("FREE", null, null, null, null);
        }
    }

    public long userCount(Long companyId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE company_id = ?", Long.class, companyId);
        return n == null ? 0 : n;
    }

    /** Call before creating or accepting one more account. */
    public void assertCanAddUser(Long companyId) {
        Integer max = of(companyId).maxUsers();
        if (max != null && userCount(companyId) >= max) {
            throw new PlanLimitException("Your plan allows up to " + max + " people and you have reached that. Contact Tenemsa to raise the limit.");
        }
    }

    private static Integer num(Object o) { return o instanceof Number n ? n.intValue() : null; }
    private static Long lng(Object o) { return o instanceof Number n ? n.longValue() : null; }
}
