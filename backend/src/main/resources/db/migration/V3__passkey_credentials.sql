CREATE TABLE IF NOT EXISTS `sys_user_passkey_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_handle` varchar(128) NOT NULL,
  `credential_id` varchar(512) NOT NULL,
  `public_key_cose` text NOT NULL,
  `sign_count` bigint NOT NULL DEFAULT 0,
  `transports` varchar(255) DEFAULT NULL,
  `backup_eligible` tinyint(1) NOT NULL DEFAULT 0,
  `backup_state` tinyint(1) NOT NULL DEFAULT 0,
  `label` varchar(128) NOT NULL DEFAULT '通行密钥',
  `last_used_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_passkey_credential_id` (`credential_id`),
  KEY `idx_passkey_user` (`tenant_id`,`user_id`,`deleted`),
  KEY `idx_passkey_user_handle` (`user_handle`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'verification.passkey.enabled', '通行密钥启用', 'true', 'PLATFORM', 0, '是否启用通行密钥登录', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1001 AND `config_key` = 'verification.passkey.enabled' AND `deleted` = 0);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'verification.passkey.passwordless-enabled', '通行密钥无账号登录', 'true', 'PLATFORM', 0, '是否允许发现式凭据无账号登录', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1001 AND `config_key` = 'verification.passkey.passwordless-enabled' AND `deleted` = 0);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'verification.passkey.self-binding-enabled', '通行密钥自助绑定', 'true', 'PLATFORM', 0, '是否允许用户在个人中心自助绑定通行密钥', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1001 AND `config_key` = 'verification.passkey.self-binding-enabled' AND `deleted` = 0);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'verification.passkey.rp-id', '通行密钥 RP ID', 'elexvx.com', 'PLATFORM', 0, 'WebAuthn RP ID', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1001 AND `config_key` = 'verification.passkey.rp-id' AND `deleted` = 0);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'verification.passkey.rp-name', '通行密钥 RP 名称', '宏翔商道后台管理系统', 'PLATFORM', 0, 'WebAuthn RP 显示名称', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1001 AND `config_key` = 'verification.passkey.rp-name' AND `deleted` = 0);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'verification.passkey.allowed-origins', '通行密钥允许 Origin', 'https://test.elexvx.com', 'PLATFORM', 0, 'WebAuthn 允许的前端 Origin', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1001 AND `config_key` = 'verification.passkey.allowed-origins' AND `deleted` = 0);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 'verification.passkey.challenge-ttl-seconds', '通行密钥 Challenge TTL', '120', 'PLATFORM', 0, 'WebAuthn challenge 有效期秒数', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 1001 AND `config_key` = 'verification.passkey.challenge-ttl-seconds' AND `deleted` = 0);
