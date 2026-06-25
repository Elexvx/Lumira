-- Convert audit tables from tenant-scoped rows to global role-protected data.
-- Safe to run repeatedly on the current single-platform schema.

CREATE TABLE IF NOT EXISTS `role_only_audit_backup_audit_login_log_20260625` AS SELECT * FROM `audit_login_log`;
CREATE TABLE IF NOT EXISTS `role_only_audit_backup_audit_operation_log_20260625` AS SELECT * FROM `audit_operation_log`;

SET @ddl = (
    SELECT IF(
        COUNT(*) > 0,
        CONCAT('ALTER TABLE `audit_login_log` ', GROUP_CONCAT(CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', ')),
        'SELECT 1'
    )
    FROM (
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'audit_login_log'
          AND column_name = 'tenant_id'
          AND index_name <> 'PRIMARY'
    ) tenant_indexes
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_login_log` DROP INDEX `idx_audit_login_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_login_log' AND index_name = 'idx_audit_login_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_login_log` DROP INDEX `idx_audit_login_user_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_login_log' AND index_name = 'idx_audit_login_user_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_login_log` DROP INDEX `idx_audit_login_result_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_login_log' AND index_name = 'idx_audit_login_result_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_login_log` DROP INDEX `idx_audit_login_user_result_recent`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_login_log' AND index_name = 'idx_audit_login_user_result_recent');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_login_log` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'audit_login_log' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `audit_login_log`
    ADD KEY `idx_audit_login_created` (`created_at`),
    ADD KEY `idx_audit_login_user_created` (`user_id`, `created_at`),
    ADD KEY `idx_audit_login_result_created` (`login_result`, `created_at`),
    ADD KEY `idx_audit_login_user_result_recent` (`user_id`, `login_result`, `created_at`, `id`);

SET @ddl = (
    SELECT IF(
        COUNT(*) > 0,
        CONCAT('ALTER TABLE `audit_operation_log` ', GROUP_CONCAT(CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', ')),
        'SELECT 1'
    )
    FROM (
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'audit_operation_log'
          AND column_name = 'tenant_id'
          AND index_name <> 'PRIMARY'
    ) tenant_indexes
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_operation_log` DROP INDEX `idx_audit_operation_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_operation_log' AND index_name = 'idx_audit_operation_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_operation_log` DROP INDEX `idx_audit_operation_user_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_operation_log' AND index_name = 'idx_audit_operation_user_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_operation_log` DROP INDEX `idx_audit_operation_module_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_operation_log' AND index_name = 'idx_audit_operation_module_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_operation_log` DROP INDEX `idx_audit_operation_result_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_operation_log' AND index_name = 'idx_audit_operation_result_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_operation_log` DROP INDEX `idx_audit_operation_user_recent`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'audit_operation_log' AND index_name = 'idx_audit_operation_user_recent');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `audit_operation_log` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'audit_operation_log' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `audit_operation_log`
    ADD KEY `idx_audit_operation_created` (`created_at`),
    ADD KEY `idx_audit_operation_user_created` (`user_id`, `created_at`),
    ADD KEY `idx_audit_operation_module_created` (`module_name`, `created_at`),
    ADD KEY `idx_audit_operation_result_created` (`result_status`, `created_at`),
    ADD KEY `idx_audit_operation_user_recent` (`username`, `created_at`, `id`);
