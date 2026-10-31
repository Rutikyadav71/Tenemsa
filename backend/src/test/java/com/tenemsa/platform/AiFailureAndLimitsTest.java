package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.tenemsa.platform.ai.provider.AiClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Phase 6a: what happens when the model misbehaves. Bad JSON gets one repair attempt, provider errors get one
 * retry, slow answers time out, and quotas stop runaway use. Quotas here are set low through test properties.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-limits-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "ai.per-user-per-hour=4", "ai.per-company-per-day=100", "ai.timeout-seconds=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFailureAndLimitsTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @MockitoBean AiClient aiClient;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    record Tenant(String token, String slug) {}

    // ---- helpers ---------------------------------------------------------

    private Tenant newTenant(String label) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse res = send(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", label + " " + id, "ownerName", label + " Owner",
                        "email", label.toLowerCase() + id + "@example.com", "password", PASSWORD))));
        assertThat(res.getStatus()).isEqualTo(201);
        JsonNode b = json.readTree(res.getContentAsString());
        return new Tenant(b.get("token").asText(), b.get("companySlug").asText());
    }

    private MockHttpServletResponse send(RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse();
    }

    private MockHttpServletResponse call(String method, String url, String token, Object body) throws Exception {
        var rb = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "PATCH" -> patch(url);
            case "DELETE" -> delete(url);
            default -> get(url);
        };
        rb.header("Authorization", "Bearer " + token);
        if (body != null) rb.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return send(rb);
    }

    private JsonNode ok(MockHttpServletResponse res, int expected) throws Exception {
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(expected);
        String body = res.getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private long project(Tenant t, String key) throws Exception {
        return ok(call("POST", "/api/projects", t.token(), Map.of("key", key, "name", "Project " + key)), 201).get("id").asLong();
    }

    private JsonNode issue(String token, long projectId, String title, Integer assignee) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("type", "BUG");
        body.put("priority", "HIGH");
        if (assignee != null) body.put("assigneeEmpId", assignee);
        return ok(call("POST", "/api/projects/" + projectId + "/issues", token, body), 201);
    }

    /** Creates an EMPLOYEE in the tenant (empId 2, 3, ...) and returns that person's own token. */
    private String employeeToken(Tenant owner, String label) throws Exception {
        String email = label + owner.slug() + "@example.com";
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", label);
        emp.put("email", email);
        emp.put("password", PASSWORD);
        emp.put("role", "EMPLOYEE");
        ok(call("POST", "/api/employees", owner.token(), emp), 200);
        JsonNode login = ok(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", owner.slug())))), 200);
        return login.get("token").asText();
    }


    private String roleToken(Tenant owner, String label, String role) throws Exception {
        String email = label + owner.slug() + "@example.com";
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", label);
        emp.put("email", email);
        emp.put("password", PASSWORD);
        emp.put("role", role);
        ok(call("POST", "/api/employees", owner.token(), emp), 200);
        JsonNode login = ok(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", owner.slug())))), 200);
        return login.get("token").asText();
    }

    private static final String GOOD = "{\"summary\":\"The team agreed to ship v2 on Friday.\",\"decisions\":[\"Ship v2 on Friday\"],\"actionItems\":[{\"title\":\"Prepare release notes\",\"description\":\"Draft the notes\",\"owner\":\"Priya\",\"deadline\":\"2030-01-15\",\"priority\":\"HIGH\"},{\"title\":\"Fix login bug\",\"description\":\"\",\"owner\":\"\",\"deadline\":\"not a date\",\"priority\":\"urgent\"}],\"openQuestions\":[\"Who owns QA?\"]}";
    private static final String NOTES = "Weekly sync. We agreed to ship v2 on Friday. Priya will prepare the release notes by January 15. Someone must fix the login bug. Open question: who owns QA?";

    private JsonNode generate(String token, String title) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("notes", NOTES);
        return ok(call("POST", "/api/meetings", token, body), 201);
    }

    private AiClient.AiCompletion good() { return new AiClient.AiCompletion(GOOD, "test-model", 10, 10); }

    @BeforeEach
    void model() {
        reset(aiClient);
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.providerName()).thenReturn("test");
    }

    private int generateStatus(String token) throws Exception {
        return call("POST", "/api/meetings", token, Map.of("title", "Sync", "notes", NOTES)).getStatus();
    }

    @Test
    void invalidJsonIsRepairedOnceThenAccepted() throws Exception {
        Tenant t = newTenant("Acme");
        when(aiClient.complete(any(), any(), any(), anyDouble()))
                .thenReturn(new AiClient.AiCompletion("Sorry, here you go: not json", "m", 5, 5))
                .thenReturn(new AiClient.AiCompletion("```json\n" + GOOD + "\n```", "m", 5, 5));
        assertThat(generateStatus(t.token())).isEqualTo(201);
        verify(aiClient, times(2)).complete(any(), any(), any(), anyDouble());
        // both attempts' tokens are counted in ONE usage row
        JsonNode usage = ok(call("GET", "/api/ai/usage", t.token(), null), 200);
        assertThat(usage.get("totalRequests").asInt()).isEqualTo(1);
        assertThat(usage.get("inputTokens").asInt()).isEqualTo(10);
    }

    @Test
    void outputThatStaysInvalidIsAClean502AndNothingIsSaved() throws Exception {
        Tenant t = newTenant("Acme");
        when(aiClient.complete(any(), any(), any(), anyDouble()))
                .thenReturn(new AiClient.AiCompletion("{\"summary\":\"\",\"actionItems\":[]}", "m", 5, 5));
        var res = call("POST", "/api/meetings", t.token(), Map.of("title", "Sync", "notes", NOTES));
        assertThat(res.getStatus()).isEqualTo(502);
        assertThat(res.getContentAsString()).contains("could not be understood");
        assertThat(ok(call("GET", "/api/meetings", t.token(), null), 200)).isEmpty();
        JsonNode usage = ok(call("GET", "/api/ai/usage", t.token(), null), 200);
        assertThat(usage.get("failed").asInt()).isEqualTo(1);
    }

    @Test
    void providerErrorsAreRetriedOnceThenReported() throws Exception {
        Tenant t = newTenant("Acme");
        when(aiClient.complete(any(), any(), any(), anyDouble()))
                .thenThrow(new com.tenemsa.platform.ai.exception.AiProviderException("boom", new RuntimeException("secret-key-123")));
        var res = call("POST", "/api/meetings", t.token(), Map.of("title", "Sync", "notes", NOTES));
        assertThat(res.getStatus()).isEqualTo(502);
        assertThat(res.getContentAsString()).doesNotContain("secret-key-123").doesNotContain("boom");
        verify(aiClient, times(2)).complete(any(), any(), any(), anyDouble());

        // a transient failure that recovers on the retry is invisible to the user
        reset(aiClient);
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.providerName()).thenReturn("test");
        when(aiClient.complete(any(), any(), any(), anyDouble()))
                .thenThrow(new com.tenemsa.platform.ai.exception.AiProviderException("blip", null))
                .thenReturn(good());
        assertThat(generateStatus(t.token())).isEqualTo(201);
    }

    @Test
    void aSlowModelTimesOutWith504() throws Exception {
        Tenant t = newTenant("Acme");
        when(aiClient.complete(any(), any(), any(), anyDouble())).thenAnswer(inv -> {
            Thread.sleep(5000);
            return good();
        });
        var res = call("POST", "/api/meetings", t.token(), Map.of("title", "Sync", "notes", NOTES));
        assertThat(res.getStatus()).isEqualTo(504);
        assertThat(res.getContentAsString()).contains("too long");
    }

    @Test
    void quotasStopRunawayUsePerUserNotPerCompany() throws Exception {
        Tenant t = newTenant("Acme");
        String manager = roleToken(t, "mgr", "MANAGER");
        when(aiClient.complete(any(), any(), any(), anyDouble())).thenReturn(good());

        for (int i = 0; i < 4; i++) assertThat(generateStatus(t.token())).as("request " + i).isEqualTo(201);
        var refused = call("POST", "/api/meetings", t.token(), Map.of("title", "Sync", "notes", NOTES));
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getContentAsString()).contains("per hour");
        verify(aiClient, times(4)).complete(any(), any(), any(), anyDouble());   // the 5th never reached the model

        assertThat(ok(call("GET", "/api/ai/status", t.token(), null), 200).get("requestsLeftThisHour").asInt()).isZero();
        assertThat(generateStatus(manager)).as("another user of the same company still has their own quota").isEqualTo(201);

        JsonNode usage = ok(call("GET", "/api/ai/usage", t.token(), null), 200);
        assertThat(usage.get("rateLimited").asInt()).isEqualTo(1);
    }
}
