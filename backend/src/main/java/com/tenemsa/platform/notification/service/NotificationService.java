package com.tenemsa.platform.notification.service;

import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.mail.EmailService;
import com.tenemsa.platform.notification.model.Notification;
import com.tenemsa.platform.notification.model.NotificationPreference;
import com.tenemsa.platform.notification.repository.NotificationPreferenceRepository;
import com.tenemsa.platform.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Sends a notification to specific people in one company: always in the app, and by email too unless the person
 * switched email off. Callers pass the company explicitly, so a notification can never reach another tenant.
 * Email goes out on a background thread and can never fail or slow the request that caused it.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;
    private final EmailService email;
    private final ExecutorService mailExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notification-mail");
        t.setDaemon(true);
        return t;
    });

    public NotificationService(NotificationRepository notifications, NotificationPreferenceRepository preferences,
                               UserRepository users, EmailService email) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.users = users;
        this.email = email;
    }

    /** Never throws: a notification problem must not undo the business action that triggered it. */
    public void send(Long companyId, Collection<Integer> recipientEmpIds, String type, String message, Long leaveId) {
        try {
            Set<Integer> ids = new LinkedHashSet<>();
            for (Integer id : recipientEmpIds) if (id != null) ids.add(id);
            if (ids.isEmpty()) return;
            Map<Integer, User> people = users.findAllByCompanyIdAndEmpIdIn(companyId, ids).stream()
                    .filter(u -> u.getEmpId() != null)
                    .collect(Collectors.toMap(User::getEmpId, u -> u, (a, b) -> a));
            Map<Integer, Boolean> emailOn = preferences.findByCompanyIdAndEmpIdIn(companyId, ids).stream()
                    .collect(Collectors.toMap(NotificationPreference::getEmpId, NotificationPreference::isEmailEnabled, (a, b) -> a));
            String shortMessage = message.length() > 250 ? message.substring(0, 250) : message;
            for (Integer id : ids) {
                User person = people.get(id);
                if (person == null) continue;
                Notification n = new Notification(shortMessage, leaveId);
                n.setCompanyId(companyId);
                n.setRecipientEmpId(id);
                n.setType(type);
                notifications.save(n);
                if (emailOn.getOrDefault(id, true) && person.getEmail() != null && !person.isFrozen()) {
                    String to = person.getEmail();
                    String name = person.getName();
                    mailExecutor.execute(() -> {
                        try { email.sendNotificationEmail(to, name, shortMessage); }
                        catch (Exception e) { log.warn("Notification email failed: {}", e.toString()); }
                    });
                }
            }
        } catch (Exception e) {
            log.warn("Could not create notification '{}': {}", type, e.toString());
        }
    }

    public void send(Long companyId, Integer recipientEmpId, String type, String message, Long leaveId) {
        send(companyId, List.of(recipientEmpId == null ? -1 : recipientEmpId), type, message, leaveId);
    }

    public boolean emailEnabled(Long companyId, Integer empId) {
        return preferences.findByCompanyIdAndEmpId(companyId, empId).map(NotificationPreference::isEmailEnabled).orElse(true);
    }

    public void setEmailEnabled(Long companyId, Integer empId, boolean enabled) {
        NotificationPreference p = preferences.findByCompanyIdAndEmpId(companyId, empId).orElseGet(() -> {
            NotificationPreference fresh = new NotificationPreference();
            fresh.setCompanyId(companyId);
            fresh.setEmpId(empId);
            return fresh;
        });
        p.setEmailEnabled(enabled);
        preferences.save(p);
    }
}
