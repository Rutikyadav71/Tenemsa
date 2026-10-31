package com.tenemsa.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.chat.model.ChatMessage;
import com.tenemsa.platform.chat.service.ChatMessageService;
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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Phase 8d: files sent in chat. Checks that the exact bytes and original name reach the other person, that only the
 * two people in the conversation can read the file, that unsafe or mismatched files are refused with a readable
 * message, and that deleting a message removes its file.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chat-attachments-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatAttachmentTest {

    private static final String PASSWORD = "Passw0rd!Test";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ChatMessageService chat;

    record Tenant(String token, String slug) {}
    record Person(String token, int empId, long companyId) {}

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

    private Person person(Tenant owner, String label) throws Exception {
        String email = label + owner.slug() + "@example.com";
        Map<String, Object> emp = new LinkedHashMap<>();
        emp.put("name", label); emp.put("email", email); emp.put("password", PASSWORD); emp.put("role", "EMPLOYEE");
        MockHttpServletResponse created = send(post("/api/employees").header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(emp)));
        assertThat(created.getStatus()).isEqualTo(200);
        JsonNode login = json.readTree(send(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "workspace", owner.slug())))).getContentAsString());
        return new Person(login.get("token").asText(), json.readTree(created.getContentAsString()).get("empId").asInt(), login.get("companyId").asLong());
    }

    private MockHttpServletResponse upload(String token, String name, byte[] bytes) throws Exception {
        return send(multipart("/api/chat/attachments").file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .header("Authorization", "Bearer " + token));
    }

    private MockHttpServletResponse fetch(String token, String url) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).header("Authorization", "Bearer " + token));
    }

    private ChatMessage sendFile(Person from, Person to, long attachmentId, String caption) {
        ChatMessage m = new ChatMessage();
        m.setSenderId(from.empId()); m.setSenderRole("USER"); m.setReceiverId(to.empId()); m.setReceiverRole("USER");
        m.setMessage(caption); m.setAttachmentId(attachmentId);
        return chat.save(from.companyId(), "USER", from.empId(), m);
    }

    private static byte[] pdf(String text) { return ("%PDF-1.4\n" + text).getBytes(StandardCharsets.UTF_8); }

    @Test
    void receiverGetsTheExactFileUnderItsOriginalNameAndOutsidersGetNothing() throws Exception {
        Tenant t = newTenant("Acme");
        Person alice = person(t, "Alice"), bob = person(t, "Bob"), carol = person(t, "Carol");
        byte[] bytes = pdf("quarterly report é");

        JsonNode up = json.readTree(upload(alice.token(), "../Q3 Report (final).pdf", bytes).getContentAsString());
        long id = up.get("id").asLong();
        assertThat(up.get("fileName").asText()).isEqualTo("Q3 Report (final).pdf");
        assertThat(up.get("contentType").asText()).isEqualTo("application/pdf");

        // Not sent yet: only the uploader can see it.
        assertThat(fetch(alice.token(), "/api/chat/attachments/" + id).getStatus()).isEqualTo(200);
        assertThat(fetch(bob.token(), "/api/chat/attachments/" + id).getStatus()).isEqualTo(404);

        ChatMessage sent = sendFile(alice, bob, id, "");
        assertThat(sent.getAttachmentName()).isEqualTo("Q3 Report (final).pdf");     // set by the server
        assertThat(sent.getAttachmentSize()).isEqualTo(bytes.length);

        MockHttpServletResponse dl = fetch(bob.token(), "/api/chat/attachments/" + id);
        assertThat(dl.getStatus()).isEqualTo(200);
        assertThat(dl.getContentAsByteArray()).isEqualTo(bytes);
        assertThat(dl.getHeader("Content-Disposition")).startsWith("attachment").contains("filename*=UTF-8''Q3%20Report%20%28final%29.pdf");   // browsers read the encoded filename*
        assertThat(dl.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(dl.getHeader("Cache-Control")).contains("no-store");

        assertThat(fetch(carol.token(), "/api/chat/attachments/" + id).getStatus()).isEqualTo(404);   // same company, not in the chat

        // History carries the name/type/size to the other side.
        JsonNode hist = json.readTree(fetch(bob.token(), "/api/chat/history?id1=" + bob.empId() + "&role1=USER&id2=" + alice.empId() + "&role2=USER").getContentAsString());
        assertThat(hist.get(0).get("attachmentName").asText()).isEqualTo("Q3 Report (final).pdf");

        // Another company can never reach it.
        Tenant other = newTenant("Other");
        assertThat(fetch(other.token(), "/api/chat/attachments/" + id).getStatus()).isEqualTo(404);
    }

    @Test
    void imagesAreServedInlineAndOtherFilesAsDownloads() throws Exception {
        Tenant t = newTenant("Img");
        Person a = person(t, "Ann"), b = person(t, "Ben");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        long id = json.readTree(upload(a.token(), "photo.png", png).getContentAsString()).get("id").asLong();
        sendFile(a, b, id, "look");
        assertThat(fetch(b.token(), "/api/chat/attachments/" + id).getHeader("Content-Disposition")).startsWith("inline");
        assertThat(fetch(b.token(), "/api/chat/attachments/" + id + "?download=true").getHeader("Content-Disposition")).startsWith("attachment");
    }

    @Test
    void unsafeOrMismatchedFilesAreRefusedWithAReadableMessage() throws Exception {
        Tenant t = newTenant("Safe");
        Person a = person(t, "Sam");
        MockHttpServletResponse html = upload(a.token(), "evil.html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        assertThat(html.getStatus()).isEqualTo(400);
        assertThat(json.readTree(html.getContentAsString()).get("message").asText()).contains("can't be sent");

        MockHttpServletResponse fake = upload(a.token(), "report.pdf", "just text".getBytes(StandardCharsets.UTF_8));
        assertThat(fake.getStatus()).isEqualTo(400);
        assertThat(json.readTree(fake.getContentAsString()).get("message").asText()).contains("don't match");
    }

    @Test
    void anAttachmentCanBeSentOnlyOnceAndOnlyByItsUploader() throws Exception {
        Tenant t = newTenant("Once");
        Person a = person(t, "Uma"), b = person(t, "Vik"), c = person(t, "Wes");
        long id = json.readTree(upload(a.token(), "a.pdf", pdf("x")).getContentAsString()).get("id").asLong();
        assertThatThrownBy(() -> sendFile(b, c, id, "stolen")).isInstanceOf(IllegalArgumentException.class);   // not Vik's file
        sendFile(a, b, id, "");
        assertThatThrownBy(() -> sendFile(a, c, id, "again")).isInstanceOf(IllegalArgumentException.class);    // already sent
        assertThatThrownBy(() -> sendFile(a, c, 999999L, "")).isInstanceOf(IllegalArgumentException.class);    // does not exist
    }

    @Test
    void deletingTheMessageRemovesTheFileForBothPeople() throws Exception {
        Tenant t = newTenant("Del");
        Person a = person(t, "Dan"), b = person(t, "Eve");
        long id = json.readTree(upload(a.token(), "gone.pdf", pdf("y")).getContentAsString()).get("id").asLong();
        ChatMessage m = sendFile(a, b, id, "");
        assertThat(send(delete("/api/chat/message/" + m.getId()).header("Authorization", "Bearer " + a.token())).getStatus()).isEqualTo(200);
        assertThat(fetch(a.token(), "/api/chat/attachments/" + id).getStatus()).isEqualTo(404);
        assertThat(fetch(b.token(), "/api/chat/attachments/" + id).getStatus()).isEqualTo(404);
    }
}