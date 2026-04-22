SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'msg_notice'
          AND column_name = 'target_role_id'
    ),
    'SELECT 1',
    'ALTER TABLE msg_notice ADD COLUMN target_role_id BIGINT DEFAULT NULL AFTER target_user_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'msg_notice'
          AND index_name = 'idx_msg_notice_tenant_target_role_created'
    ),
    'SELECT 1',
    'ALTER TABLE msg_notice ADD KEY idx_msg_notice_tenant_target_role_created (tenant_id, target_role_id, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
