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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Phase 4e: teams and attendance are isolated per company, and the entity-bound
 * endpoints (employees, teams, attendance) now validate their input and ignore
 * fields a client must not control.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamAttendanceEmployeeTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    record Tenant(String token, Long companyId, String slug) {}

    // ---- helpers ---------------------------------------------------------

    private Tenant newTenant(String label) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse res = mvc.perform(post("/api/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("companyName", label + " " + id,
                                "ownerName", label + " Owner " + id, "email", label.toLowerCase() + id + "@example.com",
                                "password", PASSWORD))))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(res.getContentAsString());
        return new Tenant(body.get("token").asText(), body.get("companyId").asLong(), body.get("companySlug").asText());
    }

    private MockHttpServletResponse send(RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse();
    }

    private RequestBuilder jsonPost(String url, Tenant t, Object body) throws Exception {
        return post(url).header("Authorization", "Bearer " + t.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private RequestBuilder jsonPut(String url, Tenant t, Object body) throws Exception {
        return put(url).header("Authorization", "Bearer " + t.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private String getBody(String url, Tenant t) throws Exception {
        return send(get(url).header("Authorization", "Bearer " + t.token())).getContentAsString();
    }

    private int getStatus(String url, Tenant t) throws Exception {
        return send(get(url).header("Authorization", "Bearer " + t.token())).getStatus();
    }

    private JsonNode createEmployee(Tenant owner, Map<String, Object> body) throws Exception {
        MockHttpServletResponse res = send(jsonPost("/api/employees", owner, body));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString());
    }

    private Map<String, Object> employee(String email) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Worker");
        m.put("email", email);
        m.put("password", PASSWORD);
        m.put("role", "EMPLOYEE");
        m.put("department", "Ops");
        m.put("salary", 1000);
        return m;
    }

    private Map<String, Object> team(String name, List<Integer> members) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("description", "d");
        m.put("projectName", "p");
        m.put("status", "ACTIVE");
        m.put("memberIds", members);
        return m;
    }

    private Map<String, Object> mark(int empId, String date, String status, String in, String out) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("empId", empId);
        m.put("date", date);
        m.put("status", status);
        m.put("checkIn", in);
        m.put("checkOut", out);
        return m;
    }

    // ---- teams -----------------------------------------------------------

    @Test
    void teamsAreInvisibleAndUntouchableAcrossCompanies() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        createEmployee(a, employee("w" + a.slug() + "@example.com")); // becomes #2 in Acme

        MockHttpServletResponse created = send(jsonPost("/api/teams", a, team("AcmeSecretTeam", List.of(1, 2))));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(200);
        long id = json.readTree(created.getContentAsString()).get("id").asLong();

        assertThat(getBody("/api/teams", a)).contains("AcmeSecretTeam");
        assertThat(getBody("/api/teams", b)).doesNotContain("AcmeSecretTeam");
        assertThat(getStatus("/api/teams/" + id, b)).isEqualTo(404);
        assertThat(send(jsonPut("/api/teams/" + id, b, team("Hijacked", List.of(1)))).getStatus()).isEqualTo(404);
        assertThat(send(post("/api/teams/" + id + "/members/1").header("Authorization", "Bearer " + b.token())).getStatus()).isEqualTo(404);
        assertThat(send(delete("/api/teams/" + id).header("Authorization", "Bearer " + b.token())).getStatus()).isEqualTo(404);

        // Still there, unchanged.
        assertThat(getBody("/api/teams/" + id, a)).contains("AcmeSecretTeam").doesNotContain("Hijacked");
    }

    @Test
    void teamInputIsValidated() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        createEmployee(a, employee("w" + a.slug() + "@example.com")); // #2 exists only in Acme

        MockHttpServletResponse blank = send(jsonPost("/api/teams", a, team("  ", List.of(1))));
        assertThat(blank.getStatus()).isEqualTo(400);
        assertThat(blank.getContentAsString()).contains("Team name is required.");

        MockHttpServletResponse unknown = send(jsonPost("/api/teams", a, team("T", List.of(1, 99))));
        assertThat(unknown.getStatus()).isEqualTo(400);
        assertThat(unknown.getContentAsString()).contains("Employee #99 does not exist in your company.");

        // Globex has no employee #2, even though Acme does.
        assertThat(send(jsonPost("/api/teams", b, team("T", List.of(2)))).getStatus()).isEqualTo(400);

        assertThat(send(jsonPost("/api/teams", a, team("T", List.of(-5)))).getStatus()).isEqualTo(400);

        // Repeated members are stored once.
        MockHttpServletResponse ok = send(jsonPost("/api/teams", a, team("Dedupe", List.of(1, 2, 2, 1))));
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(json.readTree(ok.getContentAsString()).get("memberIds")).hasSize(2);
    }

    // ---- attendance --------------------------------------------------------

    @Test
    void attendanceIsIsolatedBetweenCompaniesEvenForTheSameEmpIdAndDate() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        String date = "2026-03-10";

        assertThat(send(jsonPost("/api/attendance/mark", a, mark(1, date, "PRESENT", "09:00", "17:00"))).getStatus()).isEqualTo(200);
        // Both Owners are employee #1. Globex marking its own #1 must not touch Acme's row.
        assertThat(send(jsonPost("/api/attendance/mark", b, mark(1, date, "ABSENT", null, null))).getStatus()).isEqualTo(200);

        assertThat(getBody("/api/attendance/employee/1?start=" + date + "&end=" + date, a)).contains("PRESENT").doesNotContain("ABSENT");
        assertThat(getBody("/api/attendance/employee/1?start=" + date + "&end=" + date, b)).contains("ABSENT").doesNotContain("PRESENT");
        assertThat(getBody("/api/attendance/date/" + date, a)).doesNotContain("ABSENT");
        assertThat(getBody("/api/attendance/range?start=" + date + "&end=" + date, b)).doesNotContain("PRESENT");

        // Employee #2 only exists in Acme.
        createEmployee(a, employee("w" + a.slug() + "@example.com"));
        MockHttpServletResponse res = send(jsonPost("/api/attendance/mark", b, mark(2, date, "PRESENT", null, null)));
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentAsString()).contains("does not exist in your company");
    }

    @Test
    void attendanceInputIsValidatedAndHoursAreComputedByTheServer() throws Exception {
        Tenant a = newTenant("Acme");

        Map<String, Object> noStatus = mark(1, "2026-03-11", "PRESENT", null, null);
        noStatus.remove("status");
        MockHttpServletResponse missing = send(jsonPost("/api/attendance/mark", a, noStatus));
        assertThat(missing.getStatus()).isEqualTo(400);
        assertThat(missing.getContentAsString()).contains("Status is required.");

        MockHttpServletResponse backwards = send(jsonPost("/api/attendance/mark", a, mark(1, "2026-03-11", "PRESENT", "17:00", "09:00")));
        assertThat(backwards.getStatus()).isEqualTo(400);
        assertThat(backwards.getContentAsString()).contains("Check-out must be after check-in.");

        // An unknown enum value is the client's mistake: 400, not 500.
        assertThat(send(jsonPost("/api/attendance/mark", a, mark(1, "2026-03-11", "SLEEPING", null, null))).getStatus()).isEqualTo(400);

        // A client-supplied workHours is ignored; the server computes 8.5 from the times.
        Map<String, Object> body = mark(1, "2026-03-12", "PRESENT", "09:00", "17:30");
        body.put("workHours", 99.0);
        MockHttpServletResponse ok = send(jsonPost("/api/attendance/mark", a, body));
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(json.readTree(ok.getContentAsString()).get("workHours").asDouble()).isEqualTo(8.5);
    }

    // ---- employees ---------------------------------------------------------

    @Test
    void employeeCreateIgnoresFieldsTheClientMustNotControl() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");

        Map<String, Object> body = employee("sneaky" + a.slug() + "@example.com");
        body.put("primaryOwner", true);
        body.put("frozen", true);
        body.put("emailVerified", true);
        body.put("companyId", b.companyId());
        JsonNode created = createEmployee(a, body);

        assertThat(created.get("primaryOwner").asBoolean()).isFalse();
        assertThat(created.get("frozen").asBoolean()).isFalse();
        assertThat(created.get("emailVerified").asBoolean()).isFalse();
        assertThat(created.get("companyId").asLong()).isEqualTo(a.companyId());
        assertThat(getBody("/api/employees", b)).doesNotContain("sneaky");
    }

    @Test
    void employeeNumbersAreAssignedAndValidated() throws Exception {
        Tenant a = newTenant("Acme");

        // No empId given: the next free number in the company (the Owner is #1).
        JsonNode first = createEmployee(a, employee("one" + a.slug() + "@example.com"));
        assertThat(first.get("empId").asInt()).isEqualTo(2);

        // Asking for a number that is taken is a clear 400, not a database error.
        Map<String, Object> dup = employee("two" + a.slug() + "@example.com");
        dup.put("empId", 2);
        MockHttpServletResponse taken = send(jsonPost("/api/employees", a, dup));
        assertThat(taken.getStatus()).isEqualTo(400);
        assertThat(taken.getContentAsString()).contains("already used");

        Map<String, Object> shortPw = employee("three" + a.slug() + "@example.com");
        shortPw.put("password", "abc");
        MockHttpServletResponse weak = send(jsonPost("/api/employees", a, shortPw));
        assertThat(weak.getStatus()).isEqualTo(400);
        assertThat(weak.getContentAsString()).contains("Password must be between 6 and 128 characters.");

        Map<String, Object> badEmail = employee("not-an-email");
        assertThat(send(jsonPost("/api/employees", a, badEmail)).getStatus()).isEqualTo(400);

        Map<String, Object> negative = employee("neg" + a.slug() + "@example.com");
        negative.put("salary", -5);
        assertThat(send(jsonPost("/api/employees", a, negative)).getStatus()).isEqualTo(400);
    }

    @Test
    void employeeUpdateKeepsThePasswordWhenBlankAndNeverChangesTheRole() throws Exception {
        Tenant a = newTenant("Acme");
        String email = "up" + a.slug() + "@example.com";
        createEmployee(a, employee(email)); // #2

        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("name", "Renamed");
        upd.put("email", email);
        upd.put("password", "");
        upd.put("department", "Sales");
        upd.put("salary", 2000);
        upd.put("role", "ADMIN"); // must be ignored
        MockHttpServletResponse res = send(jsonPut("/api/employees/2", a, upd));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        JsonNode updated = json.readTree(res.getContentAsString());
        assertThat(updated.get("name").asText()).isEqualTo("Renamed");
        assertThat(updated.get("role").asText()).isEqualTo("EMPLOYEE");

        // The old password still works.
        MockHttpServletResponse login = send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", a.slug()))));
        assertThat(login.getStatus()).isEqualTo(200);

        upd.put("password", "abc");
        MockHttpServletResponse weak = send(jsonPut("/api/employees/2", a, upd));
        assertThat(weak.getStatus()).isEqualTo(400);
        assertThat(weak.getContentAsString()).contains("Password must be at least 6 characters.");
    }

    @Test
    void malformedJsonIsAClientErrorNotAServerError() throws Exception {
        Tenant a = newTenant("Acme");
        MockHttpServletResponse res = send(post("/api/teams").header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": "));
        assertThat(res.getStatus()).isEqualTo(400);
    }
}
