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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** With the default configuration (ai.provider=none) the app runs without a model or key and says so plainly. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ai-disabled-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiDisabledTest {

    private static final String PASSWORD = "Passw0rd!Test";

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


    @Test
    void featuresAnswer503WithAHelpfulMessageWhenAiIsOff() throws Exception {
        Tenant t = newTenant("Acme");
        JsonNode status = ok(call("GET", "/api/ai/status", t.token(), null), 200);
        assertThat(status.get("enabled").asBoolean()).isFalse();

        var res = call("POST", "/api/meetings", t.token(), Map.of("title", "Sync",
                "notes", "Weekly sync. We agreed to ship v2 on Friday. Priya will prepare the release notes."));
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getContentAsString()).contains("AI_PROVIDER");
        assertThat(ok(call("GET", "/api/meetings", t.token(), null), 200)).isEmpty();
        assertThat(call("GET", "/api/ai/status", "bad-token", null).getStatus()).as("needs a valid token").isIn(401, 403);
    }
}
