package com.tenemsa.platform.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    // May have no bean available at all (see OAuth2ClientConfig) — using
    // ObjectProvider rather than a direct injection means that's fine;
    // getIfAvailable() just returns null instead of failing context startup.
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

    // Single source of truth for CORS. Previously this was duplicated across
    // 10 different controllers via @CrossOrigin, each hardcoded to one origin
    // (two of them used "*", which is actually invalid together with
    // allowCredentials(true) — browsers reject that combination outright).
    // Now every controller relies on this bean, and origins are configurable
    // per environment instead of hardcoded.
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter,
                           OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                           ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider) {
        this.jwtFilter = jwtFilter;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required. Listed explicitly rather
                // than a blanket "/api/auth/**" wildcard: /api/auth/logout-all
                // needs the caller's actual identity (read from the JWT via
                // AuthContext) to know whose sessions to revoke, so it must NOT
                // be reachable without authentication — a wildcard here would
                // have silently exempted it along with the genuinely public ones.
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/verify-email",
                    "/api/auth/resend-verification",
                    "/api/auth/password-policy",
                    "/api/admin/login",
                    "/api/companies/register",
                    "/api/companies/lookup",
                    "/api/invitations/preview",
                    "/api/invitations/accept",
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/ws/**",
                    "/api/health",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                // Upload endpoints — authenticated (any valid token) but NOT admin-only
                // Must come before anyRequest().authenticated() to take effect
                .requestMatchers("/api/upload/**").authenticated()
                // Teams and attendance — both USER and ADMIN can read
                .requestMatchers("/api/teams/**").authenticated()
                .requestMatchers("/api/attendance/**").authenticated()
                // Everything else needs auth; method-level @PreAuthorize handles role checks
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        // Only wire up the OAuth2 login filter chain if a Google client
        // registration actually exists (see OAuth2ClientConfig) — with no
        // credentials configured, .oauth2Login(...) is simply never called,
        // rather than the app either crashing at startup or exposing a
        // /oauth2/authorization/google endpoint that would fail anyway.
        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler));
        }

        return http.build();
    }

}
