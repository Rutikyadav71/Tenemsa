package com.tenemsa.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder used to live as a @Bean method inside SecurityConfig.
 * Phase 2 made SecurityConfig constructor-inject OAuth2LoginSuccessHandler,
 * which depends on AuthService, which depends on PasswordEncoder — meaning
 * Spring would need PasswordEncoder ready before SecurityConfig could even
 * be constructed, but PasswordEncoder was itself only produced by a method
 * ON SecurityConfig. That's a circular dependency (BeanCurrentlyInCreation
 * at startup), caught before it shipped. Moving this bean to its own
 * standalone config breaks the cycle — nothing about password hashing has
 * any business being coupled to the HTTP security filter chain anyway.
 */
@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
