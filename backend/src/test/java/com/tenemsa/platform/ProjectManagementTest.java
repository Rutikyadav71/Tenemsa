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
 * Phase 5a: projects, issues and comments. Isolation between companies, per-project
 * issue numbering, validation, role rules and the archive/delete rules, all through
 * the real HTTP stack against in-memory H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectManagementTest {

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

    // ---- isolation ---------------------------------------------------------

    @Test
    void projectsAndIssuesAreInvisibleAndUntouchableAcrossCompanies() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        long pid = project(a, "SEC");
        long iid = issue(a.token(), pid, "Secret plan", null).get("id").asLong();
        call("POST", "/api/issues/" + iid + "/comments", a.token(), Map.of("body", "secret note"));

        assertThat(call("GET", "/api/projects", a.token(), null).getContentAsString()).contains("SEC");
        assertThat(call("GET", "/api/projects", b.token(), null).getContentAsString()).doesNotContain("SEC");

        for (String[] r : new String[][]{
                {"GET", "/api/projects/" + pid}, {"DELETE", "/api/projects/" + pid},
                {"GET", "/api/projects/" + pid + "/issues"}, {"GET", "/api/issues/" + iid},
                {"DELETE", "/api/issues/" + iid}, {"GET", "/api/issues/" + iid + "/comments"}}) {
            assertThat(call(r[0], r[1], b.token(), null).getStatus()).as(r[0] + " " + r[1]).isEqualTo(404);
        }
        assertThat(call("PUT", "/api/projects/" + pid, b.token(), Map.of("name", "Hijack")).getStatus()).isEqualTo(404);
        assertThat(call("POST", "/api/projects/" + pid + "/issues", b.token(), Map.of("title", "Injected")).getStatus()).isEqualTo(404);
        assertThat(call("PUT", "/api/issues/" + iid, b.token(), Map.of("title", "Hijack")).getStatus()).isEqualTo(404);
        assertThat(call("PATCH", "/api/issues/" + iid + "/status", b.token(), Map.of("status", "DONE")).getStatus()).isEqualTo(404);
        assertThat(call("POST", "/api/issues/" + iid + "/comments", b.token(), Map.of("body", "x")).getStatus()).isEqualTo(404);

        // Untouched for the real owner.
        JsonNode still = ok(call("GET", "/api/issues/" + iid, a.token(), null), 200);
        assertThat(still.get("title").asText()).isEqualTo("Secret plan");
        assertThat(still.get("status").asText()).isEqualTo("TODO");
        assertThat(call("GET", "/api/issues/" + iid + "/comments", a.token(), null).getContentAsString()).doesNotContain("Hijack").contains("secret note");
    }

    @Test
    void theSameKeyIsFineInTwoCompaniesButNotTwiceInOne() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        project(a, "WEB");
        MockHttpServletResponse dup = call("POST", "/api/projects", a.token(), Map.of("key", "WEB", "name", "Again"));
        assertThat(dup.getStatus()).isEqualTo(400);
        assertThat(dup.getContentAsString()).contains("already exists");
        project(b, "WEB");
    }

    // ---- numbering and board -------------------------------------------------

    @Test
    void issuesAreNumberedPerProjectAndMoveAcrossTheBoard() throws Exception {
        Tenant a = newTenant("Acme");
        long p1 = project(a, "ONE");
        long p2 = project(a, "TWO");

        assertThat(issue(a.token(), p1, "first", null).get("key").asText()).isEqualTo("ONE-1");
        assertThat(issue(a.token(), p1, "second", null).get("key").asText()).isEqualTo("ONE-2");
        JsonNode third = issue(a.token(), p1, "third", 1);
        assertThat(third.get("key").asText()).isEqualTo("ONE-3");
        assertThat(third.get("assigneeName").asText()).contains("Owner");
        assertThat(issue(a.token(), p2, "other project", null).get("key").asText()).isEqualTo("TWO-1");

        long id = third.get("id").asLong();
        assertThat(ok(call("PATCH", "/api/issues/" + id + "/status", a.token(), Map.of("status", "IN_PROGRESS")), 200)
                .get("status").asText()).isEqualTo("IN_PROGRESS");

        assertThat(call("GET", "/api/projects/" + p1 + "/issues?status=IN_PROGRESS", a.token(), null).getContentAsString())
                .contains("third").doesNotContain("first");
        assertThat(call("GET", "/api/projects/" + p1 + "/issues?q=ONE-2", a.token(), null).getContentAsString())
                .contains("second").doesNotContain("third");

        // Assigned to the Owner (#1) and not done, so it shows in "my issues".
        assertThat(call("GET", "/api/issues/mine", a.token(), null).getContentAsString()).contains("third");
        call("PATCH", "/api/issues/" + id + "/status", a.token(), Map.of("status", "DONE"));
        assertThat(call("GET", "/api/issues/mine", a.token(), null).getContentAsString()).doesNotContain("third");

        // Progress counts on the project list: 3 issues, 1 done.
        JsonNode list = ok(call("GET", "/api/projects", a.token(), null), 200);
        for (JsonNode p : list) {
            if (p.get("key").asText().equals("ONE")) {
                assertThat(p.get("totalIssues").asLong()).isEqualTo(3);
                assertThat(p.get("doneIssues").asLong()).isEqualTo(1);
            }
        }
    }

    // ---- validation ----------------------------------------------------------

    @Test
    void inputIsValidated() throws Exception {
        Tenant a = newTenant("Acme");

        MockHttpServletResponse badKey = call("POST", "/api/projects", a.token(), Map.of("key", "web", "name", "X"));
        assertThat(badKey.getStatus()).isEqualTo(400);
        assertThat(badKey.getContentAsString()).contains("Key must be 2 to 10 capital letters or digits");

        assertThat(call("POST", "/api/projects", a.token(), Map.of("key", "OK", "name", "  ")).getStatus()).isEqualTo(400);
        assertThat(call("POST", "/api/projects", a.token(), Map.of("key", "OK", "name", "N", "leadEmpId", 99)).getStatus()).isEqualTo(400);

        long pid = project(a, "VAL");
        MockHttpServletResponse noTitle = call("POST", "/api/projects/" + pid + "/issues", a.token(), Map.of("title", " "));
        assertThat(noTitle.getStatus()).isEqualTo(400);
        assertThat(noTitle.getContentAsString()).contains("Title is required.");

        MockHttpServletResponse noAssignee = call("POST", "/api/projects/" + pid + "/issues", a.token(), Map.of("title", "T", "assigneeEmpId", 99));
        assertThat(noAssignee.getStatus()).isEqualTo(400);
        assertThat(noAssignee.getContentAsString()).contains("does not exist in your company");

        assertThat(call("GET", "/api/projects/" + pid + "/issues?status=NOPE", a.token(), null).getStatus()).isEqualTo(400);
        assertThat(call("POST", "/api/projects/" + pid + "/issues", a.token(), Map.of("title", "T", "type", "EPIC")).getStatus()).isEqualTo(400);
        assertThat(call("POST", "/api/issues/1/comments", a.token(), Map.of("body", " ")).getStatus()).isEqualTo(400);
    }

    // ---- roles ---------------------------------------------------------------

    @Test
    void employeesCanRaiseIssuesButOnlyChangeTheirOwnOrAssignedOnes() throws Exception {
        Tenant a = newTenant("Acme");
        String emp2 = employeeToken(a, "worker2"); // empId 2
        String emp3 = employeeToken(a, "worker3"); // empId 3
        long pid = project(a, "TEAM");

        // Everyone can look; only managers can create projects.
        assertThat(call("GET", "/api/projects", emp2, null).getContentAsString()).contains("TEAM");
        assertThat(call("POST", "/api/projects", emp2, Map.of("key", "NOPE", "name", "x")).getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/api/projects/" + pid, emp2, Map.of("name", "x")).getStatus()).isEqualTo(403);

        // The assignee picker lists names and numbers only, never emails.
        String people = call("GET", "/api/projects/people", emp2, null).getContentAsString();
        assertThat(people).contains("worker3").doesNotContain("@example.com");

        // Employee #2 raises an issue and is its reporter.
        JsonNode created = issue(emp2, pid, "Login button broken", null);
        long id = created.get("id").asLong();
        assertThat(created.get("reporterEmpId").asInt()).isEqualTo(2);
        assertThat(call("PATCH", "/api/issues/" + id + "/status", emp2, Map.of("status", "IN_PROGRESS")).getStatus()).isEqualTo(200);

        // Employee #3 is neither reporter nor assignee: may read and comment, not change.
        assertThat(call("GET", "/api/issues/" + id, emp3, null).getStatus()).isEqualTo(200);
        assertThat(call("PATCH", "/api/issues/" + id + "/status", emp3, Map.of("status", "DONE")).getStatus()).isEqualTo(403);
        assertThat(call("DELETE", "/api/issues/" + id, emp3, null).getStatus()).isEqualTo(403);
        assertThat(call("POST", "/api/issues/" + id + "/comments", emp3, Map.of("body", "I can reproduce this")).getStatus()).isEqualTo(201);

        // The Owner assigns it to #3; now #3 may move it.
        Map<String, Object> reassign = new LinkedHashMap<>();
        reassign.put("title", "Login button broken");
        reassign.put("assigneeEmpId", 3);
        assertThat(call("PUT", "/api/issues/" + id, a.token(), reassign).getStatus()).isEqualTo(200);
        assertThat(call("PATCH", "/api/issues/" + id + "/status", emp3, Map.of("status", "DONE")).getStatus()).isEqualTo(200);

        JsonNode comments = ok(call("GET", "/api/issues/" + id + "/comments", emp2, null), 200);
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).get("authorName").asText()).isEqualTo("worker3");
    }

    // ---- archive and delete ----------------------------------------------------

    @Test
    void archivedProjectsAreReadOnlyAndOnlyArchivedOnesCanBeDeleted() throws Exception {
        Tenant a = newTenant("Acme");
        long pid = project(a, "OLD");
        long iid = issue(a.token(), pid, "legacy", null).get("id").asLong();
        call("POST", "/api/issues/" + iid + "/comments", a.token(), Map.of("body", "c"));

        MockHttpServletResponse early = call("DELETE", "/api/projects/" + pid, a.token(), null);
        assertThat(early.getStatus()).isEqualTo(400);
        assertThat(early.getContentAsString()).contains("Archive the project first");

        Map<String, Object> archive = new LinkedHashMap<>();
        archive.put("name", "Old project");
        archive.put("status", "ARCHIVED");
        assertThat(ok(call("PUT", "/api/projects/" + pid, a.token(), archive), 200).get("status").asText()).isEqualTo("ARCHIVED");

        MockHttpServletResponse readOnly = call("POST", "/api/projects/" + pid + "/issues", a.token(), Map.of("title", "new"));
        assertThat(readOnly.getStatus()).isEqualTo(400);
        assertThat(readOnly.getContentAsString()).contains("archived and read-only");
        assertThat(call("PATCH", "/api/issues/" + iid + "/status", a.token(), Map.of("status", "DONE")).getStatus()).isEqualTo(400);
        // Still readable.
        assertThat(call("GET", "/api/issues/" + iid, a.token(), null).getStatus()).isEqualTo(200);

        assertThat(call("DELETE", "/api/projects/" + pid, a.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/api/projects/" + pid, a.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("GET", "/api/issues/" + iid, a.token(), null).getStatus()).isEqualTo(404);

        // Creating and deleting projects is in the audit trail.
        String audit = call("GET", "/api/audit?size=50", a.token(), null).getContentAsString();
        assertThat(audit).contains("PROJECT_CREATED").contains("PROJECT_DELETED");
    }
}
