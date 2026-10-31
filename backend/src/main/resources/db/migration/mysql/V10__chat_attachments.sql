-- Phase 8d: files sent in chat are stored in the database (like documents), not on an external service.
ALTER TABLE chat_messages ADD COLUMN attachment_id   BIGINT;
ALTER TABLE chat_messages ADD COLUMN attachment_name VARCHAR(255);
ALTER TABLE chat_messages ADD COLUMN attachment_type VARCHAR(120);
ALTER TABLE chat_messages ADD COLUMN attachment_size BIGINT;

CREATE TABLE chat_attachments (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT NOT NULL,
    uploader_tag  VARCHAR(10) NOT NULL,
    uploader_id   INT NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(120) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    message_id    BIGINT
);
CREATE INDEX idx_chat_att_company ON chat_attachments (company_id);

CREATE TABLE chat_attachment_files (
    attachment_id  BIGINT PRIMARY KEY,
    content        LONGBLOB NOT NULL
);
