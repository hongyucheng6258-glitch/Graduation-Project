SET @temporary_password_migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='user' AND column_name='temporary_password_expires_at'),
    'DO 0', 'ALTER TABLE `user` ADD COLUMN temporary_password_expires_at DATETIME DEFAULT NULL');
PREPARE temporary_password_stmt FROM @temporary_password_migration;
EXECUTE temporary_password_stmt;
DEALLOCATE PREPARE temporary_password_stmt;
