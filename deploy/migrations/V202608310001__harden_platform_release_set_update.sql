-- lumira:owner=platform
-- lumira:migration-phase=expand
-- lumira:rollback=application-only
-- lumira:compatible-readers=202607140001..202608319999
-- lumira:cleanup-after=two-stable-releases
-- Release Set audit fields are nullable so old and new application slots can
-- read and write platform_update_task throughout the blue-green overlap.

SET @schema_name = DATABASE();

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='release_id'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN release_id varchar(128) NULL AFTER preflight_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='signature_key_id'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN signature_key_id varchar(128) NULL AFTER manifest_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='operation_epoch'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN operation_epoch bigint NULL AFTER signature_key_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='rollback_expires_at'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN rollback_expires_at datetime NULL AFTER operation_epoch'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='maintenance_mode'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN maintenance_mode varchar(32) NULL AFTER rollback_expires_at'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@schema_name AND table_name='platform_update_task' AND column_name='maintenance_reason'),
  'SELECT 1',
  'ALTER TABLE platform_update_task ADD COLUMN maintenance_reason varchar(512) NULL AFTER maintenance_mode'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
