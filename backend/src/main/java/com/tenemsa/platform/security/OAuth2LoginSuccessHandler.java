package com.tenemsa.platform.security;

import com.tenemsa.platform.dto.AuthResponse;
import com.tenemsa.platform.model.Company;
import com.tenemsa.platform.model.Role;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.CompanyRepository;
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

/**
 * Runs after Google confirms the user's identity. Finds an existing account
 * by email, or provisions a new one — same limitation as /api/admin/register
 * today: new Google sign-ins land in the single bootstrap "default" company
 * rather than picking one, because real multi-company selection is Phase 4
 * work, not yet built. Documented here rather than silently done, same as
 * everywhere else this limitation shows up.
 *
 * Redirects back to the frontend with tokens as query params rather than
 * returning JSON directly — OAuth2 redirect flows are full-page browser
 * navigations, not fetch/XHR calls, so there's no JS on this response to
 * read a JSON body. The frontend's /oauth/callback route picks the tokens
 * up from the URL and stores them the same way a normal login response
 * would, then redirects into the app.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuthService authService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final String DEFAULT_COMPANY_SLUG = "default";

    public OAuth2LoginSuccessHandler(UserRepository userRepository, CompanyRepository companyRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        if (email == null) {
            response.sendRedirect(frontendUrl + "/auth/employee?error=" + URLEncoder.encode("Google did not share an email address.", StandardCharsets.UTF_8));
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> provisionUser(email, name));

        if (user.getAuthProvider() != User.AuthProvider.GOOGLE) {
            // An account with this email already exists via normal signup.
            // Rather than silently merging (which would let anyone with a
            // Google account matching a victim's email take over that
            // account), require them to use their original sign-in method.
            response.sendRedirect(frontendUrl + "/auth/employee?error=" +
                    URLEncoder.encode("An account with this email already exists. Sign in with your password instead.", StandardCharsets.UTF_8));
            return;
        }

        if (user.isFrozen()) {
            response.sendRedirect(frontendUrl + "/auth/employee?error=" +
                    URLEncoder.encode("This account has been frozen. Contact your administrator.", StandardCharsets.UTF_8));
            return;
        }

        AuthResponse authResp = authService.issueAuthResponse(user, false, request.getHeader("User-Agent"));

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("token", authResp.getToken())
                .queryParam("refreshToken", authResp.getRefreshToken())
                .build().toUriString();
        response.sendRedirect(redirectUrl);
    }

    private User provisionUser(String email, String name) {
        Company company = companyRepository.findBySlug(DEFAULT_COMPANY_SLUG)
                .orElseGet(() -> companyRepository.save(new Company("Default Company", DEFAULT_COMPANY_SLUG)));
        User user = new User();
        user.setCompany(company);
        user.setEmail(email);
        user.setName(name != null ? name : email);
        user.setRole(Role.EMPLOYEE);
        user.setAuthProvider(User.AuthProvider.GOOGLE);
        user.setEmailVerified(true); // Google already verified it
        user.setFrozen(false);
        return userRepository.save(user);
    }
}
