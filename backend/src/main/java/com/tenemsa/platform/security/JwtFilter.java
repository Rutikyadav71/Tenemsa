package com.tenemsa.platform.security;

import com.tenemsa.platform.employee.model.Role;
import com.tenemsa.platform.security.rbac.Permission;
import com.tenemsa.platform.security.rbac.RolePermissionRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwtUtil.isTokenValid(token)) {
                    String email = jwtUtil.extractEmail(token);
                    String roleClaim = jwtUtil.extractRole(token);
                    Long companyId = jwtUtil.extractCompanyId(token);

                    List<GrantedAuthority> authorities = new ArrayList<>();
                    // Coarse role authority kept alongside permissions —
                    // hasRole('OWNER') style checks are still legitimate for
                    // a handful of things that are genuinely role-identity
                    // checks rather than capability checks (e.g. "only an
                    // Owner or Admin may freeze another admin account").
                    // Most @PreAuthorize checks should prefer hasAuthority(...)
                    // with a Permission key instead — see docs/rbac-design.md.
                    String roleName = roleClaim != null && roleClaim.startsWith("ROLE_")
                            ? roleClaim.substring(5) : roleClaim;
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

                    // Expand role -> permission set. This is the one place
                    // a request's Role becomes a set of fine-grained
                    // authorities every @PreAuthorize("hasAuthority(...)")
                    // check downstream relies on. If RolePermissionRegistry
                    // is ever swapped for a database-backed lookup (Phase B,
                    // custom company roles), this is the only call site that
                    // changes.
                    try {
                        Role role = Role.valueOf(roleName);
                        for (Permission p : RolePermissionRegistry.permissionsFor(role)) {
                            authorities.add(new SimpleGrantedAuthority(p.getKey()));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Unknown/legacy role claim — falls back to the
                        // coarse ROLE_ authority only, no permissions.
                    }

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);
                    // Tenant context for this request, read from the token instead
                    // of a per-request DB lookup. Retrieve via
                    // (Long) SecurityContextHolder.getContext().getAuthentication().getDetails()
                    // — see AuthContext for a small wrapper around that cast.
                    auth.setDetails(companyId);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {}
        }

        filterChain.doFilter(request, response);
    }
}
