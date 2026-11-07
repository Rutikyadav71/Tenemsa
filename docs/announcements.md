# Announcements, notices and plan limits

Three related features, added with Flyway `V14__announcements_and_limits.sql`.

## 1. Company announcements (Announcements page, `/admin/announcements`)

Owner, Admin and HR (permission `announcement:send`) can send a message to people in **their own company**.

- **Audience:** everyone, certain roles, certain departments, or chosen people. A live count shows how many people it reaches before you send.
- **Delivery:** always an in-app notification; plus an email if "Also send by email" is ticked. Frozen people and people without an email are skipped.
- **Writer link:** AI Writer has a **Send to people** button that opens this page with the draft filled in. Nothing is sent until you confirm.
- **History:** every announcement is listed with audience, sender, and how many emails went out or failed.
- **Safety:** up to 1,000 people per announcement and 10 announcements per company per 24 hours (`app.announcements.per-day`). Text is plain and escaped. Each send is written to the audit log.

API: `POST /api/announcements/preview`, `POST /api/announcements`, `GET /api/announcements`.

## 2. Notices from Tenemsa to companies (platform console)

In the console: **Announcements** → write a title and message, choose importance (information, warning, critical), how long to show it, all active companies or chosen ones, and optionally email the owners and admins.

- Everyone in the target companies sees a banner at the top of every page. A person can dismiss it (remembered only in that browser).
- You can **withdraw** a notice at any time; it expires by itself after the chosen days.
- Creating and withdrawing are in the platform audit log.

Company side reads `GET /api/announcements/platform`; the console uses `/api/platform/announcements` (list, create, `/{id}/withdraw`).

## 3. Plan limits (platform console → company → Plan and limits)

| Setting | Meaning |
|---|---|
| Plan | A label: Free, Starter, Business, Enterprise (for your own bookkeeping) |
| Most people allowed | New accounts are refused at the limit (adding an employee, owner adding an admin or HR, inviting, accepting an invitation). People already in are never removed. Empty = no limit |
| AI requests per day | Overrides the platform default for the whole company. Empty = default |

The message people see is: "Your plan allows up to N people and you have reached that. Contact Tenemsa to raise the limit." Every change is audited.

## Not built

Billing and payments, automatic plan upgrades, scheduled announcements, per-person unsubscribe from announcement email.

## Tests

`AnnouncementTest` covers preview and send, history, employees refused, validation, company isolation, platform notices reaching only their targets, and plan limits. Run `mvn test`.
