package com.tenemsa.platform.platformadmin.service;

import com.tenemsa.platform.auth.service.AuthService;
import com.tenemsa.platform.company.service.CompanyAccessGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * What the platform console may see and do with companies. Company-level only: names, owner contact, counts,
 * sizes, status and activity. It never reads chat, saved AI chats, private documents or salaries.
 */
@Service
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformCompanyService {

    private final JdbcTemplate jdbc;
    private final CompanyAccessGuard guard;
    private final PlatformAuditService audit;
    private final AuthService authService;

    public PlatformCompanyService(JdbcTemplate jdbc, CompanyAccessGuard guard, PlatformAuditService audit, AuthService authService) {
        this.jdbc = jdbc; this.guard = guard; this.audit = audit; this.authService = authService;
    }

    private long count(String sql, Object... a) {
        Long n = jdbc.queryForObject(sql, Long.class, a);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ overview

    public Map<String, Object> overview() {
        LocalDateTime d7 = LocalDateTime.now().minusDays(7), d30 = LocalDateTime.now().minusDays(30);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companies", count("SELECT COUNT(*) FROM companies"));
        m.put("active", count("SELECT COUNT(*) FROM companies WHERE status = 'ACTIVE'"));
        m.put("frozen", count("SELECT COUNT(*) FROM companies WHERE status = 'FROZEN'"));
        m.put("deleted", count("SELECT COUNT(*) FROM companies WHERE status = 'DELETED'"));
        m.put("demoCompanies", count("SELECT COUNT(*) FROM companies WHERE slug LIKE 'demo-%'"));
        m.put("newLast7Days", count("SELECT COUNT(*) FROM companies WHERE created_at >= ?", d7));
        m.put("newLast30Days", count("SELECT COUNT(*) FROM companies WHERE created_at >= ?", d30));
        m.put("users", count("SELECT COUNT(*) FROM users"));
        m.put("frozenUsers", count("SELECT COUNT(*) FROM users WHERE frozen = ?", true));
        m.put("activeCompaniesLast7Days", count("SELECT COUNT(DISTINCT company_id) FROM audit_logs WHERE action = 'LOGIN_SUCCESS' AND created_at >= ?", d7));
        m.put("aiRequestsLast30Days", count("SELECT COUNT(*) FROM ai_request_logs WHERE created_at >= ?", d30));
        m.put("aiFailedLast30Days", count("SELECT COUNT(*) FROM ai_request_logs WHERE status <> 'SUCCESS' AND created_at >= ?", d30));
        m.put("storageBytes", count("SELECT COALESCE(SUM(size_bytes),0) FROM documents") + count("SELECT COALESCE(SUM(size_bytes),0) FROM chat_attachments"));
        // New companies per week for the last 12 weeks (oldest first)
        List<Map<String, Object>> growth = new ArrayList<>();
        LocalDateTime start = LocalDateTime.now().minusWeeks(11).toLocalDate().atStartOfDay();
        for (int w = 0; w < 12; w++) {
            LocalDateTime from = start.plusWeeks(w), to = from.plusWeeks(1);
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("weekStart", from.toLocalDate().toString());
            g.put("companies", count("SELECT COUNT(*) FROM companies WHERE created_at >= ? AND created_at < ?", from, to));
            g.put("users", count("SELECT COUNT(*) FROM users WHERE created_at >= ? AND created_at < ?", from, to));
            growth.add(g);
        }
        m.put("growth", growth);
        List<Map<String, Object>> recent = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT id, name, slug, status, created_at FROM companies ORDER BY created_at DESC, id DESC LIMIT 6")) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", ((Number) r.get("id")).longValue()); x.put("name", r.get("name")); x.put("slug", r.get("slug"));
            x.put("status", r.get("status")); x.put("createdAt", iso(r.get("created_at")));
            recent.add(x);
        }
        m.put("recentCompanies", recent);
        return m;
    }

    // ------------------------------------------------------------------ list + detail

    private static final Map<String, String> SORTS = Map.of(
            "name", "c.name", "created", "c.created_at", "members", "members", "lastActive", "last_active", "status", "c.status");

    public Map<String, Object> list(String search, String status, String sort, boolean desc, int page, int size) {
        int p = Math.max(0, page), s = Math.min(100, Math.max(1, size));
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(c.name) LIKE ? OR LOWER(c.slug) LIKE ?)");
            String like = "%" + search.trim().toLowerCase().replace("%", "") + "%";
            args.add(like); args.add(like);
        }
        if (status != null && List.of("ACTIVE", "FROZEN", "DELETED").contains(status)) { where.append(" AND c.status = ?"); args.add(status); }
        long total = count("SELECT COUNT(*) FROM companies c" + where, args.toArray());
        String order = SORTS.getOrDefault(sort == null ? "created" : sort, "c.created_at") + (desc ? " DESC" : " ASC") + ", c.id DESC";
        String sql = "SELECT c.id, c.name, c.slug, c.status, c.ai_enabled AS ai_enabled, c.created_at AS created_at, "
                + "(SELECT COUNT(*) FROM users u WHERE u.company_id = c.id) AS members, "
                + "(SELECT MAX(a.created_at) FROM audit_logs a WHERE a.company_id = c.id AND a.action = 'LOGIN_SUCCESS') AS last_active, "
                + "(SELECT u.name FROM users u WHERE u.company_id = c.id AND u.primary_owner = ? ORDER BY u.id LIMIT 1) AS owner_name, "
                + "(SELECT u.email FROM users u WHERE u.company_id = c.id AND u.primary_owner = ? ORDER BY u.id LIMIT 1) AS owner_email "
                + "FROM companies c" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?";
        List<Object> all = new ArrayList<>();
        all.add(true); all.add(true); all.addAll(args); all.add(s); all.add(p * s);
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query(sql, rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id")); m.put("name", rs.getString("name")); m.put("slug", rs.getString("slug"));
            m.put("status", rs.getString("status")); m.put("aiEnabled", rs.getBoolean("ai_enabled"));
            m.put("createdAt", ts(rs.getTimestamp("created_at"))); m.put("members", rs.getLong("members"));
            m.put("lastActiveAt", ts(rs.getTimestamp("last_active")));
            m.put("ownerName", rs.getString("owner_name")); m.put("ownerEmail", rs.getString("owner_email"));
            rows.add(m);
        }, all.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", rows); out.put("page", p); out.put("size", s); out.put("totalElements", total);
        out.put("totalPages", (int) Math.ceil(total / (double) s));
        return out;
    }

    private static Object iso(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().toString();
        return String.valueOf(o).replace(' ', 'T');
    }

    private static String ts(java.sql.Timestamp t) { return t == null ? null : t.toLocalDateTime().toString(); }

    public Map<String, Object> detail(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, slug, status, status_reason, status_changed_at, ai_enabled, created_at, plan, max_users, ai_daily_limit FROM companies WHERE id = ?", id);
        if (rows.isEmpty()) throw new PlatformException(404, "Company not found.");
        Map<String, Object> c = rows.get(0);
        LocalDateTime d30 = LocalDateTime.now().minusDays(30);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("name", c.get("name")); m.put("slug", c.get("slug")); m.put("status", c.get("status"));
        m.put("statusReason", c.get("status_reason")); m.put("statusChangedAt", iso(c.get("status_changed_at")));
        m.put("aiEnabled", c.get("ai_enabled")); m.put("createdAt", iso(c.get("created_at")));
        Map<String, Object> lim = new LinkedHashMap<>();
        lim.put("plan", c.get("plan")); lim.put("maxUsers", c.get("max_users")); lim.put("aiDailyLimit", c.get("ai_daily_limit"));
        m.put("limits", lim);

        List<Map<String, Object>> owners = jdbc.queryForList(
                "SELECT name, email, email_verified, created_at FROM users WHERE company_id = ? AND primary_owner = ? ORDER BY id LIMIT 1", id, true);
        if (!owners.isEmpty()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", owners.get(0).get("name")); o.put("email", owners.get(0).get("email"));
            o.put("emailVerified", owners.get(0).get("email_verified"));
            m.put("owner", o);
        }
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("total", count("SELECT COUNT(*) FROM users WHERE company_id = ?", id));
        u.put("frozen", count("SELECT COUNT(*) FROM users WHERE company_id = ? AND frozen = ?", id, true));
        List<Map<String, Object>> byRole = jdbc.queryForList("SELECT role, COUNT(*) AS n FROM users WHERE company_id = ? GROUP BY role ORDER BY n DESC", id);
        List<Map<String, Object>> roles = new ArrayList<>();
        for (Map<String, Object> r : byRole) roles.add(Map.of("role", r.get("role"), "count", ((Number) r.get("n")).longValue()));
        u.put("byRole", roles);
        m.put("people", u);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("projects", count("SELECT COUNT(*) FROM projects WHERE company_id = ?", id));
        usage.put("issues", count("SELECT COUNT(*) FROM issues WHERE company_id = ?", id));
        usage.put("leaveRequests", count("SELECT COUNT(*) FROM leaves WHERE company_id = ?", id));
        usage.put("meetings", count("SELECT COUNT(*) FROM meeting_summaries WHERE company_id = ?", id));
        usage.put("documents", count("SELECT COUNT(*) FROM documents WHERE company_id = ?", id));
        usage.put("knowledgeDocuments", count("SELECT COUNT(*) FROM kb_documents WHERE company_id = ?", id));
        usage.put("storageBytes", count("SELECT COALESCE(SUM(size_bytes),0) FROM documents WHERE company_id = ?", id)
                + count("SELECT COALESCE(SUM(size_bytes),0) FROM chat_attachments WHERE company_id = ?", id));
        usage.put("aiRequestsLast30Days", count("SELECT COUNT(*) FROM ai_request_logs WHERE company_id = ? AND created_at >= ?", id, d30));
        usage.put("aiFailedLast30Days", count("SELECT COUNT(*) FROM ai_request_logs WHERE company_id = ? AND status <> 'SUCCESS' AND created_at >= ?", id, d30));
        usage.put("aiInputTokensLast30Days", count("SELECT COALESCE(SUM(input_tokens),0) FROM ai_request_logs WHERE company_id = ? AND created_at >= ?", id, d30));
        usage.put("aiOutputTokensLast30Days", count("SELECT COALESCE(SUM(output_tokens),0) FROM ai_request_logs WHERE company_id = ? AND created_at >= ?", id, d30));
        m.put("usage", usage);

        Map<String, Object> act = new LinkedHashMap<>();
        act.put("signInsLast7Days", count("SELECT COUNT(*) FROM audit_logs WHERE company_id = ? AND action = 'LOGIN_SUCCESS' AND created_at >= ?", id, LocalDateTime.now().minusDays(7)));
        act.put("failedSignInsLast7Days", count("SELECT COUNT(*) FROM audit_logs WHERE company_id = ? AND action = 'LOGIN_FAILED' AND created_at >= ?", id, LocalDateTime.now().minusDays(7)));
        List<java.sql.Timestamp> last = jdbc.queryForList("SELECT MAX(created_at) FROM audit_logs WHERE company_id = ? AND action = 'LOGIN_SUCCESS'", java.sql.Timestamp.class, id);
        act.put("lastSignInAt", last.isEmpty() ? null : ts(last.get(0)));
        m.put("activity", act);

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT action, admin_email, details, created_at FROM platform_audit_logs WHERE company_id = ? ORDER BY created_at DESC, id DESC LIMIT 15", id);
        List<Map<String, Object>> h = new ArrayList<>();
        for (Map<String, Object> r : history) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("action", r.get("action")); x.put("admin", r.get("admin_email")); x.put("details", r.get("details"));
            x.put("at", iso(r.get("created_at")));
            h.add(x);
        }
        m.put("history", h);
        return m;
    }

    // ------------------------------------------------------------------ actions

    private Map<String, Object> basic(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, name, slug, status FROM companies WHERE id = ?", id);
        if (rows.isEmpty()) throw new PlatformException(404, "Company not found.");
        return rows.get(0);
    }

    private static String needReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5) throw new PlatformException(400, "Give a reason (at least 5 characters). It is kept in the audit log.");
        return r.length() > 300 ? r.substring(0, 300) : r;
    }

    private void setStatus(long id, String status, String reason) {
        jdbc.update("UPDATE companies SET status = ?, status_reason = ?, status_changed_at = ? WHERE id = ?", status, reason, LocalDateTime.now(), id);
        guard.evict(id);
    }

    private void signEveryoneOut(long id) {
        jdbc.update("UPDATE refresh_tokens SET revoked = ? WHERE user_id IN (SELECT id FROM users WHERE company_id = ?)", true, id);
    }

    @Transactional
    public void freeze(long id, String reason, Long adminId, String adminEmail, String ip) {
        Map<String, Object> c = basic(id);
        String r = needReason(reason);
        if (!"ACTIVE".equals(c.get("status"))) throw new PlatformException(400, "Only an active company can be frozen.");
        setStatus(id, "FROZEN", r);
        signEveryoneOut(id);
        audit.record(adminId, adminEmail, "COMPANY_FROZEN", id, (String) c.get("name"), r, ip);
    }

    @Transactional
    public void unfreeze(long id, String reason, Long adminId, String adminEmail, String ip) {
        Map<String, Object> c = basic(id);
        if (!"FROZEN".equals(c.get("status"))) throw new PlatformException(400, "That company is not frozen.");
        setStatus(id, "ACTIVE", null);
        audit.record(adminId, adminEmail, "COMPANY_UNFROZEN", id, (String) c.get("name"), reason == null || reason.isBlank() ? "Unfrozen" : reason.trim(), ip);
    }

    /** Closes the company (kept in the database, nobody can sign in). Typing the slug is the confirmation. */
    @Transactional
    public void close(long id, String reason, String confirmSlug, Long adminId, String adminEmail, String ip) {
        Map<String, Object> c = basic(id);
        String r = needReason(reason);
        if (confirmSlug == null || !confirmSlug.equals(c.get("slug"))) throw new PlatformException(400, "Type the company's workspace name exactly to confirm.");
        if ("DELETED".equals(c.get("status"))) throw new PlatformException(400, "That company is already closed.");
        setStatus(id, "DELETED", r);
        signEveryoneOut(id);
        audit.record(adminId, adminEmail, "COMPANY_CLOSED", id, (String) c.get("name"), r, ip);
    }

    @Transactional
    public void restore(long id, Long adminId, String adminEmail, String ip) {
        Map<String, Object> c = basic(id);
        if (!"DELETED".equals(c.get("status"))) throw new PlatformException(400, "That company is not closed.");
        setStatus(id, "ACTIVE", null);
        audit.record(adminId, adminEmail, "COMPANY_RESTORED", id, (String) c.get("name"), "Restored", ip);
    }

    /** Plan label and limits. A null limit means unlimited. */
    @Transactional
    public void setLimits(long id, String plan, Integer maxUsers, Integer aiDailyLimit, Long adminId, String adminEmail, String ip) {
        Map<String, Object> c = basic(id);
        String p = plan == null ? "FREE" : plan.trim().toUpperCase();
        if (!java.util.Set.of("FREE", "STARTER", "BUSINESS", "ENTERPRISE").contains(p)) throw new PlatformException(400, "Unknown plan.");
        if (maxUsers != null && (maxUsers < 1 || maxUsers > 100000)) throw new PlatformException(400, "The people limit must be between 1 and 100,000, or empty for no limit.");
        if (aiDailyLimit != null && (aiDailyLimit < 0 || aiDailyLimit > 1000000)) throw new PlatformException(400, "The AI limit must be 0 or more, or empty for the default.");
        jdbc.update("UPDATE companies SET plan = ?, max_users = ?, ai_daily_limit = ? WHERE id = ?", p, maxUsers, aiDailyLimit, id);
        audit.record(adminId, adminEmail, "COMPANY_LIMITS_SET", id, (String) c.get("name"),
                "Plan " + p + ", people limit " + (maxUsers == null ? "none" : maxUsers) + ", AI per day " + (aiDailyLimit == null ? "default" : aiDailyLimit), ip);
    }

    @Transactional
    public void setAi(long id, boolean enabled, Long adminId, String adminEmail, String ip) {
        Map<String, Object> c = basic(id);
        jdbc.update("UPDATE companies SET ai_enabled = ? WHERE id = ?", enabled, id);
        audit.record(adminId, adminEmail, "COMPANY_AI_SET", id, (String) c.get("name"), enabled ? "AI switched on" : "AI switched off", ip);
    }

    /** Sends the usual verification email to the company's primary owner (a harmless nudge, not a password change). */
    public void resendOwnerVerification(long id, Long adminId, String adminEmail, String ip) {
        Map<String, Object> c = basic(id);
        List<String> owner = jdbc.queryForList("SELECT email FROM users WHERE company_id = ? AND primary_owner = ? ORDER BY id LIMIT 1", String.class, id, true);
        if (owner.isEmpty()) throw new PlatformException(404, "This company has no primary owner.");
        authService.resendVerificationEmail(owner.get(0), (String) c.get("slug"));
        audit.record(adminId, adminEmail, "OWNER_VERIFICATION_SENT", id, (String) c.get("name"), "Verification email re-sent to the owner", ip);
    }

    // ------------------------------------------------------------------ system

    public Map<String, Object> system() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("database", jdbc.execute((java.sql.Connection con) -> con.getMetaData().getDatabaseProductName() + " " + con.getMetaData().getDatabaseProductVersion()));
        m.put("serverTime", LocalDateTime.now().toString());
        m.put("javaVersion", System.getProperty("java.version"));
        m.put("usedMemoryMb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576);
        m.put("demoCompanies", count("SELECT COUNT(*) FROM companies WHERE slug LIKE 'demo-%'"));
        m.put("tables", Map.of(
                "users", count("SELECT COUNT(*) FROM users"), "attendance", count("SELECT COUNT(*) FROM attendance"),
                "issues", count("SELECT COUNT(*) FROM issues"), "chatMessages", count("SELECT COUNT(*) FROM chat_messages"),
                "auditLogs", count("SELECT COUNT(*) FROM audit_logs"), "aiRequests", count("SELECT COUNT(*) FROM ai_request_logs")));
        return m;
    }
}
