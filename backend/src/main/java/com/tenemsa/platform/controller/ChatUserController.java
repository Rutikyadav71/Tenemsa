package com.tenemsa.platform.controller;

import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Previously ran raw SQL directly against the old "employees" and "admins"
 * tables by name — that would have broken outright the moment those tables
 * were replaced by the unified "users" table (not a compile error since it's
 * a string, but a runtime SQL failure on first call). It also had no company
 * scoping at all: any authenticated user could list every chat-eligible user
 * across every company, which is a real cross-tenant data leak the moment a
 * second company exists — closing that here since User is already
 * company-scoped as of this phase, even though Attendance/Leave/Team aren't
 * yet (see LeaveService for why that's a deliberate, separate deferral).
 */
@RestController
public class ChatUserController {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AuthContext authContext;

    @GetMapping("/api/chat-users")
    public Map<String, Object> getAllChatUsers() {
        Long companyId = authContext.currentCompanyId();

        // The chat/WebSocket layer routes by a ROLE_ID composite key (see
        // chatService.js: "/user/{role}_{userId}/queue/messages") using a
        // fixed two-value "ADMIN"/"USER" tag — that's an independent
        // routing-protocol convention, not the six-tier business Role
        // hierarchy. Returning the real enum value here (e.g. "OWNER")
        // would silently break delivery: a message addressed to
        // receiverRole "OWNER" would publish to queue OWNER_5 while that
        // person's browser is subscribed to ADMIN_5 (built from their own
        // login-time role tag). Aliasing back to the stable two-value tag
        // in SQL keeps the entire chat subsystem working unchanged while
        // still using the real Role enum to decide bucket membership.
        List<Map<String, Object>> employees = jdbcTemplate.queryForList(
            "SELECT emp_id AS id, name, email, department, profile_image_url AS profileImageUrl, " +
            "'USER' AS role FROM users WHERE company_id = ? AND role NOT IN ('OWNER','SUPER_ADMIN') ORDER BY name ASC",
            companyId);

        List<Map<String, Object>> admins = jdbcTemplate.queryForList(
            "SELECT id, name, email, NULL AS department, profile_image_url AS profileImageUrl, " +
            "'ADMIN' AS role FROM users WHERE company_id = ? AND role IN ('OWNER','SUPER_ADMIN') " +
            "AND (frozen = false OR frozen IS NULL) ORDER BY name ASC",
            companyId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employees", employees);
        result.put("admins", admins);
        return result;
    }
}
