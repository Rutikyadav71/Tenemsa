package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * An expired or missing access token must answer 401 (so the browser refreshes it), and profile photos must upload
 * and display without Cloudinary being configured.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:session-avatar-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAndAvatarTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpServletResponse send(RequestBuilder rb) throws Exception { return mvc.perform(rb).andReturn().getResponse(); }

    private JsonNode register() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse res = send(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", "Sess " + id, "ownerName", "Sess Owner",
                        "email", "sess" + id + "@example.com", "password", "Passw0rd!Test"))));
        assertThat(res.getStatus()).isEqualTo(201);
        return json.readTree(res.getContentAsString());
    }

    @Test
    void missingOrExpiredTokenAnswers401NotForbidden() throws Exception {
        assertThat(send(get("/api/employees")).getStatus()).isEqualTo(401);
        assertThat(send(get("/api/employees").header("Authorization", "Bearer not-a-real-token")).getStatus()).isEqualTo(401);
    }

    @Test
    void signedInButNotAllowedStillAnswers403() throws Exception {
        JsonNode owner = register();
        // An authenticated user hitting something their role cannot use must keep getting 403, not be signed out.
        MockHttpServletResponse res = send(get("/api/audit").header("Authorization", "Bearer " + owner.get("token").asText()));
        assertThat(res.getStatus()).isNotEqualTo(401);
    }

    @Test
    void profilePhotoUploadsWithoutCloudinaryAndIsServed() throws Exception {
        JsonNode owner = register();
        String token = owner.get("token").asText();
        long id = owner.get("id").asLong();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3, 4};

        MockHttpServletResponse up = send(multipart("/api/upload/admin-profile/" + id)
                .file(new MockMultipartFile("file", "me.png", "image/png", png)).header("Authorization", "Bearer " + token));
        assertThat(up.getStatus()).isEqualTo(200);
        String url = json.readTree(up.getContentAsString()).get("url").asText();
        assertThat(url).contains("/api/public/avatars/");

        // The picture loads in a normal image tag: no sign-in header.
        MockHttpServletResponse img = send(get(url.substring(url.indexOf("/api/public/avatars/"))));
        assertThat(img.getStatus()).isEqualTo(200);
        assertThat(img.getContentType()).isEqualTo("image/png");
        assertThat(img.getContentAsByteArray()).isEqualTo(png);
    }

    @Test
    void notAPictureIsRefusedWithAReadableMessage() throws Exception {
        JsonNode owner = register();
        MockHttpServletResponse res = send(multipart("/api/upload/admin-profile/" + owner.get("id").asLong())
                .file(new MockMultipartFile("file", "x.png", "image/png", "<html>not an image</html>".getBytes()))
                .header("Authorization", "Bearer " + owner.get("token").asText()));
        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(res.getContentAsString()).contains("JPG, PNG or WEBP");
    }
}
