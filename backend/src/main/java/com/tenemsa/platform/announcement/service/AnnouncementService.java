package com.tenemsa.platform.announcement.service;

import com.tenemsa.platform.announcement.dto.AnnouncementRequest;
import com.tenemsa.platform.announcement.model.Announcement;
import com.tenemsa.platform.announcement.model.PlatformAnnouncement;
import com.tenemsa.platform.announcement.repository.AnnouncementRepository;
import com.tenemsa.platform.announcement.repository.PlatformAnnouncementRepository;
import com.tenemsa.platform.company.model.Company;
import com.tenemsa.platform.company.repository.CompanyRepository;
import com.tenemsa.platform.employee.model.Role;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.mail.EmailService;
import com.tenemsa.platform.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Company announcements: pick people, send in-app and (optionally) by email, keep a history. */
@Service
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);
    public static final int MAX_RECIPIENTS = 1000;

    public static class AnnouncementException extends RuntimeException {
        public AnnouncementException(String m) { super(m); }
    }

    private record Target(String email, String name) {}

    private final UserRepository users;
    private final CompanyRepository companies;
    private final AnnouncementRepository announcements;
    private final PlatformAnnouncementRepository platformNotices;
    private final NotificationService notifications;
    private final EmailService email;

    @Value("${app.announcements.per-day:10}")
    private int perDay;

    private final ExecutorService mailer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "announcement-mailer"); t.setDaemon(true); return t;
    });

    public AnnouncementService(UserRepository users, CompanyRepository companies, AnnouncementRepository announcements,
                               PlatformAnnouncementRepository platformNotices, NotificationService notifications, EmailService email) {
        this.users = users; this.companies = companies; this.announcements = announcements;
        this.platformNotices = platformNotices; this.notifications = notifications; this.email = email;
    }

    /** Everyone the request selects: active people of this company who have an email address. */
    public List<User> recipients(Long companyId, AnnouncementRequest r) {
        String type = r.audience() == null ? "" : r.audience().trim().toUpperCase();
        Set<Role> roles = new HashSet<>();
        if (type.equals("ROLES")) {
            for (String s : nz(r.roles())) {
                try { Role role = Role.valueOf(s.trim().toUpperCase()); if (role != Role.SUPER_ADMIN) roles.add(role); }
                catch (IllegalArgumentException e) { throw new AnnouncementException("Unknown role: " + s); }
            }
            if (roles.isEmpty()) throw new AnnouncementException("Choose at least one role.");
        }
        Set<String> depts = nz(r.departments()).stream().map(d -> d.trim().toLowerCase()).filter(d -> !d.isEmpty()).collect(Collectors.toSet());
        if (type.equals("DEPARTMENTS") && depts.isEmpty()) throw new AnnouncementException("Choose at least one department.");
        Set<Integer> ids = new HashSet<>(nz(r.empIds()));
        if (type.equals("PEOPLE") && ids.isEmpty()) throw new AnnouncementException("Choose at least one person.");
        if (!Set.of("ALL", "ROLES", "DEPARTMENTS", "PEOPLE").contains(type)) throw new AnnouncementException("Choose who should receive it.");

        List<User> out = new ArrayList<>();
        for (User u : users.findAllByCompanyId(companyId)) {
            if (u.isFrozen() || u.getRole() == Role.SUPER_ADMIN || u.getEmail() == null || u.getEmail().isBlank()) continue;
            boolean in = switch (type) {
                case "ROLES" -> roles.contains(u.getRole());
                case "DEPARTMENTS" -> u.getDepartment() != null && depts.contains(u.getDepartment().trim().toLowerCase());
                case "PEOPLE" -> u.getEmpId() != null && ids.contains(u.getEmpId());
                default -> true;
            };
            if (in) out.add(u);
        }
        return out;
    }

    public Map<String, Object> preview(Long companyId, AnnouncementRequest r) {
        List<User> list = recipients(companyId, r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", list.size());
        m.put("sample", list.stream().limit(5).map(User::getName).toList());
        m.put("tooMany", list.size() > MAX_RECIPIENTS);
        return m;
    }

    public Announcement send(Long companyId, User sender, AnnouncementRequest r) {
        if (announcements.countByCompanyIdAndCreatedAtAfter(companyId, LocalDateTime.now().minusHours(24)) >= perDay)
            throw new AnnouncementException("You can send up to " + perDay + " announcements a day. Try again tomorrow.");
        List<User> list = recipients(companyId, r);
        if (list.isEmpty()) throw new AnnouncementException("No one matches that audience, so nothing was sent.");
        if (list.size() > MAX_RECIPIENTS) throw new AnnouncementException("That is more than " + MAX_RECIPIENTS + " people. Choose a smaller audience.");
        Company company = companies.findById(companyId).orElseThrow(() -> new AnnouncementException("Company not found."));

        String subject = r.subject().trim().replaceAll("[\\r\\n]+", " ");
        String body = r.body().trim();

        Announcement a = new Announcement();
        a.setCompanyId(companyId);
        a.setSentByEmpId(sender.getEmpId());
        a.setSentByName(sender.getName());
        a.setSubject(subject);
        a.setBody(body);
        a.setAudienceLabel(label(r, list.size()));
        a.setRecipients(list.size());
        a.setSendEmail(r.sendEmail());
        a.setStatus(r.sendEmail() ? "SENDING" : "DONE");
        a = announcements.save(a);

        notifications.sendInAppOnly(companyId, list.stream().map(User::getEmpId).filter(Objects::nonNull).toList(),
                "ANNOUNCEMENT", "Announcement from " + sender.getName() + ": " + subject);

        if (r.sendEmail()) {
            List<Target> targets = list.stream().map(u -> new Target(u.getEmail(), u.getName())).toList();
            Long id = a.getId();
            String companyName = company.getName(), senderName = sender.getName();
            mailer.execute(() -> deliver(id, targets, companyName, senderName, subject, body));
        }
        return a;
    }

    private void deliver(Long id, List<Target> targets, String company, String sender, String subject, String body) {
        int ok = 0, bad = 0;
        for (Target t : targets) {
            try { if (email.sendAnnouncementEmail(t.email(), t.name(), company, sender, subject, body)) ok++; else bad++; }
            catch (Exception e) { bad++; log.warn("Announcement email failed: {}", e.toString()); }
        }
        try {
            Announcement a = announcements.findById(id).orElse(null);
            if (a != null) { a.setEmailsSent(ok); a.setEmailsFailed(bad); a.setStatus("DONE"); announcements.save(a); }
        } catch (Exception e) { log.warn("Could not record announcement result: {}", e.toString()); }
    }

    private static String label(AnnouncementRequest r, int n) {
        String t = r.audience().trim().toUpperCase();
        String what = switch (t) {
            case "ROLES" -> "Roles: " + String.join(", ", nz(r.roles()));
            case "DEPARTMENTS" -> "Departments: " + String.join(", ", nz(r.departments()));
            case "PEOPLE" -> "Selected people";
            default -> "Everyone";
        };
        String s = what + " (" + n + ")";
        return s.length() > 250 ? s.substring(0, 247) + "..." : s;
    }

    public Map<String, Object> history(Long companyId, int page, int size) {
        var p = announcements.findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50)));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Announcement a : p.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("subject", a.getSubject()); m.put("body", a.getBody());
            m.put("audience", a.getAudienceLabel()); m.put("recipients", a.getRecipients());
            m.put("sentBy", a.getSentByName()); m.put("sendEmail", a.isSendEmail());
            m.put("emailsSent", a.getEmailsSent()); m.put("emailsFailed", a.getEmailsFailed());
            m.put("status", a.getStatus()); m.put("createdAt", a.getCreatedAt().toString());
            rows.add(m);
        }
        return Map.of("content", rows, "page", p.getNumber(), "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements());
    }

    /** Notices from the Tenemsa team that apply to this company right now. */
    public List<Map<String, Object>> platformNoticesFor(Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlatformAnnouncement n : platformNotices.findByWithdrawnFalseAndExpiresAtAfterOrderByCreatedAtDesc(LocalDateTime.now())) {
            if (!"ALL".equals(n.getAudience())) {
                Set<String> ids = new HashSet<>(Arrays.asList((n.getCompanyIds() == null ? "" : n.getCompanyIds()).split(",")));
                if (!ids.contains(String.valueOf(companyId))) continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId()); m.put("title", n.getTitle()); m.put("message", n.getMessage());
            m.put("severity", n.getSeverity()); m.put("createdAt", n.getCreatedAt().toString());
            out.add(m);
        }
        return out;
    }

    private static <T> List<T> nz(List<T> l) { return l == null ? List.of() : l; }
}
