-- Expand-only migration for existing databases.
-- Fresh databases receive the same schema and seeds from lumira-backend/sql/saas.sql.

CREATE TABLE IF NOT EXISTS `aiadc_activity_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `application_no` varchar(32) NOT NULL,
  `activity_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `mobile` varchar(32) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `organization` varchar(255) DEFAULT NULL,
  `position` varchar(128) DEFAULT NULL,
  `remark` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `owner_user_id` bigint NOT NULL,
  `owner_user_uuid` char(36) NOT NULL,
  `owner_username` varchar(128) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_activity_registration_no` (`application_no`),
  KEY `idx_aiadc_activity_registration_owner` (`owner_user_id`,`deleted`,`submitted_at`),
  KEY `idx_aiadc_activity_registration_activity` (`activity_id`,`deleted`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_dict_type`
    (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('aiadc_activity_locale', '活动语言', 'ENABLED', 1, 'Activity locale and default order', 0, 0, 0),
    ('aiadc_activity_status', '活动状态', 'ENABLED', 1, 'Activity status and default order', 0, 0, 0),
    ('aiadc_activity_public_status', '活动公开状态', 'ENABLED', 1, 'Activity statuses visible to public queries', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item`
    (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'zh', '中文', 10, 'ENABLED', '活动默认语言', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'en', 'English', 20, 'ENABLED', 'Activity locale', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'draft', '草稿', 10, 'ENABLED', '活动默认状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 20, 'ENABLED', '活动状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 10, 'ENABLED', '公开查询可见状态', 0, 0, 0
FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_public_status' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
