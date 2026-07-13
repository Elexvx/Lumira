ALTER TABLE `ai_skill`
  ADD COLUMN `permission_key` varchar(128) DEFAULT NULL AFTER `need_confirm`,
  ADD COLUMN `input_schema_json` longtext AFTER `permission_key`;

INSERT INTO `ai_skill`
(`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `permission_key`, `input_schema_json`, `enabled`, `is_deleted`)
VALUES
('audit.ai_call.search', '检索 AI 工具审计', 'audit', '按数字员工、技能编码和结果状态检索 AI 调用审计日志。', 'MEDIUM', 1, 0, 'audit:view', '{"type":"object","properties":{}}', 1, 0),
('file.object.search', '检索文件对象', 'file', '按关键词、类型和状态检索文件中心对象。', 'MEDIUM', 1, 0, 'system:file:view', '{"type":"object","properties":{}}', 1, 0),
('system.config.read', '读取非敏感系统配置', 'system', '按配置键读取非敏感平台配置。', 'MEDIUM', 1, 0, 'system:config:view', '{"type":"object","properties":{}}', 1, 0),
('system.menu.list', '读取系统菜单与模块入口', 'system', '按当前账号权限读取系统菜单、路由、权限键和状态。', 'LOW', 1, 0, 'system:menu:view', '{"type":"object","properties":{}}', 1, 0),
('system.permission.snapshot', '读取当前权限上下文', 'system', '返回当前登录用户、角色、部门和权限集合。', 'LOW', 1, 0, NULL, '{"type":"object","properties":{}}', 1, 0),
('system.user.create', '新增系统用户', 'system', '在当前账号权限范围内新增系统用户。', 'HIGH', 0, 1, 'system:user:create', '{"type":"object","properties":{}}', 1, 0),
('system.user.search', '检索系统用户', 'system', '按关键词和状态检索当前系统用户。', 'MEDIUM', 1, 0, 'system:user:view', '{"type":"object","properties":{}}', 1, 0),
('system.user.update', '编辑系统用户', 'system', '在当前账号权限范围内编辑用户基础信息、角色和部门。', 'HIGH', 0, 1, 'system:user:update', '{"type":"object","properties":{}}', 1, 0)
ON DUPLICATE KEY UPDATE `is_deleted` = 0;
