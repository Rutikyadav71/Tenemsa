package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A mail server that refuses delivery (for example a wrong SMTP password) must not
 * lose the invitation or turn into a cryptic error: the invitation is saved and the
 * response says the email was not sent.
 */
// Own in-memory database: this class builds its own application context (it replaces EmailService).
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:invite-mail-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvitationEmailFailureTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService emailService;

    @Test
    void invitationIsKeptAndFlaggedWhenTheEmailCannotBeSent() throws Exception {
        when(emailService.sendInviteEmail(any(), any(), any(), any(), any(), any())).thenReturn(false);

        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse reg = mvc.perform(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", "Mailco " + id, "ownerName", "Owner " + id,
                        "email", "owner" + id + "@example.com", "password", "Passw0rd!Test")))).andReturn().getResponse();
        assertThat(reg.getStatus()).isEqualTo(201);
        String token = json.readTree(reg.getContentAsString()).get("token").asText();

        MockHttpServletResponse res = mvc.perform(post("/api/invitations").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "new" + id + "@example.com", "name", "New Hire", "role", "EMPLOYEE"))))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        JsonNode body = json.readTree(res.getContentAsString());
        assertThat(body.get("emailSent").asBoolean()).isFalse();

        String pending = mvc.perform(get("/api/invitations").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(pending).contains("new" + id + "@example.com");
    }
}
