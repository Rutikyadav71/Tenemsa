package com.tenemsa.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Rate limiting is switched OFF for the other tests (application-test.properties)
 * and ON here, with its own in-memory database so the two contexts never share state.
 * Every test calls from its own client address (limits are per IP), so the tests
 * cannot use up each other's budget whatever order JUnit runs them in.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:ratelimit-test;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitTest {

    @Autowired MockMvc mvc;

    /** Makes the request look like it came from the given client address. */
    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private int login(String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"Wrong#Pass1\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void loginIsLimitedToTenAttemptsPerMinute() throws Exception {
        for (int i = 0; i < 10; i++) assertThat(login("10.0.0.1")).isEqualTo(401);

        var blocked = mvc.perform(post("/api/auth/login").with(from("10.0.0.1")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"Wrong#Pass1\"}")).andReturn().getResponse();
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("Too many requests");
    }

    @Test
    void adminLoginSharesTheLoginBudget() throws Exception {
        // Same group as /api/auth/login: another attacker route must not give extra attempts.
        // Alternate the two routes from one address: attempts 1-10 pass, the 11th and 12th are blocked.
        int last = 0;
        for (int i = 0; i < 12; i++) {
            String route = i % 2 == 0 ? "/api/admin/login" : "/api/auth/login";
            last = mvc.perform(post(route).with(from("10.0.0.2")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"nobody@example.com\",\"password\":\"Wrong#Pass1\"}"))
                    .andReturn().getResponse().getStatus();
        }
        assertThat(last).isEqualTo(429);
    }

    @Test
    void workspaceLookupIsLimitedToThirtyPerMinute() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertThat(mvc.perform(get("/api/companies/lookup").with(from("10.0.0.3")).param("slug", "nope" + i))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
        }
        assertThat(mvc.perform(get("/api/companies/lookup").with(from("10.0.0.3")).param("slug", "nope"))
                .andReturn().getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void registrationIsLimitedToFivePerHour() throws Exception {
        for (int i = 0; i < 5; i++) {
            // Invalid body on purpose: the limiter counts the request before validation runs.
            assertThat(mvc.perform(post("/api/companies/register").with(from("10.0.0.4")).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andReturn().getResponse().getStatus()).isNotEqualTo(429);
        }
        assertThat(mvc.perform(post("/api/companies/register").with(from("10.0.0.4")).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andReturn().getResponse().getStatus()).isEqualTo(429);
    }
}
