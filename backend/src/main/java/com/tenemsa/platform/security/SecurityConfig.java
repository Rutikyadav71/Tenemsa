package com.tenemsa.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    // Single source of truth for CORS. Previously this was duplicated across
    // 10 different controllers via @CrossOrigin, each hardcoded to one origin
    // (two of them used "*", which is actually invalid together with
    // allowCredentials(true) — browsers reject that combination outright).
    // Now every controller relies on this bean, and origins are configurable
    // per environment instead of hardcoded.
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
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
                // Public endpoints — no token required
                .requestMatchers(
                    "/api/auth/**",
                    "/api/admin/login",
                    "/api/admin/register",
                    "/api/admin/exists",
                    "/ws/**",
                        "/api/health"
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

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
