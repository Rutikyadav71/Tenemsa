-- Phase 10b: connected apps. Tokens are stored encrypted (AES-GCM) and never returned by any API.
CREATE TABLE company_apps (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT       NOT NULL,
    provider    VARCHAR(30)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT uq_company_apps UNIQUE (company_id, provider)
);

CREATE TABLE connections (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    provider            VARCHAR(30)  NOT NULL,
    account_label       VARCHAR(200),
    access_token_enc    TEXT         NOT NULL,
    refresh_token_enc   TEXT,
    expires_at          DATETIME(6),
    scopes              VARCHAR(500),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(6)  NOT NULL,
    last_used_at        DATETIME(6),
    CONSTRAINT uq_connections UNIQUE (company_id, user_id, provider)
);
CREATE INDEX idx_connections_company ON connections (company_id, provider);
