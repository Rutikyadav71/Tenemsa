package com.tenemsa.platform.service;

import com.tenemsa.platform.dto.LoginRequest;
import com.tenemsa.platform.model.Role;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private static final List<Role> ADMIN_EQUIVALENT_ROLES = List.of(Role.OWNER, Role.SUPER_ADMIN);

    public User login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

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
}
