package com.tenemsa.platform.mail;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Every email Tenemsa sends is built here, once, as a matching HTML and plain-text pair.
 * Sending both parts (multipart/alternative) and keeping the HTML simple, table-based and free of
 * remote images is what mail providers expect from genuine transactional mail.
 *
 * All values are escaped. Use **double asterisks** in a paragraph for bold.
 */
public final class MailTemplates {

    private MailTemplates() {}

    public record Mail(String subject, String html, String text) {}

    static final String BRAND = "#5b5bd6";

    /** Collects content once and renders it as HTML and as plain text. */
    public static final class Builder {
        private final String subject, preheader, heading;
        private final StringBuilder html = new StringBuilder(), text = new StringBuilder();
        private String footer = "";

        Builder(String subject, String preheader, String heading) {
            this.subject = subject; this.preheader = preheader; this.heading = heading;
            text.append(heading).append("\n\n");
        }

        public Builder p(String raw) {
            html.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:24px;color:#2b3340\">").append(rich(raw)).append("</p>");
            text.append(raw.replace("**", "")).append("\n\n");
            return this;
        }

        public Builder button(String label, String url) {
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 20px\"><tr><td style=\"border-radius:8px;background:").append(BRAND).append("\">")
                .append("<a href=\"").append(attr(url)).append("\" style=\"display:inline-block;padding:12px 24px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px\">")
                .append(esc(label)).append("</a></td></tr></table>")
                .append("<p style=\"margin:0 0 16px;font-size:12px;line-height:18px;color:#6b7686\">Button not working? Copy this address into your browser:<br>")
                .append("<a href=\"").append(attr(url)).append("\" style=\"color:").append(BRAND).append(";word-break:break-all\">").append(esc(url)).append("</a></p>");
            text.append(label).append(":\n").append(url).append("\n\n");
            return this;
        }

