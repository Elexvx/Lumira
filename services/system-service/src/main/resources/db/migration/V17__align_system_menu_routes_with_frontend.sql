UPDATE `sys_menu`
SET `component` = '@/layouts/SettingsLayout',
    `updated_at` = now()
WHERE `tenant_id` = 1001
  AND `menu_code` = 'user.center.root'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `path` = '/user-center/personal-center',
    `component` = '@/layouts/SettingsLayout',
    `updated_at` = now()
WHERE `tenant_id` = 1001
  AND `menu_code` = 'user.center.personal'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `deleted` = 1,
    `status` = 'DISABLED',
    `updated_at` = now()
WHERE `tenant_id` = 1001
  AND `menu_code` = 'user.center.permissions'
  AND `path` = '/user-center/permissions'
  AND `component` = '@/pages/iam/Overview'
  AND `deleted` = 0;
