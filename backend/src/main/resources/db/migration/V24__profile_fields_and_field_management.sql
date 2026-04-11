SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND column_name = 'birth_month'
    ),
    'SELECT 1',
    'ALTER TABLE sys_user ADD COLUMN birth_month VARCHAR(16) DEFAULT NULL AFTER avatar_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND column_name = 'gender'
    ),
    'SELECT 1',
    'ALTER TABLE sys_user ADD COLUMN gender VARCHAR(16) DEFAULT NULL AFTER birth_month'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND column_name = 'region'
    ),
    'SELECT 1',
    'ALTER TABLE sys_user ADD COLUMN region VARCHAR(128) DEFAULT NULL AFTER gender'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND column_name = 'available_time'
    ),
    'SELECT 1',
    'ALTER TABLE sys_user ADD COLUMN available_time VARCHAR(255) DEFAULT NULL AFTER region'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND column_name = 'id_card_number'
    ),
    'SELECT 1',
    'ALTER TABLE sys_user ADD COLUMN id_card_number VARCHAR(64) DEFAULT NULL AFTER available_time'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3025, 1001, 3002, 'system.profile-fields', '字段管理', 'MENU', '/system/profile-fields', '@/pages/system/profile-fields', 'FormOutlined', 29, 'system:config:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.profile-fields');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4025, 1002, 4002, 'system.profile-fields', '字段管理', 'MENU', '/system/profile-fields', '@/pages/system/profile-fields', 'FormOutlined', 29, 'system:config:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.profile-fields');
