package com.tenemsa.platform.notification.controller;

import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.notification.dto.NotificationResponse;
import com.tenemsa.platform.notification.dto.NotificationSettingsRequest;
import com.tenemsa.platform.notification.model.Notification;
import com.tenemsa.platform.notification.repository.NotificationRepository;
import com.tenemsa.platform.notification.service.NotificationService;
import com.tenemsa.platform.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Notifications belong to a person. You see yours; people who approve leave company-wide (leave:approve:any) also see
 * the older company-wide ones created before notifications were personal. Nobody can read or change someone else's.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final int LIMIT = 100;

    private final NotificationRepository repo;
    private final NotificationService service;
    private final UserRepository users;
    private final AuthContext authContext;

    public NotificationController(NotificationRepository repo, NotificationService service, UserRepository users, AuthContext authContext) {
        this.repo = repo;
        this.service = service;
        this.users = users;
        this.authContext = authContext;
    }

    private Long cid() { return authContext.currentCompanyId(); }

    private Integer me() {
        return users.findByEmailAndCompanyId(authContext.currentEmail(), cid())
                .map(u -> u.getEmpId()).orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }

    private boolean legacy() { return authContext.hasAuthority("leave:approve:any"); }

    @GetMapping
    public List<NotificationResponse> mine() {
        return repo.findVisible(cid(), me(), legacy(), PageRequest.of(0, LIMIT)).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", repo.countUnread(cid(), me(), legacy()));
    }

    @PutMapping("/mark-all-read")
    @Transactional
    public void markAllRead() {
        repo.markAllRead(cid(), me(), legacy());
    }

    @PutMapping("/{id}/read")
    @Transactional
    public NotificationResponse markRead(@PathVariable Long id) {
        Notification n = repo.findVisibleById(id, cid(), me(), legacy()).orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        n.setRead(true);
        return NotificationResponse.from(repo.save(n));
    }

    @GetMapping("/settings")
    public Map<String, Boolean> settings() {
        return Map.of("emailEnabled", service.emailEnabled(cid(), me()));
    }

    @PutMapping("/settings")
    public Map<String, Boolean> updateSettings(@Valid @RequestBody NotificationSettingsRequest req) {
        service.setEmailEnabled(cid(), me(), req.emailEnabled());
        return Map.of("emailEnabled", req.emailEnabled());
    }
}
