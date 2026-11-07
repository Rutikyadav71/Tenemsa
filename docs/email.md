# Email

Every email is built in `mail/MailTemplates.java` (one HTML and one plain-text version each) and sent by `mail/EmailService.java`.

## What is sent

| Email | When | Notes |
|---|---|---|
| Confirm your email | Workspace owner signs up; resend; Platform console "resend owner verification" | Link valid 30 minutes |
| Workspace ready | Owner confirms the email | Workspace name, sign-in link, first steps |
| Added to a workspace | Admin, HR or Owner adds a person | **Set-your-password link**, workspace name, role, next steps. Valid 7 days. Following it also confirms the email. No password is ever emailed |
| Invitation | Invitations page | Link valid 7 days |
| Welcome after invitation | Person accepts the invitation | Workspace name and sign-in link |
| Reset password | Forgot password | Link valid 30 minutes, one use |
| Password changed | After a reset or a change in Settings | Security notice with a reset link if it was not them |
| Notification copy | When a person has email notifications on | Escaped plain text |

Settings (`backend/.env`): `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_HOST`, `MAIL_PORT`, `FRONTEND_URL`, and optionally `EMAIL_FROM`, `EMAIL_FROM_NAME`, `EMAIL_REPLY_TO`, `ACCOUNT_SETUP_EXPIRATION_DAYS`. `APP_EMAIL_ENABLED=false` logs instead of sending.

## Why mail lands in spam, and the real fix

The message body matters, but most of the decision is made from **who sent it**:

1. **A Gmail address sending as "your product"** is treated as suspicious, because Gmail cannot prove the mail is from a business. Mail from a Gmail account to Gmail users is judged hardest.
2. **No SPF, DKIM or DMARC for your own domain.** These three DNS records tell receivers the mail is genuinely yours.
3. **Links to `localhost`** or a bare IP. Fine for testing, flagged in real use. The backend warns at start-up when `FRONTEND_URL` contains `localhost`.
4. **A brand-new sender with no history.** Reputation builds over weeks.

What the code now does: sends a text and an HTML version, escapes all content, uses a clear sender name, plain subjects, no remote images, no password in the message, and one clear action per email.

What only you can do, in order:

1. Buy a domain (for example `tenemsa.com`) and deploy the site there so `FRONTEND_URL` is real.
2. Use a transactional mail service (Brevo, Amazon SES, Resend, Postmark, Mailgun all have free or cheap tiers). They give SMTP settings; put them in `MAIL_HOST`, `MAIL_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and set `EMAIL_FROM=no-reply@yourdomain.com`.
3. Add the SPF and DKIM records the service shows you, and a DMARC record (`v=DMARC1; p=none; rua=mailto:you@yourdomain.com`).
4. While testing with Gmail, open the first emails and press **Report not spam** a few times on each test account; later mail goes to the inbox.

## Checking it

Run the backend with `APP_EMAIL_ENABLED=false` to see the would-be sends in the log, or send a real one to yourself. `EmailServiceTest` checks the structure of each message without a mail server.

## People flow, start to finish

**Company owner:** signs up → "Confirm your email" → confirms → "Your workspace is ready" (workspace name, sign-in link, first steps).

**Person added by an admin, HR or owner:** one email, "Welcome to <company>", with a **Set my password** link (7 days), the workspace name, role and next steps. Setting the password also confirms the email, then they sign in. If the link expires, an admin can add them again or they can use Forgot password. A "password changed" notice follows any later change.

**Person invited:** "You're invited" → accepts and sets a password → "Welcome" email with the workspace name.

**Forgot password:** reset email (30 minutes, one use) → new password → "Your password was changed" notice; all other devices are signed out.

**Announcements:** company announcements and platform notices (see [announcements.md](announcements.md)) use the same layout. Platform notices are emailed only to owners and admins.
