package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.model.Invitation;
import com.tenemsa.platform.repository.InvitationRepository;
import com.tenemsa.platform.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the multi-tenant guarantees end to end through the real HTTP stack
 * (security filter, JWT, controllers, JPA) against an in-memory database.
 *
 * Every test registers its own uniquely named companies, so tests are
 * independent of each other and of execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossTenantIsolationTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired InvitationRepository invitationRepository;
    @Autowired TokenService tokenService;

    // ---- helpers ---------------------------------------------------------

    record Tenant(String token, String slug, Long companyId, String email, String ownerName) {}

    private Tenant register(String company, String owner, String email, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/companies/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", company, "ownerName", owner, "email", email, "password", password))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        return new Tenant(body.get("token").asText(), body.get("companySlug").asText(),
                body.get("companyId").asLong(), email, owner);
    }

    private Tenant newTenant(String label) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return register(label + " " + id, label + " Owner " + id, label.toLowerCase() + id + "@example.com", PASSWORD);
    }

    private String fetch(Tenant t, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + t.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int login(String email, String password, String workspace) throws Exception {
        Map<String, Object> body = workspace == null
                ? Map.of("email", email, "password", password)
                : Map.of("email", email, "password", password, "workspace", workspace);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }

    // ---- tenant data isolation ------------------------------------------

    @Test
    void employeeListAndLookupByEmpIdStayInsideTheCallersCompany() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");

        // Acme adds a second person; Globex must never see them.
        mvc.perform(post("/api/employees").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "empId", 2, "name", "Acme Only Person", "email", "only" + a.slug() + "@example.com",
                                "password", PASSWORD, "role", "EMPLOYEE", "department", "Ops", "salary", 1000))))
                .andExpect(status().isOk());

        assertThat(fetch(a, "/api/employees")).contains("Acme Only Person");
        String globexList = fetch(b, "/api/employees");
        assertThat(globexList).doesNotContain("Acme Only Person").doesNotContain(a.ownerName());

        // empId 1 exists in BOTH companies (each Owner is #1); each caller must get their own.
        assertThat(fetch(a, "/api/employees/1")).contains(a.ownerName());
        assertThat(fetch(b, "/api/employees/1")).contains(b.ownerName()).doesNotContain(a.ownerName());
    }

    @Test
    void companyEndpointsReturnOnlyTheCallersOwnCompany() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");

        JsonNode meA = json.readTree(fetch(a, "/api/companies/me"));
        JsonNode meB = json.readTree(fetch(b, "/api/companies/me"));
        assertThat(meA.get("slug").asText()).isEqualTo(a.slug());
        assertThat(meB.get("slug").asText()).isEqualTo(b.slug());
        assertThat(meA.get("memberCount").asInt()).isEqualTo(1);

        mvc.perform(put("/api/companies/me").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Renamed\"}"))
                .andExpect(status().isOk());
        assertThat(json.readTree(fetch(b, "/api/companies/me")).get("name").asText()).doesNotContain("Renamed");
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/audit")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/invitations")).andExpect(status().is4xxClientError());
    }

    // ---- per-company email + workspace login -----------------------------

    @Test
    void sameEmailCanOwnAccountsInTwoWorkspaces() throws Exception {
        String email = "shared" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String id = UUID.randomUUID().toString().substring(0, 6);
        Tenant one = register("First Co " + id, "Shared Person", email, "FirstPass1!x");
        Tenant two = register("Second Co " + id, "Shared Person", email, "SecondPass2!x");

        // With a workspace the right account is chosen even though the email is shared.
        assertThat(login(email, "FirstPass1!x", one.slug())).isEqualTo(200);
        assertThat(login(email, "SecondPass2!x", two.slug())).isEqualTo(200);
        // The password belongs to the other workspace's account.
        assertThat(login(email, "FirstPass1!x", two.slug())).isEqualTo(401);
        // Without a workspace, a password that matches exactly one account still signs in.
        assertThat(login(email, "FirstPass1!x", null)).isEqualTo(200);
        // Same password on both accounts and no workspace is ambiguous, so it is refused.
        String id2 = UUID.randomUUID().toString().substring(0, 6);
        String email2 = "twin" + id2 + "@example.com";
        register("Twin A " + id2, "Twin", email2, PASSWORD);
        register("Twin B " + id2, "Twin", email2, PASSWORD);
        assertThat(login(email2, PASSWORD, null)).isEqualTo(401);
    }

    @Test
    void loginIsRefusedForTheWrongWorkspace() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        assertThat(login(a.email(), PASSWORD, a.slug())).isEqualTo(200);
        assertThat(login(a.email(), PASSWORD, b.slug())).isEqualTo(401);
    }

    // ---- audit trail ------------------------------------------------------

    @Test
    void auditTrailIsPerCompanyAndRecordsRegistrationAndLogin() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");
        assertThat(login(a.email(), PASSWORD, a.slug())).isEqualTo(200);

        String auditA = fetch(a, "/api/audit?size=50");
        assertThat(auditA).contains("COMPANY_REGISTERED").contains("LOGIN_SUCCESS");

        String auditB = fetch(b, "/api/audit?size=50");
        assertThat(auditB).contains("COMPANY_REGISTERED")
                .doesNotContain(a.ownerName())
                .doesNotContain("LOGIN_SUCCESS");

        // Filtering by action.
        assertThat(fetch(a, "/api/audit?action=LOGIN_SUCCESS")).doesNotContain("COMPANY_REGISTERED");
    }

    // ---- invitations ------------------------------------------------------

    @Test
    void invitationsAreInvisibleAndUnrevocableAcrossCompanies() throws Exception {
        Tenant a = newTenant("Acme");
        Tenant b = newTenant("Globex");

        MvcResult created = mvc.perform(post("/api/invitations").header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "newhire@example.com", "name", "New Hire", "role", "EMPLOYEE"))))
                .andExpect(status().isCreated()).andReturn();
        long inviteId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        assertThat(fetch(a, "/api/invitations")).contains("newhire@example.com");
        assertThat(fetch(b, "/api/invitations")).doesNotContain("newhire@example.com");

        // Globex cannot revoke Acme's invitation: it does not exist from their point of view.
        mvc.perform(delete("/api/invitations/" + inviteId).header("Authorization", "Bearer " + b.token()))
                .andExpect(status().isBadRequest());
        assertThat(fetch(a, "/api/invitations")).contains("newhire@example.com");
    }

    @Test
    void invitationCanBePreviewedAcceptedOnceAndThenSignsIn() throws Exception {
        Tenant a = newTenant("Acme");

        // The raw token only exists in the email, so build the row with a known token.
        String raw = tokenService.newRawToken();
        Invitation inv = new Invitation();
        inv.setCompanyId(a.companyId());
        inv.setEmail("joiner" + a.slug() + "@example.com");
        inv.setName("Joiner");
        inv.setRole("EMPLOYEE");
        inv.setTokenHash(tokenService.hashToken(raw));
        inv.setExpiresAt(LocalDateTime.now().plusDays(1));
        invitationRepository.save(inv);

        mvc.perform(get("/api/invitations/preview").param("token", raw)).andExpect(status().isOk());
        mvc.perform(get("/api/invitations/preview").param("token", "not-a-real-token")).andExpect(status().isNotFound());

        // Weak password is refused and does not burn the invitation.
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", raw, "password", "weak"))))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", raw, "password", "Joiner#Pass1"))))
                .andExpect(status().isCreated());

        assertThat(login(inv.getEmail(), "Joiner#Pass1", a.slug())).isEqualTo(200);

        // One-time use.
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", raw, "password", "Joiner#Pass1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiredInvitationCannotBeUsed() throws Exception {
        Tenant a = newTenant("Acme");
        String raw = tokenService.newRawToken();
        Invitation inv = new Invitation();
        inv.setCompanyId(a.companyId());
        inv.setEmail("late" + a.slug() + "@example.com");
        inv.setName("Late");
        inv.setRole("EMPLOYEE");
        inv.setTokenHash(tokenService.hashToken(raw));
        inv.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        invitationRepository.save(inv);

        mvc.perform(get("/api/invitations/preview").param("token", raw)).andExpect(status().isNotFound());
        mvc.perform(post("/api/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", raw, "password", "Late#Pass12"))))
                .andExpect(status().isBadRequest());
    }
}
