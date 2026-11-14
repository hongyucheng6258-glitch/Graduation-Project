SET @group_chat_migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'chat_conversation' AND column_name = 'group_name'),
    'DO 0',
    'ALTER TABLE chat_conversation MODIFY user1_id BIGINT NULL, MODIFY user2_id BIGINT NULL, ADD COLUMN group_name VARCHAR(32) NULL');
PREPARE group_chat_stmt FROM @group_chat_migration;
EXECUTE group_chat_stmt;
DEALLOCATE PREPARE group_chat_stmt;

SET @group_receiver_migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'chat_message' AND column_name = 'receiver_id' AND is_nullable = 'NO'),
    'ALTER TABLE chat_message MODIFY receiver_id BIGINT NULL',
    'DO 0');
PREPARE group_receiver_stmt FROM @group_receiver_migration;
EXECUTE group_receiver_stmt;
DEALLOCATE PREPARE group_receiver_stmt;
