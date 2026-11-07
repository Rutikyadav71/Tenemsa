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

    public record Limits(String plan, Integer maxUsers, Integer aiDailyLimit) {}

    private final JdbcTemplate jdbc;

    public CompanyLimits(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Limits of(Long companyId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT plan, max_users, ai_daily_limit FROM companies WHERE id = ?", companyId);
            if (rows.isEmpty()) return new Limits("FREE", null, null);
            Map<String, Object> r = rows.get(0);
            return new Limits(String.valueOf(r.get("plan")), num(r.get("max_users")), num(r.get("ai_daily_limit")));
        } catch (Exception e) {
            log.warn("Could not read plan limits for company {}: {}", companyId, e.toString());
            return new Limits("FREE", null, null);
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
}
