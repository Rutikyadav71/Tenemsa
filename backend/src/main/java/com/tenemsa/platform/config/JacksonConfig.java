package com.tenemsa.platform.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot's Jackson autoconfiguration automatically registers any bean
 * of type com.fasterxml.jackson.databind.Module into the app's ObjectMapper
 * — this is the standard, documented way to add Hibernate6Module, not
 * something that needs manual ObjectMapper wiring.
 *
 * FORCE_LAZY_LOADING is deliberately left OFF (the default): an uninitialized
 * lazy relation serializes as null instead of Jackson forcing a DB fetch
 * mid-serialization. Forcing it would just move the risk rather than remove
 * it — the exact LazyInitializationException class of bug documented on
 * UserRepository would resurface for any endpoint invoked from a context
 * without an open session (same reasoning as OAuth2LoginSuccessHandler's
 * history), just deferred to serialization time instead of business-logic
 * time. This module is a safety net against crashes, not a substitute for
 * fetching what a response actually needs — see UserRepository's JOIN FETCH
 * queries and the new UserResponseDTO for the real fix on the endpoints
 * that actually hit this.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        return new Hibernate6Module();
    }
}
