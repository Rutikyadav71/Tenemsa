-- Plan limits, company announcements (bulk email) and platform announcements. See docs/announcements.md.
ALTER TABLE companies ADD COLUMN plan VARCHAR(30) NOT NULL DEFAULT 'FREE';
ALTER TABLE companies ADD COLUMN max_users INT;
ALTER TABLE companies ADD COLUMN ai_daily_limit INT;

CREATE TABLE announcements (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT NOT NULL,
    sent_by_emp_id  INT,
    sent_by_name    VARCHAR(150) NOT NULL,
    subject         VARCHAR(200) NOT NULL,
    body            TEXT NOT NULL,
    audience_label  VARCHAR(250) NOT NULL,
    recipients      INT NOT NULL,
    send_email      BOOLEAN NOT NULL,
    emails_sent     INT NOT NULL DEFAULT 0,
    emails_failed   INT NOT NULL DEFAULT 0,
    status          VARCHAR(12) NOT NULL,
    created_at      DATETIME(6) NOT NULL
);
CREATE INDEX idx_announcements_company ON announcements (company_id, created_at);

CREATE TABLE platform_announcements (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(150) NOT NULL,
    message         TEXT NOT NULL,
    severity        VARCHAR(10) NOT NULL,
    audience        VARCHAR(10) NOT NULL,
    company_ids     TEXT,
    send_email      BOOLEAN NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    expires_at      DATETIME(6) NOT NULL,
    withdrawn       BOOLEAN NOT NULL DEFAULT FALSE,
    companies_count INT NOT NULL DEFAULT 0,
    emails_sent     INT NOT NULL DEFAULT 0
);
