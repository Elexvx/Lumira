-- Manual upgrade for existing databases.
-- Run this script once before starting a release that writes configuration
-- versions. It is intentionally not a Flyway migration and is not executed
-- by the application at runtime.
--
-- Keep these four definitions byte-for-byte aligned with the corresponding
-- CREATE TABLE blocks in sql/saas.sql. sys_config remains the current-value
-- store; these tables add immutable history and metadata beside it.
CREATE TABLE IF NOT EXISTS `sys_config_metadata` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(128) NOT NULL,
  `group_code` varchar(64) NOT NULL,
  `domain_code` varchar(64) NOT NULL DEFAULT 'PLATFORM',
  `value_type` varchar(32) NOT NULL DEFAULT 'STRING',
  `sensitivity` varchar(32) NOT NULL DEFAULT 'NONE',
  `refresh_policy` varchar(32) NOT NULL DEFAULT 'CONTROLLED',
  `description` varchar(512) DEFAULT NULL,
  `owner_code` varchar(128) NOT NULL DEFAULT 'lumira-system',
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_metadata_key` (`config_key`),
  KEY `idx_sys_config_metadata_group` (`group_code`,`domain_code`,`deleted`),
  KEY `idx_sys_config_metadata_sensitivity` (`sensitivity`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_config_version_head` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_code` varchar(64) NOT NULL,
  `domain_code` varchar(64) NOT NULL DEFAULT 'PLATFORM',
  `current_version_no` bigint NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'READY',
  `last_published_at` datetime DEFAULT NULL,
  `last_failure_at` datetime DEFAULT NULL,
  `last_failure_message` varchar(1024) DEFAULT NULL,
  `last_rollback_version_no` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_version_head_scope` (`group_code`,`domain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_config_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_code` varchar(64) NOT NULL,
  `domain_code` varchar(64) NOT NULL DEFAULT 'PLATFORM',
  `version_no` bigint NOT NULL,
  `change_type` varchar(32) NOT NULL DEFAULT 'UPDATE',
  `reason` varchar(512) NOT NULL DEFAULT '',
  `operator_id` bigint DEFAULT NULL,
  `operator_uuid` char(36) DEFAULT NULL,
  `operator_name` varchar(128) DEFAULT NULL,
  `expected_version_no` bigint DEFAULT NULL,
  `source_version_no` bigint DEFAULT NULL,
  `snapshot_json` longtext NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_version_scope` (`group_code`,`domain_code`,`version_no`),
  KEY `idx_sys_config_version_created_at` (`group_code`,`domain_code`,`created_at`),
  KEY `idx_sys_config_version_operator` (`operator_id`,`operator_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_config_version_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `version_id` bigint NOT NULL,
  `config_key` varchar(128) NOT NULL,
  `value_type` varchar(32) NOT NULL DEFAULT 'STRING',
  `sensitivity` varchar(32) NOT NULL DEFAULT 'NONE',
  `change_type` varchar(32) NOT NULL DEFAULT 'UPDATE',
  `before_present` tinyint NOT NULL DEFAULT 0,
  `after_present` tinyint NOT NULL DEFAULT 0,
  `value_before` longtext,
  `value_after` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_version_item_key` (`version_id`,`config_key`),
  KEY `idx_sys_config_version_item_key` (`config_key`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Backfill metadata for existing current values. Values are not copied into
-- history here, so an upgrade cannot manufacture a fake configuration version.
INSERT INTO `sys_config_metadata` (
    `config_key`,`group_code`,`domain_code`,`value_type`,`sensitivity`,`refresh_policy`,
    `description`,`owner_code`,`created_by`,`updated_by`,`deleted`
)
SELECT
    c.`config_key`,
    CASE
        WHEN c.`config_key` LIKE 'branding.%' THEN 'BRANDING'
        WHEN c.`config_key` LIKE 'agreement.%' THEN 'AGREEMENT'
        WHEN c.`config_key` LIKE 'smtp.%' THEN 'SMTP'
        WHEN c.`config_key` LIKE 'notification.wechat-official.%' THEN 'WECHAT_OFFICIAL'
        WHEN c.`config_key` LIKE 'watermark.%' THEN 'WATERMARK'
        WHEN c.`config_key` LIKE 'floating-window.%' THEN 'FLOATING_WINDOW'
        WHEN c.`config_key` LIKE 'profile.%' THEN 'PROFILE'
        WHEN c.`config_key` LIKE 'verification.%' THEN 'VERIFICATION'
        WHEN c.`config_key` LIKE 'security.%' THEN 'SECURITY'
        WHEN c.`config_key` LIKE 'certificate.%' THEN 'CERTIFICATE'
        ELSE 'SYSTEM_CONFIG'
    END,
    'PLATFORM',
    CASE
        WHEN c.`config_key` LIKE '%.enabled' OR c.`config_key` LIKE '%.active' OR c.`config_key` LIKE '%.allow-multi-device-login' THEN 'BOOLEAN'
        WHEN c.`config_key` LIKE '%.port' OR c.`config_key` LIKE '%.seconds' OR c.`config_key` LIKE '%.minutes' OR c.`config_key` LIKE '%.weight' OR c.`config_key` LIKE '%.size' THEN 'INTEGER'
        WHEN c.`config_key` LIKE '%.json' OR c.`config_key` LIKE '%.order' OR c.`config_key` LIKE '%.origins' OR c.`config_key` LIKE '%.lines' THEN 'JSON'
        ELSE 'STRING'
    END,
    CASE
        WHEN c.`config_key` LIKE '%.password' OR c.`config_key` LIKE '%.secret' OR c.`config_key` LIKE '%.app-secret'
          OR c.`config_key` LIKE '%.access-key-secret' OR c.`config_key` LIKE '%.private-key'
          OR c.`config_key` LIKE '%.credential' OR c.`config_key` LIKE '%.token'
          OR c.`config_key` LIKE '%-credential' OR c.`config_key` LIKE '%-token' THEN 'SECRET'
        ELSE 'NONE'
    END,
    CASE
        WHEN c.`config_key` LIKE 'branding.%' OR c.`config_key` LIKE 'agreement.%'
          OR c.`config_key` LIKE 'watermark.%' OR c.`config_key` LIKE 'floating-window.%'
          OR c.`config_key` LIKE 'profile.field.%' THEN 'DYNAMIC'
        ELSE 'CONTROLLED'
    END,
    COALESCE(NULLIF(c.`remark`,''), c.`config_name`, c.`config_key`),
    'lumira-system', 0, 0, 0
FROM `sys_config` c
WHERE c.`deleted`=0
ON DUPLICATE KEY UPDATE
    `group_code`=VALUES(`group_code`),
    `domain_code`=VALUES(`domain_code`),
    `value_type`=VALUES(`value_type`),
    `sensitivity`=VALUES(`sensitivity`),
    `refresh_policy`=VALUES(`refresh_policy`),
    `description`=VALUES(`description`),
    `owner_code`=VALUES(`owner_code`),
    `updated_by`=VALUES(`updated_by`),
    `deleted`=0;
