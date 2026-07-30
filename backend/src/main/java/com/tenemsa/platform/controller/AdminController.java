package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.*;
import com.tenemsa.platform.mapper.UserMapper;
import com.tenemsa.platform.model.Company;
import com.tenemsa.platform.model.Role;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.CompanyRepository;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.security.PasswordPolicy;
import com.tenemsa.platform.security.SecurityRoles;
import com.tenemsa.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthService authService;
    @Autowired AuthContext authContext;
    @Autowired UserMapper userMapper;

    private static final List<Role> ADMIN_ROLES = List.of(Role.OWNER, Role.SUPER_ADMIN);
    // TEMPORARY (Phase 1 only, see CompanyRepository.findFirstByOrderByIdAsc):
    // the app is still single-tenant in practice, so registration bootstraps
    // against one default company rather than letting the caller create one.
    // Phase 4 replaces this with a real "create your company" flow.
    private static final String DEFAULT_COMPANY_SLUG = "default";

    // Public register: ONLY when zero admins exist (first admin/company owner)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        String policyError = PasswordPolicy.validate(req.getPassword());
        if (policyError != null) return ResponseEntity.badRequest().body(policyError);

        Company company = getOrCreateDefaultCompany();
        long existingAdmins = userRepository.countByCompanyIdAndRoleIn(company.getId(), ADMIN_ROLES);
        if (existingAdmins >= 1) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Registration closed. Ask your system administrator to add you via Admin Control.");
        }
        User owner = buildAdminUser(req, company);
        owner.setPrimaryOwner(true);
        userRepository.save(owner);
        authService.sendVerificationEmail(owner);
        return ResponseEntity.ok("Main admin registered successfully");
    }

    // Admin-panel register: authenticated OWNER/SUPER_ADMIN can add up to 4 total
    @PostMapping("/register-by-admin")
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    public ResponseEntity<?> registerByAdmin(@RequestBody RegisterRequest req) {
        String policyError = PasswordPolicy.validate(req.getPassword());
        if (policyError != null) return ResponseEntity.badRequest().body(policyError);

        Long companyId = authContext.currentCompanyId();
        long existingAdmins = userRepository.countByCompanyIdAndRoleIn(companyId, ADMIN_ROLES);
        if (existingAdmins >= 4) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Maximum of 4 admins reached.");
        }
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already registered.");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        User admin = buildAdminUser(req, company);
        admin.setPrimaryOwner(false);
        userRepository.save(admin);
        authService.sendVerificationEmail(admin);
        return ResponseEntity.ok(Map.of("message", "Admin added successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest httpRequest) {
        try {
            User admin = authService.adminLogin(req);
            AuthResponse resp = authService.issueAuthResponse(admin, req.isRememberMe(), deviceLabel(httpRequest));
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("ACCOUNT_FROZEN"))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(msg);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(msg);
        }
    }

    @GetMapping("/exists")
    public ResponseEntity<?> adminExists() {
        Optional<Company> company = companyRepository.findBySlug(DEFAULT_COMPANY_SLUG);
        long count = company.map(c -> userRepository.countByCompanyIdAndRoleIn(c.getId(), ADMIN_ROLES)).orElse(0L);
        return ResponseEntity.ok(Map.of("exists", count > 0, "full", count >= 4, "firstTime", count == 0));
    }

    @GetMapping("/all")
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    public ResponseEntity<List<UserResponseDTO>> getAllAdmins() {
        // Returns UserResponseDTO now, not raw User — see UserResponseDTO's
        // class comment. The old comment here about @JsonProperty(WRITE_ONLY)
        // covering the password field was true but incomplete: it protected
        // against leaking the password, not against the separate
        // Hibernate-proxy serialization crash on User.company (see
        // UserRepository's history) — a DTO fixes both categories at once
        // by construction, not by remembering to annotate every risky field.
        List<UserResponseDTO> admins = userRepository.findAllByCompanyId(authContext.currentCompanyId())
                .stream()
                .filter(u -> ADMIN_ROLES.contains(u.getRole()))
                .map(userMapper::toDto)
                .toList();
        return ResponseEntity.ok(admins);
    }

    // Only the primary owner can freeze/unfreeze/delete other admins
    @PutMapping("/{id}/freeze")
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    public ResponseEntity<?> freeze(@PathVariable Long id) {
        return withMainAdminCheck(() -> userRepository.findById(id).map(a -> {
            if (a.isPrimaryOwner()) return ResponseEntity.badRequest().body((Object) "Cannot freeze main admin");
            a.setFrozen(true);
            userRepository.save(a);
            return ResponseEntity.ok(Map.of("message", "Admin frozen"));
        }).orElse(ResponseEntity.notFound().build()));
    }

    @PutMapping("/{id}/unfreeze")
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    public ResponseEntity<?> unfreeze(@PathVariable Long id) {
        return withMainAdminCheck(() -> userRepository.findById(id).map(a -> {
            a.setFrozen(false);
            userRepository.save(a);
            return ResponseEntity.ok(Map.of("message", "Admin unfrozen"));
        }).orElse(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return withMainAdminCheck(() -> {
            User a = userRepository.findById(id).orElse(null);
            if (a == null) return ResponseEntity.notFound().build();
            if (a.isPrimaryOwner()) return ResponseEntity.badRequest().body("Cannot delete main admin");
            userRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Admin deleted"));
        });
    }

    // Previously took the Authorization header and re-decoded the JWT by
    // hand to find the caller. AuthContext already does this once per
    // request in JwtFilter, so this just reads that instead of parsing the
    // token a second time.
    private ResponseEntity<?> withMainAdminCheck(java.util.function.Supplier<ResponseEntity<?>> action) {
        User caller = userRepository.findByEmail(authContext.currentEmail()).orElse(null);
        if (caller == null || !caller.isPrimaryOwner())
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the main administrator can perform this action");
        return action.get();
    }

    private User buildAdminUser(RegisterRequest req, Company company) {
        User u = new User();
        u.setCompany(company);
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole(Role.OWNER);
        u.setFrozen(false);
        return u;
    }

    private Company getOrCreateDefaultCompany() {
        return companyRepository.findBySlug(DEFAULT_COMPANY_SLUG)
                .orElseGet(() -> companyRepository.save(new Company("Default Company", DEFAULT_COMPANY_SLUG)));
    }

    private String deviceLabel(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
