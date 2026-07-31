package com.tenemsa.platform.security;

import com.tenemsa.platform.dto.AuthResponse;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Tenemsa is admin-managed: accounts are created by an Owner/Admin (via
 * /api/admin/register, /api/admin/register-by-admin, or adding an
 * employee), never by self-registration. Google Sign-In is therefore
 * ONLY an alternative way to prove you are an existing user — it must
 * never create an account, and it must never reject an existing account
 * just because that account happens to have been created with a
 * password. Matching on email is enough: if a user with this email
 * already exists (Owner, Admin, HR, Manager, Employee, Intern — any
 * role, any original signup method), Google has just proven they own
 * that email address, so log them in as that user.
 *
 * (An earlier version of this handler auto-provisioned a brand-new User
 * on first Google sign-in, and separately rejected Google login for any
 * email that already had a LOCAL/password account — both wrong for this
 * app's actual account model, and the second one is the exact bug this
 * rewrite fixes: it made "Continue with Google" completely unusable for
 * every admin-created account, which is every account that exists.)
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        if (email == null) {
            redirectWithError(response, "Google did not share an email address.");
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Deliberately NOT "account already exists" style wording — this
            // is the opposite case: no account exists at all, and Google
            // sign-in cannot create one in this app. The message is aimed at
            // a real person who just tried to sign in without ever having
            // been added by an administrator.
            redirectWithError(response, "Your account has not been created by your administrator.");
            return;
        }
        User user = userOpt.get();

        if (user.isFrozen()) {
            redirectWithError(response, "This account has been frozen. Contact your administrator.");
            return;
        }

        // Note: we do NOT check or change user.getAuthProvider() here. That
        // field records how the account was originally created; it isn't a
        // gate on how the account is allowed to log in. Google is just an
        // additional valid credential for whoever owns this email, same as
        // the password is.
        AuthResponse authResp = authService.issueAuthResponse(user, false, request.getHeader("User-Agent"));

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("token", authResp.getToken())
                .queryParam("refreshToken", authResp.getRefreshToken())
                .build().toUriString();
        response.sendRedirect(redirectUrl);
    }

    private void redirectWithError(HttpServletResponse response, String message) throws IOException {
        // Routed through /oauth/callback rather than assuming /auth/employee —
        // at failure time we don't know whether this was an admin or an
        // employee attempting to sign in (Google's redirect carries no such
        // context), so landing everyone on the employee login page would
        // silently send a failed ADMIN sign-in to the wrong form. The
        // callback page shows the error with links to both.
        response.sendRedirect(frontendUrl + "/oauth/callback?error=" +
                URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
