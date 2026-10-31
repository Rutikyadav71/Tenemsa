-- Phase 8a: who decided a leave request, and per-user notifications with email preferences.
ALTER TABLE leaves ADD COLUMN decided_by_emp_id INT NULL;
ALTER TABLE leaves ADD COLUMN decided_at DATETIME(6) NULL;
ALTER TABLE leaves ADD COLUMN decision_note VARCHAR(300) NULL;

-- recipient_emp_id NULL = an older company-wide notification (shown only to people who approve leave).
ALTER TABLE notifications ADD COLUMN recipient_emp_id INT NULL;
ALTER TABLE notifications ADD COLUMN type VARCHAR(40) NULL;
CREATE INDEX idx_notifications_recipient ON notifications (company_id, recipient_emp_id, created_at);

CREATE TABLE notification_preferences (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT NOT NULL,
    emp_id         INT NOT NULL,
    email_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_notification_preferences UNIQUE (company_id, emp_id)
);
