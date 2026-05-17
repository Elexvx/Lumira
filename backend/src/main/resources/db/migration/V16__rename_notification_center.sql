UPDATE `sys_menu`
SET `menu_name` = '通知中心',
    `updated_at` = now()
WHERE `tenant_id` = 1001
  AND `menu_code` = 'settings.notifications'
  AND `deleted` = 0;

UPDATE `sys_permission`
SET `permission_name` = '查看通知中心',
    `updated_at` = now()
WHERE `tenant_id` = 1001
  AND `permission_key` = 'system:notification:view'
  AND `deleted` = 0;

UPDATE `sys_permission`
SET `permission_name` = '手动发布通知',
    `updated_at` = now()
WHERE `tenant_id` = 1001
  AND `permission_key` = 'system:notification:write'
  AND `deleted` = 0;
