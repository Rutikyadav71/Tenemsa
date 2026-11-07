package com.tenemsa.platform;

import com.tenemsa.platform.mail.EmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Plain unit test: no Spring context, no mail server. Checks what actually goes into each message. */
class EmailServiceTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final EmailService email = new EmailService(sender);

    EmailServiceTest() {
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        ReflectionTestUtils.setField(email, "emailEnabled", true);
        ReflectionTestUtils.setField(email, "smtpUser", "sender@example.test");
        ReflectionTestUtils.setField(email, "frontendUrl", "https://app.example.test/");
        ReflectionTestUtils.setField(email, "fromAddress", "");
        ReflectionTestUtils.setField(email, "fromName", "Tenemsa");
        ReflectionTestUtils.setField(email, "replyTo", "");
        ReflectionTestUtils.setField(email, "verifyMinutes", 30);
        ReflectionTestUtils.setField(email, "setupDays", 7);
    }

    private MimeMessage lastMessage() throws Exception {
        ArgumentCaptor<MimeMessage> c = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(c.capture());
        MimeMessage m = c.getValue();
        m.saveChanges();
        return m;
    }

    private String sent() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        lastMessage().writeTo(out);
        return out.toString("UTF-8");
    }

    /** Decoded text of every part with the given content type, found anywhere in the message. */
    private static String part(Object content, String type) throws Exception {
        if (content instanceof String s) return s;
        StringBuilder out = new StringBuilder();
        if (content instanceof jakarta.mail.Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                jakarta.mail.BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("multipart/*")) out.append(part(bp.getContent(), type));
                else if (bp.isMimeType(type)) out.append(bp.getContent());
            }
        }
        return out.toString();
    }

    @Test
    void accountSetupEmailHasBothPartsALinkAndNeverAPassword() throws Exception {
        assertTrue(email.sendAccountSetupEmail("asha@acme.test", "Asha", "Acme", "acme", "EMPLOYEE", "Rutik", "TOKEN123"));
        String raw = sent();
        assertTrue(raw.contains("multipart/alternative"), "text and HTML should be sent together");
        assertTrue(raw.contains("text/plain") && raw.contains("text/html"));
        assertTrue(raw.contains("https://app.example.test/reset-password?setup=1&token=TOKEN123") || raw.contains("setup=3D1"), "set-password link missing");
        assertTrue(raw.contains("Auto-Submitted: auto-generated"));
        assertTrue(raw.contains("Tenemsa <sender@example.test>"), "sender should show the product name");
        assertFalse(raw.toLowerCase().contains("temporary password"));
    }

    @Test
    void namesAreEscapedSoNobodyCanInjectMarkup() throws Exception {
        email.sendNotificationEmail("a@b.test", "<script>x</script>", "<b>hi</b>");
        MimeMessage m = lastMessage();
        String html = part(m.getContent(), "text/html");
        assertFalse(html.contains("<script>x"), "name must be escaped in the HTML part");
        assertFalse(html.contains("<b>hi</b>"), "message must be escaped in the HTML part");
        assertTrue(html.contains("&lt;b&gt;hi&lt;/b&gt;"));
        assertTrue(part(m.getContent(), "text/plain").contains("<b>hi</b>"), "plain text keeps the characters as typed");
    }

    @Test
    void lineBreaksInASubjectCannotInjectHeaders() throws Exception {
        email.sendInviteEmail("a@b.test", "Asha", "Acme", "Rutik\r\nBcc: evil@x.test", "HR", "T");
        MimeMessage m = lastMessage();
        assertNull(m.getHeader("Bcc"), "a line break must not create a new header");
        assertFalse(m.getSubject().contains("\n"));
    }

    @Test
    void switchedOffSendsNothing() {
        ReflectionTestUtils.setField(email, "emailEnabled", false);
        assertTrue(email.sendVerificationEmail("a@b.test", "A", "T"));
        verifyNoInteractions(sender);
    }

    @Test
    void aFailingMailServerReturnsFalseInsteadOfThrowing() {
        doThrow(new org.springframework.mail.MailSendException("down")).when(sender).send(any(MimeMessage.class));
        assertFalse(email.sendPasswordResetEmail("a@b.test", "A", "Acme", "T"));
    }
}
