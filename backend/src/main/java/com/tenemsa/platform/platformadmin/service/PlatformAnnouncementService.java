package com.tenemsa.platform.platformadmin.service;

import com.tenemsa.platform.announcement.model.PlatformAnnouncement;
import com.tenemsa.platform.announcement.repository.PlatformAnnouncementRepository;
import com.tenemsa.platform.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Notices from the system administrators to companies: a banner for everyone in the company and, optionally, email to owners and admins. */
@Service
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformAnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAnnouncementService.class);

    public record CreateRequest(String title, String message, String severity, List<Long> companyIds, Boolean sendEmail, Integer expiresInDays) {}

    private final PlatformAnnouncementRepository repo;
    private final JdbcTemplate jdbc;
    private final EmailService email;
    private final PlatformAuditService audit;
    private final ExecutorService mailer = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "platform-notice-mailer"); t.setDaemon(true); return t; });

    public PlatformAnnouncementService(PlatformAnnouncementRepository repo, JdbcTemplate jdbc, EmailService email, PlatformAuditService audit) {
        this.repo = repo; this.jdbc = jdbc; this.email = email; this.audit = audit;
    }

    private record Target(String email, String name) {}

    public Map<String, Object> create(CreateRequest r, Long adminId, String adminEmail, String ip) {
        String title = r.title() == null ? "" : r.title().trim().replaceAll("[\\r\\n]+", " ");
        String message = r.message() == null ? "" : r.message().trim();
        if (title.length() < 3 || title.length() > 150) throw new PlatformException(400, "The title needs 3 to 150 characters.");
        if (message.length() < 5 || message.length() > 3000) throw new PlatformException(400, "The message needs 5 to 3,000 characters.");
        String severity = r.severity() == null ? "INFO" : r.severity().trim().toUpperCase();
        if (!Set.of("INFO", "WARNING", "CRITICAL").contains(severity)) throw new PlatformException(400, "Severity must be INFO, WARNING or CRITICAL.");
        int days = r.expiresInDays() == null ? 7 : r.expiresInDays();
        if (days < 1 || days > 90) throw new PlatformException(400, "Show it for 1 to 90 days.");

        boolean all = r.companyIds() == null || r.companyIds().isEmpty();
        List<Long> ids = all
                ? jdbc.queryForList("SELECT id FROM companies WHERE status = 'ACTIVE'", Long.class)
                : jdbc.queryForList("SELECT id FROM companies WHERE status = 'ACTIVE' AND id IN (" + r.companyIds().stream().map(x -> "?").collect(Collectors.joining(",")) + ")", Long.class, r.companyIds().toArray());
        if (ids.isEmpty()) throw new PlatformException(400, "No active company matches, so nothing was sent.");

        PlatformAnnouncement n = new PlatformAnnouncement();
        n.setTitle(title); n.setMessage(message); n.setSeverity(severity);
        n.setAudience(all ? "ALL" : "SELECTED");
        n.setCompanyIds(all ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        n.setSendEmail(Boolean.TRUE.equals(r.sendEmail()));
        n.setCreatedBy(adminEmail);
        n.setExpiresAt(LocalDateTime.now().plusDays(days));
        n.setCompaniesCount(ids.size());
        n = repo.save(n);

        if (n.isSendEmail()) {
            List<Target> targets = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Long id : ids) {
                for (Map<String, Object> u : jdbc.queryForList("SELECT name, email FROM users WHERE company_id = ? AND frozen = ? AND role IN ('OWNER','ADMIN') AND email IS NOT NULL", id, false)) {
                    String e = String.valueOf(u.get("email"));
                    if (seen.add(e.toLowerCase())) targets.add(new Target(e, String.valueOf(u.get("name"))));
                }
            }
            Long nid = n.getId();
            mailer.execute(() -> {
                int ok = 0;
                for (Target t : targets) {
                    try { if (email.sendPlatformNoticeEmail(t.email(), t.name(), title, message, severity)) ok++; }
                    catch (Exception e) { log.warn("Platform notice email failed: {}", e.toString()); }
                }
                final int sent = ok;
                try { repo.findById(nid).ifPresent(x -> { x.setEmailsSent(sent); repo.save(x); }); } catch (Exception e) { log.warn("Could not record notice result: {}", e.toString()); }
            });
        }
        audit.record(adminId, adminEmail, "ANNOUNCEMENT_SENT", null, null,
                "\"" + title + "\" to " + (all ? "all " + ids.size() + " active companies" : ids.size() + " selected companies") + (n.isSendEmail() ? " with email" : ""), ip);
        return view(n);
    }

    public void withdraw(long id, Long adminId, String adminEmail, String ip) {
        PlatformAnnouncement n = repo.findById(id).orElseThrow(() -> new PlatformException(404, "Announcement not found."));
        n.setWithdrawn(true);
        repo.save(n);
        audit.record(adminId, adminEmail, "ANNOUNCEMENT_WITHDRAWN", null, null, "\"" + n.getTitle() + "\" withdrawn", ip);
    }

    public Map<String, Object> list(int page, int size) {
        var p = repo.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformAnnouncement n : p.getContent()) rows.add(view(n));
        return Map.of("content", rows, "page", p.getNumber(), "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements());
    }

    private static Map<String, Object> view(PlatformAnnouncement n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId()); m.put("title", n.getTitle()); m.put("message", n.getMessage()); m.put("severity", n.getSeverity());
        m.put("audience", n.getAudience()); m.put("companies", n.getCompaniesCount()); m.put("sendEmail", n.isSendEmail());
        m.put("emailsSent", n.getEmailsSent()); m.put("createdBy", n.getCreatedBy()); m.put("createdAt", n.getCreatedAt().toString());
        m.put("expiresAt", n.getExpiresAt().toString());
        m.put("state", n.isWithdrawn() ? "WITHDRAWN" : n.getExpiresAt().isBefore(LocalDateTime.now()) ? "EXPIRED" : "ACTIVE");
        return m;
    }
}
