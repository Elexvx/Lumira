CREATE TABLE IF NOT EXISTS `task_instance` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `task_type` varchar(32) NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint unsigned DEFAULT NULL,
  `business_title` varchar(255) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `assignee_user_id` bigint unsigned DEFAULT NULL,
  `assignee_role_id` bigint unsigned DEFAULT NULL,
  `assignee_dept_id` bigint unsigned DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `source_module` varchar(32) NOT NULL,
  `source_task_id` bigint unsigned DEFAULT NULL,
  `redirect_url` varchar(512) DEFAULT NULL,
  `due_time` datetime DEFAULT NULL,
  `completed_by` bigint unsigned DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_instance_pending_user` (`tenant_id`,`status`,`assignee_user_id`,`create_time`),
  KEY `idx_task_instance_pending_role` (`tenant_id`,`status`,`assignee_role_id`,`create_time`),
  KEY `idx_task_instance_pending_dept` (`tenant_id`,`status`,`assignee_dept_id`,`create_time`),
  KEY `idx_task_instance_source` (`tenant_id`,`source_module`,`source_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `approval_template` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `template_name` varchar(128) NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_template_business` (`tenant_id`,`business_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `approval_template_node` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `node_name` varchar(128) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `approval_policy` varchar(32) NOT NULL DEFAULT 'ANY_ONE',
  `approver_type` varchar(32) NOT NULL,
  `approver_id` bigint unsigned NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_approval_template_node_template` (`tenant_id`,`template_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `approval_instance` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint unsigned DEFAULT NULL,
  `business_title` varchar(255) NOT NULL,
  `summary` varchar(1024) DEFAULT NULL,
  `payload_json` longtext,
  `applicant_id` bigint unsigned NOT NULL,
  `applicant_name` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `current_node_id` bigint unsigned DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_approval_instance_applicant` (`tenant_id`,`applicant_id`,`create_time`),
  KEY `idx_approval_instance_status` (`tenant_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `approval_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `instance_id` bigint unsigned NOT NULL,
  `node_id` bigint unsigned NOT NULL,
  `assignee_user_id` bigint unsigned DEFAULT NULL,
  `assignee_role_id` bigint unsigned DEFAULT NULL,
  `assignee_dept_id` bigint unsigned DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `handled_by` bigint unsigned DEFAULT NULL,
  `handled_comment` varchar(1024) DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_approval_task_instance` (`tenant_id`,`instance_id`),
  KEY `idx_approval_task_status` (`tenant_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `approval_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `instance_id` bigint unsigned NOT NULL,
  `task_id` bigint unsigned DEFAULT NULL,
  `action` varchar(32) NOT NULL,
  `operator_id` bigint unsigned NOT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `comment` varchar(1024) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_approval_record_instance` (`tenant_id`,`instance_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_template` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `template_name` varchar(128) NOT NULL,
  `object_type` varchar(32) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_template_type` (`tenant_id`,`object_type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_dimension` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `dimension_name` varchar(128) NOT NULL,
  `weight` decimal(6,2) NOT NULL DEFAULT '0.00',
  `max_score` decimal(8,2) NOT NULL DEFAULT '100.00',
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_dimension_template` (`tenant_id`,`template_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_grade_rule` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `grade_code` varchar(32) NOT NULL,
  `grade_name` varchar(64) NOT NULL,
  `min_score` decimal(8,2) NOT NULL,
  `max_score` decimal(8,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_grade_template` (`tenant_id`,`template_id`,`min_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_instance` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `object_type` varchar(32) NOT NULL,
  `object_id` bigint unsigned DEFAULT NULL,
  `object_title` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SCORING',
  `creator_id` bigint unsigned NOT NULL,
  `reviewer_user_id` bigint unsigned DEFAULT NULL,
  `final_score` decimal(8,2) DEFAULT NULL,
  `final_grade` varchar(32) DEFAULT NULL,
  `archive_comment` varchar(1024) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_instance_status` (`tenant_id`,`status`,`create_time`),
  KEY `idx_evaluation_instance_creator` (`tenant_id`,`creator_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_score_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `instance_id` bigint unsigned NOT NULL,
  `assignee_user_id` bigint unsigned NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `total_score` decimal(8,2) DEFAULT NULL,
  `comment` varchar(1024) DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_score_task_user` (`tenant_id`,`assignee_user_id`,`status`,`create_time`),
  KEY `idx_evaluation_score_task_instance` (`tenant_id`,`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_score_detail` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `score_task_id` bigint unsigned NOT NULL,
  `dimension_id` bigint unsigned NOT NULL,
  `score` decimal(8,2) NOT NULL,
  `comment` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_score_detail_task` (`tenant_id`,`score_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_review_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `instance_id` bigint unsigned NOT NULL,
  `reviewer_id` bigint unsigned NOT NULL,
  `final_score` decimal(8,2) NOT NULL,
  `final_grade` varchar(32) NOT NULL,
  `comment` varchar(1024) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_evaluation_review_instance` (`tenant_id`,`instance_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `evaluation_result` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `instance_id` bigint unsigned NOT NULL,
  `object_type` varchar(32) NOT NULL,
  `object_id` bigint unsigned DEFAULT NULL,
  `object_title` varchar(255) NOT NULL,
  `final_score` decimal(8,2) NOT NULL,
  `final_grade` varchar(32) NOT NULL,
  `archive_comment` varchar(1024) DEFAULT NULL,
  `archived_by` bigint unsigned NOT NULL,
  `archived_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_result_instance` (`tenant_id`,`instance_id`),
  KEY `idx_evaluation_result_object` (`tenant_id`,`object_type`,`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `created_by`, `updated_by`, `deleted`)
SELECT t.tenant_id, p.permission_key, p.permission_name, p.permission_group, 'CORE', 0, 0, 0
FROM (SELECT 1001 AS tenant_id UNION ALL SELECT 1002) t
JOIN (
  SELECT 'task:view' permission_key, '查看任务中心' permission_name, 'task' permission_group UNION ALL
  SELECT 'approval:view', '查看审批中心', 'approval' UNION ALL
  SELECT 'approval:template:manage', '管理审批模板', 'approval' UNION ALL
  SELECT 'approval:submit', '发起审批', 'approval' UNION ALL
  SELECT 'approval:approve', '处理审批', 'approval' UNION ALL
  SELECT 'evaluation:view', '查看评审中心', 'evaluation' UNION ALL
  SELECT 'evaluation:template:manage', '管理评分模板', 'evaluation' UNION ALL
  SELECT 'evaluation:create', '发起评审', 'evaluation' UNION ALL
  SELECT 'evaluation:score', '提交评分', 'evaluation' UNION ALL
  SELECT 'evaluation:review', '复核评审', 'evaluation' UNION ALL
  SELECT 'evaluation:archive', '归档评审', 'evaluation'
) p;

INSERT IGNORE INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT t.tenant_id, t.role_id, p.permission_key, 0, 0, 0
FROM (SELECT 1001 AS tenant_id, 2001 AS role_id UNION ALL SELECT 1002, 2002) t
JOIN (
  SELECT 'task:view' permission_key UNION ALL
  SELECT 'approval:view' UNION ALL
  SELECT 'approval:template:manage' UNION ALL
  SELECT 'approval:submit' UNION ALL
  SELECT 'approval:approve' UNION ALL
  SELECT 'evaluation:view' UNION ALL
  SELECT 'evaluation:template:manage' UNION ALL
  SELECT 'evaluation:create' UNION ALL
  SELECT 'evaluation:score' UNION ALL
  SELECT 'evaluation:review' UNION ALL
  SELECT 'evaluation:archive'
) p;

INSERT IGNORE INTO `sys_menu` (`tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `updated_by`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES
  (1001, 0, 'tasks.root', '任务中心', 'MENU', '/tasks', '@/pages/tasks', 0, 0, 0, 'CheckSquareOutlined', 4, 'task:view', 'ENABLED'),
  (1001, 0, 'approvals.root', '审批中心', 'MENU', '/approvals', '@/pages/approvals', 0, 0, 0, 'AuditOutlined', 5, 'approval:view', 'ENABLED'),
  (1001, 0, 'evaluations.root', '评审中心', 'MENU', '/evaluations', '@/pages/evaluations', 0, 0, 0, 'StarOutlined', 6, 'evaluation:view', 'ENABLED'),
  (1002, 0, 'tasks.root', '任务中心', 'MENU', '/tasks', '@/pages/tasks', 0, 0, 0, 'CheckSquareOutlined', 4, 'task:view', 'ENABLED'),
  (1002, 0, 'approvals.root', '审批中心', 'MENU', '/approvals', '@/pages/approvals', 0, 0, 0, 'AuditOutlined', 5, 'approval:view', 'ENABLED'),
  (1002, 0, 'evaluations.root', '评审中心', 'MENU', '/evaluations', '@/pages/evaluations', 0, 0, 0, 'StarOutlined', 6, 'evaluation:view', 'ENABLED');
