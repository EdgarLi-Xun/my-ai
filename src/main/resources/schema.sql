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

-- 软删时间（NULL = 未软删；与 is_orphaned 区分：orphan 是被覆盖作废，deleted_at 是用户主动删除）
-- soft-delete timestamp (NULL = not deleted; distinct from is_orphaned which marks superseded rows).
ALTER TABLE message ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- ADR 0004：可观测性
-- user.role：RBAC 角色（USER / ADMIN），env var MYAI_ADMIN_EMAILS 命中设 ADMIN。
ALTER TABLE user ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE user ADD CONSTRAINT IF NOT EXISTS user_role_check
    CHECK (role IN ('USER', 'ADMIN'));

-- ai_call_log：每次 AI 调用留痕。tokens 允许 NULL（Ollama 等不一定返回 usage）。
CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT,
    message_id BIGINT,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms BIGINT,
    input_tokens INTEGER,
    output_tokens INTEGER,
    error_message TEXT,
    trace_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_call_log_user
        FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_call_log_user_id ON ai_call_log(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_call_log_created_at ON ai_call_log(created_at);
CREATE INDEX IF NOT EXISTS idx_ai_call_log_trace_id ON ai_call_log(trace_id);

-- audit_log：业务动作留痕（UserApiKey / Conversation 增删改等）。user_id 允许 NULL（系统后台）。
-- 默认查询 WHERE deleted_at IS NULL；30 天后 LogCleanupTask 物理删。
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id BIGINT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_audit_log_user
        FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_deleted_at ON audit_log(deleted_at);
