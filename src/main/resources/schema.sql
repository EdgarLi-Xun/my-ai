CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    default_key_id BIGINT,
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
