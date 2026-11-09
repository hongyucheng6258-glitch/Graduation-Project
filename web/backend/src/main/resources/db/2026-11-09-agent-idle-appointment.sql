CREATE TABLE IF NOT EXISTS agent_idle_appointment (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_title VARCHAR(64) NOT NULL,
    remark VARCHAR(200) NOT NULL,
    expires_at DATETIME NOT NULL,
    appointment_id BIGINT DEFAULT NULL,
    KEY idx_agent_idle_appointment_owner (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
