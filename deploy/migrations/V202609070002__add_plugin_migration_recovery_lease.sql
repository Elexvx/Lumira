-- lumira:owner=plugin
-- lumira:migration-phase=expand
-- lumira:rollback=application-only
-- lumira:compatible-readers=202609070001..202609079999
-- lumira:cleanup-after=two-stable-releases

ALTER TABLE `plugin_migration_execution_log`
  ADD COLUMN `lease_until` datetime DEFAULT NULL AFTER `fence_token`;

CREATE INDEX `idx_plugin_migration_execution_lease`
  ON `plugin_migration_execution_log` (`status`, `lease_until`);
