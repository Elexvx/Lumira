CREATE TABLE IF NOT EXISTS `ai_tool_policy` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `policy_name` varchar(128) NOT NULL,
  `tool_code` varchar(128) NOT NULL DEFAULT '*',
  `action_type` varchar(64) DEFAULT NULL,
  `risk_level` varchar(32) DEFAULT NULL,
  `match_type` varchar(32) NOT NULL DEFAULT 'KEYWORD',
  `match_value` varchar(512) DEFAULT NULL,
  `verdict` varchar(32) NOT NULL DEFAULT 'DENY',
  `message` varchar(1024) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_policy_tenant_enabled` (`tenant_id`,`enabled`,`is_deleted`),
  KEY `idx_ai_tool_policy_tool` (`tenant_id`,`tool_code`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_tool_call_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
  `tool_code` varchar(128) NOT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `action_type` varchar(64) DEFAULT NULL,
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `summary` varchar(1024) DEFAULT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `requires_confirm` tinyint unsigned NOT NULL DEFAULT '1',
  `supervisor_verdict` varchar(32) NOT NULL DEFAULT 'REQUIRE_CONFIRM',
  `supervisor_message` varchar(1024) DEFAULT NULL,
  `policy_verdict` varchar(32) NOT NULL DEFAULT 'ALLOW',
  `policy_message` varchar(1024) DEFAULT NULL,
  `arguments_json` longtext,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime NOT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_plan_owner` (`tenant_id`,`owner_user_id`,`status`,`expires_at`),
  KEY `idx_ai_tool_plan_conversation` (`tenant_id`,`conversation_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `ai_tool_audit_log`
  ADD COLUMN `supervisor_verdict` varchar(32) DEFAULT NULL AFTER `confirm_result`,
  ADD COLUMN `supervisor_message` varchar(1024) DEFAULT NULL AFTER `supervisor_verdict`,
  ADD COLUMN `policy_match` varchar(1024) DEFAULT NULL AFTER `supervisor_message`,
  ADD COLUMN `confirmed_by` bigint unsigned DEFAULT NULL AFTER `policy_match`,
  ADD COLUMN `confirmed_at` datetime DEFAULT NULL AFTER `confirmed_by`;

INSERT INTO `ai_tool_policy` (`tenant_id`, `policy_name`, `tool_code`, `action_type`, `risk_level`, `match_type`, `match_value`, `verdict`, `message`, `enabled`, `is_deleted`)
SELECT 1001, '禁止读取或修改密钥类配置', '*', NULL, NULL, 'KEYWORD', 'password,secret,token,credential,private,api_key,密钥,密码,令牌', 'DENY', '命中平台防护规则：敏感密钥、密码或令牌不允许由 AI 工具读取或修改。', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `ai_tool_policy` WHERE tenant_id = 1001 AND policy_name = '禁止读取或修改密钥类配置' AND is_deleted = 0);

INSERT INTO `ai_tool_policy` (`tenant_id`, `policy_name`, `tool_code`, `action_type`, `risk_level`, `match_type`, `match_value`, `verdict`, `message`, `enabled`, `is_deleted`)
SELECT 1001, '禁止直接执行 SQL 或脚本', '*', NULL, NULL, 'KEYWORD', 'drop table,truncate,delete from,update sys_,insert into,sql,脚本,命令行,shell', 'DENY', '命中平台防护规则：AI 不允许执行 SQL、脚本或命令行。', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `ai_tool_policy` WHERE tenant_id = 1001 AND policy_name = '禁止直接执行 SQL 或脚本' AND is_deleted = 0);

INSERT INTO `ai_tool_policy` (`tenant_id`, `policy_name`, `tool_code`, `action_type`, `risk_level`, `match_type`, `match_value`, `verdict`, `message`, `enabled`, `is_deleted`)
SELECT 1001, '禁止跨租户操作', '*', NULL, NULL, 'KEYWORD', 'tenantId,currentTenantId,crossTenant,跨租户', 'DENY', '命中平台防护规则：AI 工具只能操作当前登录租户和权限范围内的数据。', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `ai_tool_policy` WHERE tenant_id = 1001 AND policy_name = '禁止跨租户操作' AND is_deleted = 0);

INSERT INTO `ai_tool_policy` (`tenant_id`, `policy_name`, `tool_code`, `action_type`, `risk_level`, `match_type`, `match_value`, `verdict`, `message`, `enabled`, `is_deleted`)
SELECT 1001, '禁止修改默认管理员和自身关键状态', 'system.user.*', NULL, NULL, 'KEYWORD', '1001,admin,DISABLED,禁用自己,删除自己,默认管理员', 'DENY', '命中平台防护规则：默认管理员和当前账号关键状态不允许通过 AI 工具修改。', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `ai_tool_policy` WHERE tenant_id = 1001 AND policy_name = '禁止修改默认管理员和自身关键状态' AND is_deleted = 0);

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
SELECT * FROM (
  SELECT 'system.user.create','新增系统用户','system','在当前租户和当前账号权限范围内新增系统用户。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.user.update','编辑系统用户','system','在当前租户和当前账号权限范围内编辑用户基础信息、角色和部门。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.user.status','启停系统用户','system','在当前租户和当前账号权限范围内启用或禁用用户。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.user.delete','删除系统用户','system','在当前租户和当前账号权限范围内删除用户。','HIGH',0,1,1,0
  UNION ALL SELECT 'profile.avatar.update','修改当前用户头像','profile','仅修改当前登录用户自己的头像。','MEDIUM',0,1,1,0
  UNION ALL SELECT 'system.role.create','新增角色','system','在当前租户新增角色。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.role.update','编辑角色','system','在当前租户编辑角色基础信息。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.role.permissions','配置角色权限','system','在当前租户更新角色权限集合。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.role.delete','删除角色','system','在当前租户删除角色。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.tenant.create','新增租户','system','新增平台租户。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.tenant.update','编辑租户','system','编辑平台租户。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.tenant.delete','删除租户','system','删除平台租户。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.menu.create','新增菜单','system','新增当前租户自定义菜单。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.menu.update','编辑菜单','system','编辑当前租户自定义菜单。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.menu.status','启停菜单','system','更新当前租户菜单状态。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.menu.delete','删除菜单','system','删除当前租户自定义菜单。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.dict_type.create','新增字典类型','system','新增当前租户字典类型。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.dict_type.update','编辑字典类型','system','编辑当前租户字典类型。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.dict_type.delete','删除字典类型','system','删除当前租户非系统字典类型。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.dict_item.create','新增字典项','system','新增当前租户字典项。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.dict_item.update','编辑字典项','system','编辑当前租户字典项。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.dict_item.delete','删除字典项','system','删除当前租户字典项。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.config.create','新增系统配置','system','新增非敏感平台或租户配置。','HIGH',0,1,1,0
  UNION ALL SELECT 'system.config.update','编辑系统配置','system','编辑非敏感平台或租户配置。','HIGH',0,1,1,0
  UNION ALL SELECT 'platform.branding.update','更新品牌设置','system','更新网站名称、Logo、页脚等品牌设置。','HIGH',0,1,1,0
  UNION ALL SELECT 'platform.agreement.update','更新协议设置','system','更新用户协议与隐私协议设置。','HIGH',0,1,1,0
  UNION ALL SELECT 'platform.watermark.update','更新水印设置','system','更新平台水印设置。','HIGH',0,1,1,0
  UNION ALL SELECT 'platform.floating_window.update','更新浮窗设置','system','更新全局浮窗设置。','HIGH',0,1,1,0
) AS seeded(skill_code, skill_name, category, description, risk_level, read_only, need_confirm, enabled, is_deleted)
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_skill` existing
  WHERE existing.skill_code = seeded.skill_code
    AND existing.is_deleted = 0
);
