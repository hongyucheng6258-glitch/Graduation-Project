SET @idle_audit_revision_migration = IF(EXISTS(
    SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='idle_item' AND column_name='audit_revision'),
    'DO 0', 'ALTER TABLE idle_item ADD COLUMN audit_revision CHAR(36) DEFAULT NULL');
PREPARE idle_audit_revision_stmt FROM @idle_audit_revision_migration;
EXECUTE idle_audit_revision_stmt;
DEALLOCATE PREPARE idle_audit_revision_stmt;
