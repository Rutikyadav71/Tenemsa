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

/**
 * Phase 5b: sprints, backlog, story points and labels. Lifecycle with carry-over, isolation,
 * role and validation rules, all through
 * the real HTTP stack against in-memory H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SprintManagementTest {

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

    private long sprint(Tenant t, long pid, String name) throws Exception {
        return ok(call("POST", "/api/projects/" + pid + "/sprints", t.token(), Map.of("name", name)), 201).get("id").asLong();
    }

    private void assign(String token, long issueId, Long sprintId, int expected) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("sprintId", sprintId);
        ok(call("PATCH", "/api/issues/" + issueId + "/sprint", token, b), expected);
    }

    @Test
    void sprintsAreInvisibleAndUntouchableAcrossCompanies() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        long pid = project(a, "ISO");
        long sid = sprint(a, pid, "Secret sprint");
        long iid = issue(a.token(), pid, "Task", null).get("id").asLong();

        for (String[] r : new String[][]{
                {"GET", "/api/projects/" + pid + "/sprints"}, {"POST", "/api/sprints/" + sid + "/start"},
                {"POST", "/api/sprints/" + sid + "/complete"}, {"DELETE", "/api/sprints/" + sid}}) {
            assertThat(call(r[0], r[1], b.token(), null).getStatus()).as(r[0] + " " + r[1]).isEqualTo(404);
        }
        assertThat(call("PUT", "/api/sprints/" + sid, b.token(), Map.of("name", "X")).getStatus()).isEqualTo(404);
        assign(b.token(), iid, sid, 404);
        // B cannot sneak A's sprint into B's own issue either
        long bp = project(b, "BEE");
        long bi = issue(b.token(), bp, "B task", null).get("id").asLong();
        assign(b.token(), bi, sid, 404);
    }

    @Test
    void lifecycleCarriesUnfinishedIssuesOver() throws Exception {
        Tenant t = newTenant("Acme");
        long pid = project(t, "LIF");
        long s1 = sprint(t, pid, "Sprint 1");
        long s2 = sprint(t, pid, "Sprint 2");
        long done = issue(t.token(), pid, "Done one", null).get("id").asLong();
        long open = issue(t.token(), pid, "Open one", null).get("id").asLong();
        assign(t.token(), done, s1, 200);
        assign(t.token(), open, s1, 200);
        ok(call("PATCH", "/api/issues/" + done + "/status", t.token(), Map.of("status", "DONE")), 200);

        JsonNode started = ok(call("POST", "/api/sprints/" + s1 + "/start", t.token(), null), 200);
        assertThat(started.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(started.get("startDate").isNull()).isFalse();
        assertThat(started.get("endDate").isNull()).isFalse();

        // only one active sprint per project
        assertThat(call("POST", "/api/sprints/" + s2 + "/start", t.token(), null).getStatus()).isEqualTo(400);

        JsonNode completed = ok(call("POST", "/api/sprints/" + s1 + "/complete", t.token(), Map.of("moveUnfinishedTo", s2)), 200);
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("completedCount").asInt()).isEqualTo(1);
        assertThat(completed.get("carriedOverCount").asInt()).isEqualTo(1);

        assertThat(ok(call("GET", "/api/issues/" + open, t.token(), null), 200).get("sprintId").asLong()).isEqualTo(s2);
        assertThat(ok(call("GET", "/api/issues/" + done, t.token(), null), 200).get("sprintId").asLong()).isEqualTo(s1);

        // completed sprints are frozen
        assertThat(call("PUT", "/api/sprints/" + s1, t.token(), Map.of("name", "Renamed")).getStatus()).isEqualTo(400);
        assertThat(call("DELETE", "/api/sprints/" + s1, t.token(), null).getStatus()).isEqualTo(400);
        assertThat(call("POST", "/api/sprints/" + s1 + "/start", t.token(), null).getStatus()).isEqualTo(400);
        assign(t.token(), done, null, 400);   // cannot leave a completed sprint
        long fresh = issue(t.token(), pid, "Fresh", null).get("id").asLong();
        assign(t.token(), fresh, s1, 400);    // cannot join a completed sprint
    }

    @Test
    void completingWithoutTargetSendsUnfinishedToBacklog() throws Exception {
        Tenant t = newTenant("Acme");
        long pid = project(t, "BLG");
        long s = sprint(t, pid, "Only");
        long i = issue(t.token(), pid, "Unfinished", null).get("id").asLong();
        assign(t.token(), i, s, 200);
        ok(call("POST", "/api/sprints/" + s + "/start", t.token(), null), 200);
        ok(call("POST", "/api/sprints/" + s + "/complete", t.token(), null), 200);
        assertThat(ok(call("GET", "/api/issues/" + i, t.token(), null), 200).get("sprintId").isNull()).isTrue();
    }

    @Test
    void assignmentAndRoleRules() throws Exception {
        Tenant t = newTenant("Acme");
        long p1 = project(t, "ONE");
        long p2 = project(t, "TWO");
        long s1 = sprint(t, p1, "S1");
        long s2 = sprint(t, p2, "S2");
        long i1 = issue(t.token(), p1, "I1", null).get("id").asLong();
        assign(t.token(), i1, s2, 400);          // sprint of another project
        assign(t.token(), i1, s1, 200);
        assign(t.token(), i1, null, 200);        // back to backlog

        // employees can read sprints but not plan them
        String emp = employeeToken(t, "worker");
        assertThat(call("GET", "/api/projects/" + p1 + "/sprints", emp, null).getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/projects/" + p1 + "/sprints", emp, Map.of("name", "Nope")).getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/sprints/" + s1 + "/start", emp, null).getStatus()).isEqualTo(403);
        assertThat(call("DELETE", "/api/sprints/" + s1, emp, null).getStatus()).isEqualTo(403);
        assign(emp, i1, s1, 403);

        // completing a sprint cannot target an unplanned/other-project sprint
        ok(call("POST", "/api/sprints/" + s1 + "/start", t.token(), null), 200);
        assertThat(call("POST", "/api/sprints/" + s1 + "/complete", t.token(), Map.of("moveUnfinishedTo", s2)).getStatus()).isEqualTo(400);
    }

    @Test
    void deletingAPlannedSprintReturnsIssuesToBacklog() throws Exception {
        Tenant t = newTenant("Acme");
        long pid = project(t, "DEL");
        long s = sprint(t, pid, "Throwaway");
        long i = issue(t.token(), pid, "Planned", null).get("id").asLong();
        assign(t.token(), i, s, 200);
        ok(call("DELETE", "/api/sprints/" + s, t.token(), null), 204);
        assertThat(ok(call("GET", "/api/issues/" + i, t.token(), null), 200).get("sprintId").isNull()).isTrue();

        long active = sprint(t, pid, "Active");
        ok(call("POST", "/api/sprints/" + active + "/start", t.token(), null), 200);
        assertThat(call("DELETE", "/api/sprints/" + active, t.token(), null).getStatus()).isEqualTo(400);
    }

    @Test
    void pointsLabelsAndDatesAreValidated() throws Exception {
        Tenant t = newTenant("Acme");
        long pid = project(t, "VAL");

        Map<String, Object> good = new LinkedHashMap<>();
        good.put("title", "With extras");
        good.put("type", "STORY");
        good.put("priority", "MEDIUM");
        good.put("storyPoints", 5);
        good.put("labels", java.util.List.of("Backend", " backend ", "api-v2"));
        JsonNode created = ok(call("POST", "/api/projects/" + pid + "/issues", t.token(), good), 201);
        assertThat(created.get("storyPoints").asInt()).isEqualTo(5);
        assertThat(created.get("labels")).hasSize(2);   // normalised + de-duplicated
        assertThat(created.get("labels").get(0).asText()).isEqualTo("backend");

        for (Object bad : new Object[]{-1, 101}) {
            Map<String, Object> b = new LinkedHashMap<>(good);
            b.put("storyPoints", bad);
            assertThat(call("POST", "/api/projects/" + pid + "/issues", t.token(), b).getStatus()).as("points " + bad).isEqualTo(400);
        }
        Map<String, Object> badLabel = new LinkedHashMap<>(good);
        badLabel.put("labels", java.util.List.of("no/slashes!"));
        assertThat(call("POST", "/api/projects/" + pid + "/issues", t.token(), badLabel).getStatus()).isEqualTo(400);

        Map<String, Object> badDates = new LinkedHashMap<>();
        badDates.put("name", "Backwards");
        badDates.put("startDate", "2026-02-10");
        badDates.put("endDate", "2026-02-01");
        assertThat(call("POST", "/api/projects/" + pid + "/sprints", t.token(), badDates).getStatus()).isEqualTo(400);
        assertThat(call("POST", "/api/projects/" + pid + "/sprints", t.token(), Map.of("name", " ")).getStatus()).isEqualTo(400);
    }
}
