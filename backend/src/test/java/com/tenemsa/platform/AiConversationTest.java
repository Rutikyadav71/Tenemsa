package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.ai.provider.AiClient;
import com.tenemsa.platform.knowledge.embedding.EmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Phase 9: saved AI chats. A chat is created by the first question, continued by sending its id, remembered by the
 * model through a fenced HISTORY block, private to its owner, tied to one feature, and removable. The model and the
 * embeddings are mocks, so these tests check OUR rules and never contact a real service.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:conversations-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiConversationTest {

    private static final String PASSWORD = "Passw0rd!Test";
    private static final int DIM = 768;
    private static final String POLICY = "Annual leave policy. Every full time employee receives twenty paid vacation days each year. "
            + "Vacation days must be requested at least two weeks in advance through the leave page.";
    private static final String HR = "{\"answer\":\"You have no pending leave.\",\"factsUsed\":[\"Leave requests on record: 0\"]}";
    private static final String KB = "{\"answerable\":true,\"answer\":\"Employees get twenty paid vacation days a year.\",\"citations\":[1]}";

    @MockitoBean AiClient aiClient;
    @MockitoBean EmbeddingClient embedder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    record Tenant(String token, String slug) {}

    @BeforeEach
    void mocks() {
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.providerName()).thenReturn("test");
        when(aiClient.complete(any(), any(), any(), anyDouble())).thenAnswer(inv -> {
            String system = inv.getArgument(0);
            return new AiClient.AiCompletion(system.contains("friendly HR assistant") ? HR : KB, "test-model", 100, 50);
        });
        when(embedder.isAvailable()).thenReturn(true);
        when(embedder.providerName()).thenReturn("test");
        when(embedder.modelName()).thenReturn("test-embed");
        when(embedder.dimensions()).thenReturn(DIM);
        when(embedder.embed(any(), any())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (String t : texts) out.add(hash(t));
            return out;
        });
    }

    private static float[] hash(String text) {
        float[] v = new float[DIM];
        for (String w : text.toLowerCase().split("[^a-z0-9]+")) {
            if (w.length() < 3) continue;
            v[Math.floorMod(w.hashCode(), DIM)] += 1f;
        }
        double n = 0;
        for (float x : v) n += x * x;
        n = Math.sqrt(n);
        if (n == 0) v[0] = 1f; else for (int i = 0; i < DIM; i++) v[i] /= (float) n;
        return v;
    }

    // ---- helpers -----------------------------------------------------------------

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
            case "PATCH" -> patch(url);
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

    private String employeeToken(Tenant owner, String label) throws Exception {
        String email = label + owner.slug() + "@example.com";
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", label);
        emp.put("email", email);
        emp.put("password", PASSWORD);
        emp.put("role", "EMPLOYEE");
        ok(call("POST", "/api/employees", owner.token(), emp), 200);
        JsonNode login = ok(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", owner.slug())))), 200);
        return login.get("token").asText();
    }

    private String lastUserPrompt() {
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiClient, atLeastOnce()).complete(any(), user.capture(), any(), anyDouble());
        return user.getAllValues().get(user.getAllValues().size() - 1);
    }

    private long askHr(String token, String question, Long chatId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        if (chatId != null) body.put("conversationId", chatId);
        return ok(call("POST", "/api/ai/hr-assistant/ask", token, body), 200).get("conversationId").asLong();
    }

    // ---- tests -------------------------------------------------------------------

    @Test
    void aChatIsSavedContinuedRememberedRenamedAndDeleted() throws Exception {
        Tenant t = newTenant("Chat");
        String alice = employeeToken(t, "alice");

        long id = askHr(alice, "How many days of leave have I taken this year?", null);
        assertThat(lastUserPrompt()).doesNotContain("<<<HISTORY");                  // a new chat has no history

        assertThat(askHr(alice, "And last month?", id)).isEqualTo(id);             // same chat continues
        String prompt = lastUserPrompt();
        assertThat(prompt).contains("<<<HISTORY").contains("How many days of leave have I taken this year?")
                .contains("You have no pending leave.");                             // the model is given the earlier turns

        JsonNode list = ok(call("GET", "/api/ai/conversations?kind=HR_ASSISTANT", alice, null), 200);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("title").asText()).startsWith("How many days of leave");

        JsonNode detail = ok(call("GET", "/api/ai/conversations/" + id, alice, null), 200);
        assertThat(detail.get("messages")).hasSize(4);
        assertThat(detail.get("messages").get(0).get("role").asText()).isEqualTo("USER");
        assertThat(detail.get("messages").get(1).get("role").asText()).isEqualTo("ASSISTANT");
        assertThat(detail.get("messages").get(1).get("details").get("factsUsed")).hasSize(1);

        JsonNode renamed = ok(call("PATCH", "/api/ai/conversations/" + id, alice, Map.of("title", "My leave")), 200);
        assertThat(renamed.get("title").asText()).isEqualTo("My leave");
        assertThat(call("PATCH", "/api/ai/conversations/" + id, alice, Map.of("title", " ")).getStatus()).isEqualTo(400);

        assertThat(call("DELETE", "/api/ai/conversations/" + id, alice, null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/api/ai/conversations/" + id, alice, null).getStatus()).isEqualTo(404);
        assertThat(ok(call("GET", "/api/ai/conversations?kind=HR_ASSISTANT", alice, null), 200)).isEmpty();
    }

    @Test
    void chatsArePrivateToTheirOwner() throws Exception {
        Tenant t = newTenant("Private");
        String alice = employeeToken(t, "alice");
        String bob = employeeToken(t, "bob");
        long id = askHr(alice, "What is my attendance like lately?", null);

        assertThat(ok(call("GET", "/api/ai/conversations?kind=HR_ASSISTANT", bob, null), 200)).isEmpty();
        assertThat(call("GET", "/api/ai/conversations/" + id, bob, null).getStatus()).isEqualTo(404);
        assertThat(call("PATCH", "/api/ai/conversations/" + id, bob, Map.of("title", "Mine now")).getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/api/ai/conversations/" + id, bob, null).getStatus()).isEqualTo(404);
        assertThat(call("POST", "/api/ai/hr-assistant/ask", bob, Map.of("question", "Continue please", "conversationId", id)).getStatus()).isEqualTo(404);

        Tenant other = newTenant("Other");
        assertThat(call("GET", "/api/ai/conversations/" + id, other.token(), null).getStatus()).isEqualTo(404);
        assertThat(ok(call("GET", "/api/ai/conversations/" + id, alice, null), 200).get("messages")).hasSize(2); // untouched
    }

    @Test
    void documentChatsRememberToo_andAChatBelongsToOneFeature() throws Exception {
        Tenant t = newTenant("Docs");
        ok(call("POST", "/api/kb/documents/text", t.token(), Map.of("title", "Leave policy", "audience", "ALL_STAFF", "text", POLICY)), 201);

        JsonNode first = ok(call("POST", "/api/kb/ask", t.token(), Map.of("question", "How many vacation days do employees get each year?")), 200);
        assertThat(first.get("found").asBoolean()).isTrue();
        long id = first.get("conversationId").asLong();

        JsonNode second = ok(call("POST", "/api/kb/ask", t.token(),
                Map.of("question", "How far in advance must vacation days be requested?", "conversationId", id)), 200);
        assertThat(second.get("conversationId").asLong()).isEqualTo(id);
        assertThat(lastUserPrompt()).contains("<<<HISTORY").contains("How many vacation days do employees get each year?");

        JsonNode detail = ok(call("GET", "/api/ai/conversations/" + id, t.token(), null), 200);
        assertThat(detail.get("kind").asText()).isEqualTo("KB_ASK");
        assertThat(detail.get("messages")).hasSize(4);
        assertThat(detail.get("messages").get(1).get("details").get("sources")).isNotEmpty();

        // a document chat can't be continued from the HR assistant, and the lists are separate
        assertThat(call("POST", "/api/ai/hr-assistant/ask", t.token(), Map.of("question", "Hello there", "conversationId", id)).getStatus()).isEqualTo(404);
        assertThat(ok(call("GET", "/api/ai/conversations?kind=HR_ASSISTANT", t.token(), null), 200)).isEmpty();
        assertThat(ok(call("GET", "/api/ai/conversations?kind=KB_ASK", t.token(), null), 200)).hasSize(1);
    }

    @Test
    void unknownKindsAndMissingSignInAreRefused_andDeleteAllOnlyClearsTheCallersChats() throws Exception {
        Tenant t = newTenant("Clear");
        String alice = employeeToken(t, "alice");
        String bob = employeeToken(t, "bob");
        askHr(alice, "First question here", null);
        askHr(alice, "Second question here", null);
        askHr(bob, "Bob's question here", null);

        assertThat(call("GET", "/api/ai/conversations?kind=NOPE", alice, null).getStatus()).isEqualTo(400);
        assertThat(send(get("/api/ai/conversations?kind=HR_ASSISTANT")).getStatus()).isEqualTo(401);

        assertThat(ok(call("DELETE", "/api/ai/conversations?kind=HR_ASSISTANT", alice, null), 200).get("deleted").asInt()).isEqualTo(2);
        assertThat(ok(call("GET", "/api/ai/conversations?kind=HR_ASSISTANT", alice, null), 200)).isEmpty();
        assertThat(ok(call("GET", "/api/ai/conversations?kind=HR_ASSISTANT", bob, null), 200)).hasSize(1);
    }
}
