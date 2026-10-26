-- Phase 5b: sprints, plus story points, labels and sprint membership on issues.
CREATE TABLE sprints (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT NOT NULL,
    project_id          BIGINT NOT NULL,
    name                VARCHAR(100) NOT NULL,
    goal                VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    start_date          DATE,
    end_date            DATE,
    started_at          DATETIME(6),
    completed_at        DATETIME(6),
    completed_count     INT,
    carried_over_count  INT,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    CONSTRAINT fk_sprints_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_sprints_company_project ON sprints (company_id, project_id);

ALTER TABLE issues ADD COLUMN sprint_id BIGINT NULL;
ALTER TABLE issues ADD COLUMN story_points INT NULL;
ALTER TABLE issues ADD COLUMN labels VARCHAR(500) NULL;
ALTER TABLE issues ADD CONSTRAINT fk_issues_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id);
CREATE INDEX idx_issues_company_sprint ON issues (company_id, sprint_id);
