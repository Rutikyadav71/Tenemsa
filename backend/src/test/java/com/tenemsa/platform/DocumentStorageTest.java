package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.knowledge.embedding.EmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Phase 8b: document storage. Checks OUR rules: who may upload, who may read each visibility, that unreadable looks
 * like missing, that file type and content are verified, safe download headers, tenant isolation, and the link to the
 * knowledge base. The embedding service is a deterministic mock; nothing external is contacted.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:documents-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentStorageTest {

    private static final String PASSWORD = "Passw0rd!Test";
    private static final int DIM = 768;
    private static final String POLICY = "Remote work policy. Employees may work from home two days every week after approval from their manager. "
            + "Core hours are ten to four and the team calendar must be kept up to date.";

    @MockitoBean EmbeddingClient embedder;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    record Tenant(String token, String slug) {}
    record Person(String token, int empId) {}

    @BeforeEach
    void embedder() {
        when(embedder.isAvailable()).thenReturn(true);
        when(embedder.providerName()).thenReturn("test");
        when(embedder.modelName()).thenReturn("test-embed");
        when(embedder.dimensions()).thenReturn(DIM);
        when(embedder.embed(any(), any())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (String t : texts) { float[] v = new float[DIM]; v[Math.floorMod(t.length(), DIM)] = 1f; out.add(v); }
            return out;
        });
    }

    // ---- helpers ----------------------------------------------------------------

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
            case "PUT" -> put(url);
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

    private Person person(Tenant owner, String label, String role) throws Exception {
        String email = label + owner.slug() + "@example.com";
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", label);
        emp.put("email", email);
        emp.put("password", PASSWORD);
        emp.put("role", role);
        JsonNode created = ok(call("POST", "/api/employees", owner.token(), emp), 200);
        JsonNode login = ok(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", owner.slug())))), 200);
        return new Person(login.get("token").asText(), created.get("empId").asInt());
    }

    private MockHttpServletResponse upload(String token, String fileName, byte[] bytes, Map<String, String> params) throws Exception {
        MockMultipartHttpServletRequestBuilder rb = multipart("/api/documents").file(new MockMultipartFile("file", fileName, "application/octet-stream", bytes));
        params.forEach(rb::param);
        return send(rb.header("Authorization", "Bearer " + token));
    }

    private static byte[] pdf(String text) { return ("%PDF-1.4\n" + text).getBytes(StandardCharsets.UTF_8); }

    private JsonNode uploadOk(String token, String fileName, byte[] bytes, Map<String, String> params) throws Exception {
        return ok(upload(token, fileName, bytes, params), 201);
    }

    private boolean listed(String token, long id) throws Exception {
        for (JsonNode d : ok(call("GET", "/api/documents", token, null), 200)) if (d.get("id").asLong() == id) return true;
        return false;
    }

    // ---- tests -------------------------------------------------------------------

    @Test
    void uploadListAndDownloadRoundTripWithSafeHeaders() throws Exception {
        Tenant t = newTenant("Acme");
        byte[] bytes = pdf("handbook contents é");
        JsonNode doc = uploadOk(t.token(), "../../Staff Handbook.pdf", bytes, Map.of("title", "Staff handbook", "category", "HANDBOOK", "visibility", "ALL_STAFF"));
        assertThat(doc.get("fileName").asText()).isEqualTo("Staff Handbook.pdf");          // path stripped
        assertThat(doc.get("contentType").asText()).isEqualTo("application/pdf");          // decided by the server
        assertThat(doc.get("sizeBytes").asLong()).isEqualTo(bytes.length);
        assertThat(doc.get("uploadedByName").asText()).isEqualTo("Acme Owner");

        MockHttpServletResponse dl = call("GET", "/api/documents/" + doc.get("id").asLong() + "/download", t.token(), null);
        assertThat(dl.getStatus()).isEqualTo(200);
        assertThat(dl.getContentAsByteArray()).isEqualTo(bytes);
        assertThat(dl.getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(dl.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(dl.getHeader("Cache-Control")).contains("no-store");
        assertThat(ok(call("GET", "/api/documents?category=POLICY", t.token(), null), 200)).isEmpty();
        assertThat(ok(call("GET", "/api/documents?category=HANDBOOK", t.token(), null), 200)).hasSize(1);
    }

    @Test
    void visibilityDecidesWhoCanReadAndHiddenLooksMissing() throws Exception {
        Tenant t = newTenant("Vis");
        Person manager = person(t, "Mona", "MANAGER");
        Person alice = person(t, "Alice", "EMPLOYEE");
        Person bob = person(t, "Bob", "EMPLOYEE");

        long everyone = uploadOk(t.token(), "all.pdf", pdf("a"), Map.of("visibility", "ALL_STAFF")).get("id").asLong();
        long mgmt = uploadOk(t.token(), "mgmt.pdf", pdf("b"), Map.of("visibility", "MANAGEMENT")).get("id").asLong();
        long contract = uploadOk(t.token(), "contract.pdf", pdf("c"),
                Map.of("visibility", "PRIVATE", "ownerEmpId", String.valueOf(alice.empId()), "category", "CONTRACT")).get("id").asLong();

        assertThat(listed(alice.token(), everyone)).isTrue();
        assertThat(listed(alice.token(), mgmt)).isFalse();
        assertThat(listed(alice.token(), contract)).isTrue();
        assertThat(listed(bob.token(), contract)).isFalse();
        assertThat(listed(manager.token(), mgmt)).isTrue();
        assertThat(listed(manager.token(), contract)).isFalse();         // a manager is not HR: no one else's private files
        assertThat(listed(t.token(), contract)).isTrue();

        assertThat(call("GET", "/api/documents/" + mgmt + "/download", alice.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("GET", "/api/documents/" + contract + "/download", bob.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("GET", "/api/documents/" + contract, manager.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("GET", "/api/documents/" + contract + "/download", alice.token(), null).getStatus()).isEqualTo(200);

        // The employee it is for hears about it; nobody else does.
        assertThat(ok(call("GET", "/api/notifications", alice.token(), null), 200).toString()).contains("DOCUMENT_SHARED");
        assertThat(ok(call("GET", "/api/notifications", bob.token(), null), 200)).isEmpty();
    }

    @Test
    void onlyDocumentManagersCanUploadOrDelete() throws Exception {
        Tenant t = newTenant("Perm");
        Person manager = person(t, "Mona", "MANAGER");
        Person alice = person(t, "Alice", "EMPLOYEE");
        long id = uploadOk(t.token(), "a.pdf", pdf("a"), Map.of()).get("id").asLong();
        for (String token : List.of(manager.token(), alice.token())) {
            assertThat(upload(token, "x.pdf", pdf("x"), Map.of()).getStatus()).isEqualTo(403);
            assertThat(call("DELETE", "/api/documents/" + id, token, null).getStatus()).isEqualTo(403);
            assertThat(call("GET", "/api/documents/" + id + "/download", token, null).getStatus()).isEqualTo(200);   // reading is for everyone
        }
        assertThat(call("GET", "/api/documents", "not-a-token", null).getStatus()).isIn(401, 403);
    }

    @Test
    void fileTypeContentAndOwnerAreValidated() throws Exception {
        Tenant t = newTenant("Valid");
        Person alice = person(t, "Alice", "EMPLOYEE");
        assertThat(upload(t.token(), "run.exe", new byte[]{1, 2, 3}, Map.of()).getStatus()).isEqualTo(400);
        assertThat(upload(t.token(), "page.html", "<script>x</script>".getBytes(), Map.of()).getStatus()).isEqualTo(400);
        MockHttpServletResponse fake = upload(t.token(), "fake.pdf", "just text, not a pdf".getBytes(), Map.of());
        assertThat(fake.getStatus()).isEqualTo(400);
        assertThat(fake.getContentAsString()).contains("does not match");
        assertThat(upload(t.token(), "empty.pdf", new byte[0], Map.of()).getStatus()).isEqualTo(400);
        assertThat(upload(t.token(), "p.pdf", pdf("p"), Map.of("visibility", "PRIVATE")).getStatus()).isEqualTo(400);                 // no owner
        assertThat(upload(t.token(), "p.pdf", pdf("p"), Map.of("visibility", "PRIVATE", "ownerEmpId", "999")).getStatus()).isEqualTo(400);
        assertThat(upload(t.token(), "p.pdf", pdf("p"), Map.of("visibility", "BOGUS")).getStatus()).isEqualTo(400);
        assertThat(upload(t.token(), "ok.pdf", pdf("p"), Map.of("visibility", "PRIVATE", "ownerEmpId", String.valueOf(alice.empId()))).getStatus()).isEqualTo(201);
    }

    @Test
    void companiesNeverSeeEachOthersDocuments() throws Exception {
        Tenant a = newTenant("Alpha");
        Tenant b = newTenant("Beta");
        long id = uploadOk(a.token(), "a.pdf", pdf("secret"), Map.of("title", "Alpha secret")).get("id").asLong();
        assertThat(ok(call("GET", "/api/documents", b.token(), null), 200)).isEmpty();
        assertThat(call("GET", "/api/documents/" + id, b.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("GET", "/api/documents/" + id + "/download", b.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("DELETE", "/api/documents/" + id, b.token(), null).getStatus()).isEqualTo(404);
        assertThat(call("GET", "/api/documents/" + id + "/download", a.token(), null).getStatus()).isEqualTo(200);
    }

    @Test
    void deleteRemovesTheFile() throws Exception {
        Tenant t = newTenant("Del");
        long id = uploadOk(t.token(), "a.pdf", pdf("a"), Map.of()).get("id").asLong();
        assertThat(call("DELETE", "/api/documents/" + id, t.token(), null).getStatus()).isEqualTo(204);
        assertThat(call("GET", "/api/documents/" + id + "/download", t.token(), null).getStatus()).isEqualTo(404);
        assertThat(listed(t.token(), id)).isFalse();
    }

    @Test
    void indexForAiAddsTheKnowledgeCopyAndDeletingRemovesIt() throws Exception {
        Tenant t = newTenant("Kb");
        JsonNode doc = uploadOk(t.token(), "remote-work.txt", POLICY.getBytes(StandardCharsets.UTF_8),
                Map.of("title", "Remote work", "category", "POLICY", "visibility", "ALL_STAFF", "indexForAi", "true"));
        assertThat(doc.get("indexedForAi").asBoolean()).isTrue();
        JsonNode kb = ok(call("GET", "/api/kb/documents", t.token(), null), 200);
        assertThat(kb).hasSize(1);
        assertThat(kb.get(0).get("title").asText()).isEqualTo("Remote work");
        assertThat(kb.get(0).get("audience").asText()).isEqualTo("ALL_STAFF");

        assertThat(call("DELETE", "/api/documents/" + doc.get("id").asLong(), t.token(), null).getStatus()).isEqualTo(204);
        assertThat(ok(call("GET", "/api/kb/documents", t.token(), null), 200)).isEmpty();
    }

    @Test
    void indexForAiRulesAndGracefulFailure() throws Exception {
        Tenant t = newTenant("KbRules");
        assertThat(upload(t.token(), "p.txt", POLICY.getBytes(), Map.of("visibility", "PRIVATE", "ownerEmpId", "1", "indexForAi", "true")).getStatus()).isEqualTo(400);
        assertThat(upload(t.token(), "p.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1}, Map.of("indexForAi", "true")).getStatus()).isEqualTo(400);

        // With AI switched off the upload still works and says why it is not searchable.
        ok(call("PUT", "/api/companies/me/ai", t.token(), Map.of("enabled", false)), 200);
        JsonNode doc = uploadOk(t.token(), "p.txt", POLICY.getBytes(), Map.of("indexForAi", "true"));
        assertThat(doc.get("indexedForAi").asBoolean()).isFalse();
        assertThat(doc.get("indexNote").asText()).contains("switched off");
        assertThat(call("GET", "/api/documents/" + doc.get("id").asLong() + "/download", t.token(), null).getStatus()).isEqualTo(200);
    }
}
