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

    private final JavaMailSender mailSender;

    @Value("${app.email.enabled}")
    private boolean emailEnabled;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendWelcomeEmail(String toEmail, String name, String rawPassword) {
        send(toEmail, "Welcome to Tenemsa — Your Login Credentials",
                "<p>Hello <strong>" + name + "</strong>,</p>" +
                "<p>You've been added as an employee on Tenemsa.</p>" +
                "<p><strong>Email:</strong> " + toEmail + "<br>" +
                "<strong>Temporary password:</strong> " + rawPassword + "</p>" +
                "<p>Sign in and change your password from your profile as soon as you can.</p>" +
                "<p>— Tenemsa</p>");
    }

    public void sendVerificationEmail(String toEmail, String name, String rawToken) {
        String link = frontendUrl + "/verify-email?token=" + rawToken;
        send(toEmail, "Verify your Tenemsa email address",
                "<p>Hi <strong>" + name + "</strong>,</p>" +
                "<p>Confirm your email address to finish setting up your Tenemsa account:</p>" +
                "<p><a href=\"" + link + "\">Verify my email</a></p>" +
                "<p>This link expires in 30 minutes. If you didn't request this, you can ignore it.</p>" +
                "<p>— Tenemsa</p>");
    }

    public void sendPasswordResetEmail(String toEmail, String name, String companyName, String rawToken) {
        String link = frontendUrl + "/reset-password?token=" + rawToken;
        send(toEmail, "Reset your Tenemsa password",
                "<p>Hi <strong>" + name + "</strong>,</p>" +
                "<p>Someone requested a password reset for your account at <strong>" + companyName + "</strong>. If that was you:</p>" +
                "<p><a href=\"" + link + "\">Reset my password</a></p>" +
                "<p>This link expires in 30 minutes. If you didn't request this, your password is still safe — " +
                "just ignore this email.</p>" +
                "<p>— Tenemsa</p>");
    }

    /** Returns false when the mail server refused or could not deliver the message. */
    public boolean sendInviteEmail(String toEmail, String inviteeName, String companyName,
                                String inviterName, String roleLabel, String rawToken) {
        String link = frontendUrl + "/accept-invite?token=" + rawToken;
        return send(toEmail, inviterName + " invited you to " + companyName + " on Tenemsa",
                "<p>Hi <strong>" + inviteeName + "</strong>,</p>" +
                "<p><strong>" + inviterName + "</strong> invited you to join <strong>" + companyName +
                "</strong> on Tenemsa as <strong>" + roleLabel + "</strong>.</p>" +
                "<p><a href=\"" + link + "\">Accept the invitation</a> and choose your password.</p>" +
                "<p>This link expires in 7 days. If you weren't expecting it, you can ignore this email.</p>" +
                "<p>— Tenemsa</p>");
    }

    /** true = handed to the mail server (or email is switched off); false = delivery failed. */
    private boolean send(String toEmail, String subject, String htmlBody) {
        if (!emailEnabled) {
            System.out.println("Email disabled — would have sent \"" + subject + "\" to " + toEmail);
            return true;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            return true;
        } catch (MessagingException | MailException e) {
            // Email delivery failing shouldn't ever fail the calling request
            // (e.g. account creation succeeding but the welcome email
            // bouncing) — log and move on rather than propagate.
            // MailException (unchecked) is what Spring throws for a rejected SMTP login
            // ("Authentication failed"); catching only MessagingException let it escape and
            // roll back the whole request, including a just-saved invitation.
            System.err.println("Failed to send email \"" + subject + "\" to " + toEmail + ": " + e.getMessage());
            return false;
        }
    }
}
