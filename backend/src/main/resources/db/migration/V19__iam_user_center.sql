CREATE TABLE IF NOT EXISTS `iam_user` (
  `id` bigint NOT NULL,
  `user_no` varchar(64) NOT NULL,
  `display_name` varchar(128) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `user_type` varchar(32) NOT NULL DEFAULT 'REGISTERED',
  `source` varchar(64) NOT NULL DEFAULT 'LEGACY_SYS_USER',
  `registered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_user_no` (`user_no`),
  KEY `idx_iam_user_status_created` (`status`,`created_at`,`id`,`deleted`),
  KEY `idx_iam_user_source_created` (`source`,`created_at`,`id`,`deleted`),
  KEY `idx_iam_user_last_login` (`last_login_at`,`id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `iam_user_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `identity_type` varchar(32) NOT NULL,
  `identifier` varchar(255) NOT NULL,
  `identifier_normalized` varchar(255) NOT NULL,
  `verified` tinyint NOT NULL DEFAULT '0',
  `primary_identity` tinyint NOT NULL DEFAULT '0',
  `bound_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used_at` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity` (`identity_type`,`identifier_normalized`),
  KEY `idx_iam_identity_user` (`user_id`,`identity_type`,`deleted`),
  KEY `idx_iam_identity_last_used` (`last_used_at`,`id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `iam_user_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `credential_type` varchar(32) NOT NULL,
  `credential_secret` varchar(512) NOT NULL,
  `algorithm` varchar(64) NOT NULL DEFAULT 'BCRYPT',
  `version` int NOT NULL DEFAULT '1',
  `expire_at` datetime DEFAULT NULL,
  `last_changed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_credential_user_type` (`user_id`,`credential_type`,`version`),
  KEY `idx_iam_credential_type_status` (`credential_type`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `iam_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `nickname` varchar(128) DEFAULT NULL,
  `real_name` varchar(128) DEFAULT NULL,
  `gender` varchar(32) DEFAULT NULL,
  `birth_month` varchar(16) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `locale` varchar(32) DEFAULT NULL,
  `timezone` varchar(64) DEFAULT NULL,
  `bio` varchar(1000) DEFAULT NULL,
  `extra_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_profile_user` (`user_id`),
  KEY `idx_iam_profile_real_name` (`real_name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `iam_user_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `device_id` varchar(128) NOT NULL,
  `device_name` varchar(128) DEFAULT NULL,
  `device_type` varchar(32) DEFAULT NULL,
  `os` varchar(64) DEFAULT NULL,
  `browser` varchar(64) DEFAULT NULL,
  `last_ip` varchar(64) DEFAULT NULL,
  `last_active_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trusted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_device_user_device` (`user_id`,`device_id`),
  KEY `idx_iam_device_user_active` (`user_id`,`last_active_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `iam_user_security_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `mfa_enabled` tinyint NOT NULL DEFAULT '0',
  `password_login_enabled` tinyint NOT NULL DEFAULT '1',
  `sms_login_enabled` tinyint NOT NULL DEFAULT '1',
  `email_login_enabled` tinyint NOT NULL DEFAULT '1',
  `passkey_enabled` tinyint NOT NULL DEFAULT '0',
  `login_notify_enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_security_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `iam_user_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_source` varchar(64) NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_iam_event_user_created` (`user_id`,`created_at`),
  KEY `idx_iam_event_type_created` (`event_type`,`created_at`),
  KEY `idx_iam_event_ip_created` (`ip`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `iam_user` (
    `id`, `user_no`, `display_name`, `avatar_url`, `status`, `user_type`, `source`,
    `registered_at`, `created_at`, `updated_at`, `deleted`
)
SELECT u.id,
       concat('U', lpad(u.id, 12, '0')),
       coalesce(nullif(u.nickname, ''), nullif(u.real_name, ''), u.username),
       u.avatar_url,
       u.status,
       'REGISTERED',
       'LEGACY_SYS_USER',
       u.created_at,
       u.created_at,
       u.updated_at,
       u.deleted
from sys_user u
where not exists (select 1 from iam_user iu where iu.id = u.id);

INSERT IGNORE INTO `iam_user_identity` (
    `user_id`, `identity_type`, `identifier`, `identifier_normalized`, `verified`,
    `primary_identity`, `bound_at`, `status`, `deleted`
)
SELECT u.id, 'USERNAME', u.username, trim(lower(u.username)), 1, 1, u.created_at, u.status, u.deleted
from sys_user u
where u.username is not null and trim(u.username) <> '';

INSERT IGNORE INTO `iam_user_identity` (
    `user_id`, `identity_type`, `identifier`, `identifier_normalized`, `verified`,
    `primary_identity`, `bound_at`, `status`, `deleted`
)
SELECT u.id, 'MOBILE', u.mobile, replace(replace(replace(trim(u.mobile), ' ', ''), '-', ''), '+86', ''), 1, 0, u.created_at, u.status, u.deleted
from sys_user u
where u.mobile is not null and trim(u.mobile) <> '';

INSERT IGNORE INTO `iam_user_identity` (
    `user_id`, `identity_type`, `identifier`, `identifier_normalized`, `verified`,
    `primary_identity`, `bound_at`, `status`, `deleted`
)
SELECT u.id, 'EMAIL', u.email, trim(lower(u.email)), 1, 0, u.created_at, u.status, u.deleted
from sys_user u
where u.email is not null and trim(u.email) <> '';

INSERT IGNORE INTO `iam_user_credential` (
    `user_id`, `credential_type`, `credential_secret`, `algorithm`, `version`,
    `last_changed_at`, `status`, `deleted`
)
SELECT u.id, 'PASSWORD', u.password_hash, 'BCRYPT', 1, u.updated_at, u.status, u.deleted
from sys_user u
where u.password_hash is not null and trim(u.password_hash) <> '';

INSERT IGNORE INTO `iam_user_profile` (
    `user_id`, `nickname`, `real_name`, `gender`, `birth_month`, `region`, `locale`, `timezone`, `extra_json`, `deleted`
)
SELECT u.id, u.nickname, u.real_name, u.gender, u.birth_month, u.region, 'zh-CN', 'Asia/Shanghai',
       json_object('availableTime', u.available_time, 'idCardBound', if(u.id_card_number is null or trim(u.id_card_number) = '', false, true)),
       u.deleted
from sys_user u;

INSERT IGNORE INTO `iam_user_security_setting` (`user_id`)
SELECT u.id
from sys_user u;

INSERT INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'system:user:sensitive:view', '查看用户敏感信息', 'system', 'CORE', 0, 0, 0
where not exists (
    select 1 from sys_permission
    where tenant_id = 1001 and permission_key = 'system:user:sensitive:view' and deleted = 0
);

INSERT IGNORE INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, r.id, 'system:user:sensitive:view', 0, 0, 0
from sys_role r
where r.tenant_id = 1001 and r.role_code = 'ADMIN' and r.deleted = 0;
