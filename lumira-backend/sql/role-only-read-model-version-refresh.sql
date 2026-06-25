-- Merge duplicated read-model version rows created by older tenant-scoped schemas.
-- Safe to re-run after the role-only schema has been applied.

DELIMITER $$

DROP PROCEDURE IF EXISTS role_only_read_model_drop_index $$
CREATE PROCEDURE role_only_read_model_drop_index(IN p_index VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'ddd_read_model_version'
          AND index_name = p_index
    ) THEN
        SET @role_only_ddl = CONCAT('ALTER TABLE `ddd_read_model_version` DROP INDEX `', REPLACE(p_index, '`', '``'), '`');
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_read_model_drop_column $$
CREATE PROCEDURE role_only_read_model_drop_column(IN p_column VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'ddd_read_model_version'
          AND column_name = p_column
    ) THEN
        SET @role_only_ddl = CONCAT('ALTER TABLE `ddd_read_model_version` DROP COLUMN `', REPLACE(p_column, '`', '``'), '`');
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_read_model_add_index $$
CREATE PROCEDURE role_only_read_model_add_index(IN p_index VARCHAR(64), IN p_sql TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'ddd_read_model_version'
          AND index_name = p_index
    ) THEN
        SET @role_only_ddl = p_sql;
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_read_model_version_refresh $$
CREATE PROCEDURE role_only_read_model_version_refresh()
BEGIN
    CREATE TABLE IF NOT EXISTS role_only_backup_ddd_read_model_version_20260625 AS
        SELECT * FROM ddd_read_model_version;

    DROP TEMPORARY TABLE IF EXISTS role_only_read_model_keep;
    CREATE TEMPORARY TABLE role_only_read_model_keep AS
        SELECT
            MIN(id) AS keep_id,
            context_name,
            scope,
            MAX(version) AS version,
            SUBSTRING_INDEX(
                GROUP_CONCAT(COALESCE(last_event_key, '') ORDER BY version DESC, updated_at DESC, id DESC SEPARATOR '\n'),
                '\n',
                1
            ) AS last_event_key,
            MAX(rebuilt_at) AS rebuilt_at
        FROM ddd_read_model_version
        GROUP BY context_name, scope;

    UPDATE ddd_read_model_version version_row
    JOIN role_only_read_model_keep keep_row ON keep_row.keep_id = version_row.id
    SET version_row.version = keep_row.version,
        version_row.last_event_key = NULLIF(keep_row.last_event_key, ''),
        version_row.rebuilt_at = COALESCE(keep_row.rebuilt_at, CURRENT_TIMESTAMP),
        version_row.updated_at = CURRENT_TIMESTAMP;

    DELETE version_row
    FROM ddd_read_model_version version_row
    LEFT JOIN role_only_read_model_keep keep_row ON keep_row.keep_id = version_row.id
    WHERE keep_row.keep_id IS NULL;

    CALL role_only_read_model_drop_index('uk_ddd_read_model_version_scope');
    CALL role_only_read_model_drop_column('tenant_id');
    CALL role_only_read_model_add_index(
        'uk_ddd_read_model_version_scope',
        'ALTER TABLE `ddd_read_model_version` ADD UNIQUE INDEX `uk_ddd_read_model_version_scope` (`context_name`, `scope`)'
    );
    CALL role_only_read_model_add_index(
        'idx_ddd_read_model_version_context',
        'ALTER TABLE `ddd_read_model_version` ADD INDEX `idx_ddd_read_model_version_context` (`context_name`, `updated_at`)'
    );
    CALL role_only_read_model_add_index(
        'idx_ddd_read_model_version_event_key',
        'ALTER TABLE `ddd_read_model_version` ADD INDEX `idx_ddd_read_model_version_event_key` (`last_event_key`)'
    );
END $$

CALL role_only_read_model_version_refresh() $$

DROP PROCEDURE IF EXISTS role_only_read_model_version_refresh $$
DROP PROCEDURE IF EXISTS role_only_read_model_add_index $$
DROP PROCEDURE IF EXISTS role_only_read_model_drop_column $$
DROP PROCEDURE IF EXISTS role_only_read_model_drop_index $$

DELIMITER ;
