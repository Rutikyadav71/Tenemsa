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
 * Phase 8a: manager team-leave approval and personal notifications. Checks OUR rules: a manager sees and decides only
 * their own team's requests, never their own, only pending ones, a rejection needs a reason, the decision is recorded,
 * clashes are shown, and notifications reach only the people they are for.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:teamleave-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamLeaveApprovalTest {

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

    private Person person(Tenant owner, String label, String role) throws Exception {
        String email = label + owner.slug() + "@example.com";
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", label);
        emp.put("email", email);
        emp.put("password", PASSWORD);
        emp.put("role", role);
        JsonNode created = ok(call("POST", "/api/employees", owner.token(), emp), 200);
        JsonNode login = ok(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", owner.slug())))), 200);
        return new Person(login.get("token").asText(), created.get("empId").asInt());
    }

    private void team(Tenant owner, String name, int lead, List<Integer> members) throws Exception {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("memberIds", members);
        t.put("teamLeadId", lead);
        ok(call("POST", "/api/teams", owner.token(), t), 200);
    }

    private int apply(Person p, String from, String to, String reason) throws Exception {
        return ok(call("POST", "/api/leaves", p.token(), Map.of("empId", p.empId(), "startDate", from, "endDate", to, "reason", reason)), 200)
                .get("id").asInt();
    }

    private JsonNode notifications(String token) throws Exception { return ok(call("GET", "/api/notifications", token, null), 200); }

    // ---- tests -------------------------------------------------------------------

    @Test
    void managerSeesOnlyTheirTeamAndCanApproveWithANote() throws Exception {
        Tenant t = newTenant("Acme");
        Person manager = person(t, "Mona", "MANAGER");
        Person alice = person(t, "Alice", "EMPLOYEE");
        Person bob = person(t, "Bob", "EMPLOYEE");
        Person outsider = person(t, "Olga", "EMPLOYEE");
        team(t, "Core", manager.empId(), List.of(manager.empId(), alice.empId(), bob.empId()));

        int aliceLeave = apply(alice, "2030-05-10", "2030-05-12", "Family event");
        apply(bob, "2030-05-11", "2030-05-11", "Dentist");
        apply(outsider, "2030-05-10", "2030-05-10", "Not on the team");
        apply(manager, "2030-06-01", "2030-06-02", "Own leave");

        JsonNode rows = ok(call("GET", "/api/leaves/team", manager.token(), null), 200);
        assertThat(rows).hasSize(2);                                         // Alice and Bob; not Olga, not Mona herself
        JsonNode first = rows.get(0).get("id").asInt() == aliceLeave ? rows.get(0) : rows.get(1);
        assertThat(first.get("employeeName").asText()).isEqualTo("Alice");
        assertThat(first.get("days").asInt()).isEqualTo(3);
        assertThat(first.get("canDecide").asBoolean()).isTrue();
        assertThat(first.get("clashesWith")).extracting(JsonNode::asText).containsExactly("Bob");   // Bob is off on the 11th

        JsonNode approved = ok(call("PUT", "/api/leaves/" + aliceLeave + "/approve", manager.token(), Map.of("note", "Enjoy!")), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("decidedByEmpId").asInt()).isEqualTo(manager.empId());
        assertThat(approved.get("decisionNote").asText()).isEqualTo("Enjoy!");

        JsonNode all = ok(call("GET", "/api/leaves/team?status=ALL", manager.token(), null), 200);
        JsonNode decided = null;
        for (JsonNode r : all) if (r.get("id").asInt() == aliceLeave) decided = r;
        assertThat(decided.get("decidedByName").asText()).isEqualTo("Mona");
        assertThat(decided.get("canDecide").asBoolean()).isFalse();
        assertThat(ok(call("GET", "/api/leaves/team", manager.token(), null), 200)).hasSize(1);    // only Bob still pending
        assertThat(call("GET", "/api/leaves/team?status=bogus", manager.token(), null).getStatus()).isEqualTo(400);
    }

    @Test
    void managersCannotDecideOutsideTheirTeamOrOnTheirOwnLeave() throws Exception {
        Tenant t = newTenant("Rules");
        Person manager = person(t, "Mona", "MANAGER");
        Person otherManager = person(t, "Max", "MANAGER");
        Person alice = person(t, "Alice", "EMPLOYEE");
        Person outsider = person(t, "Olga", "EMPLOYEE");
        team(t, "Core", manager.empId(), List.of(manager.empId(), alice.empId()));

        int own = apply(manager, "2030-07-01", "2030-07-02", "Own leave");
        int out = apply(outsider, "2030-07-01", "2030-07-02", "Other team");
        int mine = apply(alice, "2030-07-03", "2030-07-03", "Mine to decide");

        assertThat(call("PUT", "/api/leaves/" + own + "/approve", manager.token(), null).getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/api/leaves/" + out + "/approve", manager.token(), null).getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/api/leaves/" + mine + "/approve", otherManager.token(), null).getStatus()).isEqualTo(403);
        assertThat(ok(call("GET", "/api/leaves/team?status=ALL", otherManager.token(), null), 200)).isEmpty();

        // An employee has no approval screen at all.
        assertThat(call("GET", "/api/leaves/team", alice.token(), null).getStatus()).isEqualTo(403);
        assertThat(call("PUT", "/api/leaves/" + mine + "/approve", alice.token(), null).getStatus()).isEqualTo(403);

        // Owner and HR can decide any request.
        assertThat(ok(call("PUT", "/api/leaves/" + out + "/approve", t.token(), null), 200).get("status").asText()).isEqualTo("APPROVED");
        assertThat(ok(call("GET", "/api/leaves/team?status=ALL", t.token(), null), 200).size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void aRejectionNeedsAReasonAndADecisionCannotBeDecidedTwice() throws Exception {
        Tenant t = newTenant("Reject");
        Person manager = person(t, "Mona", "MANAGER");
        Person alice = person(t, "Alice", "EMPLOYEE");
        team(t, "Core", manager.empId(), List.of(manager.empId(), alice.empId()));
        int id = apply(alice, "2030-08-01", "2030-08-02", "Trip");

        MockHttpServletResponse noReason = call("PUT", "/api/leaves/" + id + "/reject", manager.token(), null);
        assertThat(noReason.getStatus()).isEqualTo(400);
        assertThat(noReason.getContentAsString()).contains("reason");
        assertThat(call("PUT", "/api/leaves/" + id + "/reject", manager.token(), Map.of("note", "x".repeat(301))).getStatus()).isEqualTo(400);

        JsonNode rejected = ok(call("PUT", "/api/leaves/" + id + "/reject", manager.token(), Map.of("note", "Release week, please move it")), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");

        MockHttpServletResponse again = call("PUT", "/api/leaves/" + id + "/approve", manager.token(), null);
        assertThat(again.getStatus()).isEqualTo(400);
        assertThat(again.getContentAsString()).contains("already rejected");
    }

    @Test
    void notificationsArePersonalAndCarryTheDecision() throws Exception {
        Tenant t = newTenant("Notify");
        Person manager = person(t, "Mona", "MANAGER");
        Person alice = person(t, "Alice", "EMPLOYEE");
        Person bob = person(t, "Bob", "EMPLOYEE");
        team(t, "Core", manager.empId(), List.of(manager.empId(), alice.empId()));

        int id = apply(alice, "2030-09-01", "2030-09-03", "Wedding");

        // The request reaches her manager and the Owner, nobody else.
        assertThat(notifications(manager.token()).toString()).contains("Alice requested leave");
        assertThat(notifications(t.token()).toString()).contains("Alice requested leave");
        assertThat(notifications(alice.token())).isEmpty();
        assertThat(notifications(bob.token())).isEmpty();
        assertThat(ok(call("GET", "/api/notifications/unread-count", manager.token(), null), 200).get("unread").asInt()).isEqualTo(1);

        ok(call("PUT", "/api/leaves/" + id + "/approve", manager.token(), Map.of("note", "Congratulations")), 200);
        JsonNode mine = notifications(alice.token());
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("message").asText()).contains("approved by Mona").contains("Congratulations");
        assertThat(mine.get(0).get("type").asText()).isEqualTo("LEAVE_DECIDED");
        assertThat(notifications(bob.token())).isEmpty();

        // Reading is per person.
        long notificationId = mine.get(0).get("id").asLong();
        assertThat(call("PUT", "/api/notifications/" + notificationId + "/read", bob.token(), null).getStatus()).isEqualTo(404);
        ok(call("PUT", "/api/notifications/" + notificationId + "/read", alice.token(), null), 200);
        assertThat(ok(call("GET", "/api/notifications/unread-count", alice.token(), null), 200).get("unread").asInt()).isZero();
        ok(call("PUT", "/api/notifications/mark-all-read", manager.token(), null), 200);
        assertThat(ok(call("GET", "/api/notifications/unread-count", manager.token(), null), 200).get("unread").asInt()).isZero();
        boolean ownersCopyStillUnread = false;                       // the Owner's own copy of the request is untouched
        for (JsonNode n : notifications(t.token())) {
            if (n.get("message").asText().contains("Alice requested leave") && !n.get("read").asBoolean()) ownersCopyStillUnread = true;
        }
        assertThat(ownersCopyStillUnread).isTrue();
    }

    @Test
    void anotherCompanyNeverSeesNotificationsOrLeave() throws Exception {
        Tenant a = newTenant("Alpha");
        Tenant b = newTenant("Beta");
        Person alice = person(a, "Alice", "EMPLOYEE");
        int id = apply(alice, "2030-10-01", "2030-10-02", "Private reason");
        assertThat(notifications(b.token())).isEmpty();
        assertThat(ok(call("GET", "/api/leaves/team?status=ALL", b.token(), null), 200)).isEmpty();
        assertThat(call("PUT", "/api/leaves/" + id + "/approve", b.token(), null).getStatus()).isBetween(400, 404);
    }

    @Test
    void emailNotificationsCanBeSwitchedOff() throws Exception {
        Tenant t = newTenant("Prefs");
        Person alice = person(t, "Alice", "EMPLOYEE");
        assertThat(ok(call("GET", "/api/notifications/settings", alice.token(), null), 200).get("emailEnabled").asBoolean()).isTrue();
        assertThat(ok(call("PUT", "/api/notifications/settings", alice.token(), Map.of("emailEnabled", false)), 200).get("emailEnabled").asBoolean()).isFalse();
        assertThat(ok(call("GET", "/api/notifications/settings", alice.token(), null), 200).get("emailEnabled").asBoolean()).isFalse();
        assertThat(ok(call("GET", "/api/notifications/settings", t.token(), null), 200).get("emailEnabled").asBoolean()).isTrue();   // per person
        assertThat(call("PUT", "/api/notifications/settings", alice.token(), Map.of()).getStatus()).isEqualTo(400);
    }
}
