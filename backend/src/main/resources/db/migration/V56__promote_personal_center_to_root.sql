SET @now_ts = NOW();

UPDATE `sys_menu` personal
SET
  personal.`parent_id` = 0,
  personal.`path` = '/user-center/personal-center',
  personal.`component` = 'redirect:/user-center/profile',
  personal.`icon` = 'IdcardOutlined',
  personal.`sort_no` = 19,
  personal.`permission_key` = 'profile:view',
  personal.`updated_by` = 0,
  personal.`updated_at` = @now_ts
WHERE personal.`menu_code` = 'user.center.personal'
  AND personal.`deleted` = 0;

UPDATE `sys_menu` profile
JOIN `sys_menu` personal
  ON personal.`tenant_id` = profile.`tenant_id`
  AND personal.`menu_code` = 'user.center.personal'
  AND personal.`deleted` = 0
SET
  profile.`parent_id` = personal.`id`,
  profile.`menu_name` = '个人资料',
  profile.`sort_no` = 1,
  profile.`permission_key` = 'profile:view',
  profile.`updated_by` = 0,
  profile.`updated_at` = @now_ts
WHERE profile.`menu_code` = 'profile.center'
  AND profile.`deleted` = 0;

UPDATE `sys_menu` files
JOIN `sys_menu` personal
  ON personal.`tenant_id` = files.`tenant_id`
  AND personal.`menu_code` = 'user.center.personal'
  AND personal.`deleted` = 0
SET
  files.`parent_id` = personal.`id`,
  files.`sort_no` = 2,
  files.`permission_key` = 'system:file:view',
  files.`updated_by` = 0,
  files.`updated_at` = @now_ts
WHERE files.`menu_code` = 'files.my'
  AND files.`deleted` = 0;
