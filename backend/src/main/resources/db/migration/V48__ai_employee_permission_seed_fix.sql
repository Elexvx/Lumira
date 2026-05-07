SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
  (180, 1001, 'ai:view', '查看数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (181, 1001, 'ai:employee:create', '创建数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (182, 1001, 'ai:employee:update', '编辑数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (183, 1001, 'ai:employee:delete', '删除数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (184, 1001, 'ai:employee:status', '启停数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (185, 1001, 'ai:employee:skills', '配置数字员工技能', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (186, 1001, 'ai:llm:create', '创建 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (187, 1001, 'ai:llm:update', '编辑 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (188, 1001, 'ai:llm:delete', '删除 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (189, 1001, 'ai:llm:status', '启停 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (190, 1001, 'ai:skill:view', '查看技能列表', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (191, 1001, 'ai:chat:send', '发送 AI 对话', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0);

INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
  (213, 1001, 2001, 'ai:view', 0, NOW(), 0, NOW(), 0),
  (214, 1001, 2001, 'ai:employee:create', 0, NOW(), 0, NOW(), 0),
  (215, 1001, 2001, 'ai:employee:update', 0, NOW(), 0, NOW(), 0),
  (216, 1001, 2001, 'ai:employee:delete', 0, NOW(), 0, NOW(), 0),
  (217, 1001, 2001, 'ai:employee:status', 0, NOW(), 0, NOW(), 0),
  (218, 1001, 2001, 'ai:employee:skills', 0, NOW(), 0, NOW(), 0),
  (219, 1001, 2001, 'ai:llm:create', 0, NOW(), 0, NOW(), 0),
  (220, 1001, 2001, 'ai:llm:update', 0, NOW(), 0, NOW(), 0),
  (221, 1001, 2001, 'ai:llm:delete', 0, NOW(), 0, NOW(), 0),
  (222, 1001, 2001, 'ai:llm:status', 0, NOW(), 0, NOW(), 0),
  (223, 1001, 2001, 'ai:skill:view', 0, NOW(), 0, NOW(), 0),
  (224, 1001, 2001, 'ai:chat:send', 0, NOW(), 0, NOW(), 0);

SET FOREIGN_KEY_CHECKS = 1;
