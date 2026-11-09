CREATE TABLE IF NOT EXISTS agent_idle_draft (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    expect_item VARCHAR(128) NOT NULL,
    expires_at DATETIME NOT NULL,
    item_id BIGINT DEFAULT NULL,
    KEY idx_idle_draft_owner (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
