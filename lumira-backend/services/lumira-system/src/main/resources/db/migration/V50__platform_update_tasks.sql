CREATE TABLE IF NOT EXISTS platform_update_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  target_version VARCHAR(64) NULL,
  target_commit VARCHAR(64) NULL,
  server_image VARCHAR(255) NULL,
  frontend_image VARCHAR(255) NULL,
  updater_task_id VARCHAR(64) NULL,
  backup_path VARCHAR(512) NULL,
  log_summary TEXT NULL,
  error_message TEXT NULL,
  created_by BIGINT NULL,
  created_by_name VARCHAR(128) NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_platform_update_task_created_at (created_at),
  INDEX idx_platform_update_task_status (status),
  INDEX idx_platform_update_task_updater_task_id (updater_task_id)
);

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT 1001, 'system:update:install', 'Install platform update', 'system', 'CORE', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:update:install');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, created_by, created_at, updated_by, updated_at, deleted)
SELECT 1001, 'system:update:rollback', 'Rollback platform update', 'system', 'CORE', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:update:rollback');

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, created_at, updated_by, updated_at, deleted)
SELECT 1001, 2001, 'system:update:install', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE tenant_id = 1001 AND role_id = 2001 AND permission_key = 'system:update:install');

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, created_at, updated_by, updated_at, deleted)
SELECT 1001, 2001, 'system:update:rollback', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE tenant_id = 1001 AND role_id = 2001 AND permission_key = 'system:update:rollback');
