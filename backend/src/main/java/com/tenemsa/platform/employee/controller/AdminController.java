package com.tenemsa.platform.employee.controller;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.auth.dto.AuthResponse;
import com.tenemsa.platform.auth.dto.LoginRequest;
import com.tenemsa.platform.auth.dto.RegisterRequest;
import com.tenemsa.platform.auth.service.AuthService;
import com.tenemsa.platform.company.model.Company;
import com.tenemsa.platform.company.repository.CompanyRepository;
import com.tenemsa.platform.employee.dto.UserResponseDTO;
import com.tenemsa.platform.employee.model.Role;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.employee.service.UserMapper;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.security.PasswordPolicy;
import com.tenemsa.platform.security.SecurityRoles;
import com.tenemsa.platform.security.rbac.RoleAssignmentRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    @Autowired AuditService auditService;

    // RBAC phase 3: ADMIN added as its own role — this list now covers
    // everyone who counts as "an admin panel account" for headcount/listing
    // purposes (was previously just OWNER/SUPER_ADMIN, before ADMIN existed
    // as a distinct value).
    private static final List<Role> ADMIN_ROLES = List.of(Role.OWNER, Role.ADMIN, Role.SUPER_ADMIN);

    // Only Owner creates Admin or HR accounts (spec: "Owner: Create Admins,
    // Create HR users") — Manager/Employee/Intern creation goes through
    // EmployeeController.create() instead, which has its own broader
    // RoleAssignmentRegistry-driven check for Admin/HR creating their
    // respective tiers.
    private static final Set<Role> REGISTERABLE_BY_ADMIN_PANEL = Set.of(Role.ADMIN, Role.HR);

    // Creating a company (and its first Owner) is no longer done here: it's
    // the public POST /api/companies/register (CompanyController), so any
    // number of companies can sign up. This controller now only manages the
    // admin-tier accounts INSIDE the caller's own company.

    // Admin-panel register: Owner-only, creates an Admin or HR account.
    // (Previously open to anyone passing SecurityRoles.IS_ADMIN and always
    // created another OWNER — wrong on both counts once ADMIN became a
    // distinct role: Admin creating more Owners has no basis in the spec,
    // and Admin/HR accounts are what this endpoint should actually produce.)
    @PostMapping("/register-by-admin")
    @PreAuthorize(SecurityRoles.IS_OWNER)
    public ResponseEntity<?> registerByAdmin(@RequestBody RegisterRequest req) {
        String policyError = PasswordPolicy.validate(req.getPassword());
        if (policyError != null) return ResponseEntity.badRequest().body(policyError);

        Role targetRole = req.getRole();
        if (targetRole == null || !REGISTERABLE_BY_ADMIN_PANEL.contains(targetRole)) {
            return ResponseEntity.badRequest().body("role must be ADMIN or HR for this endpoint.");
        }
        Role callerRole = authContext.currentRole();
        if (!RoleAssignmentRegistry.canAssign(callerRole, targetRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Your role (" + callerRole + ") cannot create a user with role " + targetRole + ".");
        }

        Long companyId = authContext.currentCompanyId();
        long existingAdmins = userRepository.countByCompanyIdAndRoleIn(companyId, ADMIN_ROLES);
        if (existingAdmins >= 4) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Maximum of 4 admin-tier accounts reached.");
        }
        if (userRepository.existsByEmailAndCompanyId(req.getEmail(), companyId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already registered in this company.");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        User newUser = buildUser(req, company, targetRole);
        newUser.setPrimaryOwner(false);
        userRepository.save(newUser);
        authService.sendVerificationEmail(newUser);
        auditService.recordCurrent(AuditAction.USER_CREATED, "USER", String.valueOf(newUser.getId()),
                "Created " + targetRole + " account for " + newUser.getName());
        return ResponseEntity.ok(Map.of("message", targetRole + " added successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpRequest) {
        try {
            User admin = authService.adminLogin(req);
            AuthResponse resp = authService.issueAuthResponse(admin, req.isRememberMe(), deviceLabel(httpRequest));
            auditService.record(admin.getCompany().getId(), admin.getId(), admin.getName(),
                    AuditAction.LOGIN_SUCCESS, "USER", String.valueOf(admin.getId()), "Signed in to the admin panel");
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("ACCOUNT_FROZEN"))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(msg);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(msg);
        }
    }

    @GetMapping("/all")
    @PreAuthorize(SecurityRoles.IS_OWNER_OR_ADMIN)
    public ResponseEntity<List<UserResponseDTO>> getAllAdmins() {
        List<UserResponseDTO> admins = userRepository.findAllByCompanyId(authContext.currentCompanyId())
                .stream()
                .filter(u -> ADMIN_ROLES.contains(u.getRole()))
                .map(userMapper::toDto)
                .toList();
        return ResponseEntity.ok(admins);
    }

    // Only the primary owner can freeze/unfreeze/delete other admin-tier
    // accounts — the @PreAuthorize gate below narrows to Owner-tier callers
    // at all, withMainAdminCheck narrows further to the specific primary
    // owner (a data fact, not a role — see User.primaryOwner).
    @PutMapping("/{id}/freeze")
    @PreAuthorize(SecurityRoles.IS_OWNER)
    public ResponseEntity<?> freeze(@PathVariable Long id) {
        return withMainAdminCheck(() -> userRepository.findByIdAndCompanyId(id, authContext.currentCompanyId()).map(a -> {
            if (a.isPrimaryOwner()) return ResponseEntity.badRequest().body((Object) "Cannot freeze main admin");
            a.setFrozen(true);
            userRepository.save(a);
            auditService.recordCurrent(AuditAction.USER_FROZEN, "USER", String.valueOf(a.getId()), "Froze " + a.getName());
            return ResponseEntity.ok(Map.of("message", "Admin frozen"));
        }).orElse(ResponseEntity.notFound().build()));
    }

    @PutMapping("/{id}/unfreeze")
    @PreAuthorize(SecurityRoles.IS_OWNER)
    public ResponseEntity<?> unfreeze(@PathVariable Long id) {
        return withMainAdminCheck(() -> userRepository.findByIdAndCompanyId(id, authContext.currentCompanyId()).map(a -> {
            a.setFrozen(false);
            userRepository.save(a);
            auditService.recordCurrent(AuditAction.USER_UNFROZEN, "USER", String.valueOf(a.getId()), "Unfroze " + a.getName());
            return ResponseEntity.ok(Map.of("message", "Admin unfrozen"));
        }).orElse(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.IS_OWNER)
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return withMainAdminCheck(() -> {
            User a = userRepository.findByIdAndCompanyId(id, authContext.currentCompanyId()).orElse(null);
            if (a == null) return ResponseEntity.notFound().build();
            if (a.isPrimaryOwner()) return ResponseEntity.badRequest().body("Cannot delete main admin");
            userRepository.deleteById(id);
            auditService.recordCurrent(AuditAction.USER_DELETED, "USER", String.valueOf(id), "Deleted " + a.getName());
            return ResponseEntity.ok(Map.of("message", "Admin deleted"));
        });
    }

    // Previously took the Authorization header and re-decoded the JWT by
    // hand to find the caller. AuthContext already does this once per
    // request in JwtFilter, so this just reads that instead of parsing the
    // token a second time.
    private ResponseEntity<?> withMainAdminCheck(java.util.function.Supplier<ResponseEntity<?>> action) {
        User caller = userRepository.findByEmailAndCompanyId(authContext.currentEmail(), authContext.currentCompanyId()).orElse(null);
        if (caller == null || !caller.isPrimaryOwner())
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only the main administrator can perform this action");
        return action.get();
    }

    private User buildUser(RegisterRequest req, Company company, Role role) {
        User u = new User();
        u.setCompany(company);
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole(role);
        u.setFrozen(false);
        // Every user gets a per-company empId (chat routing and attendance key off it).
        u.setEmpId(userRepository.nextEmpId(company.getId()));
        return u;
    }

    private String deviceLabel(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
