-- Consolidated localization-service baseline. Future schema changes should start at V2.

CREATE TABLE IF NOT EXISTS `sys_localization_language` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `language_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `native_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fallback_locale` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_language_locale` (`locale_code`),
  KEY `idx_sys_localization_language_status` (`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_namespace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `namespace_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_namespace_code` (`namespace_code`),
  KEY `idx_sys_localization_namespace_status` (`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL,
  `message_key` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_locale` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_entry_namespace_key` (`namespace_id`,`message_key`),
  KEY `idx_sys_localization_entry_status` (`status`,`updated_at`),
  KEY `idx_sys_localization_entry_source` (`source_type`,`source_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_translation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `translated_message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `translation_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TRANSLATED',
  `machine_generated` tinyint(1) NOT NULL DEFAULT '0',
  `review_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `translated_by` bigint DEFAULT NULL,
  `translated_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_translation_entry_locale` (`entry_id`,`locale_code`),
  KEY `idx_sys_localization_translation_locale_status` (`locale_code`,`translation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_release` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_version` bigint NOT NULL,
  `fallback_locale` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bundle_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_flag` tinyint(1) NOT NULL DEFAULT '1',
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_release_locale_version` (`locale_code`,`release_version`),
  KEY `idx_sys_localization_release_locale_active` (`locale_code`,`active_flag`,`release_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_usage_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_line` int DEFAULT NULL,
  `source_text` text COLLATE utf8mb4_unicode_ci,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_usage_ref` (`entry_id`,`source_type`,`source_ref`,`source_line`),
  KEY `idx_sys_localization_usage_ref_entry` (`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `sys_localization_language` (`id`, `locale_code`, `language_name`, `native_name`, `fallback_locale`, `sort_no`, `is_default`, `status`, `created_by`, `updated_by`, `deleted`)
SELECT 1, 'zh-CN', 'Chinese', '简体中文', NULL, 1, 1, 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_localization_language` WHERE `locale_code` = 'zh-CN');

INSERT INTO `sys_localization_language` (`id`, `locale_code`, `language_name`, `native_name`, `fallback_locale`, `sort_no`, `is_default`, `status`, `created_by`, `updated_by`, `deleted`)
SELECT 2, 'en-US', 'English', 'English', 'zh-CN', 2, 0, 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_localization_language` WHERE `locale_code` = 'en-US');
