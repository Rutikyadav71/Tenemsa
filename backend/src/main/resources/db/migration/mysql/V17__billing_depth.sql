-- Phase 10 deepening: trials, scheduled plan changes, token top-ups and company billing details.
ALTER TABLE plans ADD COLUMN trial_days INT NOT NULL DEFAULT 0;
UPDATE plans SET trial_days = 14 WHERE code IN ('STARTER', 'BUSINESS');

ALTER TABLE subscriptions ADD COLUMN pending_plan_code VARCHAR(30);
ALTER TABLE subscriptions ADD COLUMN trial BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE topup_packs (
    code        VARCHAR(30)  PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    tokens      BIGINT       NOT NULL,
    price_paise INT          NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);
-- Placeholder prices; edit them in the platform console.
INSERT INTO topup_packs (code, name, tokens, price_paise, sort_order) VALUES
    ('SMALL',  'Small pack',   250000,   9900, 1),
    ('MEDIUM', 'Medium pack', 1000000,  34900, 2),
    ('LARGE',  'Large pack',  5000000, 149900, 3);

CREATE TABLE token_topups (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT       NOT NULL,
    pack_code            VARCHAR(30)  NOT NULL,
    tokens               BIGINT       NOT NULL,
    amount_paise         INT          NOT NULL,
    period               VARCHAR(7)   NOT NULL,
    razorpay_order_id    VARCHAR(60)  NOT NULL UNIQUE,
    razorpay_payment_id  VARCHAR(60),
    status               VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    created_at           DATETIME(6)  NOT NULL
);
CREATE INDEX idx_token_topups_company ON token_topups (company_id, period, status);

CREATE TABLE billing_details (
    company_id     BIGINT       PRIMARY KEY,
    legal_name     VARCHAR(200),
    gstin          VARCHAR(15),
    address        VARCHAR(300),
    city           VARCHAR(100),
    state          VARCHAR(100),
    pincode        VARCHAR(12),
    billing_email  VARCHAR(200),
    updated_at     DATETIME(6)  NOT NULL
);
