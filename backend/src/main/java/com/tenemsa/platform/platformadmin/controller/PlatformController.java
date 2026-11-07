package com.tenemsa.platform.platformadmin.controller;

import com.tenemsa.platform.platformadmin.model.PlatformAuditLog;
import com.tenemsa.platform.platformadmin.security.PlatformJwtFilter;
import com.tenemsa.platform.platformadmin.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Everything the platform console does after sign-in. Every method needs a valid platform token. */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
@ConditionalOnProperty(name = "platform.enabled", havingValue = "true")
public class PlatformController {

    public record ReasonRequest(String reason, String confirm) {}
    public record AiRequest(Boolean enabled) {}

    private final PlatformCompanyService companies;
    private final PlatformAuthService auth;
    private final PlatformAuditService audit;

    public PlatformController(PlatformCompanyService companies, PlatformAuthService auth, PlatformAuditService audit) {
        this.companies = companies; this.auth = auth; this.audit = audit;
    }

    private static PlatformJwtFilter.Principal me() {
        return (PlatformJwtFilter.Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        return PlatformAuthService.adminView(auth.find(me().id()).orElseThrow(() -> new PlatformException(401, "Sign in again.")));
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() { return companies.overview(); }

    @GetMapping("/companies")
    public Map<String, Object> list(@RequestParam(required = false) String search, @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "created") String sort, @RequestParam(defaultValue = "true") boolean desc,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return companies.list(search, status, sort, desc, page, size);
    }

    @GetMapping("/companies/{id}")
    public Map<String, Object> detail(@PathVariable long id) { return companies.detail(id); }

    @PostMapping("/companies/{id}/freeze")
    public Map<String, Object> freeze(@PathVariable long id, @RequestBody ReasonRequest r, HttpServletRequest req) {
        companies.freeze(id, r.reason(), me().id(), me().email(), req.getRemoteAddr());
        return companies.detail(id);
    }

    @PostMapping("/companies/{id}/unfreeze")
    public Map<String, Object> unfreeze(@PathVariable long id, @RequestBody(required = false) ReasonRequest r, HttpServletRequest req) {
        companies.unfreeze(id, r == null ? null : r.reason(), me().id(), me().email(), req.getRemoteAddr());
        return companies.detail(id);
    }

    @PostMapping("/companies/{id}/close")
    public Map<String, Object> close(@PathVariable long id, @RequestBody ReasonRequest r, HttpServletRequest req) {
        companies.close(id, r.reason(), r.confirm(), me().id(), me().email(), req.getRemoteAddr());
        return companies.detail(id);
    }

    public record LimitsRequest(String plan, Integer maxUsers, Integer aiDailyLimit) {}

    @PutMapping("/companies/{id}/limits")
    public Map<String, Object> limits(@PathVariable long id, @RequestBody LimitsRequest r, HttpServletRequest req) {
        companies.setLimits(id, r.plan(), r.maxUsers(), r.aiDailyLimit(), me().id(), me().email(), req.getRemoteAddr());
        return companies.detail(id);
    }

    @PostMapping("/companies/{id}/restore")
    public Map<String, Object> restore(@PathVariable long id, HttpServletRequest req) {
        companies.restore(id, me().id(), me().email(), req.getRemoteAddr());
        return companies.detail(id);
    }

    @PutMapping("/companies/{id}/ai")
    public Map<String, Object> ai(@PathVariable long id, @RequestBody AiRequest r, HttpServletRequest req) {
        if (r.enabled() == null) throw new PlatformException(400, "Say whether AI should be on or off.");
        companies.setAi(id, r.enabled(), me().id(), me().email(), req.getRemoteAddr());
        return companies.detail(id);
    }

    @PostMapping("/companies/{id}/resend-owner-verification")
    public Map<String, String> resend(@PathVariable long id, HttpServletRequest req) {
        companies.resendOwnerVerification(id, me().id(), me().email(), req.getRemoteAddr());
        return Map.of("message", "Verification email sent to the owner.");
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
                                     @RequestParam(required = false) String action) {
        var p = audit.page(page, size, action);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformAuditLog l : p.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId()); m.put("createdAt", l.getCreatedAt()); m.put("admin", l.getAdminEmail()); m.put("action", l.getAction());
            m.put("companyId", l.getCompanyId()); m.put("companyName", l.getCompanyName()); m.put("details", l.getDetails()); m.put("ipAddress", l.getIpAddress());
            rows.add(m);
        }
        return Map.of("content", rows, "page", p.getNumber(), "totalPages", p.getTotalPages(), "totalElements", p.getTotalElements());
    }

    @GetMapping("/admins")
    public List<Map<String, Object>> admins() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var a : auth.all()) out.add(PlatformAuthService.adminView(a));
        return out;
    }

    @GetMapping("/system")
    public Map<String, Object> system() { return companies.system(); }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<?> handle(PlatformException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }
}
