-- Refresh export task and security audit event tables from tenant-scoped storage to role-only/global storage.
-- Safe to re-run: helpers check existing columns and indexes before changing them.

DELIMITER $$

DROP PROCEDURE IF EXISTS role_only_export_sec_drop_index $$
CREATE PROCEDURE role_only_export_sec_drop_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
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

DROP PROCEDURE IF EXISTS role_only_export_sec_drop_indexes_with_column $$
CREATE PROCEDURE role_only_export_sec_drop_indexes_with_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
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
        CALL role_only_export_sec_drop_index(p_table, v_index);
    END LOOP;
    CLOSE index_cursor;
END $$

DROP PROCEDURE IF EXISTS role_only_export_sec_drop_column $$
CREATE PROCEDURE role_only_export_sec_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
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

DROP PROCEDURE IF EXISTS role_only_export_sec_add_index $$
CREATE PROCEDURE role_only_export_sec_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_sql TEXT)
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

DROP PROCEDURE IF EXISTS role_only_export_security_schema_refresh $$
CREATE PROCEDURE role_only_export_security_schema_refresh()
BEGIN
    CREATE TABLE IF NOT EXISTS role_only_export_backup_sys_export_task_20260625 AS
        SELECT * FROM sys_export_task;
    CREATE TABLE IF NOT EXISTS role_only_security_backup_security_audit_event_20260625 AS
        SELECT * FROM security_audit_event;

    CALL role_only_export_sec_drop_index('sys_export_task', 'idx_sys_export_task_tenant_creator');
    CALL role_only_export_sec_drop_index('sys_export_task', 'idx_sys_export_task_creator');
    CALL role_only_export_sec_drop_indexes_with_column('sys_export_task', 'tenant_id');
    CALL role_only_export_sec_drop_column('sys_export_task', 'tenant_id');
    CALL role_only_export_sec_add_index('sys_export_task', 'idx_sys_export_task_creator',
        'ALTER TABLE `sys_export_task` ADD INDEX `idx_sys_export_task_creator` (`created_by`, `created_at`)');
    CALL role_only_export_sec_add_index('sys_export_task', 'idx_sys_export_task_status',
        'ALTER TABLE `sys_export_task` ADD INDEX `idx_sys_export_task_status` (`status`, `created_at`)');

    CALL role_only_export_sec_drop_index('security_audit_event', 'idx_security_audit_tenant_created_at');
    CALL role_only_export_sec_drop_indexes_with_column('security_audit_event', 'tenant_id');
    CALL role_only_export_sec_drop_column('security_audit_event', 'tenant_id');
    CALL role_only_export_sec_add_index('security_audit_event', 'idx_security_audit_created_at',
        'ALTER TABLE `security_audit_event` ADD INDEX `idx_security_audit_created_at` (`created_at`)');
    CALL role_only_export_sec_add_index('security_audit_event', 'idx_security_audit_event_type_created_at',
        'ALTER TABLE `security_audit_event` ADD INDEX `idx_security_audit_event_type_created_at` (`event_type`, `created_at`)');
    CALL role_only_export_sec_add_index('security_audit_event', 'idx_security_audit_request_id',
        'ALTER TABLE `security_audit_event` ADD INDEX `idx_security_audit_request_id` (`request_id`)');
    CALL role_only_export_sec_add_index('security_audit_event', 'idx_security_audit_source_ip_created_at',
        'ALTER TABLE `security_audit_event` ADD INDEX `idx_security_audit_source_ip_created_at` (`source_ip`, `created_at`)');
END $$

CALL role_only_export_security_schema_refresh() $$

DROP PROCEDURE IF EXISTS role_only_export_security_schema_refresh $$
DROP PROCEDURE IF EXISTS role_only_export_sec_add_index $$
DROP PROCEDURE IF EXISTS role_only_export_sec_drop_column $$
DROP PROCEDURE IF EXISTS role_only_export_sec_drop_indexes_with_column $$
DROP PROCEDURE IF EXISTS role_only_export_sec_drop_index $$

DELIMITER ;
