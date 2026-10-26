package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.tenemsa.platform.chat.model.ChatMessage;
import com.tenemsa.platform.chat.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Phase 4d: per-record ownership (leave apply, chat messages), leave isolation
 * between companies, and request validation. Runs through the real HTTP stack
 * against in-memory H2 (rate limiting is off in the "test" profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnershipAndValidationTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ChatMessageRepository chatRepo;

    // ---- helpers ---------------------------------------------------------

    record Tenant(String token, Long companyId, String email, String slug) {}

    private Tenant newTenant(String label) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String email = label.toLowerCase() + id + "@example.com";
        MockHttpServletResponse res = mvc.perform(post("/api/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("companyName", label + " " + id,
                                "ownerName", label + " Owner " + id, "email", email, "password", PASSWORD))))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(res.getContentAsString());
        return new Tenant(body.get("token").asText(), body.get("companyId").asLong(), email, body.get("companySlug").asText());
    }

    private void addEmployee(Tenant owner, int empId, String email) throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/employees")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("empId", empId, "name", "Emp " + empId,
                                "email", email, "password", PASSWORD, "role", "EMPLOYEE",
                                "department", "Ops", "salary", 1000))))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    /** Signs an existing user in through the real login endpoint and returns the access token. */
    private String loginToken(String email, String workspace) throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", workspace))))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        return json.readTree(res.getContentAsString()).get("token").asText();
    }

    private int status(org.springframework.test.web.servlet.RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse().getStatus();
    }

    private String body(org.springframework.test.web.servlet.RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> leaveBody(int empId, String reason) {
        return Map.of("empId", empId, "startDate", "2026-12-01", "endDate", "2026-12-02", "reason", reason);
    }

    // ---- leave -----------------------------------------------------------

    @Test
    void anEmployeeCanApplyForLeaveOnlyForThemselves() throws Exception {
        Tenant a = newTenant("Acme");
        String email = "emp2" + a.slug() + "@example.com";
        addEmployee(a, 2, email);
        String emp = loginToken(email, a.slug());

        // Someone else's empId (the Owner is #1): refused.
        assertThat(status(post("/api/leaves").header("Authorization", "Bearer " + emp)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(leaveBody(1, "for someone else")))))
                .isEqualTo(403);
        // Their own empId: allowed.
        assertThat(status(post("/api/leaves").header("Authorization", "Bearer " + emp)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(leaveBody(2, "my own leave")))))
                .isEqualTo(200);
    }

    @Test
    void leaveRequestsAreInvisibleAndUnapprovableAcrossCompanies() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        String reason = "UniqueReason" + UUID.randomUUID();

        MockHttpServletResponse created = mvc.perform(post("/api/leaves").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(leaveBody(1, reason))))
                .andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(200);
        long leaveId = json.readTree(created.getContentAsString()).get("id").asLong();

        assertThat(body(get("/api/leaves").header("Authorization", "Bearer " + a.token()))).contains(reason);
        assertThat(body(get("/api/leaves").header("Authorization", "Bearer " + b.token()))).doesNotContain(reason);

        // Globex's Owner holds leave:approve:any, but the leave is not theirs to find.
        assertThat(status(put("/api/leaves/" + leaveId + "/approve").header("Authorization", "Bearer " + b.token())))
                .isBetween(400, 404);
        assertThat(body(get("/api/leaves").header("Authorization", "Bearer " + a.token()))).contains("PENDING");
    }

    // ---- chat ------------------------------------------------------------

    @Test
    void chatConversationsAndMessagesAreVisibleOnlyToTheirParticipants() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        String e2 = "emp2" + a.slug() + "@example.com", e3 = "emp3" + a.slug() + "@example.com", e4 = "emp4" + a.slug() + "@example.com";
        addEmployee(a, 2, e2);
        addEmployee(a, 3, e3);
        addEmployee(a, 4, e4);
        String t2 = loginToken(e2, a.slug()), t3 = loginToken(e3, a.slug()), t4 = loginToken(e4, a.slug());

        // Messages are created over WebSocket in the app; insert one directly.
        ChatMessage m = new ChatMessage(2, "USER", 3, "USER", "private-hello");
        m.setCompanyId(a.companyId());
        m = chatRepo.save(m);
        long id = m.getId();

        String hist = "/api/chat/history?id1=2&role1=USER&id2=3&role2=USER";
        // Participants can read it...
        assertThat(body(get(hist).header("Authorization", "Bearer " + t2))).contains("private-hello");
        assertThat(body(get(hist).header("Authorization", "Bearer " + t3))).contains("private-hello");
        // ...a colleague, the company Owner and another company's Owner cannot.
        assertThat(status(get(hist).header("Authorization", "Bearer " + t4))).isEqualTo(403);
        assertThat(status(get(hist).header("Authorization", "Bearer " + a.token()))).isEqualTo(403);
        assertThat(status(get(hist).header("Authorization", "Bearer " + b.token()))).isEqualTo(403);
        // Starred list and conversation delete are guarded the same way.
        assertThat(status(get("/api/chat/starred?id1=2&role1=USER&id2=3&role2=USER").header("Authorization", "Bearer " + t4))).isEqualTo(403);
        assertThat(status(delete("/api/chat/delete?id1=2&role1=USER&id2=3&role2=USER").header("Authorization", "Bearer " + t4))).isEqualTo(403);
        // Unread count / mark-read only for the receiver themselves.
        assertThat(status(get("/api/chat/unread?senderId=2&senderRole=USER&receiverId=3&receiverRole=USER").header("Authorization", "Bearer " + t4))).isEqualTo(403);
        assertThat(status(put("/api/chat/read?senderId=2&senderRole=USER&receiverId=3&receiverRole=USER").header("Authorization", "Bearer " + t4))).isEqualTo(403);

        // Message-level: a non-participant gets "not found", a participant can star/react.
        assertThat(status(put("/api/chat/message/" + id + "/star").header("Authorization", "Bearer " + t4))).isEqualTo(404);
        assertThat(status(put("/api/chat/message/" + id + "/react").header("Authorization", "Bearer " + t4)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reaction\":\"x\"}"))).isEqualTo(404);
        assertThat(status(put("/api/chat/message/" + id + "/star").header("Authorization", "Bearer " + t3))).isEqualTo(200);

        // Only the sender may delete.
        assertThat(status(delete("/api/chat/message/" + id).header("Authorization", "Bearer " + t4))).isEqualTo(404);
        assertThat(status(delete("/api/chat/message/" + id).header("Authorization", "Bearer " + t3))).isEqualTo(404);
        assertThat(status(delete("/api/chat/message/" + id).header("Authorization", "Bearer " + t2))).isEqualTo(200);
    }

    // ---- validation --------------------------------------------------------

    @Test
    void registrationRejectsBlankAndMalformedInputWithAReadableMessage() throws Exception {
        MockHttpServletResponse blank = mvc.perform(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", "", "ownerName", "Jane", "email", "jane@example.com", "password", PASSWORD))))
                .andReturn().getResponse();
        assertThat(blank.getStatus()).isEqualTo(400);
        assertThat(blank.getContentAsString()).contains("Company name is required");

        assertThat(status(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", "Valid Co", "ownerName", "Jane", "email", "not-an-email", "password", PASSWORD)))))
                .isEqualTo(400);
        assertThat(status(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", "Valid Co", "ownerName", "Jane", "email", "jane@example.com", "password", "A".repeat(200))))))
                .isEqualTo(400);
    }

    @Test
    void loginRejectsMissingFieldsBeforeTouchingTheDatabase() throws Exception {
        assertThat(status(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))).isEqualTo(400);
        assertThat(status(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))).isEqualTo(400);
    }
}
