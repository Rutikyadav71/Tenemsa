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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Phase 8b: a company cannot store more than documents.max-company-bytes. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:doc-quota-test;MODE=MySQL;DB_CLOSE_DELAY=-1", "documents.max-company-bytes=300"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentQuotaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void storageLimitStopsFurtherUploads() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse reg = mvc.perform(post("/api/companies/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("companyName", "Quota " + id, "ownerName", "Quota Owner",
                        "email", "quota" + id + "@example.com", "password", "Passw0rd!Test")))).andReturn().getResponse();
        JsonNode body = json.readTree(reg.getContentAsString());
        String token = body.get("token").asText();

        byte[] big = ("%PDF-1.4\n" + "x".repeat(200)).getBytes();
        MockHttpServletResponse first = mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "a.pdf", "application/pdf", big))
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(201);
        MockHttpServletResponse second = mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "b.pdf", "application/pdf", big))
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(second.getStatus()).isEqualTo(400);
        assertThat(second.getContentAsString()).contains("storage is full");
    }
}