        /** Small table of facts, for example workspace and sign-in email. */
        public Builder details(String[][] rows) {
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"margin:0 0 20px;background:#f4f6fb;border-radius:8px\">");
            for (String[] r : rows) {
                html.append("<tr><td style=\"padding:8px 14px;font-size:13px;color:#6b7686;width:38%\">").append(esc(r[0]))
                    .append("</td><td style=\"padding:8px 14px;font-size:14px;color:#1c2330;font-weight:600\">").append(esc(r[1])).append("</td></tr>");
                text.append(r[0]).append(": ").append(r[1]).append("\n");
            }
            html.append("</table>");
            text.append("\n");
            return this;
        }

        public Builder steps(String title, List<String> items) {
            html.append("<p style=\"margin:0 0 8px;font-size:15px;font-weight:600;color:#1c2330\">").append(esc(title)).append("</p><ol style=\"margin:0 0 18px;padding-left:20px;font-size:15px;line-height:24px;color:#2b3340\">");
            text.append(title).append("\n");
            int i = 1;
            for (String s : items) { html.append("<li style=\"margin:0 0 4px\">").append(rich(s)).append("</li>"); text.append(i++).append(". ").append(s.replace("**", "")).append("\n"); }
            html.append("</ol>");
            text.append("\n");
            return this;
        }

        /** User-written text: escaped only (no bold markup), blank lines become paragraphs, single line breaks are kept. */
        public Builder block(String raw) {
            for (String para : raw.replace("\r\n", "\n").trim().split("\n{2,}")) {
                html.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:24px;color:#2b3340\">").append(esc(para).replace("\n", "<br>")).append("</p>");
            }
            text.append(raw.trim()).append("\n\n");
            return this;
        }

        public Builder small(String raw) {
            html.append("<p style=\"margin:0 0 12px;font-size:13px;line-height:20px;color:#6b7686\">").append(rich(raw)).append("</p>");
            text.append(raw.replace("**", "")).append("\n\n");
            return this;
        }

        /** Why the person received this email. */
        public Builder footer(String raw) { this.footer = raw; return this; }

        public Mail build() {
            String f = footer.isBlank() ? "" : esc(footer) + "<br>";
            String page = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"><title>" + esc(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:#eef1f7;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent\">" + esc(preheader) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#eef1f7\"><tr><td align=\"center\" style=\"padding:28px 12px\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px\">"
                + "<tr><td style=\"padding:0 4px 14px;font-size:22px;font-weight:700;color:" + BRAND + ";letter-spacing:-0.3px\">Tenemsa</td></tr>"
                + "<tr><td style=\"background:#ffffff;border-radius:14px;padding:32px 30px;border:1px solid #e1e6f0\">"
                + "<h1 style=\"margin:0 0 18px;font-size:21px;line-height:28px;color:#1c2330\">" + esc(heading) + "</h1>"
                + html
                + "</td></tr>"
                + "<tr><td style=\"padding:16px 8px;font-size:12px;line-height:18px;color:#8a94a6;text-align:center\">" + f
                + "Tenemsa &middot; people and project management for teams</td></tr>"
                + "</table></td></tr></table></body></html>";
            String plain = text.toString().trim() + "\n\n--\n" + (footer.isBlank() ? "" : footer + "\n") + "Tenemsa";
            return new Mail(subject, page, plain);
        }
    }

    static String esc(String s) { return HtmlUtils.htmlEscape(s == null ? "" : s); }
    static String attr(String s) { return HtmlUtils.htmlEscape(s == null ? "" : s); }

    /** Escapes, then turns **bold** into <strong>. */
    static String rich(String raw) {
        String e = esc(raw);
        StringBuilder out = new StringBuilder();
        boolean open = false;
        int i = 0;
        while (i < e.length()) {
            if (e.startsWith("**", i)) { out.append(open ? "</strong>" : "<strong>"); open = !open; i += 2; }
            else out.append(e.charAt(i++));
        }
        if (open) out.append("</strong>");
        return out.toString();
    }

    private static Builder b(String subject, String preheader, String heading) { return new Builder(subject, preheader, heading); }

    // ---------------------------------------------------------------- the emails

    public static Mail verifyEmail(String name, String link, int minutes) {
        return b("Confirm your email for Tenemsa", "One click to finish setting up your account.", "Confirm your email address")
            .p("Hi **" + first(name) + "**,")
            .p("Thanks for signing up. Please confirm this is your email address so we can keep your account safe and reach you when it matters.")
            .button("Confirm my email", link)
            .small("This link works for " + minutes + " minutes. If you didn't create a Tenemsa account, you can ignore this message.")
            .footer("You received this because someone used this address to sign up for Tenemsa.").build();
    }

    public static Mail welcomeOwner(String name, String company, String slug, String email, String signInUrl) {
        return b("Your " + company + " workspace is ready", "Here is how to get your team started.", "Welcome to Tenemsa")
            .p("Hi **" + first(name) + "**,")
            .p("Your email is confirmed and **" + company + "** is ready to use. You are the owner of this workspace.")
            .details(new String[][]{{"Workspace name", slug}, {"Your sign-in email", email}})
            .button("Sign in to Tenemsa", signInUrl)
            .steps("Good first steps", List.of(
                "Add your people under **Employees**, or invite them by email from **Invitations**.",
                "Set up **Teams** and create your first **Project**.",
                "Check **Settings** to choose notifications and your theme."))
            .small("Keep your workspace name handy: your team enters it on the sign-in page.")
            .footer("You received this because you created the workspace " + company + ".").build();
    }

    /** Sent when an admin adds a person. No password is ever included. */
    public static Mail accountSetup(String name, String company, String slug, String email, String role, String addedBy, String link, int days) {
        return b(addedBy + " added you to " + company, "Set your password to get started.", "Welcome to " + company)
            .p("Hi **" + first(name) + "**,")
            .p("**" + addedBy + "** has added you to **" + company + "** on Tenemsa as **" + nice(role) + "**. Set your own password to activate your account.")
            .button("Set my password", link)
            .details(new String[][]{{"Workspace name", slug}, {"Sign-in email", email}, {"Your role", nice(role)}})
            .steps("What happens next", List.of(
                "Choose a password with the button above. This also confirms your email.",
                "Sign in using the workspace name and your email.",
                "Complete your profile and look through your dashboard."))
            .small("The password link works for " + days + " days. If you weren't expecting this, you can ignore the message and nothing will happen.")
            .footer("You received this because " + addedBy + " added this address to " + company + ".").build();
    }

    public static Mail invitation(String name, String company, String inviter, String role, String link, int days) {
        return b(inviter + " invited you to " + company, "Accept the invitation and choose your password.", "You're invited to " + company)
            .p("Hi **" + first(name) + "**,")
            .p("**" + inviter + "** invited you to join **" + company + "** on Tenemsa as **" + nice(role) + "**.")
            .button("Accept the invitation", link)
            .small("The invitation works for " + days + " days. If you weren't expecting it, you can ignore this email.")
            .footer("You received this because " + inviter + " invited this address to " + company + ".").build();
    }

    public static Mail welcomeAfterInvite(String name, String company, String slug, String email, String signInUrl) {
        return b("Welcome to " + company, "Your account is active.", "You're in, " + first(name))
            .p("Your account at **" + company + "** is active. Here is what you need to sign in:")
            .details(new String[][]{{"Workspace name", slug}, {"Sign-in email", email}})
            .button("Open Tenemsa", signInUrl)
            .footer("You received this because you accepted an invitation to " + company + ".").build();
    }

    public static Mail passwordReset(String name, String company, String link, int minutes) {
        return b("Reset your Tenemsa password", "Use this link to choose a new password.", "Reset your password")
            .p("Hi **" + first(name) + "**,")
            .p("We received a request to reset the password for your account at **" + company + "**. If that was you, choose a new password here:")
            .button("Choose a new password", link)
            .small("This link works for " + minutes + " minutes and can be used once. If you didn't ask for this, ignore the email: your password has not changed.")
            .footer("You received this because a password reset was requested for this address.").build();
    }

    public static Mail passwordChanged(String name, String company, String forgotUrl) {
        return b("Your Tenemsa password was changed", "If this wasn't you, act now.", "Your password was changed")
            .p("Hi **" + first(name) + "**,")
            .p("The password for your **" + company + "** account was just changed, and you were signed out on your other devices.")
            .small("If you made this change, there is nothing more to do. If you did not, reset your password straight away:")
            .button("Reset my password", forgotUrl)
            .footer("This is a security notice. You can't turn it off.").build();
    }

    public static Mail notification(String name, String message, String openUrl) {
        String one = message.replaceAll("[\\r\\n]+", " ");
        return b(one.length() > 70 ? one.substring(0, 67) + "..." : one, one, "You have a new notification")
            .p("Hi **" + first(name) + "**,")
            .p(message)
            .button("Open Tenemsa", openUrl)
            .small("You can turn these emails off on your Notifications page.")
            .footer("You received this because email notifications are on for your account.").build();
    }

    public static Mail announcement(String name, String company, String sender, String subject, String body, String openUrl) {
        return b(subject, "A message from " + company, subject)
            .p("Hi **" + first(name) + "**,")
            .block(body)
            .small("Sent by **" + sender + "** at **" + company + "**.")
            .button("Open Tenemsa", openUrl)
            .footer("You received this because you are part of " + company + " on Tenemsa.").build();
    }

    /** From the Tenemsa team to workspace owners and admins. */
    public static Mail platformNotice(String name, String title, String message, String severity, String openUrl) {
        String tag = "CRITICAL".equals(severity) ? "Important: " : "WARNING".equals(severity) ? "Heads up: " : "";
        return b(tag + title, title, title)
            .p("Hi **" + first(name) + "**,")
            .block(message)
            .button("Open Tenemsa", openUrl)
            .footer("You received this because you manage a workspace on Tenemsa. This notice comes from the Tenemsa team.").build();
    }

    private static String first(String name) { return name == null || name.isBlank() ? "there" : name.trim(); }

    static String nice(String role) {
        if (role == null || role.isBlank()) return "member";
        String r = role.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(r.charAt(0)) + r.substring(1);
    }
}
