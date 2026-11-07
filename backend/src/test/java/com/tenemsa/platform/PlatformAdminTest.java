package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.platformadmin.service.TotpService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** The system-administrator console: one-time registration of two admins, 2FA sign-in, freeze and unfreeze. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-admin-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "platform.enabled=true",
        "platform.jwt-secret=platform-test-secret-platform-test-secret-123456",
        "platform.setup-key=let-me-in-once",
        "app.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformAdminTest {

    private static final String PASSWORD = "Platform#Admin1234";
    private static final String COMPANY_PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpServletResponse send(RequestBuilder rb) throws Exception { return mvc.perform(rb).andReturn().getResponse(); }
    private JsonNode body(MockHttpServletResponse r) throws Exception { return json.readTree(r.getContentAsString()); }
    private MockHttpServletResponse post(String url, Map<String, ?> b, String token) throws Exception {
        var rb = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(b));
        if (token != null) rb.header("Authorization", "Bearer " + token);
        return send(rb);
    }

    private MockHttpServletResponse register(String email, String key) throws Exception {
        return post("/api/platform/auth/register", Map.of("name", "Admin", "email", email, "password", PASSWORD, "setupKey", key), null);
    }

    // The tests run in a fixed order and share the two administrator accounts (that is the whole point of the lock).
    private static String secret1, token1;

    @Test @Order(1)
    void totpMatchesTheRfc6238TestVector() {
        // RFC 6238 appendix B: secret "12345678901234567890", time 59s => 94287082; the last six digits are what we use.
        assertThat(TotpService.code("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1)).isEqualTo("287082");
    }

    @Test @Order(2)
    void onlyTwoAdminsCanEverRegisterAndOnlyWithTheSetupKey() throws Exception {
        assertThat(body(send(get("/api/platform/auth/status"))).get("registrationOpen").asBoolean()).isTrue();
        assertThat(register("nokey@example.com", "wrong-key").getStatus()).isEqualTo(403);
        assertThat(post("/api/platform/auth/register", Map.of("name", "A", "email", "weak@example.com", "password", "short", "setupKey", "let-me-in-once"), null).getStatus()).isEqualTo(400);

        MockHttpServletResponse a = register("one@example.com", "let-me-in-once");
        assertThat(a.getStatus()).isEqualTo(201);
        secret1 = body(a).get("secret").asText();
        assertThat(secret1).isNotBlank();
        assertThat(register("one@example.com", "let-me-in-once").getStatus()).isEqualTo(403);   // same email twice
        assertThat(register("two@example.com", "let-me-in-once").getStatus()).isEqualTo(201);

        assertThat(register("three@example.com", "let-me-in-once").getStatus()).isEqualTo(403);  // the door is shut
        assertThat(body(send(get("/api/platform/auth/status"))).get("registrationOpen").asBoolean()).isFalse();
    }

    @Test @Order(3)
    void signInNeedsPasswordAndAFreshAuthenticatorCode() throws Exception {
        String email = "one@example.com";
        assertThat(post("/api/platform/auth/login", Map.of("email", email, "password", "Wrong#Password99"), null).getStatus()).isEqualTo(401);
        // First sign-in shows the setup secret and needs a real code to switch two-factor on.
        assertThat(body(post("/api/platform/auth/login", Map.of("email", email, "password", PASSWORD), null)).get("status").asText()).isEqualTo("TOTP_SETUP");
        String code = TotpService.code(secret1, TotpService.currentStep());
        MockHttpServletResponse ok = post("/api/platform/auth/confirm-2fa", Map.of("email", email, "password", PASSWORD, "code", code), null);
        assertThat(ok.getStatus()).isEqualTo(200);
        token1 = body(ok).get("token").asText();

        // Now two-factor is on: password alone only asks for the code, a wrong code is refused,
        // and the code that was just used cannot be used a second time.
        assertThat(body(post("/api/platform/auth/login", Map.of("email", email, "password", PASSWORD), null)).get("status").asText()).isEqualTo("CODE_REQUIRED");
        assertThat(post("/api/platform/auth/login", Map.of("email", email, "password", PASSWORD, "code", "000000"), null).getStatus()).isEqualTo(401);
        assertThat(post("/api/platform/auth/login", Map.of("email", email, "password", PASSWORD, "code", code), null).getStatus()).isEqualTo(401);
        assertThat(send(get("/api/platform/overview").header("Authorization", "Bearer " + token1)).getStatus()).isEqualTo(200);
        assertThat(send(get("/api/platform/me").header("Authorization", "Bearer " + token1)).getStatus()).isEqualTo(200);
    }

    @Test @Order(4)
    void consoleIsClosedToCompanyUsersAndToAnonymousCallers() throws Exception {
        assertThat(send(get("/api/platform/overview")).getStatus()).isEqualTo(401);
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse reg = post("/api/companies/register", Map.of("companyName", "Co " + id, "ownerName", "Owner", "email", "o" + id + "@example.com", "password", COMPANY_PASSWORD), null);
        assertThat(reg.getStatus()).isEqualTo(201);
        String ownerToken = body(reg).get("token").asText();
        assertThat(send(get("/api/platform/overview").header("Authorization", "Bearer " + ownerToken)).getStatus()).isEqualTo(401);
        assertThat(send(get("/api/platform/companies").header("Authorization", "Bearer " + ownerToken)).getStatus()).isEqualTo(401);
        // And a platform token is not a company token.
        assertThat(send(get("/api/companies/me").header("Authorization", "Bearer " + token1)).getStatus()).isEqualTo(401);
    }

    @Test @Order(5)
    void freezingACompanyLocksEveryoneOutAndUnfreezingBringsThemBack() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "o" + id + "@example.com";
        MockHttpServletResponse reg = post("/api/companies/register", Map.of("companyName", "Frozen " + id, "ownerName", "Owner", "email", ownerEmail, "password", COMPANY_PASSWORD), null);
        assertThat(reg.getStatus()).isEqualTo(201);
        String ownerToken = body(reg).get("token").asText();
        String refresh = body(reg).get("refreshToken").asText();
        long companyId = body(reg).get("companyId").asLong();
        assertThat(send(get("/api/companies/me").header("Authorization", "Bearer " + ownerToken)).getStatus()).isEqualTo(200);
        String admin = token1;

        // It shows up in the list and the detail view with company-level numbers only.
        assertThat(send(get("/api/platform/companies?search=Frozen " + id).header("Authorization", "Bearer " + admin)).getContentAsString()).contains("Frozen " + id);
        JsonNode detail = body(send(get("/api/platform/companies/" + companyId).header("Authorization", "Bearer " + admin)));
        assertThat(detail.get("people").get("total").asLong()).isEqualTo(1);
        assertThat(detail.get("owner").get("email").asText()).isEqualTo(ownerEmail);

        // A reason is required, and a company cannot be frozen twice.
        assertThat(post("/api/platform/companies/" + companyId + "/freeze", Map.of("reason", ""), admin).getStatus()).isEqualTo(400);
        MockHttpServletResponse frozen = post("/api/platform/companies/" + companyId + "/freeze", Map.of("reason", "Unpaid invoice"), admin);
        assertThat(frozen.getStatus()).isEqualTo(200);
        assertThat(body(frozen).get("status").asText()).isEqualTo("FROZEN");
        assertThat(post("/api/platform/companies/" + companyId + "/freeze", Map.of("reason", "Again please"), admin).getStatus()).isEqualTo(400);

        // The owner's token no longer works, refresh is refused and so is signing in again.
        assertThat(send(get("/api/companies/me").header("Authorization", "Bearer " + ownerToken)).getStatus()).isEqualTo(401);
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", refresh), null).getStatus()).isGreaterThanOrEqualTo(400);
        MockHttpServletResponse login = post("/api/auth/login", Map.of("email", ownerEmail, "password", COMPANY_PASSWORD), null);
        assertThat(login.getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(login.getContentAsString()).contains("suspended");

        assertThat(post("/api/platform/companies/" + companyId + "/unfreeze", Map.of("reason", "Paid"), admin).getStatus()).isEqualTo(200);
        assertThat(post("/api/auth/login", Map.of("email", ownerEmail, "password", COMPANY_PASSWORD), null).getStatus()).isEqualTo(200);

        // Closing needs the workspace name typed exactly.
        assertThat(post("/api/platform/companies/" + companyId + "/close", Map.of("reason", "Customer left", "confirm", "nope"), admin).getStatus()).isEqualTo(400);

        MockHttpServletResponse audit = send(get("/api/platform/audit").header("Authorization", "Bearer " + admin));
        assertThat(audit.getContentAsString()).contains("COMPANY_FROZEN").contains("COMPANY_UNFROZEN");
    }
}
