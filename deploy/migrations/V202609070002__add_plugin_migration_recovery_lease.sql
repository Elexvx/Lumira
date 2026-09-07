-- lumira:owner=plugin
-- lumira:migration-phase=expand
-- lumira:rollback=application-only
-- lumira:compatible-readers=202609070001..202609079999
-- lumira:cleanup-after=two-stable-releases

SET @schema_name = DATABASE();

SET @ddl = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'plugin_migration_execution_log'
      AND column_name = 'lease_until'
  ),
  'SELECT 1',
  'ALTER TABLE plugin_migration_execution_log ADD COLUMN lease_until datetime DEFAULT NULL AFTER fence_token'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'plugin_migration_execution_log'
      AND index_name = 'idx_plugin_migration_execution_lease'
  ),
  'SELECT 1',
  'CREATE INDEX idx_plugin_migration_execution_lease ON plugin_migration_execution_log (status, lease_until)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
