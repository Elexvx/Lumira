DELETE FROM `platform_module_dependency`
WHERE `module_code` IN ('approval', 'evaluation', 'task')
   OR `dependency_module_code` IN ('approval', 'evaluation', 'task');

DELETE FROM `platform_module_definition`
WHERE `module_code` IN ('approval', 'evaluation', 'task');

DELETE FROM `sys_role_permission`
WHERE `permission_key` IN (
  'task:view',
  'approval:view',
  'approval:template:manage',
  'approval:submit',
  'approval:approve',
  'evaluation:view',
  'evaluation:template:manage',
  'evaluation:create',
  'evaluation:score',
  'evaluation:review',
  'evaluation:archive'
);

DELETE FROM `sys_menu`
WHERE `menu_code` IN ('tasks.root', 'approvals.root', 'evaluations.root')
   OR `path` IN ('/tasks', '/approvals', '/evaluations')
   OR `component` IN ('@/pages/tasks', '@/pages/approvals', '@/pages/evaluations');

DELETE FROM `sys_permission`
WHERE `permission_key` IN (
  'task:view',
  'approval:view',
  'approval:template:manage',
  'approval:submit',
  'approval:approve',
  'evaluation:view',
  'evaluation:template:manage',
  'evaluation:create',
  'evaluation:score',
  'evaluation:review',
  'evaluation:archive'
);

DROP TABLE IF EXISTS `task_instance`;
DROP TABLE IF EXISTS `evaluation_score_detail`;
DROP TABLE IF EXISTS `evaluation_score_task`;
DROP TABLE IF EXISTS `evaluation_review_record`;
DROP TABLE IF EXISTS `evaluation_result`;
DROP TABLE IF EXISTS `evaluation_instance`;
DROP TABLE IF EXISTS `evaluation_grade_rule`;
DROP TABLE IF EXISTS `evaluation_dimension`;
DROP TABLE IF EXISTS `evaluation_template`;
DROP TABLE IF EXISTS `approval_record`;
DROP TABLE IF EXISTS `approval_task`;
DROP TABLE IF EXISTS `approval_template_node`;
DROP TABLE IF EXISTS `approval_instance`;
DROP TABLE IF EXISTS `approval_template`;
