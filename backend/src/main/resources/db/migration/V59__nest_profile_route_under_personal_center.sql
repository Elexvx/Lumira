SET @now_ts = NOW();

UPDATE `sys_menu` personal
SET
  personal.`component` = 'redirect:/user-center/personal-center/profile',
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
  profile.`path` = '/user-center/personal-center/profile',
  profile.`component` = '@/pages/profile/Center',
  profile.`sort_no` = 1,
  profile.`permission_key` = 'profile:view',
  profile.`updated_by` = 0,
  profile.`updated_at` = @now_ts
WHERE profile.`menu_code` = 'profile.center'
  AND profile.`deleted` = 0;
