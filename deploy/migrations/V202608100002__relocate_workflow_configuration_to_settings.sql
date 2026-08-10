-- Workflow definitions are platform configuration. Keep approval task handling
-- in the main approval center and expose the designer through system settings.

UPDATE `sys_menu`
SET `menu_name` = '审批中心',
    `component` = 'redirect:/workflows/tasks',
    `status` = 'ENABLED',
    `updated_by` = 0,
    `deleted` = 0
WHERE `menu_code` = 'workflow.root';

UPDATE `sys_menu` AS workflow_config
JOIN `sys_menu` AS settings_root
  ON settings_root.`menu_code` = 'settings.root'
 AND settings_root.`deleted` = 0
SET workflow_config.`parent_id` = settings_root.`id`,
    workflow_config.`menu_name` = '工作流配置',
    workflow_config.`menu_type` = 'MENU',
    workflow_config.`path` = '/settings/workflows',
    workflow_config.`component` = '@/pages/workflow/WorkflowConfigPage',
    workflow_config.`icon` = 'BranchesOutlined',
    workflow_config.`sort_no` = 9,
    workflow_config.`permission_key` = 'workflow:config',
    workflow_config.`status` = 'ENABLED',
    workflow_config.`updated_by` = 0,
    workflow_config.`deleted` = 0
WHERE workflow_config.`menu_code` = 'workflow.config';

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT -1117, settings_root.`id`, 'workflow.config', '工作流配置', 'MENU',
       '/settings/workflows', '@/pages/workflow/WorkflowConfigPage', 'BranchesOutlined',
       9, 'workflow:config', 'ENABLED', 0, 0, 0
FROM `sys_menu` AS settings_root
WHERE settings_root.`menu_code` = 'settings.root'
  AND settings_root.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1
      FROM `sys_menu` AS existing_workflow_config
      WHERE existing_workflow_config.`menu_code` = 'workflow.config'
  )
LIMIT 1;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES ('platform', 'menu-tree', 1, 'migration:V202608100002:workflow-settings-navigation', NOW())
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
