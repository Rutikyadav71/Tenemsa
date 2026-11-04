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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Settings → Security: change password and manage signed-in devices. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:settings-security-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsSecurityTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpServletResponse send(RequestBuilder rb) throws Exception { return mvc.perform(rb).andReturn().getResponse(); }

    private record Account(String email, String token, String refresh) {}

    private Account register() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String email = "sec" + id + "@example.com";
        MockHttpServletResponse res = send(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", "Sec " + id, "ownerName", "Sec Owner",
                        "email", email, "password", PASSWORD))));
        assertThat(res.getStatus()).isEqualTo(201);
        JsonNode n = json.readTree(res.getContentAsString());
        return new Account(email, n.get("token").asText(), n.get("refreshToken").asText());
    }

    private MockHttpServletResponse change(Account a, String current, String next, String keep) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("currentPassword", current, "newPassword", next));
        if (keep != null) body.put("refreshToken", keep);
        return send(post("/api/auth/change-password").header("Authorization", "Bearer " + a.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void wrongCurrentPasswordSamePasswordAndWeakPasswordAreRefused() throws Exception {
        Account a = register();
        assertThat(change(a, "Wrong1234", "Brand9New", null).getStatus()).isEqualTo(400);
        MockHttpServletResponse same = change(a, PASSWORD, PASSWORD, null);
        assertThat(same.getStatus()).isEqualTo(400);
        assertThat(same.getContentAsString()).contains("different");
        assertThat(change(a, PASSWORD, "short", null).getStatus()).isEqualTo(400);
    }

    @Test
    void changingPasswordKeepsThisDeviceAndSignsOutTheOthers() throws Exception {
        Account a = register();
        MockHttpServletResponse login = send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Other Browser")
                .content(json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD))));
        assertThat(login.getStatus()).isEqualTo(200);
        String otherRefresh = json.readTree(login.getContentAsString()).get("refreshToken").asText();

        assertThat(change(a, PASSWORD, "Brand9NewPass", a.refresh()).getStatus()).isEqualTo(200);

        assertThat(send(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", otherRefresh)))).getStatus()).isNotEqualTo(200);
        assertThat(send(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", a.refresh())))).getStatus()).isEqualTo(200);
        // The new password signs in; the old one no longer does.
        assertThat(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", a.email(), "password", "Brand9NewPass")))).getStatus()).isEqualTo(200);
        assertThat(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD)))).getStatus()).isNotEqualTo(200);
    }

    @Test
    void sessionsListMarksThisDeviceAndCanBeRevoked() throws Exception {
        Account a = register();
        MockHttpServletResponse login = send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Second Device")
                .content(json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD))));
        assertThat(login.getStatus()).isEqualTo(200);

        JsonNode list = json.readTree(send(get("/api/auth/sessions").header("Authorization", "Bearer " + a.token())
                .header("X-Refresh-Token", a.refresh())).getContentAsString());
        assertThat(list.size()).isEqualTo(2);
        long current = 0, other = 0;
        for (JsonNode s : list) { if (s.get("current").asBoolean()) current++; else other = s.get("id").asLong(); }
        assertThat(current).isEqualTo(1);
        assertThat(list.toString()).doesNotContain(a.refresh());

        assertThat(send(delete("/api/auth/sessions/" + other).header("Authorization", "Bearer " + a.token())).getStatus()).isEqualTo(200);
        assertThat(send(delete("/api/auth/sessions/" + other).header("Authorization", "Bearer " + a.token())).getStatus()).isEqualTo(404);
        JsonNode after = json.readTree(send(get("/api/auth/sessions").header("Authorization", "Bearer " + a.token())).getContentAsString());
        assertThat(after.size()).isEqualTo(1);
    }

    @Test
    void cannotSeeOrRevokeAnotherUsersSessions() throws Exception {
        Account a = register();
        Account b = register();
        JsonNode aList = json.readTree(send(get("/api/auth/sessions").header("Authorization", "Bearer " + a.token())).getContentAsString());
        long aSession = aList.get(0).get("id").asLong();
        assertThat(send(delete("/api/auth/sessions/" + aSession).header("Authorization", "Bearer " + b.token())).getStatus()).isEqualTo(404);
        assertThat(send(get("/api/auth/sessions")).getStatus()).isEqualTo(401);
    }
}
