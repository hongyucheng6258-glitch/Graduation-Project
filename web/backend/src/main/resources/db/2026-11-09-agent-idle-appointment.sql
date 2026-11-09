CREATE TABLE IF NOT EXISTS agent_idle_appointment (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_title VARCHAR(64) NOT NULL,
    remark VARCHAR(200) NOT NULL,
    expires_at DATETIME NOT NULL,
    appointment_id BIGINT DEFAULT NULL,
    offer_version CHAR(64) DEFAULT NULL,
    KEY idx_agent_idle_appointment_owner (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @agent_idle_offer_migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='agent_idle_appointment' AND column_name='offer_version'),
    'DO 0', 'ALTER TABLE agent_idle_appointment ADD COLUMN offer_version CHAR(64) DEFAULT NULL');
PREPARE agent_idle_offer_stmt FROM @agent_idle_offer_migration;
EXECUTE agent_idle_offer_stmt;
DEALLOCATE PREPARE agent_idle_offer_stmt;
