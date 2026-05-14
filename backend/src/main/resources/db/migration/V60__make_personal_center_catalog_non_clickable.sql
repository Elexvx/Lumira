SET @now_ts = NOW();

UPDATE `sys_menu`
SET
  `path` = NULL,
  `component` = NULL,
  `updated_by` = 0,
  `updated_at` = @now_ts
WHERE `menu_code` = 'user.center.personal'
  AND `menu_type` = 'CATALOG'
  AND `deleted` = 0;
