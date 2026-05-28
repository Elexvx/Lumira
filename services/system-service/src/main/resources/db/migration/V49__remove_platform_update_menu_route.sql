UPDATE `sys_menu`
SET
  `deleted` = 1,
  `status` = 'DISABLED',
  `updated_by` = 0,
  `updated_at` = CURRENT_TIMESTAMP
WHERE `tenant_id` = 1001
  AND `menu_code` = 'settings.monitoring.update';
