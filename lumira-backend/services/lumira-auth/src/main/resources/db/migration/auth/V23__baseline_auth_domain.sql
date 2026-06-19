-- Consolidated auth-service baseline. Future schema changes should start at V2.

CREATE TABLE IF NOT EXISTS `sys_user_passkey_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_handle` varchar(128) NOT NULL,
  `credential_id` varchar(512) NOT NULL,
  `public_key_cose` text NOT NULL,
  `sign_count` bigint NOT NULL DEFAULT '0',
  `transports` varchar(255) DEFAULT NULL,
  `backup_eligible` tinyint NOT NULL DEFAULT '0',
  `backup_state` tinyint NOT NULL DEFAULT '0',
  `label` varchar(128) NOT NULL DEFAULT '通行密钥',
  `last_used_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_passkey_credential_id` (`credential_id`),
  KEY `idx_passkey_user` (`tenant_id`,`user_id`,`deleted`),
  KEY `idx_passkey_user_handle` (`user_handle`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_user_wechat_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `openid` varchar(128) NOT NULL,
  `unionid` varchar(128) DEFAULT NULL,
  `scope` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_wechat_openid` (`openid`),
  UNIQUE KEY `uk_sys_user_wechat_unionid` (`unionid`),
  KEY `idx_sys_user_wechat_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_verification_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_code` varchar(32) NOT NULL,
  `factor_name` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `bound` tinyint NOT NULL DEFAULT '0',
  `email_required` tinyint NOT NULL DEFAULT '0',
  `masked_contact` varchar(255) DEFAULT NULL,
  `secret_key` varchar(512) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `verified_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_binding` (`tenant_id`,`user_id`,`factor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_verification_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_code` varchar(32) NOT NULL,
  `challenge_type` varchar(16) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_flag` tinyint NOT NULL DEFAULT '0',
  `setup_secret` varchar(512) DEFAULT NULL,
  `setup_uri` varchar(512) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `code_hash` varchar(128) DEFAULT NULL,
  `masked_contact` varchar(255) DEFAULT NULL,
  `debug_code` varchar(32) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_challenge` (`challenge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
