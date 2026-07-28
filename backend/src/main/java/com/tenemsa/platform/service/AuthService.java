package com.tenemsa.platform.service;

import com.tenemsa.platform.dto.AuthResponse;
import com.tenemsa.platform.dto.LoginRequest;
import com.tenemsa.platform.model.Role;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.model.VerificationToken;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.JwtUtil;
import com.tenemsa.platform.security.PasswordPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Previously had two near-identical methods (adminLogin, employeeLogin) doing
 * the same email+password check against two different tables. Now there's
 * one table, so there's one login method — the two controllers that call in
 * (AuthController for general login, AdminController for the admin panel)
 * each apply their own role check afterward, since "is this an admin login"
 * is a caller concern, not an authentication concern.
 */
@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;
    @Autowired private EmailService emailService;
    @Autowired private JwtUtil jwtUtil;

    private static final List<Role> ADMIN_EQUIVALENT_ROLES = List.of(Role.OWNER, Role.SUPER_ADMIN);

    // Builds the full login/refresh response: short-lived JWT access token +
    // long-lived opaque refresh token + every profile field the frontend
    // needs. Previously this construction was duplicated almost verbatim in
    // both AuthController and AdminController; centralizing it here means a
    // field added once (like companyName below) reaches both login paths
    // automatically instead of needing two edits that can drift apart.
    public AuthResponse issueAuthResponse(User user, boolean rememberMe, String deviceLabel) {
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getCompany().getId());
        String refreshToken = tokenService.issueRefreshToken(user, deviceLabel, rememberMe);

        AuthResponse resp = new AuthResponse(accessToken, user.getRole().name(), user.getName(), user.getEmail());
        resp.setRefreshToken(refreshToken);
        resp.setId(user.getId());
        resp.setEmpId(user.getEmpId());
        resp.setCompanyId(user.getCompany().getId());
        resp.setCompanyName(user.getCompany().getName());
        resp.setDepartment(user.getDepartment());
        resp.setSalary(user.getSalary());
        resp.setPrimaryOwner(user.isPrimaryOwner());
        resp.setEmailVerified(user.isEmailVerified());
        return resp;
    }

    public User login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // Phase 2: accounts created via Google OAuth2 have no password at
        // all (see User.password now being nullable). Without this check,
        // passwordEncoder.matches(raw, null) would throw an unchecked
        // IllegalArgumentException from BCrypt instead of a clean, expected
        // "use Google to sign in" message.
        if (user.getAuthProvider() == User.AuthProvider.GOOGLE || user.getPassword() == null) {
            throw new RuntimeException("This account signs in with Google. Use \"Continue with Google\" instead.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (user.isFrozen()) {
            throw new RuntimeException("ACCOUNT_FROZEN: Your account has been frozen. Contact your administrator.");
        }

        return user;
    }

    // Same as login(), but additionally requires an admin-equivalent role.
    // AdminController needs this because /api/admin/login used to be
    // implicitly admin-only by virtue of querying the separate Admin table —
    // now that both admins and employees live in the same table, that
    // implicit boundary has to become an explicit check here instead, or any
    // employee could authenticate through the admin endpoint.
    public User adminLogin(LoginRequest request) {
        User user = login(request);
        if (!ADMIN_EQUIVALENT_ROLES.contains(user.getRole())) {
            throw new RuntimeException("Not an admin account");
        }
        return user;
    }

    // ---- Current user ------------------------------------------------

    // Used by the OAuth2 callback page: the backend redirect after Google
    // login only carries token+refreshToken in the URL (a full-page
    // redirect has no JSON body to read), so the frontend needs a way to
    // fetch the rest of the profile (name, role, company) once it has an
    // access token. Also generally useful as a "refresh my own profile"
    // call. Does NOT issue new tokens — reuses the ones already held.
    public AuthResponse getCurrentUserProfile(User user) {
        AuthResponse resp = new AuthResponse(null, user.getRole().name(), user.getName(), user.getEmail());
        resp.setId(user.getId());
        resp.setEmpId(user.getEmpId());
        resp.setCompanyId(user.getCompany().getId());
        resp.setCompanyName(user.getCompany().getName());
        resp.setDepartment(user.getDepartment());
        resp.setSalary(user.getSalary());
        resp.setPrimaryOwner(user.isPrimaryOwner());
        resp.setEmailVerified(user.isEmailVerified());
        return resp;
    }

    // ---- Token refresh --------------------------------------------------

    public AuthResponse refresh(String rawRefreshToken) {
        var token = tokenService.validateRefreshToken(rawRefreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token is invalid or has expired. Please sign in again."));
        User user = token.getUser();
        if (user.isFrozen()) {
            throw new RuntimeException("ACCOUNT_FROZEN: Your account has been frozen. Contact your administrator.");
        }
        String newRawRefreshToken = tokenService.rotateRefreshToken(token);
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getCompany().getId());

        AuthResponse resp = new AuthResponse(accessToken, user.getRole().name(), user.getName(), user.getEmail());
        resp.setRefreshToken(newRawRefreshToken);
        resp.setId(user.getId());
        resp.setEmpId(user.getEmpId());
        resp.setCompanyId(user.getCompany().getId());
        resp.setCompanyName(user.getCompany().getName());
        resp.setDepartment(user.getDepartment());
        resp.setSalary(user.getSalary());
        resp.setPrimaryOwner(user.isPrimaryOwner());
        resp.setEmailVerified(user.isEmailVerified());
        return resp;
    }

    public void logout(String rawRefreshToken) {
        tokenService.validateRefreshToken(rawRefreshToken).ifPresent(tokenService::revokeRefreshToken);
    }

    public void logoutAllDevices(Long userId) {
        tokenService.revokeAllRefreshTokensForUser(userId);
    }

    // ---- Email verification --------------------------------------------

    public void sendVerificationEmail(User user) {
        if (user.isEmailVerified()) return;
        String rawToken = tokenService.issueVerificationToken(user, VerificationToken.Purpose.EMAIL_VERIFICATION);
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), rawToken);
    }

    /** Silently no-ops for unknown emails and already-verified accounts — deliberately not revealing
     *  which case applies, same reasoning as requestPasswordReset() below. */
    public void resendVerificationEmail(String email) {
        userRepository.findByEmail(email).ifPresent(this::sendVerificationEmail);
    }

    public void verifyEmail(String rawToken) {
        VerificationToken token = tokenService.validateVerificationToken(rawToken, VerificationToken.Purpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new RuntimeException("This verification link is invalid or has expired."));
        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenService.consumeVerificationToken(token);
    }

    // ---- Password reset --------------------------------------------------

    /** Deliberately does not throw or return any signal distinguishing "no such email" from
     *  "email sent" — telling an attacker which emails are registered is a real enumeration
     *  risk, so the controller always responds with the same generic message regardless. */
    public void requestPasswordReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return;
        User user = userOpt.get();
        if (user.getAuthProvider() == User.AuthProvider.GOOGLE) return; // nothing to reset
        String rawToken = tokenService.issueVerificationToken(user, VerificationToken.Purpose.PASSWORD_RESET);
        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), rawToken);
    }

    public void resetPassword(String rawToken, String newPassword) {
        String policyError = PasswordPolicy.validate(newPassword);
        if (policyError != null) throw new RuntimeException(policyError);

        VerificationToken token = tokenService.validateVerificationToken(rawToken, VerificationToken.Purpose.PASSWORD_RESET)
                .orElseThrow(() -> new RuntimeException("This reset link is invalid or has expired."));
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenService.consumeVerificationToken(token);

        // Resetting a password should invalidate every existing session —
        // otherwise a leaked/stolen refresh token would survive the very
        // password change meant to lock the attacker out.
        tokenService.revokeAllRefreshTokensForUser(user.getId());
    }
}
