package com.tenemsa.platform.security;

import com.tenemsa.platform.model.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single place to read "who is making this request and which company are
 * they in" — reads straight off the JWT-derived Authentication object
 * (see JwtFilter), so no repository lookup is needed just to answer that
 * question.
 */
@Component
public class AuthContext {

    public String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    public Long currentCompanyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object details = auth.getDetails();
        return details instanceof Long ? (Long) details : null;
    }

    /**
     * Reads the caller's Role back out of their "ROLE_X" authority (see
     * JwtFilter — exactly one such authority is set per request, everything
     * else on the Authentication is a Permission key). Needed anywhere
     * business logic depends on WHICH role the caller has, not just whether
     * they hold a given permission — e.g. RoleAssignmentRegistry's
     * "who may create a user of which target role" check, which a generic
     * hasAuthority('user:create') can't express on its own.
     */
    /** Whether the caller's current request carries a given authority (permission key or
     *  "ROLE_X"). Used where a controller needs to branch behavior based on WHICH of two
     *  permitted authorities applies — e.g. EmployeeController.getAll() serving the whole
     *  company for user:view:any vs. just a Manager's team for user:view:team — rather than
     *  just gating access to the method at all via @PreAuthorize. */
    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    public Role currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if (value.startsWith("ROLE_")) {
                try {
                    return Role.valueOf(value.substring(5));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
