package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.announcement.model.PlatformAnnouncement;
import com.tenemsa.platform.announcement.repository.PlatformAnnouncementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Company announcements, notices from the platform, and plan limits. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:announcement-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnouncementTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformAnnouncementRepository notices;

    private MockHttpServletResponse postJson(String url, String token, Object body) throws Exception {
        var rb = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) rb.header("Authorization", "Bearer " + token);
        return mvc.perform(rb).andReturn().getResponse();
    }

    private MockHttpServletResponse getJson(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    private record Co(String id, String slug, String ownerToken, long companyId) {}

    private Co company() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse reg = postJson("/api/companies/register", null, Map.of("companyName", "Ann " + id,
                "ownerName", "Owner", "email", "owner" + id + "@example.com", "password", PASSWORD));
        assertThat(reg.getStatus()).isEqualTo(201);
        JsonNode r = json.readTree(reg.getContentAsString());
        String slug = r.get("companySlug").asText();
        Long companyId = jdbc.queryForObject("SELECT id FROM companies WHERE slug = ?", Long.class, slug);
        return new Co(id, slug, r.get("token").asText(), companyId);
    }

    private MockHttpServletResponse addPerson(Co c, String role, boolean withPassword) throws Exception {
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", role.toLowerCase());
        emp.put("email", role.toLowerCase() + c.id() + "@example.com");
        if (withPassword) emp.put("password", PASSWORD);
        emp.put("role", role);
        return postJson("/api/employees", c.ownerToken(), emp);
    }

    private String signIn(Co c, String role) throws Exception {
        MockHttpServletResponse res = postJson("/api/auth/login", null,
                Map.of("email", role.toLowerCase() + c.id() + "@example.com", "password", PASSWORD, "workspace", c.slug()));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString()).get("token").asText();
    }

    private Map<String, Object> req(String audience, List<String> roles) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subject", "Office closed Friday"); m.put("body", "Hello team.\n\nThe office is closed on Friday.");
        m.put("audience", audience); m.put("roles", roles); m.put("sendEmail", true);
        return m;
    }

    @Test
    void ownerCanPreviewSendAndSeeHistoryButAnEmployeeCannotSend() throws Exception {
        Co c = company();
        assertThat(addPerson(c, "HR", true).getStatus()).isEqualTo(200);
        assertThat(addPerson(c, "EMPLOYEE", false).getStatus()).as("password is optional now").isEqualTo(200);

        JsonNode all = json.readTree(postJson("/api/announcements/preview", c.ownerToken(), req("ALL", null)).getContentAsString());
        assertThat(all.get("count").asInt()).isEqualTo(3);
        JsonNode emps = json.readTree(postJson("/api/announcements/preview", c.ownerToken(), req("ROLES", List.of("EMPLOYEE"))).getContentAsString());
        assertThat(emps.get("count").asInt()).isEqualTo(1);

        MockHttpServletResponse sent = postJson("/api/announcements", c.ownerToken(), req("ROLES", List.of("EMPLOYEE", "HR")));
        assertThat(sent.getStatus()).as(sent.getContentAsString()).isEqualTo(201);
        assertThat(json.readTree(sent.getContentAsString()).get("recipients").asInt()).isEqualTo(2);

        JsonNode history = json.readTree(getJson("/api/announcements", c.ownerToken()).getContentAsString());
        assertThat(history.get("totalElements").asInt()).isEqualTo(1);
        assertThat(history.get("content").get(0).get("subject").asText()).isEqualTo("Office closed Friday");

        // The HR person had a password set, so they can sign in; an employee must not be able to send.
        String hr = signIn(c, "HR");
        assertThat(postJson("/api/announcements", hr, req("ALL", null)).getStatus()).isEqualTo(201);
        // The employee has no password yet (set from the emailed link), so use the owner-created token of a person with one.
        assertThat(postJson("/api/employees", c.ownerToken(), Map.of("name", "intern", "email", "intern" + c.id() + "@example.com", "password", PASSWORD, "role", "INTERN")).getStatus()).isEqualTo(200);
        String intern = signIn(c, "INTERN");
        assertThat(postJson("/api/announcements", intern, req("ALL", null)).getStatus()).isEqualTo(403);
    }

    @Test
    void badAudiencesAndMissingTextAreRefused() throws Exception {
        Co c = company();
        Map<String, Object> noSubject = req("ALL", null); noSubject.put("subject", "");
        assertThat(postJson("/api/announcements", c.ownerToken(), noSubject).getStatus()).isEqualTo(400);
        MockHttpServletResponse noRoles = postJson("/api/announcements", c.ownerToken(), req("ROLES", List.of()));
        assertThat(noRoles.getStatus()).isEqualTo(400);
        assertThat(noRoles.getContentAsString()).contains("role");
        assertThat(postJson("/api/announcements", c.ownerToken(), req("NOBODY", null)).getStatus()).isEqualTo(400);
    }

    @Test
    void announcementsStayInsideTheirOwnCompany() throws Exception {
        Co a = company(), b = company();
        postJson("/api/announcements", a.ownerToken(), req("ALL", null));
        JsonNode other = json.readTree(getJson("/api/announcements", b.ownerToken()).getContentAsString());
        assertThat(other.get("totalElements").asInt()).isZero();
    }

    @Test
    void planLimitStopsNewPeopleButNotExistingOnes() throws Exception {
        Co c = company();
        jdbc.update("UPDATE companies SET max_users = ? WHERE id = ?", 2, c.companyId());
        assertThat(addPerson(c, "HR", true).getStatus()).isEqualTo(200);          // second person: allowed
        MockHttpServletResponse third = addPerson(c, "MANAGER", true);             // third: over the limit
        assertThat(third.getStatus()).isEqualTo(400);
        assertThat(third.getContentAsString()).contains("plan allows up to 2");
        jdbc.update("UPDATE companies SET max_users = NULL WHERE id = ?", c.companyId());
        assertThat(addPerson(c, "MANAGER", true).getStatus()).isEqualTo(200);      // limit removed
    }

    @Test
    void platformNoticesReachOnlyTheirTargetCompanies() throws Exception {
        Co target = company(), other = company();
        PlatformAnnouncement forAll = new PlatformAnnouncement();
        forAll.setTitle("Maintenance tonight"); forAll.setMessage("Short downtime at 2 am."); forAll.setSeverity("WARNING");
        forAll.setAudience("ALL"); forAll.setCreatedBy("admin@example.test"); forAll.setExpiresAt(LocalDateTime.now().plusDays(1));
        notices.save(forAll);
        PlatformAnnouncement onlyOne = new PlatformAnnouncement();
        onlyOne.setTitle("Just for you"); onlyOne.setMessage("Hello."); onlyOne.setSeverity("INFO"); onlyOne.setAudience("SELECTED");
        onlyOne.setCompanyIds(String.valueOf(target.companyId())); onlyOne.setCreatedBy("admin@example.test"); onlyOne.setExpiresAt(LocalDateTime.now().plusDays(1));
        notices.save(onlyOne);
        PlatformAnnouncement gone = new PlatformAnnouncement();
        gone.setTitle("Withdrawn"); gone.setMessage("Old."); gone.setSeverity("INFO"); gone.setAudience("ALL"); gone.setCreatedBy("x");
        gone.setExpiresAt(LocalDateTime.now().plusDays(1)); gone.setWithdrawn(true);
        notices.save(gone);

        String t = getJson("/api/announcements/platform", target.ownerToken()).getContentAsString();
        assertThat(t).contains("Maintenance tonight").contains("Just for you").doesNotContain("Withdrawn");
        String o = getJson("/api/announcements/platform", other.ownerToken()).getContentAsString();
        assertThat(o).contains("Maintenance tonight").doesNotContain("Just for you");
    }
}
