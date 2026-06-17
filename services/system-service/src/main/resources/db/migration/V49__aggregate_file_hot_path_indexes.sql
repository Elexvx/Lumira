SET @processing_task_queue_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'file_processing_task'
    AND index_name = 'idx_file_processing_task_queue'
);

SET @processing_task_tenant_created_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'file_processing_task'
    AND index_name = 'idx_file_processing_task_tenant_created'
);

SET @storage_object_bucket_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'file_object'
    AND index_name = 'idx_file_object_tenant_deleted_bucket'
);

SET @storage_object_created_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'file_object'
    AND index_name = 'idx_file_object_tenant_deleted_created_id'
);

SET @storage_space_tenant_default_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'file_storage_space'
    AND index_name = 'idx_file_storage_space_tenant_deleted_default_id'
);

SET @ddl := IF(
  @processing_task_queue_exists = 0,
  'ALTER TABLE file_processing_task ADD INDEX idx_file_processing_task_queue (deleted, status, next_retry_at, priority, created_at, id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  @processing_task_tenant_created_exists = 0,
  'ALTER TABLE file_processing_task ADD INDEX idx_file_processing_task_tenant_created (tenant_id, deleted, status, created_at, id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  @storage_object_bucket_exists = 0,
  'ALTER TABLE file_object ADD INDEX idx_file_object_tenant_deleted_bucket (tenant_id, deleted, bucket)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  @storage_object_created_exists = 0,
  'ALTER TABLE file_object ADD INDEX idx_file_object_tenant_deleted_created_id (tenant_id, deleted, created_at, id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  @storage_space_tenant_default_exists = 0,
  'ALTER TABLE file_storage_space ADD INDEX idx_file_storage_space_tenant_deleted_default_id (tenant_id, deleted, default_flag, id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
