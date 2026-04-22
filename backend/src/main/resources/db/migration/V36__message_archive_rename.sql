UPDATE sys_permission
SET permission_name = '查看消息归档'
WHERE permission_key = 'system:notification:view'
  AND deleted = 0;

UPDATE sys_permission
SET permission_name = '手动发布站内信'
WHERE permission_key = 'system:notification:write'
  AND deleted = 0;

UPDATE sys_menu
SET menu_name = '消息归档'
WHERE menu_code = 'system.notifications'
  AND deleted = 0;
