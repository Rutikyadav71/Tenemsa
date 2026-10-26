package com.tenemsa.platform.audit.controller;

import com.tenemsa.platform.audit.dto.AuditLogResponse;
import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only view of the caller's own company audit trail. Tenant comes from the JWT. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final AuthContext authContext;

    public AuditController(AuditService auditService, AuthContext authContext) {
        this.auditService = auditService;
        this.authContext = authContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String action) {
        Page<AuditLogResponse> result = auditService
                .search(authContext.currentCompanyId(), action, page, size)
                .map(AuditLogResponse::from);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('audit:view')")
    public List<String> actions() {
        return Arrays.stream(AuditAction.values()).map(Enum::name).toList();
    }
}
