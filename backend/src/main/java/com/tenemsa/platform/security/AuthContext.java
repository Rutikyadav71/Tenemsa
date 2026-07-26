package com.tenemsa.platform.security;

import org.springframework.security.core.Authentication;
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
}
