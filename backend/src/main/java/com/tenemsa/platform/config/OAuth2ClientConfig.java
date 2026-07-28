package com.tenemsa.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Manually builds the Google ClientRegistration instead of relying on Spring
 * Boot's property-based OAuth2 autoconfiguration — see the comment in
 * application.properties on app.oauth2.google.* for why that path crashes
 * at startup with no Google credentials configured, which is the default
 * local-dev case. Returning null from a @Bean method is a deliberate,
 * supported way to make a bean conditionally absent: no
 * ClientRegistrationRepository bean means SecurityConfig's
 * ObjectProvider<ClientRegistrationRepository> sees nothing available and
 * skips oauth2Login() entirely, rather than the app failing to start.
 */
@Configuration
public class OAuth2ClientConfig {

    @Value("${app.oauth2.google.client-id}")
    private String googleClientId;

    @Value("${app.oauth2.google.client-secret}")
    private String googleClientSecret;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        if (googleClientId == null || googleClientId.isBlank()
                || googleClientSecret == null || googleClientSecret.isBlank()) {
            return null; // Google login disabled — no credentials configured
        }
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(googleClientId)
                .clientSecret(googleClientSecret)
                .scope("email", "profile")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
