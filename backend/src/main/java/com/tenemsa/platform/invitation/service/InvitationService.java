package com.tenemsa.platform.invitation.service;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.auth.service.TokenService;
import com.tenemsa.platform.company.model.Company;
import com.tenemsa.platform.company.repository.CompanyRepository;
import com.tenemsa.platform.employee.model.Role;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.invitation.dto.InvitationPreview;
import com.tenemsa.platform.invitation.dto.InvitationRequest;
import com.tenemsa.platform.invitation.model.Invitation;
import com.tenemsa.platform.invitation.repository.InvitationRepository;
import com.tenemsa.platform.mail.EmailService;
import com.tenemsa.platform.security.PasswordPolicy;
import com.tenemsa.platform.security.rbac.RoleAssignmentRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Invite-by-email onboarding: an admin-tier user invites someone, the invitee
 * follows an emailed link and picks their own password. Replaces handing out
 * a temporary password in plain email.
 *
 * Tenant rules: the company always comes from the caller's JWT; the invited
 * role is checked with the same RoleAssignmentRegistry the direct "add
 * employee" form uses, so an invitation can never grant more than the
 * inviter could create by hand.
 */
@Service
public class InvitationService {

    public static final int EXPIRY_DAYS = 7;
    private static final int MAX_ADMIN_TIER = 4;
    private static final List<Role> ADMIN_TIER = List.of(Role.OWNER, Role.ADMIN, Role.SUPER_ADMIN);

    /** A user-correctable problem; the controller maps it to HTTP 400. */
    public static class InvitationException extends RuntimeException {
        public InvitationException(String message) { super(message); }
    }

    @Autowired private InvitationRepository invitationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private EmailService emailService;
    @Autowired private AuditService auditService;

    /** The saved invitation, plus whether the email with the link could be delivered. */
    public record InviteResult(Invitation invitation, boolean emailSent) {}

    @Transactional
    public InviteResult invite(Long companyId, User inviter, InvitationRequest req) {
        String email = req.email() == null ? "" : req.email().trim();
        String name = req.name() == null ? "" : req.name().trim();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new InvitationException("Enter a valid email address.");
        if (name.length() < 2) throw new InvitationException("Enter the person's name.");
        Role role = req.role();
        if (role == null || role == Role.OWNER || role == Role.SUPER_ADMIN) {
            throw new InvitationException("Choose a role to invite for.");
        }
        if (!RoleAssignmentRegistry.canAssign(inviter.getRole(), role)) {
            throw new AccessDeniedException("Your role (" + inviter.getRole() + ") cannot invite a " + role + ".");
        }
        if (userRepository.existsByEmailAndCompanyId(email, companyId)) {
            throw new InvitationException("That email already belongs to someone in your company.");
        }
        requireAdminSlot(companyId, role);

        // Re-inviting the same address replaces the earlier link instead of leaving two live ones.
        for (Invitation old : invitationRepository.findByCompanyIdAndEmailIgnoreCaseAndStatus(companyId, email, Invitation.PENDING)) {
            old.setStatus(Invitation.REVOKED);
            invitationRepository.save(old);
        }

        String raw = tokenService.newRawToken();
        Invitation inv = new Invitation();
        inv.setCompanyId(companyId);
        inv.setEmail(email);
        inv.setName(name);
        inv.setRole(role.name());
        inv.setDepartment(req.department() == null || req.department().isBlank() ? null : req.department().trim());
        inv.setTokenHash(tokenService.hashToken(raw));
        inv.setInvitedById(inviter.getId());
        inv.setExpiresAt(LocalDateTime.now().plusDays(EXPIRY_DAYS));
        inv = invitationRepository.save(inv);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        boolean emailSent = emailService.sendInviteEmail(email, name, company.getName(), inviter.getName(), role.name(), raw);
        auditService.record(companyId, inviter.getId(), inviter.getName(), AuditAction.INVITE_SENT,
                "INVITATION", String.valueOf(inv.getId()), "Invited " + email + " as " + role
                        + (emailSent ? "" : " (email could not be delivered)"));
        return new InviteResult(inv, emailSent);
    }

    public List<Invitation> pending(Long companyId) {
        return invitationRepository.findByCompanyIdAndStatusOrderByCreatedAtDesc(companyId, Invitation.PENDING);
    }

    @Transactional
    public void revoke(Long companyId, Long id, User actor) {
        Invitation inv = invitationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new InvitationException("Invitation not found."));
        if (!Invitation.PENDING.equals(inv.getStatus())) throw new InvitationException("Only pending invitations can be revoked.");
        inv.setStatus(Invitation.REVOKED);
        invitationRepository.save(inv);
        auditService.record(companyId, actor.getId(), actor.getName(), AuditAction.INVITE_REVOKED,
                "INVITATION", String.valueOf(id), "Revoked invitation for " + inv.getEmail());
    }

    public InvitationPreview preview(String rawToken) {
        Invitation inv = usable(rawToken);
        Company c = companyRepository.findById(inv.getCompanyId())
                .orElseThrow(() -> new InvitationException("This invitation is invalid or has expired."));
        return new InvitationPreview(c.getName(), c.getSlug(), inv.getEmail(), inv.getName(), inv.getRole());
    }

    /** Creates the account. The link proves ownership of the mailbox, so the email is marked verified. */
    @Transactional
    public User accept(String rawToken, String name, String password) {
        Invitation inv = usable(rawToken);
        String policyError = PasswordPolicy.validate(password);
        if (policyError != null) throw new InvitationException(policyError);

        Long companyId = inv.getCompanyId();
        if (userRepository.existsByEmailAndCompanyId(inv.getEmail(), companyId)) {
            throw new InvitationException("An account with this email already exists in this company.");
        }
        Role role = Role.valueOf(inv.getRole());
        requireAdminSlot(companyId, role);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new InvitationException("This invitation is invalid or has expired."));

        User u = new User();
        u.setCompany(company);
        u.setName(name == null || name.trim().length() < 2 ? inv.getName() : name.trim());
        u.setEmail(inv.getEmail());
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        u.setDepartment(inv.getDepartment());
        u.setFrozen(false);
        u.setPrimaryOwner(false);
        u.setEmailVerified(true);
        u.setEmpId(userRepository.nextEmpId(companyId));
        u = userRepository.save(u);

        inv.setStatus(Invitation.ACCEPTED);
        inv.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(inv);
        auditService.record(companyId, u.getId(), u.getName(), AuditAction.INVITE_ACCEPTED,
                "USER", String.valueOf(u.getId()), u.getName() + " joined as " + role);
        return u;
    }

    private Invitation usable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new InvitationException("This invitation is invalid or has expired.");
        return invitationRepository.findByTokenHash(tokenService.hashToken(rawToken.trim()))
                .filter(Invitation::isUsable)
                .orElseThrow(() -> new InvitationException("This invitation is invalid or has expired."));
    }

    private void requireAdminSlot(Long companyId, Role role) {
        if (role == Role.ADMIN && userRepository.countByCompanyIdAndRoleIn(companyId, ADMIN_TIER) >= MAX_ADMIN_TIER) {
            throw new InvitationException("Maximum of " + MAX_ADMIN_TIER + " admin-tier accounts reached.");
        }
    }
}
