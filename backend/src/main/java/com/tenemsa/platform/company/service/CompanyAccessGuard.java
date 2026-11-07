package com.tenemsa.platform.company.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "may people of this company use the app right now?". A company the platform has frozen or
 * closed is refused at sign-in, token refresh, every API call and the chat socket.
 *
 * The answer is cached for a few seconds so this adds no database query to most requests; the
 * platform console evicts the entry the moment it changes a status.
 */
@Component
public class CompanyAccessGuard {

    public static final String ACTIVE = "ACTIVE", FROZEN = "FROZEN", DELETED = "DELETED";
    private static final long TTL_MS = 15_000;

    private record Entry(String status, long expires) {}

    private final JdbcTemplate jdbc;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public CompanyAccessGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String statusOf(Long companyId) {
        if (companyId == null) return ACTIVE;
        long now = System.currentTimeMillis();
        Entry e = cache.get(companyId);
        if (e != null && e.expires > now) return e.status;
        String status;
        try {
            List<String> rows = jdbc.queryForList("SELECT status FROM companies WHERE id = ?", String.class, companyId);
            status = rows.isEmpty() || rows.get(0) == null ? ACTIVE : rows.get(0);
        } catch (Exception ex) {
            return ACTIVE;   // never lock everybody out because of a lookup problem
        }
        cache.put(companyId, new Entry(status, now + TTL_MS));
        return status;
    }

    public boolean isSuspended(Long companyId) { return !ACTIVE.equals(statusOf(companyId)); }

    public void evict(Long companyId) { if (companyId != null) cache.remove(companyId); }

    /** @throws RuntimeException with the standard message when the company is not active. */
    public void assertActive(Long companyId) {
        String s = statusOf(companyId);
        if (DELETED.equals(s)) throw new RuntimeException("WORKSPACE_SUSPENDED: This workspace has been closed. Contact Tenemsa support.");
        if (!ACTIVE.equals(s)) throw new RuntimeException("WORKSPACE_SUSPENDED: This workspace has been suspended. Contact Tenemsa support.");
    }
}
