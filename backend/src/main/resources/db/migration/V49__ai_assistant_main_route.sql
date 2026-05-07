SET FOREIGN_KEY_CHECKS = 0;

INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES
  (3029, 1001, 0, 'ai.assistant', 'AI 助手', 'MENU', '/ai', '@/pages/ai/Assistant', 0, NOW(), 0, NOW(), 0, 'RobotOutlined', 2, 'ai:chat:send', 'ENABLED'),
  (4029, 1002, 0, 'ai.assistant', 'AI 助手', 'MENU', '/ai', '@/pages/ai/Assistant', 0, NOW(), 0, NOW(), 0, 'RobotOutlined', 2, 'ai:chat:send', 'ENABLED');

SET FOREIGN_KEY_CHECKS = 1;
