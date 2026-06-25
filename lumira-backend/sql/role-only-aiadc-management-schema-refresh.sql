-- Convert AIADC management content tables from tenant-scoped keys to global role-protected data.
-- Safe to run repeatedly on the current single-platform schema.

CREATE TABLE IF NOT EXISTS `role_only_aiadc_backup_aiadc_activity_20260625` AS SELECT * FROM `aiadc_activity`;
CREATE TABLE IF NOT EXISTS `role_only_aiadc_backup_aiadc_competition_20260625` AS SELECT * FROM `aiadc_competition`;
CREATE TABLE IF NOT EXISTS `role_only_aiadc_backup_aiadc_expert_20260625` AS SELECT * FROM `aiadc_expert`;
CREATE TABLE IF NOT EXISTS `role_only_aiadc_backup_aiadc_project_20260625` AS SELECT * FROM `aiadc_project`;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_activity` DROP INDEX `uk_aiadc_activity_code`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_activity' AND index_name = 'uk_aiadc_activity_code');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_activity` DROP INDEX `idx_aiadc_activity_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_activity' AND index_name = 'idx_aiadc_activity_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_activity` DROP INDEX `idx_aiadc_activity_featured`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_activity' AND index_name = 'idx_aiadc_activity_featured');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `aiadc_activity`
    MODIFY COLUMN `locale` varchar(64) NOT NULL DEFAULT 'zh',
    ADD UNIQUE KEY `uk_aiadc_activity_code` (`code`, `locale`, `deleted`),
    ADD KEY `idx_aiadc_activity_status` (`status`, `deleted`, `sort`),
    ADD KEY `idx_aiadc_activity_featured` (`featured`, `deleted`, `sort`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_activity` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'aiadc_activity' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_competition` DROP INDEX `uk_aiadc_competition_code`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_competition' AND index_name = 'uk_aiadc_competition_code');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_competition` DROP INDEX `idx_aiadc_competition_category`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_competition' AND index_name = 'idx_aiadc_competition_category');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_competition` DROP INDEX `idx_aiadc_competition_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_competition' AND index_name = 'idx_aiadc_competition_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_competition` DROP INDEX `idx_aiadc_competition_featured`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_competition' AND index_name = 'idx_aiadc_competition_featured');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `aiadc_competition`
    MODIFY COLUMN `locale` varchar(64) NOT NULL DEFAULT 'zh',
    ADD UNIQUE KEY `uk_aiadc_competition_code` (`code`, `locale`, `deleted`),
    ADD KEY `idx_aiadc_competition_category` (`category`, `deleted`, `sort`),
    ADD KEY `idx_aiadc_competition_status` (`status`, `deleted`, `sort`),
    ADD KEY `idx_aiadc_competition_featured` (`featured`, `deleted`, `sort`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_competition` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'aiadc_competition' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_expert` DROP INDEX `uk_aiadc_expert_code`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_expert' AND index_name = 'uk_aiadc_expert_code');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_expert` DROP INDEX `idx_aiadc_expert_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_expert' AND index_name = 'idx_aiadc_expert_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_expert` DROP INDEX `idx_aiadc_expert_name`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_expert' AND index_name = 'idx_aiadc_expert_name');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `aiadc_expert`
    ADD UNIQUE KEY `uk_aiadc_expert_code` (`code`, `deleted`),
    ADD KEY `idx_aiadc_expert_status` (`status`, `deleted`, `sort`),
    ADD KEY `idx_aiadc_expert_name` (`name`, `deleted`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_expert` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'aiadc_expert' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_project` DROP INDEX `uk_aiadc_project_code`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_project' AND index_name = 'uk_aiadc_project_code');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_project` DROP INDEX `idx_aiadc_project_category`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_project' AND index_name = 'idx_aiadc_project_category');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_project` DROP INDEX `idx_aiadc_project_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_project' AND index_name = 'idx_aiadc_project_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_project` DROP INDEX `idx_aiadc_project_featured`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'aiadc_project' AND index_name = 'idx_aiadc_project_featured');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `aiadc_project`
    ADD UNIQUE KEY `uk_aiadc_project_code` (`code`, `locale`, `deleted`),
    ADD KEY `idx_aiadc_project_category` (`category`, `deleted`, `sort`),
    ADD KEY `idx_aiadc_project_status` (`status`, `deleted`, `sort`),
    ADD KEY `idx_aiadc_project_featured` (`featured`, `deleted`, `sort`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `aiadc_project` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'aiadc_project' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
