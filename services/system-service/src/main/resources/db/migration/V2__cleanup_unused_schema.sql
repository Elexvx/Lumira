-- Remove schema elements that were kept in the squashed baseline but have no runtime usage.

DROP TABLE IF EXISTS `ai_knowledge_retrieval_log`;

SET @sys_user_tenant_joined_at_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_user_tenant'
    AND column_name = 'joined_at'
);

SET @drop_sys_user_tenant_joined_at_sql := IF(
  @sys_user_tenant_joined_at_exists > 0,
  'ALTER TABLE `sys_user_tenant` DROP COLUMN `joined_at`',
  'SELECT 1'
);

PREPARE drop_sys_user_tenant_joined_at_stmt FROM @drop_sys_user_tenant_joined_at_sql;
EXECUTE drop_sys_user_tenant_joined_at_stmt;
DEALLOCATE PREPARE drop_sys_user_tenant_joined_at_stmt;
