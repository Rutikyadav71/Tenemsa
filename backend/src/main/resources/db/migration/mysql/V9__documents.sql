-- Phase 8b: document storage. Metadata and file bytes are separate tables so lists never load file contents.
CREATE TABLE documents (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT NOT NULL,
    title               VARCHAR(200) NOT NULL,
    category            VARCHAR(20) NOT NULL,
    visibility          VARCHAR(20) NOT NULL,
    owner_emp_id        INT,
    file_name           VARCHAR(255) NOT NULL,
    content_type        VARCHAR(120) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    uploaded_by_emp_id  INT,
    kb_document_id      BIGINT,
    created_at          DATETIME(6) NOT NULL
);
CREATE INDEX idx_documents_company ON documents (company_id, created_at);

CREATE TABLE document_files (
    document_id  BIGINT PRIMARY KEY,
    content      LONGBLOB NOT NULL
);
