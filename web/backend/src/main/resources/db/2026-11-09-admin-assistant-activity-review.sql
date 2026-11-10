SET @review_kind_migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='admin_assistant_review' AND column_name='item_kind'),
    'DO 0', 'ALTER TABLE admin_assistant_review ADD COLUMN item_kind VARCHAR(16) NOT NULL DEFAULT ''idle''');
PREPARE review_kind_stmt FROM @review_kind_migration;
EXECUTE review_kind_stmt;
DEALLOCATE PREPARE review_kind_stmt;

SET @activity_revision_migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='activity' AND column_name='audit_revision'),
    'DO 0', 'ALTER TABLE activity ADD COLUMN audit_revision CHAR(36) DEFAULT NULL');
PREPARE activity_revision_stmt FROM @activity_revision_migration;
EXECUTE activity_revision_stmt;
DEALLOCATE PREPARE activity_revision_stmt;
