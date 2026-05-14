SET @now_ts = NOW();

CREATE TABLE IF NOT EXISTS `site_site` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `primary_domain` varchar(255) DEFAULT NULL,
  `logo_file_id` bigint DEFAULT NULL,
  `favicon_file_id` bigint DEFAULT NULL,
  `theme_json` json DEFAULT NULL,
  `seo_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_site_tenant_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_site_site_tenant_status` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `site_navigation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `link_type` varchar(32) NOT NULL,
  `link_target` varchar(512) NOT NULL,
  `open_type` varchar(32) NOT NULL DEFAULT 'SELF',
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'VISIBLE',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_site_navigation_tree` (`tenant_id`,`site_id`,`parent_id`,`sort_order`,`deleted`),
  KEY `idx_site_navigation_status` (`tenant_id`,`site_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `site_page` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `title` varchar(180) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `page_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `seo_json` json DEFAULT NULL,
  `current_draft_version` bigint DEFAULT NULL,
  `current_published_version` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_page_slug` (`tenant_id`,`site_id`,`slug`,`deleted`),
  KEY `idx_site_page_status` (`tenant_id`,`site_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `site_page_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `page_id` bigint NOT NULL,
  `version_no` bigint NOT NULL,
  `blocks_json` json NOT NULL,
  `snapshot_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_page_version_no` (`tenant_id`,`page_id`,`version_no`),
  KEY `idx_site_page_version_status` (`tenant_id`,`page_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `site_content_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_content_category_code` (`tenant_id`,`site_id`,`code`,`deleted`),
  KEY `idx_site_content_category_tree` (`tenant_id`,`site_id`,`parent_id`,`sort_order`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `site_content` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `title` varchar(220) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `cover_file_id` bigint DEFAULT NULL,
  `body_type` varchar(32) NOT NULL DEFAULT 'RICH_TEXT',
  `body_text` mediumtext,
  `body_json` json DEFAULT NULL,
  `seo_json` json DEFAULT NULL,
  `tags_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_content_slug` (`tenant_id`,`site_id`,`slug`,`deleted`),
  KEY `idx_site_content_list` (`tenant_id`,`site_id`,`category_id`,`status`,`published_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `site_form` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `submit_policy` varchar(32) NOT NULL DEFAULT 'ANONYMOUS',
  `schema_json` json NOT NULL,
  `notification_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_form_code` (`tenant_id`,`site_id`,`code`,`deleted`),
  KEY `idx_site_form_status` (`tenant_id`,`site_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `site_form_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `form_id` bigint NOT NULL,
  `submitter_user_id` bigint DEFAULT NULL,
  `submitter_ip` varchar(64) DEFAULT NULL,
  `data_json` json NOT NULL,
  `attachment_file_ids_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_remark` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_site_form_submission_list` (`tenant_id`,`site_id`,`form_id`,`status`,`created_at`,`deleted`),
  KEY `idx_site_form_submission_user` (`tenant_id`,`submitter_user_id`,`created_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
SELECT t.`tenant_id`, p.`permission_key`, p.`permission_name`, 'site', 'CORE', 0, @now_ts, 0, @now_ts, 0
FROM (
  SELECT DISTINCT `tenant_id`
  FROM `sys_role`
  WHERE `role_code` = 'ADMIN'
    AND `deleted` = 0
) t
JOIN (
  SELECT 'site:view' AS `permission_key`, '查看官网管理' AS `permission_name` UNION ALL
  SELECT 'site:settings', '管理官网设置' UNION ALL
  SELECT 'site:navigation', '管理官网导航' UNION ALL
  SELECT 'site:page', '查看官网页面' UNION ALL
  SELECT 'site:page:create', '新增官网页面' UNION ALL
  SELECT 'site:page:update', '编辑官网页面' UNION ALL
  SELECT 'site:page:publish', '发布官网页面' UNION ALL
  SELECT 'site:content', '查看官网内容' UNION ALL
  SELECT 'site:content:create', '新增官网内容' UNION ALL
  SELECT 'site:content:update', '编辑官网内容' UNION ALL
  SELECT 'site:content:publish', '发布官网内容' UNION ALL
  SELECT 'site:form', '管理官网表单' UNION ALL
  SELECT 'site:submission', '查看官网提交记录' UNION ALL
  SELECT 'site:submission:review', '审核官网提交记录'
) p;

INSERT IGNORE INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
SELECT r.`tenant_id`, r.`id`, p.`permission_key`, 0, @now_ts, 0, @now_ts, 0
FROM `sys_role` r
JOIN (
  SELECT 'site:view' AS `permission_key` UNION ALL
  SELECT 'site:settings' UNION ALL
  SELECT 'site:navigation' UNION ALL
  SELECT 'site:page' UNION ALL
  SELECT 'site:page:create' UNION ALL
  SELECT 'site:page:update' UNION ALL
  SELECT 'site:page:publish' UNION ALL
  SELECT 'site:content' UNION ALL
  SELECT 'site:content:create' UNION ALL
  SELECT 'site:content:update' UNION ALL
  SELECT 'site:content:publish' UNION ALL
  SELECT 'site:form' UNION ALL
  SELECT 'site:submission' UNION ALL
  SELECT 'site:submission:review'
) p
WHERE r.`role_code` = 'ADMIN'
  AND r.`deleted` = 0;

INSERT INTO `sys_menu` (
  `tenant_id`,
  `parent_id`,
  `menu_code`,
  `menu_name`,
  `menu_type`,
  `path`,
  `component`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`,
  `icon`,
  `sort_no`,
  `permission_key`,
  `status`
)
SELECT
  t.`tenant_id`,
  0,
  'site.root',
  '官网管理',
  'CATALOG',
  '/site',
  'redirect:/site/settings',
  0,
  @now_ts,
  0,
  @now_ts,
  0,
  'GlobalOutlined',
  7,
  'site:view',
  'ENABLED'
FROM (
  SELECT DISTINCT `tenant_id`
  FROM `sys_role`
  WHERE `role_code` = 'ADMIN'
    AND `deleted` = 0
) t
WHERE NOT EXISTS (
  SELECT 1
  FROM `sys_menu` existing
  WHERE existing.`tenant_id` = t.`tenant_id`
    AND existing.`menu_code` = 'site.root'
    AND existing.`deleted` = 0
);

INSERT INTO `sys_menu` (
  `tenant_id`,
  `parent_id`,
  `menu_code`,
  `menu_name`,
  `menu_type`,
  `path`,
  `component`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`,
  `icon`,
  `sort_no`,
  `permission_key`,
  `status`
)
SELECT root.`tenant_id`, root.`id`, child.`menu_code`, child.`menu_name`, 'MENU', child.`path`, child.`component`, 0, @now_ts, 0, @now_ts, 0, child.`icon`, child.`sort_no`, child.`permission_key`, 'ENABLED'
FROM `sys_menu` root
JOIN (
  SELECT 'site.settings' AS `menu_code`, '站点设置' AS `menu_name`, '/site/settings' AS `path`, '@/pages/site/settings' AS `component`, 'SettingOutlined' AS `icon`, 1 AS `sort_no`, 'site:settings' AS `permission_key` UNION ALL
  SELECT 'site.navigation', '导航管理', '/site/navigation', '@/pages/site/navigation', 'MenuOutlined', 2, 'site:navigation' UNION ALL
  SELECT 'site.pages', '页面管理', '/site/pages', '@/pages/site/pages', 'LayoutOutlined', 3, 'site:page' UNION ALL
  SELECT 'site.contents', '内容管理', '/site/contents', '@/pages/site/contents', 'ReadOutlined', 4, 'site:content' UNION ALL
  SELECT 'site.forms', '表单管理', '/site/forms', '@/pages/site/forms', 'FormOutlined', 5, 'site:form' UNION ALL
  SELECT 'site.submissions', '提交记录', '/site/submissions', '@/pages/site/submissions', 'InboxOutlined', 6, 'site:submission'
) child
WHERE root.`menu_code` = 'site.root'
  AND root.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_menu` existing
    WHERE existing.`tenant_id` = root.`tenant_id`
      AND existing.`menu_code` = child.`menu_code`
      AND existing.`deleted` = 0
  );
