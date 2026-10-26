package com.tenemsa.platform.company.service;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.auth.service.AuthService;
import com.tenemsa.platform.company.dto.CompanyRegisterRequest;
import com.tenemsa.platform.company.dto.CompanyResponse;
import com.tenemsa.platform.company.dto.WorkspaceLookupResponse;
import com.tenemsa.platform.company.model.Company;
import com.tenemsa.platform.company.repository.CompanyRepository;
import com.tenemsa.platform.employee.model.Role;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Optional;
import java.util.Set;

/**
 * Company lifecycle: self-service registration (company + first Owner in ONE
 * transaction, so a half-created tenant can never exist), workspace lookup for
 * the sign-in flow, and company profile reads/updates.
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    /** Slugs that would collide with app routes or look official. */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "admin", "api", "app", "auth", "login", "logout", "register", "workspace", "www",
            "tenemsa", "support", "help", "root", "static", "assets", "oauth", "ws");

    private static final int MAX_NAME = 150;

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;
    @Autowired private AuditService auditService;

    /** Thrown for any user-correctable problem; the controller maps it to HTTP 400. */
    public static class RegistrationException extends RuntimeException {
        public RegistrationException(String message) { super(message); }
    }

    /** Creates the company and its Owner. Returns the Owner so the caller can sign them in. */
    @Transactional
    public User register(CompanyRegisterRequest req) {
        String companyName = clean(req.companyName());
        String ownerName = clean(req.ownerName());
        String email = req.email() == null ? "" : req.email().trim();

        if (companyName.length() < 2) throw new RegistrationException("Company name must be at least 2 characters.");
        if (companyName.length() > MAX_NAME) throw new RegistrationException("Company name is too long.");
        if (ownerName.length() < 2) throw new RegistrationException("Your name must be at least 2 characters.");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new RegistrationException("Enter a valid email address.");
        String policyError = PasswordPolicy.validate(req.password());
        if (policyError != null) throw new RegistrationException(policyError);

        Company company = companyRepository.save(new Company(companyName, uniqueSlug(companyName)));

        User owner = new User();
        owner.setCompany(company);
        owner.setName(ownerName);
        owner.setEmail(email);
        owner.setPassword(passwordEncoder.encode(req.password()));
        owner.setRole(Role.OWNER);
        owner.setPrimaryOwner(true);
        owner.setFrozen(false);
        owner.setEmpId(1); // first user of a brand-new company
        owner = userRepository.save(owner);
        auditService.record(company.getId(), owner.getId(), owner.getName(), AuditAction.COMPANY_REGISTERED,
                "COMPANY", String.valueOf(company.getId()), "Workspace \"" + company.getName() + "\" created");

        try {
            authService.sendVerificationEmail(owner);
        } catch (Exception e) {
            // A mail outage must not block creating a workspace; the user can resend from the app.
            log.warn("Could not send verification email for new company {}: {}", company.getSlug(), e.getMessage());
        }
        return owner;
    }

    public Optional<WorkspaceLookupResponse> lookup(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        return companyRepository.findBySlug(slug.trim().toLowerCase())
                .map(c -> new WorkspaceLookupResponse(c.getName(), c.getSlug()));
    }

    public CompanyResponse get(Long companyId) {
        Company c = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        return toResponse(c);
    }

    @Transactional
    public CompanyResponse rename(Long companyId, String newName) {
        String name = clean(newName);
        if (name.length() < 2 || name.length() > MAX_NAME) {
            throw new RegistrationException("Company name must be between 2 and " + MAX_NAME + " characters.");
        }
        Company c = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        c.setName(name); // slug is deliberately immutable: it is the workspace's stable address
        return toResponse(companyRepository.save(c));
    }

    private CompanyResponse toResponse(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getSlug(), c.getCreatedAt(),
                userRepository.countByCompanyId(c.getId()));
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    /** "Acme Corp!" -> "acme-corp"; on collision "acme-corp-2", "acme-corp-3", ... */
    String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int n = 2;
        while (RESERVED_SLUGS.contains(candidate) || companyRepository.existsBySlug(candidate)) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    static String slugify(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("-+$", "");
        return s.length() < 3 ? "workspace" : s;
    }
}
