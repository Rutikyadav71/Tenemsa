-- Phase 5a: project management (projects, issues, issue comments).
-- Every table carries company_id (tenant isolation, see docs/architecture.md).

CREATE TABLE projects (
    id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT NOT NULL,
    project_key        VARCHAR(10) NOT NULL,
    name               VARCHAR(150) NOT NULL,
    description        VARCHAR(1000),
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    lead_emp_id        INT,
    next_issue_number  INT NOT NULL DEFAULT 1,
    created_by         BIGINT,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    CONSTRAINT fk_projects_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT uk_projects_company_key UNIQUE (company_id, project_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_projects_company ON projects (company_id);

CREATE TABLE issues (
    id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT NOT NULL,
    project_id       BIGINT NOT NULL,
    issue_number     INT NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    type             VARCHAR(20) NOT NULL DEFAULT 'TASK',
    status           VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority         VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assignee_emp_id  INT,
    reporter_emp_id  INT,
    due_date         DATE,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    CONSTRAINT fk_issues_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uk_issues_project_number UNIQUE (project_id, issue_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_issues_company_project ON issues (company_id, project_id);
CREATE INDEX idx_issues_company_assignee ON issues (company_id, assignee_emp_id);

CREATE TABLE issue_comments (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT NOT NULL,
    issue_id      BIGINT NOT NULL,
    author_emp_id INT,
    author_name   VARCHAR(150) NOT NULL,
    body          TEXT NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_issue_comments_issue FOREIGN KEY (issue_id) REFERENCES issues (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_issue_comments_company_issue ON issue_comments (company_id, issue_id);
