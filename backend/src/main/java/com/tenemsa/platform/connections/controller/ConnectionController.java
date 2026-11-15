package com.tenemsa.platform.connections.controller;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.connections.service.ConnectionException;
import com.tenemsa.platform.connections.service.ConnectionService;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Connected apps for the signed-in person. Anyone may connect their own accounts once their company has switched the app on. */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    public record EnabledRequest(Boolean enabled) {}

    private final ConnectionService service;
    private final AuthContext auth;
    private final UserRepository users;
    private final AuditService audit;

    public ConnectionController(ConnectionService service, AuthContext auth, UserRepository users, AuditService audit) {
        this.service = service; this.auth = auth; this.users = users; this.audit = audit;
    }

    private Long me() {
        return users.findByEmailAndCompanyId(auth.currentEmail(), auth.currentCompanyId())
                .orElseThrow(() -> new ConnectionException(401, "Please sign in again.")).getId();
    }

    @GetMapping
    public Map<String, Object> list() { return service.list(auth.currentCompanyId(), me()); }

    @PostMapping("/{provider}/authorize")
    public Map<String, String> authorize(@PathVariable String provider) {
        return Map.of("url", service.authorizeUrl(auth.currentCompanyId(), me(), provider));
    }

    @DeleteMapping("/{provider}")
    public Map<String, Object> disconnect(@PathVariable String provider) {
        service.disconnect(auth.currentCompanyId(), me(), provider);
        return service.list(auth.currentCompanyId(), me());
    }

    @GetMapping("/{provider}/preview")
    public List<Map<String, Object>> preview(@PathVariable String provider) {
        return service.preview(auth.currentCompanyId(), me(), provider);
    }

    /** Owners and admins decide which apps the company allows. */
    @PreAuthorize("hasAuthority('company:manage')")
    @PutMapping("/apps/{provider}")
    public Map<String, Object> enable(@PathVariable String provider, @RequestBody EnabledRequest r) {
        if (r.enabled() == null) throw new ConnectionException(400, "Say whether the app should be on or off.");
        service.setEnabled(auth.currentCompanyId(), provider, r.enabled());
        audit.recordCurrent(AuditAction.APP_SETTING_CHANGED, "CONNECTION", provider, (r.enabled() ? "Switched on " : "Switched off ") + provider);
        return service.list(auth.currentCompanyId(), me());
    }

    @ExceptionHandler(ConnectionException.class)
    public ResponseEntity<?> handle(ConnectionException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
