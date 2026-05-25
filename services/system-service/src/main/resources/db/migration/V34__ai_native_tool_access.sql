INSERT INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1001, 'ai:tool:view', '查看 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:tool:execute', '执行 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT r.tenant_id, r.id, p.permission_key, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.tenant_id = r.tenant_id
 AND p.permission_key IN ('ai:tool:view', 'ai:tool:execute')
 AND p.deleted = 0
WHERE r.tenant_id = 1001
  AND upper(r.role_code) = 'ADMIN'
  AND r.deleted = 0
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
VALUES
  ('system.permission.snapshot', '读取当前权限上下文', 'system', '返回当前登录用户、租户、角色、部门和权限集合，供 AI 判断可访问边界。', 'LOW', 1, 0, 1, 0),
  ('system.menu.list', '读取系统菜单与模块入口', 'system', '按当前账号权限读取系统菜单、路由、权限键和状态，供 AI 理解平台能力地图。', 'LOW', 1, 0, 1, 0),
  ('system.config.read', '读取非敏感系统配置', 'system', '按配置键读取非敏感平台配置；敏感配置会被拒绝。', 'MEDIUM', 1, 0, 1, 0),
  ('system.user.search', '检索系统用户', 'system', '按关键词和状态检索当前租户用户，返回脱敏后的基础资料。', 'MEDIUM', 1, 0, 1, 0),
  ('file.object.search', '检索文件对象', 'file', '按关键词、类型和状态检索文件中心对象。', 'MEDIUM', 1, 0, 1, 0),
  ('audit.ai_call.search', '检索 AI 工具审计', 'audit', '按数字员工、技能编码和结果状态检索 AI 调用审计日志。', 'MEDIUM', 1, 0, 1, 0)
ON DUPLICATE KEY UPDATE
  `skill_name` = VALUES(`skill_name`),
  `category` = VALUES(`category`),
  `description` = VALUES(`description`),
  `risk_level` = VALUES(`risk_level`),
  `read_only` = VALUES(`read_only`),
  `need_confirm` = VALUES(`need_confirm`),
  `enabled` = VALUES(`enabled`),
  `is_deleted` = 0,
  `update_time` = CURRENT_TIMESTAMP;
