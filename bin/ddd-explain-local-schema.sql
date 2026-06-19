DROP TABLE IF EXISTS platform_event_outbox;
DROP TABLE IF EXISTS ai_knowledge_document;
DROP TABLE IF EXISTS msg_notice_read;
DROP TABLE IF EXISTS msg_notice;
DROP TABLE IF EXISTS sys_plugin_tenant;
DROP TABLE IF EXISTS sys_plugin_version;
DROP TABLE IF EXISTS sys_plugin_definition;
DROP TABLE IF EXISTS sys_config;

CREATE TABLE sys_config (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  config_key VARCHAR(128) NOT NULL,
  config_value TEXT NULL,
  config_scope VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_config_key (tenant_id, config_key),
  KEY idx_sys_config_scope_key_tenant_deleted (config_scope, config_key, tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_plugin_definition (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plugin_code VARCHAR(128) NOT NULL,
  plugin_name VARCHAR(128) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_plugin_definition_code (plugin_code),
  KEY idx_sys_plugin_definition_sort (deleted, sort_no, plugin_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_plugin_version (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  plugin_code VARCHAR(128) NOT NULL,
  version VARCHAR(64) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_plugin_version_code_version (plugin_code, version),
  KEY idx_sys_plugin_version_lookup (plugin_code, version, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_plugin_tenant (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  plugin_code VARCHAR(128) NOT NULL,
  plugin_version VARCHAR(64) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_plugin_tenant_rel (tenant_id, plugin_code),
  KEY idx_sys_plugin_tenant_enabled (tenant_id, enabled, deleted, plugin_code, plugin_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE msg_notice (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT UNSIGNED NOT NULL,
  publish_status VARCHAR(32) NOT NULL,
  target_scope VARCHAR(32) NOT NULL,
  target_user_id BIGINT UNSIGNED NULL,
  target_role_id BIGINT UNSIGNED NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_msg_notice_visible_recent (tenant_id, deleted, publish_status, target_scope, target_user_id, target_role_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE msg_notice_read (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  notice_id BIGINT UNSIGNED NOT NULL,
  tenant_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_msg_notice_read_user_notice (notice_id, tenant_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_knowledge_document (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  status VARCHAR(32) NOT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  index_next_retry_at DATETIME NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_knowledge_document_index_retry (status, is_deleted, index_next_retry_at, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE platform_event_outbox (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  source_type VARCHAR(32) NOT NULL,
  dispatch_status VARCHAR(32) NOT NULL,
  next_retry_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_platform_event_outbox_owner_queue (deleted, source_type, dispatch_status, next_retry_at, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
