SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO `sys_permission` (
    `tenant_id`,
    `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    (1001, 'ai:assistant:view', '查看 AI 助手', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:chat:send', '发送 AI 对话', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:create', '新建数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:delete', '删除数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:skills', '配置数字员工技能', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:status', '启停数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:update', '编辑数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:bind', '绑定数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:create', '新建知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:delete', '删除知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:document:delete', '删除知识库文档', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:document:index', '重建文档索引', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:document:upload', '上传知识库文档', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:query', '检索知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:share', '发布企业知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:update', '编辑知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:view', '查看知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:create', '新建模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:delete', '删除模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:status', '启停模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:update', '编辑模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:tool:execute', '执行 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:tool:invoke', '调用 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:tool:view', '查看 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:view', '访问 AI', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'audit:login:view', '查看登录日志', 'audit', 'CORE', NULL, 0, 0, 0),
    (1001, 'audit:operation:view', '查看操作日志', 'audit', 'CORE', NULL, 0, 0, 0),
    (1001, 'audit:view', '查看审计中心', 'audit', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:activity:view', '查看活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:activity:create', '新建活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:activity:update', '编辑活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:activity:delete', '删除活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:competition:view', '查看赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:competition:create', '新建赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:competition:update', '编辑赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:competition:delete', '删除赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:registration:view', '查看赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:registration:create', '创建赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:registration:update', '编辑赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:registration:pay', '支付报名费用', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:material:view', '查看报名材料', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:material:submit', '提交报名材料', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:stage:view', '查看赛事阶段', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:stage:manage', '管理赛事阶段', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-template:view', '查看证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-template:create', '新建证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-template:update', '编辑证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-template:publish', '发布证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-template:delete', '删除证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-batch:view', '查看证书批次', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-batch:create', '生成证书批次', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate-batch:download', '下载证书批次', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate:view', '查看证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate:download', '下载证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate:regenerate', '重新生成证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:certificate:revoke', '撤销证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'expert:view', '查看专家', 'expert', 'CORE', NULL, 0, 0, 0),
    (1001, 'expert:create', '新建专家', 'expert', 'CORE', NULL, 0, 0, 0),
    (1001, 'expert:update', '编辑专家', 'expert', 'CORE', NULL, 0, 0, 0),
    (1001, 'expert:delete', '删除专家', 'expert', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:project:view', '查看项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:project:create', '新建项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:project:update', '编辑项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:project:delete', '删除项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'dashboard:view', '查看工作台', 'dashboard', 'CORE', NULL, 0, 0, 0),
    (1001, 'download:center:view', '查看下载中心', 'download', 'CORE', NULL, 0, 0, 0),
    (1001, 'localization:view', '查看多语言设置', 'localization', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:config:test', '测试支付配置', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:config:update', '编辑支付配置', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:config:view', '查看支付配置', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:order:create', '创建支付订单', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:order:view', '查看支付订单', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:refund:create', '创建退款', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:refund:view', '查看退款', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:view', '访问支付中心', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:webhook:retry', '重试支付回调', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:webhook:view', '查看支付回调', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'plugin:management:view', '查看插件管理', 'plugin', 'CORE', NULL, 0, 0, 0),
    (1001, 'plugin:sensitive-words:import', '导入敏感词', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    (1001, 'plugin:sensitive-words:manage', '管理敏感词', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    (1001, 'plugin:sensitive-words:view', '查看敏感词', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    (1001, 'plugin:work-order-feedback:create', '提交工单反馈', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    (1001, 'plugin:work-order-feedback:manage', '处理工单反馈', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    (1001, 'plugin:work-order-feedback:view', '查看工单反馈', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    (1001, 'profile:view', '查看个人中心', 'profile', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:config:update', '编辑系统配置', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:config:view', '查看系统配置', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:create', '新建部门', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:delete', '删除部门', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:update', '编辑部门', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:view', '查看部门', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:create', '新建字典', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:delete', '删除字典', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:update', '编辑字典', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:view', '查看字典', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:delete', '删除文件', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:manage', '管理文件', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:upload', '上传文件', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:view', '查看文件', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:create', '新建菜单', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:delete', '删除菜单', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:status', '启停菜单', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:update', '编辑菜单', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:view', '查看菜单', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:docs:view', '查看接口文档', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:redis:view', '查看 Redis 监控', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:service:view', '查看服务监控', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:view', '查看系统监控', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:notification:view', '查看消息通知', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:online-user:ban', '封禁在线用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:online-user:kick', '强退在线用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:online-user:view', '查看在线用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:create', '新建角色', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:delete', '删除角色', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:grant', '分配角色', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:permissions', '配置角色权限', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:update', '编辑角色', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:view', '查看角色', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:check', '检查系统更新', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:install', '安装系统更新', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:rollback', '回滚系统更新', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:view', '查看系统更新', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:create', '新建用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:delete', '删除用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:export', '导出用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:sensitive:view', '查看用户敏感信息', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:status', '启停用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:update', '编辑用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:view', '查看用户', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:verification:manage', '管理认证设置', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:verification:view', '查看认证设置', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:view', '访问系统管理', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:view', '查看团队', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:create', '新建团队', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:update', '编辑团队', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:delete', '删除团队', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:view', '查看团队成员', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:invite', '邀请团队成员', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:remove', '移除团队成员', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:role-update', '调整团队成员角色', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'user:center:view', '访问用户中心', 'user', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `plugin_code` = VALUES(`plugin_code`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_menu` (
    `tenant_id`,
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES
    (1001, -955, 0, 'dashboard.home', '首页', 'MENU', '/dashboard/home', '@/pages/dashboard/DashboardHomePage', 'DashboardOutlined', 0, 'dashboard:view', 'ENABLED', 0, 0, 0),
    (1001, -956, 0, 'files.download-center', '下载中心', 'MENU', '/download-center', '@/pages/files/DownloadCenter', 'DownloadOutlined', 1, 'download:center:view', 'ENABLED', 0, 0, 0),
    (1001, -990, 0, 'ai.root', 'AI', 'CATALOG', '/ai', 'redirect:/ai/assistant', 'RobotOutlined', 2, NULL, 'ENABLED', 0, 0, 0),
    (1001, -989, -990, 'ai.assistant', 'AI 助手', 'MENU', '/ai/assistant', '@/pages/ai/Assistant', 'RobotOutlined', 1, 'ai:assistant:view', 'ENABLED', 0, 0, 0),
    (1001, -1051, -989, 'ai.assistant.send', '发送对话', 'BUTTON', NULL, NULL, NULL, 1, 'ai:chat:send', 'ENABLED', 0, 0, 0),
    (1001, -988, -990, 'ai.knowledge', '知识库', 'MENU', '/ai/knowledge', '@/pages/ai/knowledge/KnowledgePage', 'FileSearchOutlined', 2, 'ai:knowledge:view', 'ENABLED', 0, 0, 0),
    (1001, -1041, 0, 'activity.root', '活动', 'CATALOG', '/activities', 'redirect:/activities/management', 'CalendarOutlined', 3, NULL, 'ENABLED', 0, 0, 0),
    (1001, -1052, -1041, 'activity.activities', '活动管理', 'MENU', '/activities/management', '@/pages/activity', 'CalendarOutlined', 1, 'aiadc:activity:view', 'ENABLED', 0, 0, 0),
    (1001, -1053, -1041, 'activity.search', '活动查询', 'MENU', '/activities/search', '@/pages/activity', 'SearchOutlined', 2, 'aiadc:activity:view', 'ENABLED', 0, 0, 0),
    (1001, -1043, -1052, 'activity.activities.create', '新增活动', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:activity:create', 'ENABLED', 0, 0, 0),
    (1001, -1044, -1052, 'activity.activities.update', '编辑活动', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:activity:update', 'ENABLED', 0, 0, 0),
    (1001, -1045, -1052, 'activity.activities.delete', '删除活动', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:activity:delete', 'ENABLED', 0, 0, 0),
    (1001, -1070, 0, 'competition.root', '赛事', 'CATALOG', '/competitions', 'redirect:/competitions/management', 'TrophyOutlined', 4, NULL, 'ENABLED', 0, 0, 0),
    (1001, -1071, -1070, 'competition.management', '赛事管理', 'MENU', '/competitions/management', '@/pages/competition', 'TrophyOutlined', 1, 'aiadc:competition:view', 'ENABLED', 0, 0, 0),
    (1001, -1075, -1070, 'competition.registration', '赛事报名', 'MENU', '/competitions/register', '@/pages/competition', 'FormOutlined', 2, 'aiadc:registration:create', 'ENABLED', 0, 0, 0),
    (1001, -1091, 0, 'project.root', '项目', 'CATALOG', '/projects', 'redirect:/projects/management', 'ProjectOutlined', 5, NULL, 'ENABLED', 0, 0, 0),
    (1001, -1092, -1091, 'project.management', '项目管理', 'MENU', '/projects/management', '@/pages/project', 'ProjectOutlined', 1, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (1001, -1093, -1092, 'project.management.create', '新增项目', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:project:create', 'ENABLED', 0, 0, 0),
    (1001, -1094, -1092, 'project.management.update', '编辑项目', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:project:update', 'ENABLED', 0, 0, 0),
    (1001, -1095, -1092, 'project.management.delete', '删除项目', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:project:delete', 'ENABLED', 0, 0, 0),
    (1001, -1079, 0, 'certificate.root', '证书管理', 'CATALOG', '/certificates', 'redirect:/certificates/templates', 'FileProtectOutlined', 5, NULL, 'ENABLED', 0, 0, 0),
    (1001, -1080, -1079, 'certificate.templates', '证书模板', 'MENU', '/certificates/templates', '@/pages/certificates/TemplatesPage', 'FileProtectOutlined', 1, 'aiadc:certificate-template:view', 'ENABLED', 0, 0, 0),
    (1001, -1081, -1079, 'certificate.generate', '证书生成', 'MENU', '/certificates/generate', '@/pages/certificates/GeneratePage', 'FileDoneOutlined', 2, 'aiadc:certificate-batch:create', 'ENABLED', 0, 0, 0),
    (1001, -1082, -1079, 'certificate.records', '证书记录', 'MENU', '/certificates/records', '@/pages/certificates/RecordsPage', 'AuditOutlined', 3, 'aiadc:certificate:view', 'ENABLED', 0, 0, 0),
    (1001, -1072, -1071, 'competition.management.create', '新增赛事', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:competition:create', 'ENABLED', 0, 0, 0),
    (1001, -1073, -1071, 'competition.management.update', '编辑赛事', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:competition:update', 'ENABLED', 0, 0, 0),
    (1001, -1074, -1071, 'competition.management.delete', '删除赛事', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:competition:delete', 'ENABLED', 0, 0, 0),
    (1001, -1083, -1080, 'certificate.templates.create', 'Create certificate template', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate-template:create', 'ENABLED', 0, 0, 0),
    (1001, -1084, -1080, 'certificate.templates.update', 'Update certificate template', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:certificate-template:update', 'ENABLED', 0, 0, 0),
    (1001, -1085, -1080, 'certificate.templates.publish', 'Publish certificate template', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:certificate-template:publish', 'ENABLED', 0, 0, 0),
    (1001, -1086, -1080, 'certificate.templates.delete', 'Archive certificate template', 'BUTTON', NULL, NULL, NULL, 4, 'aiadc:certificate-template:delete', 'ENABLED', 0, 0, 0),
    (1001, -1087, -1081, 'certificate.generate.create', 'Generate certificates', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate-batch:create', 'ENABLED', 0, 0, 0),
    (1001, -1088, -1082, 'certificate.records.download', 'Download certificate', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate:download', 'ENABLED', 0, 0, 0),
    (1001, -1089, -1082, 'certificate.records.regenerate', 'Regenerate certificate', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:certificate:regenerate', 'ENABLED', 0, 0, 0),
    (1001, -1090, -1082, 'certificate.records.revoke', 'Revoke certificate', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:certificate:revoke', 'ENABLED', 0, 0, 0),
    (1001, -1060, 0, 'expert.root', '专家库', 'CATALOG', '/experts', 'redirect:/experts/management', 'SolutionOutlined', 6, NULL, 'ENABLED', 0, 0, 0),
    (1001, -1061, -1060, 'expert.management', '专家管理', 'MENU', '/experts/management', '@/pages/expert', 'SolutionOutlined', 1, 'expert:view', 'ENABLED', 0, 0, 0),
    (1001, -1065, -1060, 'expert.query', '专家查询', 'MENU', '/experts/query', '@/pages/expert', 'SearchOutlined', 2, 'expert:view', 'ENABLED', 0, 0, 0),
    (1001, -1062, -1061, 'expert.management.create', '创建专家', 'BUTTON', NULL, NULL, NULL, 1, 'expert:create', 'ENABLED', 0, 0, 0),
    (1001, -1063, -1061, 'expert.management.update', '编辑专家', 'BUTTON', NULL, NULL, NULL, 2, 'expert:update', 'ENABLED', 0, 0, 0),
    (1001, -1064, -1061, 'expert.management.delete', '删除专家', 'BUTTON', NULL, NULL, NULL, 3, 'expert:delete', 'ENABLED', 0, 0, 0),
    (1001, -957, 0, 'team.root', '团队', 'CATALOG', '/team', 'redirect:/team/management', 'TeamOutlined', 6, 'team:view', 'ENABLED', 0, 0, 0),
    (1001, -1040, -957, 'team.management', '团队管理', 'MENU', '/team/management', '@/pages/team', 'TeamOutlined', 1, 'team:view', 'ENABLED', 0, 0, 0),
    (1001, -1050, -957, 'team.search', '团队查询', 'MENU', '/team/search', '@/pages/team', 'SearchOutlined', 2, 'team:view', 'ENABLED', 0, 0, 0),
    (1001, -958, -1040, 'team.create', '创建团队', 'BUTTON', NULL, NULL, NULL, 1, 'team:create', 'ENABLED', 0, 0, 0),
    (1001, -959, -1040, 'team.update', '编辑团队', 'BUTTON', NULL, NULL, NULL, 2, 'team:update', 'ENABLED', 0, 0, 0),
    (1001, -960, -1040, 'team.delete', '删除团队', 'BUTTON', NULL, NULL, NULL, 3, 'team:delete', 'ENABLED', 0, 0, 0),
    (1001, -961, -1040, 'team.member.view', '查看成员', 'BUTTON', NULL, NULL, NULL, 4, 'team:member:view', 'ENABLED', 0, 0, 0),
    (1001, -962, -1040, 'team.member.invite', '邀请成员', 'BUTTON', NULL, NULL, NULL, 5, 'team:member:invite', 'ENABLED', 0, 0, 0),
    (1001, -963, -1040, 'team.member.remove', '移除成员', 'BUTTON', NULL, NULL, NULL, 6, 'team:member:remove', 'ENABLED', 0, 0, 0),
    (1001, -964, -1040, 'team.member.role-update', '更新成员角色', 'BUTTON', NULL, NULL, NULL, 7, 'team:member:role-update', 'ENABLED', 0, 0, 0),
    (1001, -950, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/layouts/SettingsLayout', 'TeamOutlined', 18, 'user:center:view', 'ENABLED', 0, 0, 0),
    (1001, -951, -950, 'system.users', '用户管理', 'MENU', '/user-center/users', '@/pages/system/users', 'TeamOutlined', 21, 'system:user:view', 'ENABLED', 0, 0, 0),
    (1001, -965, -951, 'system.users.create', '创建用户', 'BUTTON', NULL, NULL, NULL, 1, 'system:user:create', 'ENABLED', 0, 0, 0),
    (1001, -966, -951, 'system.users.update', '编辑用户', 'BUTTON', NULL, NULL, NULL, 2, 'system:user:update', 'ENABLED', 0, 0, 0),
    (1001, -967, -951, 'system.users.delete', '删除用户', 'BUTTON', NULL, NULL, NULL, 3, 'system:user:delete', 'ENABLED', 0, 0, 0),
    (1001, -968, -951, 'system.users.export', '导出用户', 'BUTTON', NULL, NULL, NULL, 4, 'system:user:export', 'ENABLED', 0, 0, 0),
    (1001, -954, -950, 'system.departments', '组织部门', 'MENU', '/user-center/departments', '@/pages/system/departments', 'ApartmentOutlined', 22, 'system:department:view', 'ENABLED', 0, 0, 0),
    (1001, -952, -950, 'system.online-users', '在线用户', 'MENU', '/user-center/online-users', '@/pages/system/online-users', 'UserSwitchOutlined', 23, 'system:online-user:view', 'ENABLED', 0, 0, 0),
    (1001, -953, -950, 'system.roles', '角色管理', 'MENU', '/user-center/roles', '@/pages/system/roles', 'SafetyOutlined', 24, 'system:role:view', 'ENABLED', 0, 0, 0),
    (1001, -969, -953, 'system.roles.create', '创建角色', 'BUTTON', NULL, NULL, NULL, 1, 'system:role:create', 'ENABLED', 0, 0, 0),
    (1001, -970, -953, 'system.roles.update', '编辑角色', 'BUTTON', NULL, NULL, NULL, 2, 'system:role:update', 'ENABLED', 0, 0, 0),
    (1001, -971, -953, 'system.roles.delete', '删除角色', 'BUTTON', NULL, NULL, NULL, 3, 'system:role:delete', 'ENABLED', 0, 0, 0),
    (1001, -972, -953, 'system.roles.grant', '授权角色', 'BUTTON', NULL, NULL, NULL, 4, 'system:role:grant', 'ENABLED', 0, 0, 0),
    (1001, -940, 0, 'user.center.personal', '个人中心', 'CATALOG', '/user-center/personal-center', '@/layouts/SettingsLayout', 'IdcardOutlined', 19, 'profile:view', 'ENABLED', 0, 0, 0),
    (1001, -941, -940, 'profile.center', '个人资料', 'MENU', '/user-center/personal-center/profile', '@/pages/profile/Center', 'UserOutlined', 1, 'profile:view', 'ENABLED', 0, 0, 0),
    (1001, -942, -940, 'files.my', '我的文件', 'MENU', '/user-center/personal-center/files', '@/pages/files/Center', 'FileOutlined', 2, 'system:file:view', 'ENABLED', 0, 0, 0),
    (1001, -1000, 0, 'settings.root', '系统设置', 'CATALOG', '/settings', '@/layouts/SettingsLayout', 'SettingOutlined', 20, 'system:view', 'ENABLED', 0, 0, 0),
    (1001, -1001, -1000, 'settings.menus', '菜单管理', 'MENU', '/settings/menus', '@/pages/settings/menus', 'AppstoreOutlined', 2, 'system:menu:view', 'ENABLED', 0, 0, 0),
    (1001, -1020, -1001, 'settings.menus.create', '创建菜单', 'BUTTON', NULL, NULL, NULL, 1, 'system:menu:create', 'ENABLED', 0, 0, 0),
    (1001, -1021, -1001, 'settings.menus.update', '编辑菜单', 'BUTTON', NULL, NULL, NULL, 2, 'system:menu:update', 'ENABLED', 0, 0, 0),
    (1001, -1022, -1001, 'settings.menus.delete', '删除菜单', 'BUTTON', NULL, NULL, NULL, 3, 'system:menu:delete', 'ENABLED', 0, 0, 0),
    (1001, -1002, -1000, 'settings.dicts', '字典管理', 'MENU', '/settings/dicts', '@/pages/settings/dicts', 'DatabaseOutlined', 3, 'system:dict:view', 'ENABLED', 0, 0, 0),
    (1001, -1023, -1002, 'settings.dicts.create', '创建字典', 'BUTTON', NULL, NULL, NULL, 1, 'system:dict:create', 'ENABLED', 0, 0, 0),
    (1001, -1024, -1002, 'settings.dicts.update', '编辑字典', 'BUTTON', NULL, NULL, NULL, 2, 'system:dict:update', 'ENABLED', 0, 0, 0),
    (1001, -1025, -1002, 'settings.dicts.delete', '删除字典', 'BUTTON', NULL, NULL, NULL, 3, 'system:dict:delete', 'ENABLED', 0, 0, 0),
    (1001, -1003, -1000, 'settings.profile-fields', '字段管理', 'MENU', '/settings/profile-fields', '@/pages/settings/profile-fields', 'FormOutlined', 4, 'system:config:view', 'ENABLED', 0, 0, 0),
    (1001, -1004, -1000, 'settings.personalization', '个性化设置', 'MENU', '/settings/personalization', '@/pages/settings/personalization', 'SkinOutlined', 5, 'system:config:view', 'ENABLED', 0, 0, 0),
    (1001, -1005, -1000, 'settings.security', '安全设置', 'MENU', '/settings/security', '@/pages/settings/security', 'SafetyOutlined', 6, 'system:config:view', 'ENABLED', 0, 0, 0),
    (1001, -1006, -1000, 'settings.verification', '验证管理', 'MENU', '/settings/verification', '@/pages/settings/verification', 'SafetyOutlined', 7, 'system:verification:view', 'ENABLED', 0, 0, 0),
    (1001, -1007, -1000, 'settings.payment', '支付设置', 'MENU', '/settings/payment', '@/pages/settings/payment', 'CreditCardOutlined', 8, 'payment:view', 'ENABLED', 0, 0, 0),
    (1001, -1012, -1000, 'settings.files', '全站文件管理', 'MENU', '/settings/files/all', '@/pages/settings/files/Center', 'FolderOpenOutlined', 9, 'system:file:manage', 'ENABLED', 0, 0, 0),
    (1001, -1008, -1000, 'settings.notifications', '通知中心', 'MENU', '/settings/notifications', '@/pages/settings/notifications/index', 'NotificationOutlined', 9, 'system:notification:view', 'ENABLED', 0, 0, 0),
    (1001, -1015, -1000, 'settings.monitoring', '系统监控', 'MENU', '/settings/monitoring', '@/pages/settings/monitoring/index', 'FundOutlined', 10, 'system:monitor:view', 'ENABLED', 0, 0, 0),
    (1001, -1013, -1000, 'settings.monitoring.api-docs', '接口文档', 'MENU', '/settings/api-docs', '@/pages/settings/monitoring/ApiDocs', 'FileTextOutlined', 11, 'system:monitor:docs:view', 'ENABLED', 0, 0, 0),
    (1001, -1014, -1000, 'settings.monitoring.audit', '审计中心', 'MENU', '/settings/audit', '@/pages/settings/monitoring/Audit', 'AuditOutlined', 12, 'audit:view', 'ENABLED', 0, 0, 0),
    (1001, -1009, -1000, 'settings.plugins', '插件管理中心', 'MENU', '/settings/plugins', '@/pages/settings/plugins', 'ApiOutlined', 10, 'plugin:management:view', 'ENABLED', 0, 0, 0),
    (1001, -1010, -1000, 'settings.ai-employees', '数字员工', 'MENU', '/settings/ai-employees', '@/pages/settings/ai-employees', 'RobotOutlined', 24, 'ai:view', 'ENABLED', 0, 0, 0),
    (1001, -1011, -1000, 'localization.root', '本地化中心', 'MENU', '/settings/localization', '@/pages/settings/localization', 'TranslationOutlined', 29, 'localization:view', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `parent_id` = VALUES(`parent_id`),
    `menu_name` = VALUES(`menu_name`),
    `menu_type` = VALUES(`menu_type`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `icon` = VALUES(`icon`),
    `sort_no` = VALUES(`sort_no`),
    `permission_key` = VALUES(`permission_key`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `default_home_path`, `created_by`, `updated_by`, `deleted`)
VALUES
    (1001, 1001, 'ADMIN', 'Administrator', 'SYSTEM', '/dashboard/home', 0, 0, 0),
    (1002, 1001, 'commonuser', 'Common User', 'BUSINESS', '/dashboard/home', 0, 0, 0),
    (1003, 1001, 'EXPERT', 'Expert', 'BUSINESS', '/dashboard/home', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `role_type` = VALUES(`role_type`),
    `default_home_path` = VALUES(`default_home_path`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

DELETE rp
FROM `sys_role_permission` rp
JOIN `sys_role` r
  ON r.`tenant_id` = rp.`tenant_id`
 AND r.`id` = rp.`role_id`
WHERE r.`tenant_id` = 1001
  AND r.`role_code` = 'ADMIN'
  AND rp.`permission_key` = '*';

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, r.`id`, p.`permission_key`, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p ON p.`tenant_id` = r.`tenant_id` AND p.`deleted` = 0
WHERE r.`tenant_id` = 1001
  AND r.`role_code` = 'ADMIN'
  AND r.`deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, r.`id`, p.`permission_key`, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p ON p.`tenant_id` = r.`tenant_id` AND p.`deleted` = 0
WHERE r.`tenant_id` = 1001
  AND r.`role_code` = 'commonuser'
  AND r.`deleted` = 0
  AND p.`permission_key` IN (
      'dashboard:view',
      'download:center:view',
      'user:center:view',
      'profile:view',
      'system:file:view',
      'system:file:upload',
      'ai:view',
      'ai:assistant:view',
      'ai:chat:send',
      'ai:knowledge:view',
      'aiadc:activity:view',
      'aiadc:competition:view',
      'aiadc:registration:view',
      'aiadc:registration:create',
      'aiadc:registration:update',
      'aiadc:registration:pay',
      'aiadc:material:view',
      'aiadc:material:submit',
      'aiadc:stage:view',
      'expert:view',
      'expert:create',
      'expert:update',
      'expert:delete',
      'aiadc:project:view',
      'aiadc:project:create',
      'team:view',
      'team:create',
      'team:member:view'
  )
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, r.`id`, p.`permission_key`, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p ON p.`tenant_id` = r.`tenant_id` AND p.`deleted` = 0
WHERE r.`tenant_id` = 1001
  AND r.`role_code` = 'EXPERT'
  AND r.`deleted` = 0
  AND p.`permission_key` IN ('dashboard:view', 'expert:view')
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'auth.default-registration-role-code', 'Default registration role', 'commonuser', 'PLATFORM', 1, 'Default role code assigned to newly registered users', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `config_name` = VALUES(`config_name`),
    `config_value` = VALUES(`config_value`),
    `config_scope` = VALUES(`config_scope`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `ddd_read_model_version` (`tenant_id`, `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`)
VALUES (1001, 'IAM', 'permission-snapshot', 1, 'manual.menu-refresh.20260625', NOW())
ON DUPLICATE KEY UPDATE
    `version` = `version` + 1,
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
COMMIT;
