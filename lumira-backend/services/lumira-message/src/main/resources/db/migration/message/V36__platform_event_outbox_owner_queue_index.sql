SET @index_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'platform_event_outbox'
    AND index_name = 'idx_platform_event_outbox_owner_queue'
);

SET @ddl := IF(
  @index_exists = 0,
  'ALTER TABLE platform_event_outbox ADD INDEX idx_platform_event_outbox_owner_queue (source_type, created_at, id, dispatch_status, next_retry_at, deleted)',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
