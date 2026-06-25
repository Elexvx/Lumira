-- Convert core IAM tables from tenant-scoped keys to role-only keys.
-- This script is safe to re-run on the current single-platform schema.
-- It keeps one backup table per affected table using the 20260625 suffix.

CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_menu_20260625` AS SELECT * FROM `sys_menu`;
CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_permission_20260625` AS SELECT * FROM `sys_permission`;
CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_role_20260625` AS SELECT * FROM `sys_role`;
CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_role_data_scope_20260625` AS SELECT * FROM `sys_role_data_scope`;
CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_role_permission_20260625` AS SELECT * FROM `sys_role_permission`;
CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_user_department_20260625` AS SELECT * FROM `sys_user_department`;
CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_user_role_20260625` AS SELECT * FROM `sys_user_role`;

SET @ddl = (
    SELECT IF(
        COUNT(*) > 0,
        'CREATE TABLE IF NOT EXISTS `role_only_iam_backup_sys_tenant_20260625` AS SELECT * FROM `sys_tenant`',
        'SELECT 1'
    )
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_tenant'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS `tmp_role_only_menu_map`;
CREATE TEMPORARY TABLE `tmp_role_only_menu_map` AS
SELECT `id` AS `old_id`, MIN(`id`) OVER (PARTITION BY `menu_code`) AS `keep_id`
FROM `sys_menu`;
UPDATE `sys_menu` m
JOIN `tmp_role_only_menu_map` d ON d.`old_id` = m.`parent_id`
SET m.`parent_id` = d.`keep_id`
WHERE d.`old_id` <> d.`keep_id`;
DELETE m
FROM `sys_menu` m
JOIN `tmp_role_only_menu_map` d ON d.`old_id` = m.`id`
WHERE d.`old_id` <> d.`keep_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_role_only_permission_map`;
CREATE TEMPORARY TABLE `tmp_role_only_permission_map` AS
SELECT `id` AS `old_id`, MIN(`id`) OVER (PARTITION BY `permission_key`) AS `keep_id`
FROM `sys_permission`;
DELETE p
FROM `sys_permission` p
JOIN `tmp_role_only_permission_map` d ON d.`old_id` = p.`id`
WHERE d.`old_id` <> d.`keep_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_role_only_role_map`;
CREATE TEMPORARY TABLE `tmp_role_only_role_map` AS
SELECT `id` AS `old_id`, MIN(`id`) OVER (PARTITION BY `role_code`) AS `keep_id`
FROM `sys_role`;
UPDATE `sys_role_permission` rp
JOIN `tmp_role_only_role_map` d ON d.`old_id` = rp.`role_id`
SET rp.`role_id` = d.`keep_id`
WHERE d.`old_id` <> d.`keep_id`;
UPDATE `sys_role_data_scope` rds
JOIN `tmp_role_only_role_map` d ON d.`old_id` = rds.`role_id`
SET rds.`role_id` = d.`keep_id`
WHERE d.`old_id` <> d.`keep_id`;
UPDATE `sys_user_role` ur
JOIN `tmp_role_only_role_map` d ON d.`old_id` = ur.`role_id`
SET ur.`role_id` = d.`keep_id`
WHERE d.`old_id` <> d.`keep_id`;
DELETE r
FROM `sys_role` r
JOIN `tmp_role_only_role_map` d ON d.`old_id` = r.`id`
WHERE d.`old_id` <> d.`keep_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_role_only_role_permission_keep`;
CREATE TEMPORARY TABLE `tmp_role_only_role_permission_keep` AS
SELECT MIN(`id`) AS `keep_id`, `role_id`, `permission_key`
FROM `sys_role_permission`
GROUP BY `role_id`, `permission_key`;
DELETE rp
FROM `sys_role_permission` rp
JOIN `tmp_role_only_role_permission_keep` k
  ON k.`role_id` = rp.`role_id`
 AND k.`permission_key` = rp.`permission_key`
WHERE rp.`id` <> k.`keep_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_role_only_role_data_scope_keep`;
CREATE TEMPORARY TABLE `tmp_role_only_role_data_scope_keep` AS
SELECT MIN(`id`) AS `keep_id`, `role_id`, `resource_code`
FROM `sys_role_data_scope`
GROUP BY `role_id`, `resource_code`;
DELETE rds
FROM `sys_role_data_scope` rds
JOIN `tmp_role_only_role_data_scope_keep` k
  ON k.`role_id` = rds.`role_id`
 AND k.`resource_code` = rds.`resource_code`
WHERE rds.`id` <> k.`keep_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_role_only_user_department_keep`;
CREATE TEMPORARY TABLE `tmp_role_only_user_department_keep` AS
SELECT MIN(`id`) AS `keep_id`, `user_id`, `dept_id`
FROM `sys_user_department`
GROUP BY `user_id`, `dept_id`;
DELETE ud
FROM `sys_user_department` ud
JOIN `tmp_role_only_user_department_keep` k
  ON k.`user_id` = ud.`user_id`
 AND k.`dept_id` = ud.`dept_id`
WHERE ud.`id` <> k.`keep_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_role_only_user_role_keep`;
CREATE TEMPORARY TABLE `tmp_role_only_user_role_keep` AS
SELECT MIN(`id`) AS `keep_id`, `user_id`, `role_id`
FROM `sys_user_role`
GROUP BY `user_id`, `role_id`;
DELETE ur
FROM `sys_user_role` ur
JOIN `tmp_role_only_user_role_keep` k
  ON k.`user_id` = ur.`user_id`
 AND k.`role_id` = ur.`role_id`
WHERE ur.`id` <> k.`keep_id`;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_menu` DROP INDEX `idx_sys_menu_tenant_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND index_name = 'idx_sys_menu_tenant_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_menu` DROP INDEX `uk_sys_menu_code`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND index_name = 'uk_sys_menu_code');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_menu` DROP INDEX `idx_sys_menu_status`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND index_name = 'idx_sys_menu_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `sys_menu`
    ADD UNIQUE KEY `uk_sys_menu_code` (`menu_code`),
    ADD KEY `idx_sys_menu_status` (`status`, `sort_no`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_menu` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_permission` DROP INDEX `uk_sys_permission_key`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_permission' AND index_name = 'uk_sys_permission_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `sys_permission`
    ADD UNIQUE KEY `uk_sys_permission_key` (`permission_key`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_permission` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_permission' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role` DROP INDEX `uk_sys_role_code`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND index_name = 'uk_sys_role_code');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `sys_role`
    ADD UNIQUE KEY `uk_sys_role_code` (`role_code`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role_data_scope` DROP INDEX `idx_sys_role_data_scope_role`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_role_data_scope' AND index_name = 'idx_sys_role_data_scope_role');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role_data_scope` DROP INDEX `uk_sys_role_data_scope_resource`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_role_data_scope' AND index_name = 'uk_sys_role_data_scope_resource');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `sys_role_data_scope`
    ADD UNIQUE KEY `uk_sys_role_data_scope_resource` (`role_id`, `resource_code`),
    ADD KEY `idx_sys_role_data_scope_role` (`role_id`, `deleted`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role_data_scope` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_data_scope' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role_permission` DROP INDEX `idx_sys_role_permission_tenant_role_deleted_perm`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_role_permission' AND index_name = 'idx_sys_role_permission_tenant_role_deleted_perm');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role_permission` DROP INDEX `idx_sys_role_permission_role_deleted_perm`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_role_permission' AND index_name = 'idx_sys_role_permission_role_deleted_perm');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role_permission` DROP INDEX `uk_sys_role_permission_rel`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_role_permission' AND index_name = 'uk_sys_role_permission_rel');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `sys_role_permission`
    ADD UNIQUE KEY `uk_sys_role_permission_rel` (`role_id`, `permission_key`),
    ADD KEY `idx_sys_role_permission_role_deleted_perm` (`role_id`, `deleted`, `permission_key`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_role_permission` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_permission' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user_department` DROP INDEX `idx_sys_user_department_dept`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_user_department' AND index_name = 'idx_sys_user_department_dept');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user_department` DROP INDEX `uk_sys_user_department_rel`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_user_department' AND index_name = 'uk_sys_user_department_rel');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `sys_user_department`
    ADD UNIQUE KEY `uk_sys_user_department_rel` (`user_id`, `dept_id`),
    ADD KEY `idx_sys_user_department_dept` (`dept_id`, `deleted`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user_department` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_department' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user_role` DROP INDEX `idx_sys_user_role_tenant_user_deleted`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_user_role' AND index_name = 'idx_sys_user_role_tenant_user_deleted');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user_role` DROP INDEX `idx_sys_user_role_user_deleted`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_user_role' AND index_name = 'idx_sys_user_role_user_deleted');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user_role` DROP INDEX `uk_sys_user_role_rel`', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_user_role' AND index_name = 'uk_sys_user_role_rel');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE `sys_user_role`
    ADD UNIQUE KEY `uk_sys_user_role_rel` (`user_id`, `role_id`),
    ADD KEY `idx_sys_user_role_user_deleted` (`user_id`, `deleted`, `role_id`);
SET @ddl = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user_role` DROP COLUMN `tenant_id`', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user_role' AND column_name = 'tenant_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS `sys_tenant`;
