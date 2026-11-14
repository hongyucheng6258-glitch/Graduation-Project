SET @migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'chat_conversation' AND column_name = 'group_owner_id'),
    'DO 0', 'ALTER TABLE chat_conversation ADD COLUMN group_owner_id BIGINT NULL');
PREPARE social_stmt FROM @migration;
EXECUTE social_stmt;
DEALLOCATE PREPARE social_stmt;

SET @migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'chat_conversation_member' AND column_name = 'pinned'),
    'DO 0', 'ALTER TABLE chat_conversation_member ADD COLUMN pinned TINYINT NOT NULL DEFAULT 0, ADD COLUMN joined_after_message_id BIGINT NULL');
PREPARE social_stmt FROM @migration;
EXECUTE social_stmt;
DEALLOCATE PREPARE social_stmt;

UPDATE chat_conversation c
JOIN (
    SELECT m.conversation_id, m.user_id
    FROM chat_conversation_member m
    JOIN (SELECT conversation_id, MIN(id) AS id FROM chat_conversation_member GROUP BY conversation_id) first_member
      ON first_member.id = m.id
) owner ON owner.conversation_id = c.id
SET c.group_owner_id = owner.user_id
WHERE c.group_name IS NOT NULL AND c.group_owner_id IS NULL;

CREATE TABLE IF NOT EXISTS chat_group_invite (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    invitee_id BIGINT NOT NULL,
    inviter_id BIGINT NOT NULL,
    UNIQUE KEY uk_chat_group_invite (conversation_id, invitee_id),
    KEY idx_group_invitee (invitee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
