CREATE TABLE IF NOT EXISTS `sys_localization_language` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) NOT NULL,
  `language_name` varchar(128) NOT NULL,
  `native_name` varchar(128) DEFAULT NULL,
  `fallback_locale` varchar(64) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `is_default` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_language_locale` (`locale_code`),
  KEY `idx_sys_localization_language_status` (`status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_namespace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_code` varchar(128) NOT NULL,
  `namespace_name` varchar(128) NOT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_namespace_code` (`namespace_code`),
  KEY `idx_sys_localization_namespace_status` (`status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL,
  `message_key` varchar(256) NOT NULL,
  `default_message` text NOT NULL,
  `source_locale` varchar(64) NOT NULL DEFAULT 'zh-CN',
  `source_type` varchar(32) NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_entry_namespace_key` (`namespace_id`, `message_key`),
  KEY `idx_sys_localization_entry_status` (`status`, `updated_at`),
  KEY `idx_sys_localization_entry_source` (`source_type`, `source_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_usage_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) NOT NULL,
  `source_line` int DEFAULT NULL,
  `source_text` text DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_usage_ref` (`entry_id`, `source_type`, `source_ref`, `source_line`),
  KEY `idx_sys_localization_usage_ref_entry` (`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_translation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `locale_code` varchar(64) NOT NULL,
  `translated_message` text NOT NULL,
  `translation_status` varchar(32) NOT NULL DEFAULT 'TRANSLATED',
  `machine_generated` tinyint(1) NOT NULL DEFAULT 0,
  `review_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `translated_by` bigint DEFAULT NULL,
  `translated_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_translation_entry_locale` (`entry_id`, `locale_code`),
  KEY `idx_sys_localization_translation_locale_status` (`locale_code`, `translation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sys_localization_release` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) NOT NULL,
  `release_version` bigint NOT NULL,
  `fallback_locale` varchar(64) DEFAULT NULL,
  `bundle_json` longtext NOT NULL,
  `note` varchar(512) DEFAULT NULL,
  `active_flag` tinyint(1) NOT NULL DEFAULT 1,
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_release_locale_version` (`locale_code`, `release_version`),
  KEY `idx_sys_localization_release_locale_active` (`locale_code`, `active_flag`, `release_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `sys_localization_language`
  (`locale_code`, `language_name`, `native_name`, `fallback_locale`, `sort_no`, `is_default`, `status`, `created_by`, `updated_by`)
VALUES
  ('zh-CN', 'Chinese', '简体中文', NULL, 1, 1, 'ENABLED', 0, 0),
  ('en-US', 'English', 'English', 'zh-CN', 2, 0, 'ENABLED', 0, 0);

INSERT IGNORE INTO `sys_permission`
  (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
VALUES
  (156, 1001, 'localization:view', '查看本地化中心', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (157, 1001, 'localization:create', '新增翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (158, 1001, 'localization:update', '编辑翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (159, 1001, 'localization:delete', '删除翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (160, 1001, 'localization:sync', '同步翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (161, 1001, 'localization:publish', '发布翻译版本', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (162, 1001, 'localization:rollback', '回滚翻译版本', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (163, 1002, 'localization:view', '查看本地化中心', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (164, 1002, 'localization:create', '新增翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (165, 1002, 'localization:update', '编辑翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (166, 1002, 'localization:delete', '删除翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (167, 1002, 'localization:sync', '同步翻译词条', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (168, 1002, 'localization:publish', '发布翻译版本', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (169, 1002, 'localization:rollback', '回滚翻译版本', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0);

INSERT IGNORE INTO `sys_menu`
  (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`)
VALUES
  (3031, 1001, 3002, 'localization.root', '本地化中心', 'MENU', '/settings/localization', '@/pages/settings/localization', 0, NOW(), 0, NOW(), 0, 'TranslationOutlined', 29, 'localization:view', 'ENABLED'),
  (4031, 1002, 4002, 'localization.root', '本地化中心', 'MENU', '/settings/localization', '@/pages/settings/localization', 0, NOW(), 0, NOW(), 0, 'TranslationOutlined', 29, 'localization:view', 'ENABLED');

INSERT IGNORE INTO `sys_role_permission`
  (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
VALUES
  (189, 1001, 2001, 'localization:view', 0, NOW(), 0, NOW(), 0),
  (190, 1001, 2001, 'localization:create', 0, NOW(), 0, NOW(), 0),
  (191, 1001, 2001, 'localization:update', 0, NOW(), 0, NOW(), 0),
  (192, 1001, 2001, 'localization:delete', 0, NOW(), 0, NOW(), 0),
  (193, 1001, 2001, 'localization:sync', 0, NOW(), 0, NOW(), 0),
  (194, 1001, 2001, 'localization:publish', 0, NOW(), 0, NOW(), 0),
  (195, 1001, 2001, 'localization:rollback', 0, NOW(), 0, NOW(), 0),
  (196, 1002, 2002, 'localization:view', 0, NOW(), 0, NOW(), 0),
  (197, 1002, 2002, 'localization:create', 0, NOW(), 0, NOW(), 0),
  (198, 1002, 2002, 'localization:update', 0, NOW(), 0, NOW(), 0),
  (199, 1002, 2002, 'localization:delete', 0, NOW(), 0, NOW(), 0),
  (200, 1002, 2002, 'localization:sync', 0, NOW(), 0, NOW(), 0),
  (201, 1002, 2002, 'localization:publish', 0, NOW(), 0, NOW(), 0),
  (202, 1002, 2002, 'localization:rollback', 0, NOW(), 0, NOW(), 0);
