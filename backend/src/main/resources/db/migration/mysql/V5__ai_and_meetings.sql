-- Phase 6a: AI usage log and AI meeting summaries.
CREATE TABLE ai_request_logs (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT NOT NULL,
    emp_id          INT,
    feature         VARCHAR(40) NOT NULL,
    prompt_version  VARCHAR(40),
    provider        VARCHAR(40),
    model           VARCHAR(80),
    status          VARCHAR(20) NOT NULL,
    latency_ms      BIGINT,
    input_tokens    INT,
    output_tokens   INT,
    error_code      VARCHAR(80),
    created_at      DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_ai_logs_company_created ON ai_request_logs (company_id, created_at);
CREATE INDEX idx_ai_logs_company_emp_created ON ai_request_logs (company_id, emp_id, created_at);

CREATE TABLE meeting_summaries (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT NOT NULL,
    created_by_emp_id   INT,
    title               VARCHAR(200) NOT NULL,
    meeting_date        DATE,
    source_ref          VARCHAR(300),
    source_text         MEDIUMTEXT,
    summary             TEXT,
    status              VARCHAR(20) NOT NULL,
    project_id          BIGINT,
    model               VARCHAR(80),
    prompt_version      VARCHAR(40),
    ai_request_log_id   BIGINT,
    approved_by_emp_id  INT,
    approved_at         DATETIME(6),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_meetings_company_created ON meeting_summaries (company_id, created_at);

CREATE TABLE meeting_points (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT NOT NULL,
    meeting_id  BIGINT NOT NULL,
    type        VARCHAR(20) NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    sort_order  INT NOT NULL,
    CONSTRAINT fk_meeting_points_meeting FOREIGN KEY (meeting_id) REFERENCES meeting_summaries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_meeting_points_meeting ON meeting_points (company_id, meeting_id);

CREATE TABLE meeting_action_items (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT NOT NULL,
    meeting_id    BIGINT NOT NULL,
    title         VARCHAR(200) NOT NULL,
    description   VARCHAR(2000),
    owner_name    VARCHAR(150),
    owner_emp_id  INT,
    due_date      DATE,
    priority      VARCHAR(20) NOT NULL,
    sort_order    INT NOT NULL,
    issue_id      BIGINT,
    issue_key     VARCHAR(40),
    converted_at  DATETIME(6),
    CONSTRAINT fk_meeting_actions_meeting FOREIGN KEY (meeting_id) REFERENCES meeting_summaries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_meeting_actions_meeting ON meeting_action_items (company_id, meeting_id);
