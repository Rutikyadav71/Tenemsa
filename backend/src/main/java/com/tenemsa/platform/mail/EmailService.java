package com.tenemsa.platform.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * One place for every outbound email the app sends. Previously
 * EmployeeServiceImpl had its own private sendWelcomeEmail() method built
 * directly on JavaMailSender, duplicating exactly this pattern; a separate,
 * entirely unused EmailService.java also existed (removed during the
 * repository cleanup pass — dead code, never wired in anywhere). This is the
 * real one, now actually used by every email-sending code path.
 */
@Service
public class EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.email.enabled}")
    private boolean emailEnabled;

    @Value("${spring.mail.username:}")
    private String smtpUser;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Sender address. Empty means the SMTP account itself, which is what most providers require. */
    @Value("${app.email.from:}")
    private String fromAddress;

    @Value("${app.email.from-name:Tenemsa}")
    private String fromName;

    @Value("${app.email.reply-to:}")
    private String replyTo;

    @Value("${app.verification-token.expiration-minutes:30}")
    private int verifyMinutes;

    @Value("${app.account-setup.expiration-days:7}")
    private int setupDays;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private String url(String path) { return frontendUrl.replaceAll("/+$", "") + path; }

    private static String enc(String s) { return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8); }

    public String signInUrl(String workspaceSlug) { return url("/auth/employee?workspace=" + enc(workspaceSlug)); }

    public boolean sendVerificationEmail(String toEmail, String name, String rawToken) {
        return send(toEmail, MailTemplates.verifyEmail(name, url("/verify-email?token=" + enc(rawToken)), verifyMinutes));
    }

    public boolean sendOwnerWelcomeEmail(String toEmail, String name, String company, String slug) {
        return send(toEmail, MailTemplates.welcomeOwner(name, company, slug, toEmail, signInUrl(slug)));
    }

    /** Sent when an administrator adds someone. The person sets their own password; none is ever emailed. */
    public boolean sendAccountSetupEmail(String toEmail, String name, String company, String slug, String role, String addedBy, String rawToken) {
        return send(toEmail, MailTemplates.accountSetup(name, company, slug, toEmail, role, addedBy,
                url("/reset-password?setup=1&token=" + enc(rawToken)), setupDays));
    }

    public boolean sendPasswordResetEmail(String toEmail, String name, String companyName, String rawToken) {
        return send(toEmail, MailTemplates.passwordReset(name, companyName, url("/reset-password?token=" + enc(rawToken)), verifyMinutes));
    }

    public boolean sendPasswordChangedEmail(String toEmail, String name, String companyName) {
        return send(toEmail, MailTemplates.passwordChanged(name, companyName, url("/forgot-password")));
    }

    /** Returns false when the mail server refused or could not deliver the message. */
    public boolean sendInviteEmail(String toEmail, String inviteeName, String companyName,
                                String inviterName, String roleLabel, String rawToken) {
        return send(toEmail, MailTemplates.invitation(inviteeName, companyName, inviterName, roleLabel,
                url("/accept-invite?token=" + enc(rawToken)), 7));
    }

    public boolean sendInviteAcceptedEmail(String toEmail, String name, String company, String slug) {
        return send(toEmail, MailTemplates.welcomeAfterInvite(name, company, slug, toEmail, signInUrl(slug)));
    }

    /** In-app notification copied to email. The message is plain text and is escaped by the template. */
    public boolean sendNotificationEmail(String toEmail, String name, String message) {
        return send(toEmail, MailTemplates.notification(name, message, url("/")));
    }

    public boolean sendAnnouncementEmail(String toEmail, String name, String company, String sender, String subject, String body) {
        return send(toEmail, MailTemplates.announcement(name, company, sender, subject, body, url("/")));
    }

    public boolean sendPlatformNoticeEmail(String toEmail, String name, String title, String message, String severity) {
        return send(toEmail, MailTemplates.platformNotice(name, title, message, severity, url("/")));
    }

    private boolean send(String toEmail, MailTemplates.Mail mail) {
        return send(toEmail, mail.subject(), mail.html(), mail.text());
    }

    /** true = handed to the mail server (or email is switched off); false = delivery failed. */
    private boolean send(String toEmail, String rawSubject, String htmlBody, String textBody) {
        String subject = rawSubject.replaceAll("[\\r\\n]+", " ");   // a line break here would let text reach a mail header
        if (!emailEnabled) {
            log.info("Email is switched off - would have sent \"{}\" to {}", subject, toEmail);
            return true;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = fromAddress == null || fromAddress.isBlank() ? smtpUser : fromAddress;
            if (from != null && !from.isBlank()) helper.setFrom(from, fromName);
            if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);   // plain text and HTML in one multipart/alternative message
            message.setHeader("Auto-Submitted", "auto-generated");
            mailSender.send(message);
            return true;
        } catch (MessagingException | MailException | java.io.UnsupportedEncodingException e) {
            // Email delivery failing shouldn't ever fail the calling request
            // (e.g. account creation succeeding but the welcome email
            // bouncing) — log and move on rather than propagate.
            // MailException (unchecked) is what Spring throws for a rejected SMTP login
            // ("Authentication failed"); catching only MessagingException let it escape and
            // roll back the whole request, including a just-saved invitation.
            String cause = rootMessage(e);
            String hint = "";
            if (cause.contains("535") || cause.toLowerCase().contains("authentication") || cause.toLowerCase().contains("username and password"))
                hint = " Hint: the mail server rejected the sign-in. For Gmail use an App Password (Google Account > Security > 2-Step Verification > App passwords) as SMTP_PASSWORD, with your full Gmail address as SMTP_USERNAME.";
            else if (cause.toLowerCase().contains("connect") || cause.toLowerCase().contains("timed out") || cause.toLowerCase().contains("unknown host"))
                hint = " Hint: the mail server could not be reached. Check MAIL_HOST, MAIL_PORT and your internet connection.";
            log.error("Failed to send email \"{}\" to {}: {}.{}", subject, toEmail, cause, hint);
            return false;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getMessage();
        return m == null || m.isBlank() ? r.getClass().getSimpleName() : m.replaceAll("[\\r\\n]+", " ");
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warnIfMailNotConfigured() {
        if (emailEnabled && (smtpUser == null || smtpUser.isBlank()))
            log.warn("Email is on but SMTP_USERNAME is empty, so password-reset, invitation and verification emails will NOT be delivered. Set SMTP_USERNAME and SMTP_PASSWORD in backend/.env.");
        if (emailEnabled && frontendUrl.contains("localhost"))
            log.warn("FRONTEND_URL is {}: links in emails point at your own computer and mail providers often treat such mail as spam. Fine for testing; set FRONTEND_URL to the real site before going live.", frontendUrl);
        if (emailEnabled && smtpUser != null && smtpUser.toLowerCase().endsWith("@gmail.com"))
            log.warn("Sending from a Gmail address: expect some mail in spam. For real use send from your own domain (SPF, DKIM and DMARC set up) through a mail service. See docs/email.md.");
    }
}
