package com.tenemsa.platform.announcement.controller;

import com.tenemsa.platform.announcement.dto.AnnouncementRequest;
import com.tenemsa.platform.announcement.service.AnnouncementService;
import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService service;
    private final AuthContext auth;
    private final UserRepository users;
    private final AuditService audit;

    public AnnouncementController(AnnouncementService service, AuthContext auth, UserRepository users, AuditService audit) {
        this.service = service; this.auth = auth; this.users = users; this.audit = audit;
    }

    /** How many people an audience reaches, before sending. */
    @PreAuthorize("hasAuthority('announcement:send')")
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody AnnouncementRequest req) {
        return service.preview(auth.currentCompanyId(), req);
    }

    @PreAuthorize("hasAuthority('announcement:send')")
    @PostMapping
    public ResponseEntity<?> send(@Valid @RequestBody AnnouncementRequest req) {
        Long companyId = auth.currentCompanyId();
        User sender = users.findByEmailAndCompanyId(auth.currentEmail(), companyId).orElseThrow();
        var a = service.send(companyId, sender, req);
        audit.recordCurrent(AuditAction.ANNOUNCEMENT_SENT, "ANNOUNCEMENT", String.valueOf(a.getId()),
                "Sent \"" + a.getSubject() + "\" to " + a.getAudienceLabel());
        return ResponseEntity.status(201).body(Map.of("id", a.getId(), "recipients", a.getRecipients(), "emailing", a.isSendEmail()));
    }

    @PreAuthorize("hasAuthority('announcement:send')")
    @GetMapping
    public Map<String, Object> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.history(auth.currentCompanyId(), page, size);
    }

    /** Notices from the Tenemsa team for the signed-in person's company. Every role may read these. */
    @GetMapping("/platform")
    public List<Map<String, Object>> platformNotices() {
        return service.platformNoticesFor(auth.currentCompanyId());
    }

    @ExceptionHandler(AnnouncementService.AnnouncementException.class)
    public ResponseEntity<?> bad(AnnouncementService.AnnouncementException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
