SET @sys_role_default_home_path_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_role'
    AND column_name = 'default_home_path'
);

SET @add_sys_role_default_home_path_sql := IF(
  @sys_role_default_home_path_exists = 0,
  'ALTER TABLE `sys_role` ADD COLUMN `default_home_path` varchar(255) NOT NULL DEFAULT ''/dashboard/home'' AFTER `role_type`',
  'SELECT 1'
);

PREPARE add_sys_role_default_home_path_stmt FROM @add_sys_role_default_home_path_sql;
EXECUTE add_sys_role_default_home_path_stmt;
DEALLOCATE PREPARE add_sys_role_default_home_path_stmt;

UPDATE `sys_role`
SET `default_home_path` = '/dashboard/home'
WHERE (`default_home_path` IS NULL OR TRIM(`default_home_path`) = '')
  AND `deleted` = 0;
