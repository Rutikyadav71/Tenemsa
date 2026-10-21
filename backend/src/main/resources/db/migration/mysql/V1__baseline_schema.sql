-- Tenemsa baseline schema (MySQL) as of Phase 4d.
--
-- Fresh databases run this file. EXISTING databases (created earlier by Hibernate's
-- ddl-auto=update) are baselined at version 1 by Flyway, so this file is skipped for
-- them and only V2 and later run (see spring.flyway.baseline-on-migrate).
--
-- Rule from here on: every schema change is a new V<n>__*.sql file, never an edit to
-- an old one. Every tenant-owned table carries company_id.

CREATE TABLE companies (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_companies_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT NOT NULL,
    emp_id             INT,
    name               VARCHAR(150) NOT NULL,
    email              VARCHAR(255) NOT NULL,
    password           VARCHAR(255),
    auth_provider      VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    email_verified     BIT(1) NOT NULL DEFAULT b'0',
    role               VARCHAR(20) NOT NULL,
    department         VARCHAR(100),
    salary             NUMERIC(12, 2),
    profile_image_url  VARCHAR(500),
    frozen             BIT(1) NOT NULL DEFAULT b'0',
    primary_owner      BIT(1) NOT NULL DEFAULT b'0',
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    created_by         BIGINT,
    updated_by         BIGINT,
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT uk_users_company_email UNIQUE (company_id, email),
    CONSTRAINT uk_users_company_empid UNIQUE (company_id, emp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_users_company_id ON users (company_id);
CREATE INDEX idx_users_email ON users (email);

CREATE TABLE refresh_tokens (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT NOT NULL,
    token_hash    VARCHAR(64) NOT NULL,
    device_label  VARCHAR(255),
    expires_at    DATETIME(6) NOT NULL,
    revoked       BIT(1) NOT NULL DEFAULT b'0',
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

CREATE TABLE verification_tokens (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    purpose     VARCHAR(30) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used        BIT(1) NOT NULL DEFAULT b'0',
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_verification_tokens_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_verification_tokens_token_hash ON verification_tokens (token_hash);

CREATE TABLE leaves (
    id          INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT NOT NULL,
    emp_id      INT NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    reason      VARCHAR(255),
    status      VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_leaves_company ON leaves (company_id);

CREATE TABLE teams (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(255),
    project_name   VARCHAR(255),
    created_date   DATE,
    deadline       DATE,
    status         VARCHAR(255),
    team_lead_id   INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_teams_company ON teams (company_id);

CREATE TABLE team_members (
    team_id  BIGINT NOT NULL,
    emp_id   INT,
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attendance (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT NOT NULL,
    emp_id      INT NOT NULL,
    date        DATE NOT NULL,
    check_in    TIME,
    check_out   TIME,
    status      VARCHAR(255) NOT NULL,
    work_hours  DOUBLE,
    notes       VARCHAR(255),
    marked_by   VARCHAR(255),
    CONSTRAINT uk_attendance_company_emp_date UNIQUE (company_id, emp_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_attendance_company ON attendance (company_id);

CREATE TABLE chat_messages (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT NOT NULL,
    sender_id      INT NOT NULL,
    sender_role    VARCHAR(10),
    receiver_id    INT NOT NULL,
    receiver_role  VARCHAR(10),
    message        TEXT NOT NULL,
    `timestamp`    DATETIME(6) NOT NULL,
    status         VARCHAR(20),
    reaction       VARCHAR(10),
    starred        BIT(1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_chat_company ON chat_messages (company_id);

CREATE TABLE notifications (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT NOT NULL,
    message     VARCHAR(255) NOT NULL,
    is_read     BIT(1) NOT NULL DEFAULT b'0',
    created_at  DATETIME(6) NOT NULL,
    leave_id    BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_notifications_company ON notifications (company_id);

CREATE TABLE invitations (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT NOT NULL,
    email          VARCHAR(255) NOT NULL,
    name           VARCHAR(150) NOT NULL,
    role           VARCHAR(30) NOT NULL,
    department     VARCHAR(100),
    token_hash     VARCHAR(64) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    invited_by_id  BIGINT,
    expires_at     DATETIME(6) NOT NULL,
    accepted_at    DATETIME(6),
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT uk_invitations_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_invitations_company ON invitations (company_id);
CREATE INDEX idx_invitations_token_hash ON invitations (token_hash);

CREATE TABLE audit_logs (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT NOT NULL,
    actor_id     BIGINT,
    actor_name   VARCHAR(150),
    action       VARCHAR(40) NOT NULL,
    entity_type  VARCHAR(40),
    entity_id    VARCHAR(64),
    details      VARCHAR(500),
    ip_address   VARCHAR(64),
    created_at   DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_audit_company_created ON audit_logs (company_id, created_at);
CREATE INDEX idx_audit_company_action ON audit_logs (company_id, action);
