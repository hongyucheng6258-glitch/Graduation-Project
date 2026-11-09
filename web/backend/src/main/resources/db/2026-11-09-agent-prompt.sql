CREATE TABLE IF NOT EXISTS agent_prompt_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scene VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_prompt_scene (scene, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_prompt_active (
    scene VARCHAR(32) NOT NULL PRIMARY KEY,
    version_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id BIGINT DEFAULT NULL,
    prompt_version BIGINT NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    tools VARCHAR(300) NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_message_attachment (
    message_id BIGINT NOT NULL PRIMARY KEY,
    body TEXT NOT NULL,
    CONSTRAINT fk_agent_attachment_message FOREIGN KEY (message_id)
        REFERENCES ai_message(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
