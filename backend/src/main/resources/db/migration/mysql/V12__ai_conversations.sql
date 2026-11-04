-- Saved AI chats (HR assistant and document questions) so people can reopen a chat and continue it.
CREATE TABLE ai_conversations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    kind        VARCHAR(20) NOT NULL,
    title       VARCHAR(120) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL
);
CREATE INDEX idx_ai_conv_owner ON ai_conversations (company_id, user_id, kind, updated_at);

CREATE TABLE ai_conversation_messages (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id  BIGINT NOT NULL,
    role             VARCHAR(12) NOT NULL,
    content          TEXT NOT NULL,
    metadata         TEXT NULL,
    created_at       DATETIME(6) NOT NULL
);
CREATE INDEX idx_ai_conv_msg_conv ON ai_conversation_messages (conversation_id, id);
