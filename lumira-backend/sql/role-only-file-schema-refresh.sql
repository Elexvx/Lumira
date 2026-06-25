-- Refresh file-service tables from tenant-scoped storage to role-only/global storage.
-- Safe to re-run: helpers check existing columns and indexes before changing them.

DELIMITER $$

DROP PROCEDURE IF EXISTS role_only_file_drop_index $$
CREATE PROCEDURE role_only_file_drop_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @role_only_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` DROP INDEX `', REPLACE(p_index, '`', '``'), '`'
        );
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_file_drop_indexes_with_column $$
CREATE PROCEDURE role_only_file_drop_indexes_with_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_index VARCHAR(64);
    DECLARE index_cursor CURSOR FOR
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
          AND index_name <> 'PRIMARY';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN index_cursor;
    read_loop: LOOP
        FETCH index_cursor INTO v_index;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;
        CALL role_only_file_drop_index(p_table, v_index);
    END LOOP;
    CLOSE index_cursor;
END $$

DROP PROCEDURE IF EXISTS role_only_file_drop_column $$
CREATE PROCEDURE role_only_file_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @role_only_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` DROP COLUMN `', REPLACE(p_column, '`', '``'), '`'
        );
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_file_add_index $$
CREATE PROCEDURE role_only_file_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_sql TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @role_only_ddl = p_sql;
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_file_schema_refresh $$
CREATE PROCEDURE role_only_file_schema_refresh()
BEGIN
    CREATE TABLE IF NOT EXISTS role_only_file_backup_file_object_20260625 AS
        SELECT * FROM file_object;
    CREATE TABLE IF NOT EXISTS role_only_file_backup_file_processing_artifact_20260625 AS
        SELECT * FROM file_processing_artifact;
    CREATE TABLE IF NOT EXISTS role_only_file_backup_file_processing_task_20260625 AS
        SELECT * FROM file_processing_task;
    CREATE TABLE IF NOT EXISTS role_only_file_backup_file_storage_space_20260625 AS
        SELECT * FROM file_storage_space;
    CREATE TABLE IF NOT EXISTS role_only_file_backup_platform_event_outbox_20260625 AS
        SELECT * FROM platform_event_outbox;

    UPDATE file_storage_space space
    JOIN (
        SELECT storage_key, MIN(id) AS keep_id
        FROM file_storage_space
        GROUP BY storage_key
        HAVING COUNT(*) > 1
    ) duplicate_space ON duplicate_space.storage_key = space.storage_key
    SET space.storage_key = LEFT(CONCAT(LEFT(space.storage_key, 40), '__archived_', space.id), 64),
        space.deleted = 1,
        space.updated_at = NOW()
    WHERE space.id <> duplicate_space.keep_id;

    UPDATE file_processing_artifact artifact
    JOIN (
        SELECT file_id, artifact_type, MIN(id) AS keep_id
        FROM file_processing_artifact
        GROUP BY file_id, artifact_type
        HAVING COUNT(*) > 1
    ) duplicate_artifact
      ON duplicate_artifact.file_id = artifact.file_id
     AND duplicate_artifact.artifact_type = artifact.artifact_type
    SET artifact.artifact_type = LEFT(CONCAT(LEFT(artifact.artifact_type, 40), '__archived_', artifact.id), 64),
        artifact.deleted = 1,
        artifact.updated_at = NOW()
    WHERE artifact.id <> duplicate_artifact.keep_id;

    UPDATE file_processing_task task
    JOIN (
        SELECT file_id, task_type, MIN(id) AS keep_id
        FROM file_processing_task
        GROUP BY file_id, task_type
        HAVING COUNT(*) > 1
    ) duplicate_task
      ON duplicate_task.file_id = task.file_id
     AND duplicate_task.task_type = task.task_type
    SET task.task_type = LEFT(CONCAT(LEFT(task.task_type, 40), '__archived_', task.id), 64),
        task.deleted = 1,
        task.updated_at = NOW()
    WHERE task.id <> duplicate_task.keep_id;

    CALL role_only_file_drop_index('file_object', 'uk_file_object_key');
    CALL role_only_file_drop_index('file_object', 'idx_file_object_key');
    CALL role_only_file_drop_index('file_object', 'idx_file_object_department');
    CALL role_only_file_drop_index('file_object', 'idx_file_object_visibility');
    CALL role_only_file_drop_index('file_object', 'idx_file_object_tenant_deleted_bucket');
    CALL role_only_file_drop_index('file_object', 'idx_file_object_deleted_bucket');
    CALL role_only_file_drop_index('file_object', 'idx_file_object_tenant_deleted_created_id');
    CALL role_only_file_drop_index('file_object', 'idx_file_object_deleted_created_id');
    CALL role_only_file_drop_indexes_with_column('file_object', 'tenant_id');
    CALL role_only_file_drop_column('file_object', 'tenant_id');
    CALL role_only_file_add_index('file_object', 'idx_file_object_key',
        'ALTER TABLE `file_object` ADD INDEX `idx_file_object_key` (`object_key`)');
    CALL role_only_file_add_index('file_object', 'idx_file_object_department',
        'ALTER TABLE `file_object` ADD INDEX `idx_file_object_department` (`department_id`, `deleted`)');
    CALL role_only_file_add_index('file_object', 'idx_file_object_visibility',
        'ALTER TABLE `file_object` ADD INDEX `idx_file_object_visibility` (`visibility_scope`, `deleted`)');
    CALL role_only_file_add_index('file_object', 'idx_file_object_deleted_bucket',
        'ALTER TABLE `file_object` ADD INDEX `idx_file_object_deleted_bucket` (`deleted`, `bucket`)');
    CALL role_only_file_add_index('file_object', 'idx_file_object_deleted_created_id',
        'ALTER TABLE `file_object` ADD INDEX `idx_file_object_deleted_created_id` (`deleted`, `created_at`, `id`)');

    CALL role_only_file_drop_index('file_processing_artifact', 'uk_file_processing_artifact');
    CALL role_only_file_drop_index('file_processing_artifact', 'idx_file_processing_artifact_file');
    CALL role_only_file_drop_index('file_processing_artifact', 'idx_file_processing_artifact_type');
    CALL role_only_file_drop_indexes_with_column('file_processing_artifact', 'tenant_id');
    CALL role_only_file_drop_column('file_processing_artifact', 'tenant_id');
    CALL role_only_file_add_index('file_processing_artifact', 'uk_file_processing_artifact',
        'ALTER TABLE `file_processing_artifact` ADD UNIQUE INDEX `uk_file_processing_artifact` (`file_id`, `artifact_type`)');
    CALL role_only_file_add_index('file_processing_artifact', 'idx_file_processing_artifact_file',
        'ALTER TABLE `file_processing_artifact` ADD INDEX `idx_file_processing_artifact_file` (`file_id`, `deleted`)');
    CALL role_only_file_add_index('file_processing_artifact', 'idx_file_processing_artifact_type',
        'ALTER TABLE `file_processing_artifact` ADD INDEX `idx_file_processing_artifact_type` (`artifact_type`, `updated_at`)');

    CALL role_only_file_drop_index('file_processing_task', 'uk_file_processing_task_file_type');
    CALL role_only_file_drop_index('file_processing_task', 'idx_file_processing_task_file');
    CALL role_only_file_drop_index('file_processing_task', 'idx_file_processing_task_tenant_created');
    CALL role_only_file_drop_index('file_processing_task', 'idx_file_processing_task_created');
    CALL role_only_file_drop_indexes_with_column('file_processing_task', 'tenant_id');
    CALL role_only_file_drop_column('file_processing_task', 'tenant_id');
    CALL role_only_file_add_index('file_processing_task', 'uk_file_processing_task_file_type',
        'ALTER TABLE `file_processing_task` ADD UNIQUE INDEX `uk_file_processing_task_file_type` (`file_id`, `task_type`)');
    CALL role_only_file_add_index('file_processing_task', 'idx_file_processing_task_file',
        'ALTER TABLE `file_processing_task` ADD INDEX `idx_file_processing_task_file` (`file_id`, `deleted`)');
    CALL role_only_file_add_index('file_processing_task', 'idx_file_processing_task_created',
        'ALTER TABLE `file_processing_task` ADD INDEX `idx_file_processing_task_created` (`deleted`, `status`, `created_at`, `id`)');

    CALL role_only_file_drop_index('file_storage_space', 'uk_file_storage_space_key');
    CALL role_only_file_drop_index('file_storage_space', 'idx_file_storage_space_default');
    CALL role_only_file_drop_index('file_storage_space', 'idx_file_storage_space_tenant_deleted_default_id');
    CALL role_only_file_drop_index('file_storage_space', 'idx_file_storage_space_deleted_default_id');
    CALL role_only_file_drop_indexes_with_column('file_storage_space', 'tenant_id');
    CALL role_only_file_drop_column('file_storage_space', 'tenant_id');
    CALL role_only_file_add_index('file_storage_space', 'uk_file_storage_space_key',
        'ALTER TABLE `file_storage_space` ADD UNIQUE INDEX `uk_file_storage_space_key` (`storage_key`)');
    CALL role_only_file_add_index('file_storage_space', 'idx_file_storage_space_default',
        'ALTER TABLE `file_storage_space` ADD INDEX `idx_file_storage_space_default` (`default_flag`, `deleted`)');
    CALL role_only_file_add_index('file_storage_space', 'idx_file_storage_space_deleted_default_id',
        'ALTER TABLE `file_storage_space` ADD INDEX `idx_file_storage_space_deleted_default_id` (`deleted`, `default_flag`, `id`)');

    CALL role_only_file_drop_indexes_with_column('platform_event_outbox', 'tenant_id');
    CALL role_only_file_drop_column('platform_event_outbox', 'tenant_id');
    CALL role_only_file_add_index('platform_event_outbox', 'idx_platform_event_outbox_owner_queue',
        'ALTER TABLE `platform_event_outbox` ADD INDEX `idx_platform_event_outbox_owner_queue` (`source_type`, `created_at`, `id`, `dispatch_status`, `next_retry_at`, `deleted`)');
    CALL role_only_file_add_index('platform_event_outbox', 'idx_platform_event_outbox_batch_claim',
        'ALTER TABLE `platform_event_outbox` ADD INDEX `idx_platform_event_outbox_batch_claim` (`source_type`, `deleted`, `dispatch_status`, `next_retry_at`, `created_at`, `id`)');
    CALL role_only_file_add_index('platform_event_outbox', 'idx_platform_event_outbox_claim_token',
        'ALTER TABLE `platform_event_outbox` ADD INDEX `idx_platform_event_outbox_claim_token` (`claim_token`)');
END $$

CALL role_only_file_schema_refresh() $$

DROP PROCEDURE IF EXISTS role_only_file_schema_refresh $$
DROP PROCEDURE IF EXISTS role_only_file_add_index $$
DROP PROCEDURE IF EXISTS role_only_file_drop_column $$
DROP PROCEDURE IF EXISTS role_only_file_drop_indexes_with_column $$
DROP PROCEDURE IF EXISTS role_only_file_drop_index $$

DELIMITER ;
