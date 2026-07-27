CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    default_key_id BIGINT,
    password_hash VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE user ADD COLUMN IF NOT EXISTS default_key_id BIGINT;

CREATE TABLE IF NOT EXISTS user_api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    api_key VARCHAR(2048),
    base_url VARCHAR(500) NOT NULL,
    model_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_api_key_user
        FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_api_key_user_id ON user_api_key(user_id);

ALTER TABLE user ADD CONSTRAINT IF NOT EXISTS fk_user_default_key
    FOREIGN KEY (default_key_id) REFERENCES user_api_key(id) ON DELETE SET NULL;

ALTER TABLE user ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

ALTER TABLE user_api_key ADD COLUMN IF NOT EXISTS protocol VARCHAR(30);

-- ADR 0003：对话与消息
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    title_manually_set BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_conversation_user
        FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_conversation_user_id ON conversation(user_id);

CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    is_orphaned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_message_conversation_id ON message(conversation_id);

ALTER TABLE message ADD CONSTRAINT IF NOT EXISTS message_role_check
    CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'));
