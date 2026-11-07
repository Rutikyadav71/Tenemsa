-- Platform (system) administrators and company suspension. See docs/platform-admin.md.
ALTER TABLE companies ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE companies ADD COLUMN status_reason VARCHAR(300) NULL;
ALTER TABLE companies ADD COLUMN status_changed_at DATETIME(6) NULL;

-- Exactly two accounts can ever exist: slot is 1 or 2 and unique, so the database itself refuses a third.
CREATE TABLE platform_admins (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot              INT NOT NULL,
    name              VARCHAR(150) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    totp_secret_enc   VARCHAR(255) NOT NULL,
    totp_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    last_totp_step    BIGINT NULL,
    failed_attempts   INT NOT NULL DEFAULT 0,
    locked_until      DATETIME(6) NULL,
    last_login_at     DATETIME(6) NULL,
    created_at        DATETIME(6) NOT NULL,
    CONSTRAINT uk_platform_admins_slot UNIQUE (slot),
    CONSTRAINT uk_platform_admins_email UNIQUE (email),
    CONSTRAINT ck_platform_admins_slot CHECK (slot IN (1, 2))
);

CREATE TABLE platform_audit_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id      BIGINT NULL,
    admin_email   VARCHAR(255) NULL,
    action        VARCHAR(40) NOT NULL,
    company_id    BIGINT NULL,
    company_name  VARCHAR(150) NULL,
    details       VARCHAR(500) NULL,
    ip_address    VARCHAR(64) NULL,
    created_at    DATETIME(6) NOT NULL
);
CREATE INDEX idx_platform_audit_created ON platform_audit_logs (created_at);
CREATE INDEX idx_platform_audit_company ON platform_audit_logs (company_id);
