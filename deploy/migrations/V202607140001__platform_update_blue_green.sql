-- Expand-only migration. This file must remain compatible with the previous
-- application image so traffic can be rolled back without restoring the DB.

SET @schema_name = DATABASE();

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='strategy'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN strategy varchar(64) NULL AFTER status'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='phase'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN phase varchar(64) NULL AFTER strategy'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='progress_percent'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN progress_percent int NULL AFTER phase'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='active_slot'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN active_slot varchar(16) NULL AFTER progress_percent'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='target_slot'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN target_slot varchar(16) NULL AFTER active_slot'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='preflight_id'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN preflight_id varchar(64) NULL AFTER target_slot'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='manifest_hash'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN manifest_hash char(64) NULL AFTER preflight_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='rollback_of_task_id'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN rollback_of_task_id bigint NULL AFTER manifest_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='active_key'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN active_key varchar(32) NULL AFTER rollback_of_task_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@schema_name AND table_name='platform_update_task' AND index_name='uk_platform_update_task_active_key'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD UNIQUE KEY uk_platform_update_task_active_key (active_key)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
