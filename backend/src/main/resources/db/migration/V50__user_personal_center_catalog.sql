SET @now_ts = NOW();

INSERT INTO `sys_menu` (
  `tenant_id`,
  `parent_id`,
  `menu_code`,
  `menu_name`,
  `menu_type`,
  `path`,
  `component`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`,
  `icon`,
  `sort_no`,
  `permission_key`,
  `status`
)
SELECT
  root.`tenant_id`,
  root.`id`,
  'user.center.personal',
  '个人中心',
  'CATALOG',
  '/user-center/personal-center',
  'redirect:/user-center/profile',
  0,
  @now_ts,
  0,
  @now_ts,
  0,
  'IdcardOutlined',
  24,
  'user:center:view',
  'ENABLED'
FROM `sys_menu` root
WHERE root.`menu_code` = 'user.center.root'
  AND root.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_menu` existing
    WHERE existing.`tenant_id` = root.`tenant_id`
      AND existing.`menu_code` = 'user.center.personal'
      AND existing.`deleted` = 0
  );

UPDATE `sys_menu` child
JOIN `sys_menu` personal
  ON personal.`tenant_id` = child.`tenant_id`
  AND personal.`menu_code` = 'user.center.personal'
  AND personal.`deleted` = 0
SET
  child.`parent_id` = personal.`id`,
  child.`updated_by` = 0,
  child.`updated_at` = @now_ts
WHERE child.`menu_code` IN ('profile.center', 'files.my')
  AND child.`deleted` = 0;
