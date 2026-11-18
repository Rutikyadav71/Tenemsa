package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.billing.model.Plan;
import com.tenemsa.platform.billing.repository.PlanRepository;
import com.tenemsa.platform.connections.provider.ConnectorHttp;
import com.tenemsa.platform.connections.service.ConnectionCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Connected apps: switching apps on, signing in, encrypted tokens, plan limits and forged sign-in attempts. The apps themselves are stubbed. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:connections-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "connections.secret=0123456789abcdef0123456789abcdef-test",
        "connections.github.client-id=gh-client", "connections.github.client-secret=gh-secret",
        "connections.google.client-id=g-client", "connections.google.client-secret=g-secret",
        "connections.microsoft.client-id=ms-client", "connections.microsoft.client-secret=ms-secret",
        "app.backend-url=http://localhost:8080", "app.frontend-url=http://localhost:5173"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConnectionsTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanRepository plans;
    @Autowired ConnectionCrypto crypto;
    @MockitoBean ConnectorHttp http;

    @BeforeEach
    void stubApps() throws Exception {
        if (plans.count() == 0) {
            Plan free = new Plan();
            free.setCode("FREE"); free.setName("Free"); free.setPricePaise(0); free.setMaxConnections(1); free.setSortOrder(1); free.setActive(true);
            plans.save(free);
        }
        when(http.postForm(anyString(), anyMap())).thenReturn(json.readTree("{\"access_token\":\"gho_secret_token\",\"scope\":\"read:user,repo\"}"));
        when(http.get(anyString(), anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            if (url.endsWith("/user")) return json.readTree("{\"login\":\"octo\"}");
            if (url.contains("/issues")) return json.readTree("[{\"title\":\"Fix login\",\"html_url\":\"https://x/1\",\"repository\":{\"full_name\":\"o/r\"}}]");
            if (url.contains("/user/repos")) return json.readTree("[{\"full_name\":\"o/r\",\"description\":\"d\",\"html_url\":\"https://x/r\"}]");
            return json.readTree("{}");
        });
    }

    private MockHttpServletResponse call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rb, String token, Object body) throws Exception {
        if (token != null) rb.header("Authorization", "Bearer " + token);
        if (body != null) rb.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(rb).andReturn().getResponse();
    }

    private record Co(String id, String slug, String ownerToken, long companyId) {}

    private Co company() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse reg = call(post("/api/companies/register"), null, Map.of("companyName", "Con " + id,
                "ownerName", "Owner", "email", "owner" + id + "@example.com", "password", PASSWORD));
        assertThat(reg.getStatus()).isEqualTo(201);
        JsonNode r = json.readTree(reg.getContentAsString());
        String slug = r.get("companySlug").asText();
        return new Co(id, slug, r.get("token").asText(), jdbc.queryForObject("SELECT id FROM companies WHERE slug = ?", Long.class, slug));
    }

    private String employeeToken(Co c) throws Exception {
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", "emp"); emp.put("email", "emp" + c.id() + "@example.com"); emp.put("password", PASSWORD); emp.put("role", "EMPLOYEE");
        assertThat(call(post("/api/employees"), c.ownerToken(), emp).getStatus()).isEqualTo(200);
        MockHttpServletResponse res = call(post("/api/auth/login"), null, Map.of("email", "emp" + c.id() + "@example.com", "password", PASSWORD, "workspace", c.slug()));
        return json.readTree(res.getContentAsString()).get("token").asText();
    }

    private String stateOf(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
    }

    private String connectGithub(Co c, String token) throws Exception {
        MockHttpServletResponse a = call(post("/api/connections/github/authorize"), token, null);
        assertThat(a.getStatus()).as(a.getContentAsString()).isEqualTo(200);
        String url = json.readTree(a.getContentAsString()).get("url").asText();
        assertThat(url).startsWith("https://github.com/login/oauth/authorize").contains("client_id=gh-client").contains("localhost%3A8080%2Fapi%2Fconnections%2Fcallback%2Fgithub");
        String state = java.net.URLDecoder.decode(stateOf(url), java.nio.charset.StandardCharsets.UTF_8);
        return mvc.perform(get("/api/connections/callback/github").param("code", "abc").param("state", state))
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    void anAppIsOffUntilAnOwnerSwitchesItOnAndEmployeesCannotDoThat() throws Exception {
        Co c = company();
        JsonNode l = json.readTree(call(get("/api/connections"), c.ownerToken(), null).getContentAsString());
        assertThat(l.get("ready").asBoolean()).isTrue();
        assertThat(l.get("apps")).hasSize(5);
        assertThat(l.get("apps").get(0).get("enabled").asBoolean()).isFalse();
        assertThat(call(post("/api/connections/github/authorize"), c.ownerToken(), null).getStatus()).isEqualTo(403);

        String emp = employeeToken(c);
        assertThat(call(put("/api/connections/apps/github"), emp, Map.of("enabled", true)).getStatus()).isEqualTo(403);
        assertThat(call(put("/api/connections/apps/github"), c.ownerToken(), Map.of("enabled", true)).getStatus()).isEqualTo(200);
        assertThat(call(post("/api/connections/github/authorize"), emp, null).getStatus()).isEqualTo(200);
    }

    @Test
    void signingInStoresEncryptedTokensAndPreviewShowsData() throws Exception {
        Co c = company();
        call(put("/api/connections/apps/github"), c.ownerToken(), Map.of("enabled", true));
        String next = connectGithub(c, c.ownerToken());
        assertThat(next).contains("/connections/done").contains("status=ok");

        String stored = jdbc.queryForObject("SELECT access_token_enc FROM connections WHERE company_id = ?", String.class, c.companyId());
        assertThat(stored).doesNotContain("gho_secret_token");
        assertThat(crypto.decrypt(stored)).isEqualTo("gho_secret_token");

        JsonNode l = json.readTree(call(get("/api/connections"), c.ownerToken(), null).getContentAsString());
        JsonNode gh = l.get("apps").get(0);
        assertThat(gh.get("connected").asBoolean()).isTrue();
        assertThat(gh.get("account").asText()).isEqualTo("octo");
        assertThat(call(get("/api/connections"), c.ownerToken(), null).getContentAsString()).doesNotContain("gho_secret_token").doesNotContain(stored);

        JsonNode items = json.readTree(call(get("/api/connections/github/preview"), c.ownerToken(), null).getContentAsString());
        assertThat(items.get(0).get("title").asText()).contains("Fix login");

        assertThat(call(delete("/api/connections/github"), c.ownerToken(), null).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE company_id = ?", Long.class, c.companyId())).isZero();
    }

    @Test
    void aForgedOrExpiredSignInIsRejected() throws Exception {
        Co c = company();
        call(put("/api/connections/apps/github"), c.ownerToken(), Map.of("enabled", true));
        String forged = mvc.perform(get("/api/connections/callback/github").param("code", "abc").param("state", "bogus.value"))
                .andReturn().getResponse().getHeader("Location");
        assertThat(forged).contains("status=error");
        String signedForOther = crypto.sign(c.companyId() + "|1|google_drive|" + (System.currentTimeMillis() + 60000) + "|x");
        assertThat(mvc.perform(get("/api/connections/callback/github").param("code", "abc").param("state", signedForOther)).andReturn().getResponse().getHeader("Location")).contains("status=error");
        String expired = crypto.sign(c.companyId() + "|1|github|" + (System.currentTimeMillis() - 1000) + "|x");
        assertThat(mvc.perform(get("/api/connections/callback/github").param("code", "abc").param("state", expired)).andReturn().getResponse().getHeader("Location")).contains("status=error");
        assertThat(mvc.perform(get("/api/connections/callback/github").param("error", "access_denied")).andReturn().getResponse().getHeader("Location")).contains("status=denied");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE company_id = ?", Long.class, c.companyId())).isZero();
    }

    @Test
    void thePlanCapsHowManyAppsACompanyCanConnect() throws Exception {
        Co c = company();   // Free plan in this test allows 1 connection
        call(put("/api/connections/apps/github"), c.ownerToken(), Map.of("enabled", true));
        call(put("/api/connections/apps/google_calendar"), c.ownerToken(), Map.of("enabled", true));
        assertThat(connectGithub(c, c.ownerToken())).contains("status=ok");
        MockHttpServletResponse second = call(post("/api/connections/google_calendar/authorize"), c.ownerToken(), null);
        assertThat(second.getStatus()).isEqualTo(409);
        assertThat(second.getContentAsString()).contains("Upgrade");
        // reconnecting the app already connected is still allowed
        assertThat(call(post("/api/connections/github/authorize"), c.ownerToken(), null).getStatus()).isEqualTo(200);
    }

    @Test
    void switchingAnAppOffRemovesEveryonesSignIn() throws Exception {
        Co c = company();
        call(put("/api/connections/apps/github"), c.ownerToken(), Map.of("enabled", true));
        connectGithub(c, c.ownerToken());
        call(put("/api/connections/apps/github"), c.ownerToken(), Map.of("enabled", false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE company_id = ?", Long.class, c.companyId())).isZero();
        assertThat(call(get("/api/connections/github/preview"), c.ownerToken(), null).getStatus()).isEqualTo(403);
    }

    @Test
    void connectionsAreNeverVisibleToAnotherCompany() throws Exception {
        Co a = company();
        Co b = company();
        call(put("/api/connections/apps/github"), a.ownerToken(), Map.of("enabled", true));
        connectGithub(a, a.ownerToken());
        JsonNode l = json.readTree(call(get("/api/connections"), b.ownerToken(), null).getContentAsString());
        assertThat(l.get("apps").get(0).get("connected").asBoolean()).isFalse();
        assertThat(call(get("/api/connections/github/preview"), b.ownerToken(), null).getStatus()).isIn(403, 404);
    }

    @Test
    void googleSignInUsesPkce() throws Exception {
        Co c = company();
        call(put("/api/connections/apps/google_drive"), c.ownerToken(), Map.of("enabled", true));
        String url = json.readTree(call(post("/api/connections/google_drive/authorize"), c.ownerToken(), null).getContentAsString()).get("url").asText();
        assertThat(url).contains("code_challenge=").contains("code_challenge_method=S256").contains("access_type=offline");
        String state = java.net.URLDecoder.decode(stateOf(url), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(crypto.pkceChallenge(crypto.pkceVerifier(state))).isEqualTo(
                java.net.URLDecoder.decode(UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("code_challenge"), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void microsoftSignInUsesPkceStoresRotatingTokensAndShowsOutlookEvents() throws Exception {
        Co c = company();
        call(put("/api/connections/apps/microsoft_calendar"), c.ownerToken(), Map.of("enabled", true));
        String url = json.readTree(call(post("/api/connections/microsoft_calendar/authorize"), c.ownerToken(), null).getContentAsString()).get("url").asText();
        assertThat(url).startsWith("https://login.microsoftonline.com/common/oauth2/v2.0/authorize").contains("client_id=ms-client")
                .contains("code_challenge_method=S256").contains("offline_access").contains("Calendars.Read");
        when(http.postForm(anyString(), anyMap())).thenReturn(json.readTree("{\"access_token\":\"eyJ.ms.token\",\"refresh_token\":\"ms-refresh\",\"expires_in\":3600}"));
        when(http.get(anyString(), anyString())).thenAnswer(inv -> {
            String u = inv.getArgument(0);
            if (u.contains("graph.microsoft.com/v1.0/me?")) return json.readTree("{\"mail\":\"me@corp.example\"}");
            if (u.contains("/calendarView")) return json.readTree("{\"value\":[{\"subject\":\"Planning\",\"start\":{\"dateTime\":\"2026-11-18T10:00:00.0000000\",\"timeZone\":\"UTC\"},\"webLink\":\"https://outlook.office.com/x\"}]}");
            return json.readTree("{}");
        });
        String state = java.net.URLDecoder.decode(stateOf(url), java.nio.charset.StandardCharsets.UTF_8);
        String next = mvc.perform(get("/api/connections/callback/microsoft_calendar").param("code", "abc").param("state", state)).andReturn().getResponse().getHeader("Location");
        assertThat(next).contains("status=ok");
        // the PKCE verifier went to Microsoft's token endpoint
        org.mockito.ArgumentCaptor<java.util.Map<String, String>> form = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(http).postForm(org.mockito.ArgumentMatchers.contains("login.microsoftonline.com/common/oauth2/v2.0/token"), form.capture());
        assertThat(form.getValue().get("code_verifier")).isEqualTo(crypto.pkceVerifier(state));

        String stored = jdbc.queryForObject("SELECT access_token_enc FROM connections WHERE company_id = ?", String.class, c.companyId());
        assertThat(stored).doesNotContain("eyJ.ms.token");
        JsonNode apps = json.readTree(call(get("/api/connections"), c.ownerToken(), null).getContentAsString()).get("apps");
        JsonNode ms = null;
        for (JsonNode a : apps) if (a.get("code").asText().equals("microsoft_calendar")) ms = a;
        assertThat(ms.get("account").asText()).isEqualTo("me@corp.example");
        JsonNode items = json.readTree(call(get("/api/connections/microsoft_calendar/preview"), c.ownerToken(), null).getContentAsString());
        assertThat(items.get(0).get("title").asText()).isEqualTo("Planning");
    }

    @Test
    void removingAPersonRemovesTheirAppConnections() throws Exception {
        Co c = company();
        call(put("/api/connections/apps/github"), c.ownerToken(), Map.of("enabled", true));
        String emp = employeeToken(c);
        assertThat(connectGithub(c, emp)).contains("status=ok");
        Long empId = jdbc.queryForObject("SELECT emp_id FROM users WHERE company_id = ? AND email = ?", Long.class, c.companyId(), "emp" + c.id() + "@example.com");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE company_id = ?", Long.class, c.companyId())).isEqualTo(1L);
        assertThat(call(delete("/api/employees/" + empId), c.ownerToken(), null).getStatus()).isIn(200, 204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE company_id = ?", Long.class, c.companyId())).isZero();
    }

    @Test
    void secretsCanBeRotatedWithoutLosingSavedSignIns() {
        ConnectionCrypto oldKey = new ConnectionCrypto("old-secret-old-secret-old-secret-old-0000", "");
        String enc = oldKey.encrypt("token-1");
        ConnectionCrypto newKey = new ConnectionCrypto("new-secret-new-secret-new-secret-new-1111", "old-secret-old-secret-old-secret-old-0000");
        assertThat(newKey.decrypt(enc)).isEqualTo("token-1");
        assertThat(newKey.needsRotation(enc)).isTrue();
        assertThat(newKey.needsRotation(newKey.encrypt("x"))).isFalse();
        // without the old secret listed, it cannot be read
        ConnectionCrypto forgot = new ConnectionCrypto("new-secret-new-secret-new-secret-new-1111", "");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> forgot.decrypt(enc)).hasMessageContaining("reconnect");
    }

    @Test
    void cryptoRoundTripsAndRejectsTampering() {
        String enc = crypto.encrypt("secret value");
        assertThat(enc).doesNotContain("secret");
        assertThat(crypto.decrypt(enc)).isEqualTo("secret value");
        String signed = crypto.sign("a|b");
        assertThat(crypto.verify(signed)).isEqualTo("a|b");
        assertThat(crypto.verify(signed + "x")).isNull();
        assertThat(crypto.verify(null)).isNull();
    }
}
