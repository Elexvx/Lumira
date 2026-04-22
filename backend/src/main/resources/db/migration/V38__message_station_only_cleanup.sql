UPDATE sys_permission
SET permission_name = '查看站内信'
WHERE permission_key = 'message:message:view'
  AND deleted = 0;

UPDATE sys_permission
SET permission_name = '发送站内信'
WHERE permission_key = 'message:message:write'
  AND deleted = 0;

UPDATE sys_permission
SET permission_name = '标记站内信已读'
WHERE permission_key = 'message:message:read'
  AND deleted = 0;

UPDATE sys_permission
SET permission_name = '撤回站内信'
WHERE permission_key = 'message:message:retract'
  AND deleted = 0;

DELETE FROM sys_role_permission
WHERE permission_key IN (
    'message:announcement:view',
    'message:announcement:write',
    'message:announcement:read',
    'message:announcement:retract'
);

DELETE FROM sys_permission
WHERE permission_key IN (
    'message:announcement:view',
    'message:announcement:write',
    'message:announcement:read',
    'message:announcement:retract'
);
