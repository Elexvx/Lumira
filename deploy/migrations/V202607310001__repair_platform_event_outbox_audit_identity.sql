-- Repair legacy platform event outbox schemas that predate UUID audit identities.

SET @platform_event_created_by_uuid_exists = (
    SELECT COUNT(1)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'platform_event_outbox'
       AND column_name = 'created_by_uuid'
);
SET @platform_event_created_by_uuid_sql = IF(
    @platform_event_created_by_uuid_exists = 0,
    'ALTER TABLE `platform_event_outbox` ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL AFTER `created_by`',
    'SELECT 1'
);
PREPARE platform_event_created_by_uuid_statement FROM @platform_event_created_by_uuid_sql;
EXECUTE platform_event_created_by_uuid_statement;
DEALLOCATE PREPARE platform_event_created_by_uuid_statement;

SET @platform_event_updated_by_uuid_exists = (
    SELECT COUNT(1)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'platform_event_outbox'
       AND column_name = 'updated_by_uuid'
);
SET @platform_event_updated_by_uuid_sql = IF(
    @platform_event_updated_by_uuid_exists = 0,
    'ALTER TABLE `platform_event_outbox` ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL AFTER `updated_by`',
    'SELECT 1'
);
PREPARE platform_event_updated_by_uuid_statement FROM @platform_event_updated_by_uuid_sql;
EXECUTE platform_event_updated_by_uuid_statement;
DEALLOCATE PREPARE platform_event_updated_by_uuid_statement;

SET @platform_event_creator_uuid_index_exists = (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'platform_event_outbox'
       AND index_name = 'idx_platform_event_outbox_creator_uuid'
);
SET @platform_event_creator_uuid_index_sql = IF(
    @platform_event_creator_uuid_index_exists = 0,
    'ALTER TABLE `platform_event_outbox` ADD INDEX `idx_platform_event_outbox_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)',
    'SELECT 1'
);
PREPARE platform_event_creator_uuid_index_statement FROM @platform_event_creator_uuid_index_sql;
EXECUTE platform_event_creator_uuid_index_statement;
DEALLOCATE PREPARE platform_event_creator_uuid_index_statement;
