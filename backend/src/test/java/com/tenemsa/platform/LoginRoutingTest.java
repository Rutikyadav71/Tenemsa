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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Phase 6b: login returns the role's permissions (used only to build menus) and HR may use the admin sign-in. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:login-routing-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginRoutingTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpServletResponse postJson(String url, String token, Object body) throws Exception {
        var rb = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) rb.header("Authorization", "Bearer " + token);
        return mvc.perform(rb).andReturn().getResponse();
    }

    private boolean has(JsonNode permissions, String key) {
        return StreamSupport.stream(permissions.spliterator(), false).anyMatch(n -> n.asText().equals(key));
    }

    @Test
    void loginReturnsPermissionsAndHrCanUseAdminSignIn() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse reg = postJson("/api/companies/register", null, Map.of("companyName", "Routing " + id,
                "ownerName", "Owner", "email", "owner" + id + "@example.com", "password", PASSWORD));
        assertThat(reg.getStatus()).isEqualTo(201);
        JsonNode r = json.readTree(reg.getContentAsString());
        String token = r.get("token").asText();
        String slug = r.get("companySlug").asText();

        for (String role : new String[]{"HR", "MANAGER", "EMPLOYEE"}) {
            Map<String, Object> emp = new LinkedHashMap<>();
            emp.put("name", role.toLowerCase());
            emp.put("email", role.toLowerCase() + id + "@example.com");
            emp.put("password", PASSWORD);
            emp.put("role", role);
            assertThat(postJson("/api/employees", token, emp).getStatus()).isEqualTo(200);
        }

        // HR: admin sign-in works and the response lists HR's permissions.
        MockHttpServletResponse hr = postJson("/api/admin/login", null,
                Map.of("email", "hr" + id + "@example.com", "password", PASSWORD, "workspace", slug));
        assertThat(hr.getStatus()).as(hr.getContentAsString()).isEqualTo(200);
        JsonNode hrBody = json.readTree(hr.getContentAsString());
        assertThat(hrBody.get("role").asText()).isEqualTo("HR");
        assertThat(has(hrBody.get("permissions"), "leave:view:any")).isTrue();
        assertThat(has(hrBody.get("permissions"), "audit:view")).isFalse();

        // Manager and employee are not admin-portal accounts, but the normal sign-in works for them.
        for (String role : new String[]{"manager", "employee"}) {
            assertThat(postJson("/api/admin/login", null,
                    Map.of("email", role + id + "@example.com", "password", PASSWORD, "workspace", slug)).getStatus())
                    .isEqualTo(401);
        }
        MockHttpServletResponse mgr = postJson("/api/auth/login", null,
                Map.of("email", "manager" + id + "@example.com", "password", PASSWORD, "workspace", slug));
        assertThat(mgr.getStatus()).isEqualTo(200);
        JsonNode mgrPerms = json.readTree(mgr.getContentAsString()).get("permissions");
        assertThat(has(mgrPerms, "ai:use")).isTrue();
        assertThat(has(mgrPerms, "user:create")).isFalse();

        // Employee gets no AI permission.
        MockHttpServletResponse emp = postJson("/api/auth/login", null,
                Map.of("email", "employee" + id + "@example.com", "password", PASSWORD, "workspace", slug));
        assertThat(has(json.readTree(emp.getContentAsString()).get("permissions"), "ai:use")).isFalse();
    }
}
