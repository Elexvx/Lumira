-- Expand-only review workflow extension: roster, invitation, check-in and
-- batch-level workload configuration. Existing review rows are preserved.

SET @review_batch_reviewer_count_exists = (
    SELECT COUNT(1)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'competition_review_batch'
       AND column_name = 'reviewer_count_per_candidate'
);
SET @review_batch_reviewer_count_sql = IF(
    @review_batch_reviewer_count_exists = 0,
    'ALTER TABLE `competition_review_batch` ADD COLUMN `reviewer_count_per_candidate` int NOT NULL DEFAULT 3 AFTER `minimum_reviewer_count`',
    'SELECT 1'
);
PREPARE review_batch_reviewer_count_statement FROM @review_batch_reviewer_count_sql;
EXECUTE review_batch_reviewer_count_statement;
DEALLOCATE PREPARE review_batch_reviewer_count_statement;

SET @review_batch_min_assignments_exists = (
    SELECT COUNT(1) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'competition_review_batch'
       AND column_name = 'expert_min_assignments'
);
SET @review_batch_min_assignments_sql = IF(
    @review_batch_min_assignments_exists = 0,
    'ALTER TABLE `competition_review_batch` ADD COLUMN `expert_min_assignments` int NOT NULL DEFAULT 5 AFTER `reviewer_count_per_candidate`',
    'SELECT 1'
);
PREPARE review_batch_min_assignments_statement FROM @review_batch_min_assignments_sql;
EXECUTE review_batch_min_assignments_statement;
DEALLOCATE PREPARE review_batch_min_assignments_statement;

SET @review_batch_target_assignments_exists = (
    SELECT COUNT(1) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'competition_review_batch'
       AND column_name = 'expert_target_assignments'
);
SET @review_batch_target_assignments_sql = IF(
    @review_batch_target_assignments_exists = 0,
    'ALTER TABLE `competition_review_batch` ADD COLUMN `expert_target_assignments` int NOT NULL DEFAULT 6 AFTER `expert_min_assignments`',
    'SELECT 1'
);
PREPARE review_batch_target_assignments_statement FROM @review_batch_target_assignments_sql;
EXECUTE review_batch_target_assignments_statement;
DEALLOCATE PREPARE review_batch_target_assignments_statement;

SET @review_batch_max_assignments_exists = (
    SELECT COUNT(1) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'competition_review_batch'
       AND column_name = 'expert_max_assignments'
);
SET @review_batch_max_assignments_sql = IF(
    @review_batch_max_assignments_exists = 0,
    'ALTER TABLE `competition_review_batch` ADD COLUMN `expert_max_assignments` int NOT NULL DEFAULT 6 AFTER `expert_target_assignments`',
    'SELECT 1'
);
PREPARE review_batch_max_assignments_statement FROM @review_batch_max_assignments_sql;
EXECUTE review_batch_max_assignments_statement;
DEALLOCATE PREPARE review_batch_max_assignments_statement;

SET @review_batch_assignment_confirmed_exists = (
    SELECT COUNT(1) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'competition_review_batch'
       AND column_name = 'assignment_confirmed_at'
);
SET @review_batch_assignment_confirmed_sql = IF(
    @review_batch_assignment_confirmed_exists = 0,
    'ALTER TABLE `competition_review_batch` ADD COLUMN `assignment_confirmed_at` datetime DEFAULT NULL AFTER `frozen_at`',
    'SELECT 1'
);
PREPARE review_batch_assignment_confirmed_statement FROM @review_batch_assignment_confirmed_sql;
EXECUTE review_batch_assignment_confirmed_statement;
DEALLOCATE PREPARE review_batch_assignment_confirmed_statement;

CREATE TABLE IF NOT EXISTS `competition_review_roster` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `expert_id` bigint NOT NULL,
  `expert_user_id` bigint NOT NULL,
  `expert_user_uuid` char(36) NOT NULL,
  `expert_name` varchar(255) NOT NULL,
  `email` varchar(320) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SELECTED',
  `selected_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `removed_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_roster` (`batch_id`,`expert_id`,`deleted`),
  KEY `idx_competition_review_roster_batch` (`batch_id`,`status`,`deleted`),
  KEY `idx_competition_review_roster_expert` (`expert_id`,`expert_user_id`,`expert_user_uuid`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `roster_id` bigint NOT NULL,
  `expert_id` bigint NOT NULL,
  `expert_user_id` bigint NOT NULL,
  `expert_user_uuid` char(36) NOT NULL,
  `email` varchar(320) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `token_expires_at` datetime NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `sent_at` datetime DEFAULT NULL,
  `opened_at` datetime DEFAULT NULL,
  `qr_token_hash` char(64) DEFAULT NULL,
  `qr_expires_at` datetime DEFAULT NULL,
  `qr_used_at` datetime DEFAULT NULL,
  `checked_in_at` datetime DEFAULT NULL,
  `checked_in_by` bigint DEFAULT NULL,
  `checked_in_by_uuid` char(36) DEFAULT NULL,
  `send_attempts` int NOT NULL DEFAULT '0',
  `failure_reason` varchar(1000) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_invitation` (`batch_id`,`expert_id`,`deleted`),
  UNIQUE KEY `uk_competition_review_invitation_token` (`token_hash`),
  KEY `idx_competition_review_invitation_qr` (`qr_token_hash`,`status`,`deleted`),
  KEY `idx_competition_review_invitation_batch` (`batch_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_notification_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `invitation_id` bigint NOT NULL,
  `dedupe_key` varchar(128) NOT NULL,
  `recipient_email` varchar(320) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `content` longtext NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `failure_reason` varchar(1000) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_review_notification_outbox_dedupe` (`dedupe_key`),
  KEY `idx_competition_review_notification_outbox_queue` (`status`,`next_retry_at`,`created_at`,`id`),
  KEY `idx_competition_review_notification_outbox_batch` (`batch_id`,`invitation_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `competition_review_checkin_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint DEFAULT NULL,
  `invitation_id` bigint DEFAULT NULL,
  `expert_id` bigint DEFAULT NULL,
  `qr_token_hash` char(64) NOT NULL,
  `status` varchar(32) NOT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `checked_in_by` bigint DEFAULT NULL,
  `checked_in_by_uuid` char(36) DEFAULT NULL,
  `attempted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_competition_review_checkin_batch` (`batch_id`,`status`,`attempted_at`),
  KEY `idx_competition_review_checkin_token` (`qr_token_hash`,`attempted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_permission` (
    `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('review:roster:manage', '管理本批次评审专家名单', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:notification:send', '发送评审邀请通知', 'review', 'CORE', NULL, 0, 0, 0),
    ('review:checkin:scan', '扫描评审签到二维码', 'review', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT role_row.`id`, permission_row.`permission_key`, 0, 0, 0
  FROM `sys_role` role_row
  JOIN `sys_permission` permission_row
    ON permission_row.`permission_key` IN (
        'review:roster:manage', 'review:notification:send', 'review:checkin:scan'
    ) AND permission_row.`deleted` = 0
 WHERE role_row.`deleted` = 0 AND UPPER(role_row.`role_code`) = 'ADMIN'
ON DUPLICATE KEY UPDATE `updated_by` = VALUES(`updated_by`), `deleted` = 0;
