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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Phase 8c: role dashboards. A manager's numbers cover only their teams, an employee gets only their own, other companies are never mixed in. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:analytics-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    record Tenant(String token, String slug) {}
    record Person(String token, int empId) {}

    private MockHttpServletResponse send(RequestBuilder rb) throws Exception { return mvc.perform(rb).andReturn().getResponse(); }

    private Tenant newTenant(String label) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse res = send(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", label + " " + id, "ownerName", label + " Owner",
                        "email", label.toLowerCase() + id + "@example.com", "password", PASSWORD))));
        assertThat(res.getStatus()).isEqualTo(201);
        JsonNode b = json.readTree(res.getContentAsString());
        return new Tenant(b.get("token").asText(), b.get("companySlug").asText());
    }

    private MockHttpServletResponse call(String method, String url, String token, Object body) throws Exception {
        var rb = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
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

    private Person person(Tenant owner, String label, String role, String department) throws Exception {
        String email = label + owner.slug() + "@example.com";
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", label);
        emp.put("email", email);
        emp.put("password", PASSWORD);
        emp.put("role", role);
        emp.put("department", department);
        JsonNode created = ok(call("POST", "/api/employees", owner.token(), emp), 200);
        JsonNode login = ok(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", owner.slug())))), 200);
        return new Person(login.get("token").asText(), created.get("empId").asInt());
    }

    private int apply(Person p, LocalDate from, LocalDate to) throws Exception {
        return ok(call("POST", "/api/leaves", p.token(), Map.of("empId", p.empId(), "startDate", from.toString(), "endDate", to.toString(), "reason", "Trip")), 200)
                .get("id").asInt();
    }

    private long count(JsonNode list, String label) {
        for (JsonNode c : list) if (c.get("label").asText().equals(label)) return c.get("value").asLong();
        return 0;
    }

    @Test
    void companyOverviewCountsPeopleLeaveAndProjects() throws Exception {
        Tenant t = newTenant("Acme");
        Person alice = person(t, "Alice", "EMPLOYEE", "Engineering");
        Person bob = person(t, "Bob", "EMPLOYEE", "Engineering");
        Person carol = person(t, "Carol", "EMPLOYEE", "Sales");

        LocalDate today = LocalDate.now();
        int approvedId = apply(alice, today, today.plusDays(1));
        apply(bob, today.plusDays(10), today.plusDays(11));          // stays pending
        ok(call("PUT", "/api/leaves/" + approvedId + "/approve", t.token(), null), 200);

        JsonNode o = ok(call("GET", "/api/analytics/overview", t.token(), null), 200);
        assertThat(o.get("scope").asText()).isEqualTo("COMPANY");
        assertThat(o.get("headcount").asInt()).isEqualTo(4);                          // owner + 3
        assertThat(count(o.get("byDepartment"), "Engineering")).isEqualTo(2);
        assertThat(count(o.get("byDepartment"), "Sales")).isEqualTo(1);
        assertThat(count(o.get("byRole"), "EMPLOYEE")).isEqualTo(3);
        JsonNode leave = o.get("leave");
        assertThat(leave.get("pending").asInt()).isEqualTo(1);
        assertThat(leave.get("offToday")).extracting(JsonNode::asText).containsExactly("Alice");
        assertThat(leave.get("daysByMonth")).hasSize(6);
        assertThat(leave.get("daysByMonth").get(5).get("label").asText()).isEqualTo(java.time.YearMonth.now().toString());
        assertThat(leave.get("daysByMonth").get(5).get("value").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(count(leave.get("statusCounts"), "APPROVED")).isEqualTo(1);
        assertThat(o.get("attendance").get("days").asInt()).isEqualTo(30);
        assertThat(o.get("projects").get("issuesByStatus")).hasSize(4);
        assertThat(o.get("projects").get("overdueIssues").asLong()).isZero();
        assertThat(carol.empId()).isPositive();
    }

    @Test
    void managerSeesOnlyTheirTeamsAndEmployeesAreRefused() throws Exception {
        Tenant t = newTenant("Team");
        Person manager = person(t, "Mona", "MANAGER", "Engineering");
        Person alice = person(t, "Alice", "EMPLOYEE", "Engineering");
        Person outsider = person(t, "Olga", "EMPLOYEE", "Sales");
        Map<String, Object> team = new LinkedHashMap<>();
        team.put("name", "Core");
        team.put("memberIds", List.of(manager.empId(), alice.empId()));
        team.put("teamLeadId", manager.empId());
        ok(call("POST", "/api/teams", t.token(), team), 200);

        LocalDate today = LocalDate.now();
        int a = apply(alice, today, today);
        int o = apply(outsider, today, today);
        ok(call("PUT", "/api/leaves/" + a + "/approve", t.token(), null), 200);
        ok(call("PUT", "/api/leaves/" + o + "/approve", t.token(), null), 200);

        JsonNode view = ok(call("GET", "/api/analytics/overview", manager.token(), null), 200);
        assertThat(view.get("scope").asText()).isEqualTo("TEAM");
        assertThat(view.get("headcount").asInt()).isEqualTo(2);
        assertThat(view.get("leave").get("offToday")).extracting(JsonNode::asText).containsExactly("Alice");   // not Olga
        assertThat(count(view.get("byDepartment"), "Sales")).isZero();
        assertThat(view.get("byRole")).isEmpty();
        assertThat(view.get("projects").isNull()).isTrue();

        assertThat(call("GET", "/api/analytics/overview", alice.token(), null).getStatus()).isEqualTo(403);
    }

    @Test
    void everyonePersonalSummaryIsTheirOwn() throws Exception {
        Tenant t = newTenant("Mine");
        Person alice = person(t, "Alice", "EMPLOYEE", "Engineering");
        Person bob = person(t, "Bob", "EMPLOYEE", "Engineering");
        LocalDate today = LocalDate.now();
        int id = apply(alice, today.withDayOfMonth(1), today.withDayOfMonth(1).plusDays(2));
        apply(alice, today.plusDays(20), today.plusDays(21));
        ok(call("PUT", "/api/leaves/" + id + "/approve", t.token(), Map.of("note", "ok")), 200);

        JsonNode mine = ok(call("GET", "/api/analytics/me", alice.token(), null), 200);
        assertThat(mine.get("leaveDaysApprovedThisYear").asInt()).isEqualTo(3);
        assertThat(mine.get("pendingLeaveRequests").asInt()).isEqualTo(1);
        assertThat(mine.get("unreadNotifications").asLong()).isGreaterThanOrEqualTo(1);   // the decision

        JsonNode others = ok(call("GET", "/api/analytics/me", bob.token(), null), 200);
        assertThat(others.get("leaveDaysApprovedThisYear").asInt()).isZero();
        assertThat(others.get("pendingLeaveRequests").asInt()).isZero();
        assertThat(call("GET", "/api/analytics/me", "not-a-token", null).getStatus()).isIn(401, 403);
    }

    @Test
    void companiesAreNeverMixed() throws Exception {
        Tenant a = newTenant("Alpha");
        Tenant b = newTenant("Beta");
        person(a, "Alice", "EMPLOYEE", "Engineering");
        person(a, "Adam", "EMPLOYEE", "Engineering");
        JsonNode other = ok(call("GET", "/api/analytics/overview", b.token(), null), 200);
        assertThat(other.get("headcount").asInt()).isEqualTo(1);
        assertThat(other.get("byDepartment").toString()).doesNotContain("Engineering");
        assertThat(other.get("leave").get("offToday")).isEmpty();
    }
}
