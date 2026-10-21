package com.tenemsa.platform.security;

import com.tenemsa.platform.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Bean name intentionally kept as "employeeSecurity" — it's referenced from
 * @PreAuthorize SpEL expressions (@employeeSecurity.isSelf(...)) across
 * EmployeeController, LeaveController, etc., and renaming it would mean
 * touching every one of those annotations for no behavioral benefit.
 */
@Component
public class EmployeeSecurity {

    private final UserRepository userRepository;

    public EmployeeSecurity(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static Long currentCompanyId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof Long id ? id : null;
    }

    public boolean isSelf(int empId) {
        // principal = email (from JwtFilter)
        String loggedInEmail =
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        // Previously this looked up the target row by empId FIRST, then
        // compared its email to the caller's. That was safe when empId was
        // globally unique. Now that empId is only unique per company (see
        // User's uk_users_company_empid constraint), looking it up by empId
        // alone with no company filter could match a DIFFERENT company's
        // employee who happens to share the same empId - a real cross-tenant
        // data leak this change would otherwise introduce silently.
        //
        // Looking the caller up by their own (globally unique) email first
        // and comparing empId the other way around sidesteps the ambiguity
        // entirely: there's no company to disambiguate because we already
        // know exactly which row "the caller" is.
        return userRepository.findByEmailAndCompanyId(loggedInEmail, currentCompanyId())
                .map(user -> user.getEmpId() != null && user.getEmpId() == empId)
                .orElse(false);
    }
}
