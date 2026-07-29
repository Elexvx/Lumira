-- Expand-only migration for the versioned competition review domain.
CREATE TABLE IF NOT EXISTS `competition_review_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `plan_name` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `blind_mode` varchar(32) NOT NULL DEFAULT 'NONE',
  `required_reviewer_count` int NOT NULL DEFAULT '1',
  `minimum_submitted_count` int NOT NULL DEFAULT '1',
  `aggregate_method` varchar(32) NOT NULL DEFAULT 'AVERAGE',
  `score_scale` decimal(10,2) NOT NULL DEFAULT '100.00',
  `trim_highest_count` int NOT NULL DEFAULT '0',
  `trim_lowest_count` int NOT NULL DEFAULT '0',
  `criteria_version_id` bigint DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_plan_stage` (`competition_id`,`stage_id`,`deleted`),
  KEY `idx_competition_review_plan_status` (`status`,`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_criteria_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `version_name` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `total_weight` decimal(10,4) NOT NULL DEFAULT '1.0000',
  `content_hash` char(64) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_criteria_version` (`plan_id`,`version_no`,`deleted`),
  KEY `idx_competition_review_criteria_status` (`plan_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_criterion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `criteria_version_id` bigint NOT NULL,
  `criterion_code` varchar(64) NOT NULL,
  `criterion_name` varchar(255) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `weight` decimal(10,4) NOT NULL,
  `maximum_score` decimal(10,2) NOT NULL,
  `required` tinyint NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_criterion_code` (`criteria_version_id`,`criterion_code`,`deleted`),
  KEY `idx_competition_review_criterion_sort` (`criteria_version_id`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `criteria_version_id` bigint NOT NULL,
  `batch_no` varchar(64) NOT NULL,
  `batch_name` varchar(255) NOT NULL,
  `batch_type` varchar(32) NOT NULL DEFAULT 'STANDARD',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `assignment_strategy` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `minimum_reviewer_count` int NOT NULL DEFAULT '1',
  `candidate_count` int NOT NULL DEFAULT '0',
  `freeze_token` char(36) DEFAULT NULL,
  `frozen_at` datetime DEFAULT NULL,
  `review_deadline` datetime DEFAULT NULL,
  `finalized_at` datetime DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_batch_no` (`batch_no`,`deleted`),
  KEY `idx_competition_review_batch_plan` (`plan_id`,`status`,`deleted`,`created_at`),
  KEY `idx_competition_review_batch_stage` (`competition_id`,`stage_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `blind_code` varchar(64) DEFAULT NULL,
  `snapshot_json` longtext NOT NULL,
  `review_snapshot_json` longtext NOT NULL,
  `snapshot_hash` char(64) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'FROZEN',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_candidate` (`batch_id`,`registration_id`,`deleted`),
  UNIQUE KEY `uk_competition_review_blind_code` (`batch_id`,`blind_code`,`deleted`),
  KEY `idx_competition_review_candidate_status` (`batch_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `candidate_id` bigint NOT NULL,
  `expert_id` bigint NOT NULL,
  `expert_user_id` bigint DEFAULT NULL,
  `expert_user_uuid` char(36) DEFAULT NULL,
  `reviewer_weight` decimal(10,4) NOT NULL DEFAULT '1.0000',
  `status` varchar(32) NOT NULL DEFAULT 'ASSIGNED',
  `due_at` datetime DEFAULT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `declined_at` datetime DEFAULT NULL,
  `decline_reason` varchar(1000) DEFAULT NULL,
  `expired_at` datetime DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `revoke_reason` varchar(1000) DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `reassigned_from_id` bigint DEFAULT NULL,
  `conflict_reason` varchar(1000) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_assignment` (`candidate_id`,`expert_id`,`deleted`),
  KEY `idx_competition_review_assignment_expert` (`expert_user_id`,`expert_user_uuid`,`status`,`deleted`,`due_at`),
  KEY `idx_competition_review_assignment_batch` (`batch_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_sheet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `batch_id` bigint NOT NULL,
  `candidate_id` bigint NOT NULL,
  `expert_id` bigint NOT NULL,
  `version_no` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `total_score` decimal(12,4) DEFAULT NULL,
  `review_comment` varchar(4000) DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `corrected_from_id` bigint DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_sheet_version` (`assignment_id`,`version_no`,`deleted`),
  KEY `idx_competition_review_sheet_candidate` (`batch_id`,`candidate_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_score_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sheet_id` bigint NOT NULL,
  `criterion_id` bigint NOT NULL,
  `score` decimal(12,4) NOT NULL,
  `comment` varchar(2000) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_score_item` (`sheet_id`,`criterion_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_aggregate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `candidate_id` bigint NOT NULL,
  `aggregate_score` decimal(12,4) DEFAULT NULL,
  `minimum_score` decimal(12,4) DEFAULT NULL,
  `maximum_score` decimal(12,4) DEFAULT NULL,
  `score_stddev` decimal(12,4) DEFAULT NULL,
  `submitted_reviewer_count` int NOT NULL DEFAULT '0',
  `valid_reviewer_count` int NOT NULL DEFAULT '0',
  `rank_no` int DEFAULT NULL,
  `decision` varchar(32) NOT NULL DEFAULT 'PENDING',
  `decision_reason` varchar(2000) DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_by_uuid` char(36) DEFAULT NULL,
  `decided_at` datetime DEFAULT NULL,
  `anomaly_flags_json` longtext,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `calculated_at` datetime DEFAULT NULL,
  `finalized_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_aggregate` (`batch_id`,`candidate_id`,`deleted`),
  KEY `idx_competition_review_aggregate_rank` (`batch_id`,`status`,`deleted`,`rank_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `publication_version` int NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `payload_json` longtext NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `published_at` datetime DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `revoke_reason` varchar(1000) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_publication` (`batch_id`,`publication_version`,`deleted`),
  KEY `idx_competition_review_publication_status` (`batch_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_appeal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `publication_id` bigint NOT NULL,
  `candidate_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `appeal_no` varchar(64) NOT NULL,
  `appeal_reason` varchar(4000) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `resolution` varchar(4000) DEFAULT NULL,
  `resolved_by` bigint DEFAULT NULL,
  `resolved_by_uuid` char(36) DEFAULT NULL,
  `resolved_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_appeal_no` (`appeal_no`,`deleted`),
  UNIQUE KEY `uk_competition_review_appeal_result` (`publication_id`,`candidate_id`,`deleted`),
  KEY `idx_competition_review_appeal_registration` (`registration_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @review_appeal_result_index_exists = (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'competition_review_appeal'
       AND index_name = 'uk_competition_review_appeal_result'
);
SET @review_appeal_result_index_sql = IF(
    @review_appeal_result_index_exists = 0,
    'ALTER TABLE `competition_review_appeal` ADD UNIQUE INDEX `uk_competition_review_appeal_result` (`publication_id`,`candidate_id`,`deleted`)',
    'SELECT 1'
);
PREPARE review_appeal_result_index_statement FROM @review_appeal_result_index_sql;
EXECUTE review_appeal_result_index_statement;
DEALLOCATE PREPARE review_appeal_result_index_statement;

INSERT INTO `sys_permission` (
    `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('registration:dataset:view', '查看赛事报名数据集', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('registration:dataset:view-sensitive', '查看赛事报名敏感数据', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('registration:dataset:export', '导出赛事报名数据集', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('registration:dataset:export-sensitive', '导出赛事报名敏感数据', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('registration:material:download', '下载赛事报名材料', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('review:workbench:view', '访问评审工作台', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:plan:manage', '管理评审方案', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:batch:create', '创建评审批次', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:assignment:manage', '管理评审任务分配', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:task:view', '查看评审任务', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:score:submit', '提交评审评分', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:result:aggregate', '汇总评审结果', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:result:finalize', '终审确认评审结果', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:result:publish', '发布评审结果', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:appeal:submit', '提交评审结果申诉', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:appeal:manage', '处理评审结果申诉', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:audit:view', '查看评审审计记录', 'review', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `plugin_code` = VALUES(`plugin_code`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES
    (-1068, 0, 'expert.review.root', '专家与评审', 'CATALOG', '/expert-review', 'redirect:/expert-review/reviews', 'SolutionOutlined', 8, NULL, 'ENABLED', 0, 0, 0),
    (-1078, -1068, 'expert.review.tasks', '评审工作台', 'MENU', '/expert-review/reviews', '@/pages/competition/CompetitionReviewPage', 'AuditOutlined', 1, 'review:workbench:view', 'ENABLED', 0, 0, 0),
    (-1074, -1069, 'competition.review-results', '评审结果与申诉', 'MENU', '/competitions/review-results', '@/pages/competition/CompetitionReviewResultsPage', 'FileSearchOutlined', 3, 'review:appeal:submit', 'ENABLED', 0, 0, 0)
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
    `deleted` = VALUES(`deleted`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT role_row.`id`, permission_row.`permission_key`, 0, 0, 0
FROM `sys_role` role_row
JOIN `sys_permission` permission_row
  ON permission_row.`deleted` = 0
WHERE role_row.`deleted` = 0
  AND UPPER(role_row.`role_code`) = 'ADMIN'
  AND permission_row.`permission_key` LIKE 'review:%'
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT role_row.`id`, 'review:appeal:submit', 0, 0, 0
FROM `sys_role` role_row
WHERE role_row.`deleted` = 0
  AND UPPER(role_row.`role_code`) = 'COMMONUSER'
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT role_row.`id`, permission_row.`permission_key`, 0, 0, 0
FROM `sys_role` role_row
JOIN `sys_permission` permission_row
  ON permission_row.`permission_key` IN (
      'review:workbench:view',
      'review:task:view',
      'review:score:submit'
  )
 AND permission_row.`deleted` = 0
WHERE role_row.`deleted` = 0
  AND UPPER(role_row.`role_code`) = 'EXPERT'
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
