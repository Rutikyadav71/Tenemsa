package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The public health endpoint hosts and Docker use to decide whether the app is ready. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:health-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void reportsUpWithoutSigningIn() throws Exception {
        MockHttpServletResponse res = mvc.perform(get("/api/health")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        JsonNode n = json.readTree(res.getContentAsString());
        assertThat(n.get("status").asText()).isEqualTo("UP");
        assertThat(n.get("database").asText()).isEqualTo("UP");
        // Anyone can call it, so it must not say anything beyond up or down.
        assertThat(n.size()).isEqualTo(2);
    }
}
