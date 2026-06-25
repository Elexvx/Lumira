-- Convert message tables from tenant-scoped rows to global role-protected data.
-- Safe to run repeatedly on the current single-platform schema.

CREATE TABLE IF NOT EXISTS `role_only_message_backup_msg_delivery_log_20260625` AS SELECT * FROM `msg_delivery_log`;
CREATE TABLE IF NOT EXISTS `role_only_message_backup_msg_notice_20260625` AS SELECT * FROM `msg_notice`;
CREATE TABLE IF NOT EXISTS `role_only_message_backup_msg_notice_read_20260625` AS SELECT * FROM `msg_notice_read`;

UPDATE `msg_notice`
SET `target_scope` = 'PLATFORM'
WHERE `target_scope` = 'TENANT';

UPDATE `msg_delivery_log`
SET `target_scope` = 'PLATFORM'
WHERE `target_scope` = 'TENANT';

DELETE r1
FROM `msg_notice_read` r1
JOIN `msg_notice_read` r2
  ON r1.`notice_id` = r2.`notice_id`
 AND r1.`user_id` = r2.`user_id`
 AND r1.`id` > r2.`id`;

SET @ddl = (
    SELECT IF(
        COUNT(*) > 0,
        CONCAT('ALTER TABLE `msg_delivery_log` ', GROUP_CONCAT(CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', ')),
        'SELECT 1'
    )
    FROM (
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'msg_delivery_log'
          AND column_name = 'tenant_id'
          AND index_name <> 'PRIMARY'
    ) tenant_indexes
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_delivery_log` DROP INDEX `idx_msg_delivery_log_channel_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_delivery_log' AND index_name = 'idx_msg_delivery_log_channel_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_delivery_log` DROP INDEX `idx_msg_delivery_log_status_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_delivery_log' AND index_name = 'idx_msg_delivery_log_status_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_delivery_log` DROP INDEX `idx_msg_delivery_log_notice`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_delivery_log' AND index_name = 'idx_msg_delivery_log_notice');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_delivery_log` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'msg_delivery_log' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `msg_delivery_log`
    ADD KEY `idx_msg_delivery_log_channel_created` (`channel`, `created_at`),
    ADD KEY `idx_msg_delivery_log_status_created` (`send_status`, `created_at`),
    ADD KEY `idx_msg_delivery_log_notice` (`notice_id`);

SET @ddl = (
    SELECT IF(
        COUNT(*) > 0,
        CONCAT('ALTER TABLE `msg_notice` ', GROUP_CONCAT(CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', ')),
        'SELECT 1'
    )
    FROM (
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'msg_notice'
          AND column_name = 'tenant_id'
          AND index_name <> 'PRIMARY'
    ) tenant_indexes
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice` DROP INDEX `idx_msg_notice_type_status_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice' AND index_name = 'idx_msg_notice_type_status_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice` DROP INDEX `idx_msg_notice_target_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice' AND index_name = 'idx_msg_notice_target_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice` DROP INDEX `idx_msg_notice_target_role_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice' AND index_name = 'idx_msg_notice_target_role_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice` DROP INDEX `idx_msg_notice_visible_recent`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice' AND index_name = 'idx_msg_notice_visible_recent');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice` DROP INDEX `idx_msg_notice_visible_target_user_recent`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice' AND index_name = 'idx_msg_notice_visible_target_user_recent');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice` DROP INDEX `idx_msg_notice_visible_target_role_recent`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice' AND index_name = 'idx_msg_notice_visible_target_role_recent');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'msg_notice' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `msg_notice`
    ADD KEY `idx_msg_notice_type_status_created` (`notice_type`, `publish_status`, `created_at`),
    ADD KEY `idx_msg_notice_target_created` (`target_user_id`, `created_at`),
    ADD KEY `idx_msg_notice_target_role_created` (`target_role_id`, `created_at`),
    ADD KEY `idx_msg_notice_visible_recent` (`publish_status`, `deleted`, `id`),
    ADD KEY `idx_msg_notice_visible_target_user_recent` (`publish_status`, `deleted`, `target_user_id`, `id`),
    ADD KEY `idx_msg_notice_visible_target_role_recent` (`publish_status`, `deleted`, `target_role_id`, `id`);

SET @ddl = (
    SELECT IF(
        COUNT(*) > 0,
        CONCAT('ALTER TABLE `msg_notice_read` ', GROUP_CONCAT(CONCAT('DROP INDEX `', index_name, '`') SEPARATOR ', ')),
        'SELECT 1'
    )
    FROM (
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'msg_notice_read'
          AND column_name = 'tenant_id'
          AND index_name <> 'PRIMARY'
    ) tenant_indexes
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice_read` DROP INDEX `uk_msg_notice_read`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice_read' AND index_name = 'uk_msg_notice_read');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice_read` DROP INDEX `idx_msg_notice_read_user_created`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice_read' AND index_name = 'idx_msg_notice_read_user_created');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice_read` DROP INDEX `idx_msg_notice_read_notice_user_deleted`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'msg_notice_read' AND index_name = 'idx_msg_notice_read_notice_user_deleted');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `msg_notice_read` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'msg_notice_read' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `msg_notice_read`
    ADD UNIQUE KEY `uk_msg_notice_read` (`notice_id`, `user_id`),
    ADD KEY `idx_msg_notice_read_user_created` (`user_id`, `read_at`),
    ADD KEY `idx_msg_notice_read_notice_user_deleted` (`notice_id`, `user_id`, `deleted`);
