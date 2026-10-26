package com.tenemsa.platform.company.controller;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.auth.dto.AuthResponse;
import com.tenemsa.platform.auth.service.AuthService;
import com.tenemsa.platform.company.dto.CompanyRegisterRequest;
import com.tenemsa.platform.company.dto.CompanyResponse;
import com.tenemsa.platform.company.service.CompanyService;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Company (tenant) lifecycle.
 *  - POST /register and GET /lookup are public (a visitor has no account yet).
 *  - /me endpoints always act on the CALLER's company, taken from the JWT —
 *    there is deliberately no /{id} variant, so one tenant can't address another.
 */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    @Autowired private CompanyService companyService;
    @Autowired private AuthService authService;
    @Autowired private AuthContext authContext;
    @Autowired private AuditService auditService;

    /** Creates the company and its Owner, and signs the Owner straight in. */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody CompanyRegisterRequest req, HttpServletRequest httpRequest) {
        try {
            User owner = companyService.register(req);
            String ua = httpRequest.getHeader("User-Agent");
            AuthResponse resp = authService.issueAuthResponse(owner, false,
                    ua != null && ua.length() > 255 ? ua.substring(0, 255) : ua);
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (CompanyService.RegistrationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** "Find your workspace": returns just the display name for a slug. */
    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String slug) {
        return companyService.lookup(slug)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "We couldn't find that workspace.")));
    }

    @GetMapping("/me")
    public CompanyResponse me() {
        return companyService.get(authContext.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('company:manage')")
    @PutMapping("/me")
    public ResponseEntity<?> rename(@RequestBody Map<String, String> body) {
        try {
            var updated = companyService.rename(authContext.currentCompanyId(), body.get("name"));
            auditService.recordCurrent(AuditAction.COMPANY_RENAMED, "COMPANY", String.valueOf(updated.id()),
                    "Renamed company to \"" + updated.name() + "\"");
            return ResponseEntity.ok(updated);
        } catch (CompanyService.RegistrationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
