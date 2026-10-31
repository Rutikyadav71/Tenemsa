-- Phase 7: company knowledge base (RAG). Vectors live in embedding_json on MySQL (portable scan search);
-- production PostgreSQL additionally gets a pgvector column and index (see the postgresql file).
CREATE TABLE kb_documents (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT NOT NULL,
    title              VARCHAR(200) NOT NULL,
    file_name          VARCHAR(255),
    audience           VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    error_message      VARCHAR(300),
    content_hash       VARCHAR(64) NOT NULL,
    char_count         INT NOT NULL,
    chunk_count        INT NOT NULL,
    embedding_model    VARCHAR(80),
    uploaded_by_emp_id INT,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL
);
CREATE INDEX idx_kb_documents_company ON kb_documents (company_id, created_at);
CREATE INDEX idx_kb_documents_hash ON kb_documents (company_id, content_hash);

CREATE TABLE kb_chunks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT NOT NULL,
    document_id     BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    embedding_json  LONGTEXT,
    embedding_model VARCHAR(80)
);
CREATE INDEX idx_kb_chunks_document ON kb_chunks (document_id, chunk_index);
CREATE INDEX idx_kb_chunks_company ON kb_chunks (company_id);
