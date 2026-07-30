package com.tenemsa.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Google OAuth2 login is entirely optional.
 *
 * IMPORTANT — how this bean is made conditional, and why:
 *
 * An earlier version had an unconditional @Bean method that returned null
 * when no Google credentials were configured. That doesn't work: returning
 * null from a @Bean method still registers a bean DEFINITION of type
 * ClientRegistrationRepository in the context. Other Spring
 * Security/OAuth2 autoconfiguration classes see that a bean of this type
 * is declared and wire themselves to require it via normal (non-optional)
 * injection — when the factory method is actually invoked and returns
 * null, that injection fails and the app won't start. "No bean" and "a
 * bean that resolves to null" are different things to the container, and
 * only the former is actually safe.
 *
 * @ConditionalOnExpression is evaluated at configuration-processing time,
 * BEFORE any bean definition is registered — so when the expression is
 * false, there is no ClientRegistrationRepository bean definition at all,
 * and SecurityConfig's ObjectProvider<ClientRegistrationRepository> sees a
 * clean "nothing available" rather than a broken one.
 *
 * Using @ConditionalOnExpression rather than the simpler
 * @ConditionalOnProperty deliberately: @ConditionalOnProperty only checks
 * whether a property is ABSENT (or literally "false") — it treats a
 * property that resolves to an empty string as "present". That matters
 * here because GOOGLE_CLIENT_ID being set-but-empty is a realistic case,
 * not a hypothetical one: env-file tooling (Docker --env-file, direnv,
 * etc.) commonly exports every key from a .env file as a real environment
 * variable even when its value is blank, and .env.example ships
 * GOOGLE_CLIENT_ID= with nothing after the equals sign as the documented
 * "disabled" state. The SpEL expression below explicitly checks for
 * blank, not just absent, so that case is handled correctly too.
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    @ConditionalOnExpression(
        "!'${GOOGLE_CLIENT_ID:}'.trim().isEmpty() and !'${GOOGLE_CLIENT_SECRET:}'.trim().isEmpty()"
    )
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(googleClientId)
                .clientSecret(googleClientSecret)
                .scope("email", "profile")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
