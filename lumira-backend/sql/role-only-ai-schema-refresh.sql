-- Refresh AI tables from tenant-scoped storage to role-only/global storage.
-- Safe to re-run: helpers check existing columns and indexes before changing them.

DELIMITER $$

DROP PROCEDURE IF EXISTS role_only_ai_drop_index $$
CREATE PROCEDURE role_only_ai_drop_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        IF p_index = 'PRIMARY' THEN
            SET @role_only_ddl = CONCAT(
                'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` DROP PRIMARY KEY'
            );
        ELSE
            SET @role_only_ddl = CONCAT(
                'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` DROP INDEX `', REPLACE(p_index, '`', '``'), '`'
            );
        END IF;
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_ai_drop_indexes_with_column $$
CREATE PROCEDURE role_only_ai_drop_indexes_with_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_index VARCHAR(64);
    DECLARE index_cursor CURSOR FOR
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
          AND index_name <> 'PRIMARY';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN index_cursor;
    read_loop: LOOP
        FETCH index_cursor INTO v_index;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;
        CALL role_only_ai_drop_index(p_table, v_index);
    END LOOP;
    CLOSE index_cursor;
END $$

DROP PROCEDURE IF EXISTS role_only_ai_drop_primary_with_column $$
CREATE PROCEDURE role_only_ai_drop_primary_with_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = 'PRIMARY'
          AND column_name = p_column
    ) THEN
        CALL role_only_ai_drop_index(p_table, 'PRIMARY');
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_ai_drop_column $$
CREATE PROCEDURE role_only_ai_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @role_only_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` DROP COLUMN `', REPLACE(p_column, '`', '``'), '`'
        );
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_ai_add_index $$
CREATE PROCEDURE role_only_ai_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_sql TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @role_only_ddl = p_sql;
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_ai_schema_refresh $$
CREATE PROCEDURE role_only_ai_schema_refresh()
BEGIN
    CREATE TABLE IF NOT EXISTS ai_conversation_share (
        id bigint unsigned NOT NULL AUTO_INCREMENT,
        conversation_id bigint unsigned NOT NULL,
        share_token varchar(128) NOT NULL,
        title varchar(255) DEFAULT NULL,
        status varchar(32) NOT NULL DEFAULT 'ACTIVE',
        expires_at datetime DEFAULT NULL,
        created_by bigint unsigned NOT NULL DEFAULT '0',
        is_deleted tinyint unsigned NOT NULL DEFAULT '0',
        create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
        update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        UNIQUE KEY uk_ai_conversation_share_token (share_token),
        KEY idx_ai_conversation_share_conversation (conversation_id, is_deleted),
        KEY idx_ai_conversation_share_status (status, expires_at, is_deleted)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_conversation_20260625 AS SELECT * FROM ai_conversation;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_employee_20260625 AS SELECT * FROM ai_employee;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_employee_knowledge_base_20260625 AS SELECT * FROM ai_employee_knowledge_base;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_employee_skill_20260625 AS SELECT * FROM ai_employee_skill;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_employee_tool_grant_20260625 AS SELECT * FROM ai_employee_tool_grant;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_employee_tool_grant_dept_20260625 AS SELECT * FROM ai_employee_tool_grant_dept;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_employee_tool_grant_user_20260625 AS SELECT * FROM ai_employee_tool_grant_user;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_knowledge_base_20260625 AS SELECT * FROM ai_knowledge_base;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_knowledge_base_acl_20260625 AS SELECT * FROM ai_knowledge_base_acl;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_knowledge_base_stats_20260625 AS SELECT * FROM ai_knowledge_base_stats;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_knowledge_chunk_20260625 AS SELECT * FROM ai_knowledge_chunk;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_knowledge_document_20260625 AS SELECT * FROM ai_knowledge_document;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_llm_model_20260625 AS SELECT * FROM ai_llm_model;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_llm_service_20260625 AS SELECT * FROM ai_llm_service;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_message_20260625 AS SELECT * FROM ai_message;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_message_attachment_20260625 AS SELECT * FROM ai_message_attachment;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_tool_audit_log_20260625 AS SELECT * FROM ai_tool_audit_log;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_tool_call_plan_20260625 AS SELECT * FROM ai_tool_call_plan;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_tool_execution_audit_20260625 AS SELECT * FROM ai_tool_execution_audit;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_tool_policy_20260625 AS SELECT * FROM ai_tool_policy;
    CREATE TABLE IF NOT EXISTS role_only_ai_backup_ai_conversation_share_20260625 AS SELECT * FROM ai_conversation_share;

    UPDATE ai_conversation row_data
    JOIN (
        SELECT conversation_code, MIN(id) AS keep_id
        FROM ai_conversation
        GROUP BY conversation_code
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.conversation_code = row_data.conversation_code
    SET row_data.conversation_code = LEFT(CONCAT(LEFT(row_data.conversation_code, 40), '__archived_', row_data.id), 64),
        row_data.is_deleted = 1,
        row_data.update_time = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE ai_employee row_data
    JOIN (
        SELECT username, is_deleted, MIN(id) AS keep_id
        FROM ai_employee
        GROUP BY username, is_deleted
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.username = row_data.username
     AND duplicate_row.is_deleted = row_data.is_deleted
    SET row_data.username = LEFT(CONCAT(LEFT(row_data.username, 40), '__archived_', row_data.id), 64),
        row_data.is_deleted = 1,
        row_data.update_time = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    DELETE row_data
    FROM ai_employee_knowledge_base row_data
    JOIN (
        SELECT employee_id, knowledge_base_id, MIN(id) AS keep_id
        FROM ai_employee_knowledge_base
        GROUP BY employee_id, knowledge_base_id
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.employee_id = row_data.employee_id
     AND duplicate_row.knowledge_base_id = row_data.knowledge_base_id
    WHERE row_data.id <> duplicate_row.keep_id;

    DELETE row_data
    FROM ai_employee_skill row_data
    JOIN (
        SELECT employee_id, skill_code, MIN(id) AS keep_id
        FROM ai_employee_skill
        GROUP BY employee_id, skill_code
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.employee_id <=> row_data.employee_id
     AND duplicate_row.skill_code = row_data.skill_code
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE ai_knowledge_base row_data
    JOIN (
        SELECT kb_code, MIN(id) AS keep_id
        FROM ai_knowledge_base
        GROUP BY kb_code
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.kb_code = row_data.kb_code
    SET row_data.kb_code = LEFT(CONCAT(LEFT(row_data.kb_code, 40), '__archived_', row_data.id), 64),
        row_data.is_deleted = 1,
        row_data.update_time = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE ai_knowledge_base row_data
    JOIN (
        SELECT owner_user_id, name, is_deleted, MIN(id) AS keep_id
        FROM ai_knowledge_base
        GROUP BY owner_user_id, name, is_deleted
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.owner_user_id = row_data.owner_user_id
     AND duplicate_row.name = row_data.name
     AND duplicate_row.is_deleted = row_data.is_deleted
    SET row_data.name = LEFT(CONCAT(LEFT(row_data.name, 96), '__archived_', row_data.id), 128),
        row_data.is_deleted = 1,
        row_data.update_time = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    DELETE row_data
    FROM ai_knowledge_base_acl row_data
    JOIN (
        SELECT knowledge_base_id, subject_type, subject_id, permission, is_deleted, MIN(id) AS keep_id
        FROM ai_knowledge_base_acl
        GROUP BY knowledge_base_id, subject_type, subject_id, permission, is_deleted
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.knowledge_base_id = row_data.knowledge_base_id
     AND duplicate_row.subject_type = row_data.subject_type
     AND duplicate_row.subject_id = row_data.subject_id
     AND duplicate_row.permission = row_data.permission
     AND duplicate_row.is_deleted = row_data.is_deleted
    WHERE row_data.id <> duplicate_row.keep_id;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_knowledge_base_stats'
          AND column_name = 'tenant_id'
    ) THEN
        SET @role_only_ddl = '
            DELETE row_data
            FROM ai_knowledge_base_stats row_data
            JOIN (
                SELECT knowledge_base_id, MIN(tenant_id) AS keep_tenant_id
                FROM ai_knowledge_base_stats
                GROUP BY knowledge_base_id
                HAVING COUNT(*) > 1
            ) duplicate_row
              ON duplicate_row.knowledge_base_id = row_data.knowledge_base_id
            WHERE row_data.tenant_id <> duplicate_row.keep_tenant_id';
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;

    DELETE row_data
    FROM ai_knowledge_chunk row_data
    JOIN (
        SELECT document_id, chunk_index, is_deleted, MIN(id) AS keep_id
        FROM ai_knowledge_chunk
        GROUP BY document_id, chunk_index, is_deleted
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.document_id = row_data.document_id
     AND duplicate_row.chunk_index = row_data.chunk_index
     AND duplicate_row.is_deleted = row_data.is_deleted
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE ai_llm_model row_data
    JOIN (
        SELECT llm_service_id, model_code, is_deleted, MIN(id) AS keep_id
        FROM ai_llm_model
        GROUP BY llm_service_id, model_code, is_deleted
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.llm_service_id = row_data.llm_service_id
     AND duplicate_row.model_code = row_data.model_code
     AND duplicate_row.is_deleted = row_data.is_deleted
    SET row_data.model_code = LEFT(CONCAT(LEFT(row_data.model_code, 96), '__archived_', row_data.id), 128),
        row_data.is_deleted = 1,
        row_data.update_time = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE ai_llm_service row_data
    JOIN (
        SELECT code, is_deleted, MIN(id) AS keep_id
        FROM ai_llm_service
        GROUP BY code, is_deleted
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.code = row_data.code
     AND duplicate_row.is_deleted = row_data.is_deleted
    SET row_data.code = LEFT(CONCAT(LEFT(row_data.code, 40), '__archived_', row_data.id), 64),
        row_data.is_deleted = 1,
        row_data.update_time = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    DELETE row_data
    FROM ai_message_attachment row_data
    JOIN (
        SELECT message_id, file_id, MIN(id) AS keep_id
        FROM ai_message_attachment
        WHERE file_id IS NOT NULL
        GROUP BY message_id, file_id
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.message_id = row_data.message_id
     AND duplicate_row.file_id = row_data.file_id
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE ai_conversation_share row_data
    JOIN (
        SELECT share_token, MIN(id) AS keep_id
        FROM ai_conversation_share
        GROUP BY share_token
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.share_token = row_data.share_token
    SET row_data.share_token = LEFT(CONCAT(LEFT(row_data.share_token, 96), '__archived_', row_data.id), 128),
        row_data.is_deleted = 1,
        row_data.update_time = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    DELETE row_data
    FROM ai_employee_tool_grant row_data
    JOIN (
        SELECT employee_id, tool_code, deleted, MIN(id) AS keep_id
        FROM ai_employee_tool_grant
        GROUP BY employee_id, tool_code, deleted
        HAVING COUNT(*) > 1
    ) duplicate_row
      ON duplicate_row.employee_id = row_data.employee_id
     AND duplicate_row.tool_code = row_data.tool_code
     AND duplicate_row.deleted = row_data.deleted
    WHERE row_data.id <> duplicate_row.keep_id;

    CALL role_only_ai_drop_indexes_with_column('ai_conversation', 'tenant_id');
    CALL role_only_ai_drop_column('ai_conversation', 'tenant_id');
    CALL role_only_ai_add_index('ai_conversation', 'uk_ai_conversation_code',
        'ALTER TABLE `ai_conversation` ADD UNIQUE INDEX `uk_ai_conversation_code` (`conversation_code`)');
    CALL role_only_ai_add_index('ai_conversation', 'idx_ai_conversation_owner',
        'ALTER TABLE `ai_conversation` ADD INDEX `idx_ai_conversation_owner` (`owner_user_id`, `is_pinned`, `latest_message_at`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_conversation', 'idx_ai_conversation_employee',
        'ALTER TABLE `ai_conversation` ADD INDEX `idx_ai_conversation_employee` (`employee_id`, `latest_message_at`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_employee', 'tenant_id');
    CALL role_only_ai_drop_column('ai_employee', 'tenant_id');
    CALL role_only_ai_add_index('ai_employee', 'uk_ai_employee_username',
        'ALTER TABLE `ai_employee` ADD UNIQUE INDEX `uk_ai_employee_username` (`username`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_employee', 'idx_ai_employee_enabled_sort',
        'ALTER TABLE `ai_employee` ADD INDEX `idx_ai_employee_enabled_sort` (`enabled`, `sort_order`, `id`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_employee', 'idx_ai_employee_llm',
        'ALTER TABLE `ai_employee` ADD INDEX `idx_ai_employee_llm` (`default_llm_service_id`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_employee_knowledge_base', 'tenant_id');
    CALL role_only_ai_drop_column('ai_employee_knowledge_base', 'tenant_id');
    CALL role_only_ai_add_index('ai_employee_knowledge_base', 'uk_ai_employee_knowledge_base_rel',
        'ALTER TABLE `ai_employee_knowledge_base` ADD UNIQUE INDEX `uk_ai_employee_knowledge_base_rel` (`employee_id`, `knowledge_base_id`)');
    CALL role_only_ai_add_index('ai_employee_knowledge_base', 'idx_ai_employee_knowledge_base_employee',
        'ALTER TABLE `ai_employee_knowledge_base` ADD INDEX `idx_ai_employee_knowledge_base_employee` (`employee_id`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_employee_skill', 'tenant_id');
    CALL role_only_ai_drop_column('ai_employee_skill', 'tenant_id');
    CALL role_only_ai_add_index('ai_employee_skill', 'uk_ai_employee_skill',
        'ALTER TABLE `ai_employee_skill` ADD UNIQUE INDEX `uk_ai_employee_skill` (`employee_id`, `skill_code`)');
    CALL role_only_ai_add_index('ai_employee_skill', 'idx_ai_employee_skill_code',
        'ALTER TABLE `ai_employee_skill` ADD INDEX `idx_ai_employee_skill_code` (`skill_code`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_knowledge_base', 'tenant_id');
    CALL role_only_ai_drop_column('ai_knowledge_base', 'tenant_id');
    CALL role_only_ai_add_index('ai_knowledge_base', 'uk_ai_knowledge_base_code',
        'ALTER TABLE `ai_knowledge_base` ADD UNIQUE INDEX `uk_ai_knowledge_base_code` (`kb_code`)');
    CALL role_only_ai_add_index('ai_knowledge_base', 'uk_ai_knowledge_base_owner_name',
        'ALTER TABLE `ai_knowledge_base` ADD UNIQUE INDEX `uk_ai_knowledge_base_owner_name` (`owner_user_id`, `name`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_base', 'idx_ai_knowledge_base_status',
        'ALTER TABLE `ai_knowledge_base` ADD INDEX `idx_ai_knowledge_base_status` (`status`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_base', 'idx_ai_knowledge_base_owner',
        'ALTER TABLE `ai_knowledge_base` ADD INDEX `idx_ai_knowledge_base_owner` (`owner_user_id`, `status`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_base', 'idx_ai_knowledge_base_access',
        'ALTER TABLE `ai_knowledge_base` ADD INDEX `idx_ai_knowledge_base_access` (`owner_user_id`, `visibility_scope`, `status`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_knowledge_base_acl', 'tenant_id');
    CALL role_only_ai_drop_column('ai_knowledge_base_acl', 'tenant_id');
    CALL role_only_ai_add_index('ai_knowledge_base_acl', 'uk_ai_knowledge_acl_subject',
        'ALTER TABLE `ai_knowledge_base_acl` ADD UNIQUE INDEX `uk_ai_knowledge_acl_subject` (`knowledge_base_id`, `subject_type`, `subject_id`, `permission`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_base_acl', 'idx_ai_knowledge_acl_subject',
        'ALTER TABLE `ai_knowledge_base_acl` ADD INDEX `idx_ai_knowledge_acl_subject` (`subject_type`, `subject_id`, `permission`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_base_acl', 'idx_ai_knowledge_acl_base',
        'ALTER TABLE `ai_knowledge_base_acl` ADD INDEX `idx_ai_knowledge_acl_base` (`knowledge_base_id`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_knowledge_chunk', 'tenant_id');
    CALL role_only_ai_drop_column('ai_knowledge_chunk', 'tenant_id');
    CALL role_only_ai_add_index('ai_knowledge_chunk', 'uk_ai_knowledge_chunk_index',
        'ALTER TABLE `ai_knowledge_chunk` ADD UNIQUE INDEX `uk_ai_knowledge_chunk_index` (`document_id`, `chunk_index`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_chunk', 'idx_ai_knowledge_chunk_base',
        'ALTER TABLE `ai_knowledge_chunk` ADD INDEX `idx_ai_knowledge_chunk_base` (`knowledge_base_id`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_chunk', 'idx_ai_knowledge_chunk_document',
        'ALTER TABLE `ai_knowledge_chunk` ADD INDEX `idx_ai_knowledge_chunk_document` (`document_id`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_chunk', 'idx_ai_knowledge_chunk_vector',
        'ALTER TABLE `ai_knowledge_chunk` ADD INDEX `idx_ai_knowledge_chunk_vector` (`knowledge_base_id`, `is_deleted`, `embedding_model`, `update_time`)');
    CALL role_only_ai_add_index('ai_knowledge_chunk', 'idx_ai_knowledge_chunk_acl',
        'ALTER TABLE `ai_knowledge_chunk` ADD INDEX `idx_ai_knowledge_chunk_acl` (`knowledge_base_id`, `document_id`, `is_deleted`, `update_time`, `id`)');

    CALL role_only_ai_drop_primary_with_column('ai_knowledge_base_stats', 'tenant_id');
    CALL role_only_ai_drop_indexes_with_column('ai_knowledge_base_stats', 'tenant_id');
    CALL role_only_ai_drop_column('ai_knowledge_base_stats', 'tenant_id');
    CALL role_only_ai_add_index('ai_knowledge_base_stats', 'PRIMARY',
        'ALTER TABLE `ai_knowledge_base_stats` ADD PRIMARY KEY (`knowledge_base_id`)');

    CALL role_only_ai_drop_indexes_with_column('ai_knowledge_document', 'tenant_id');
    CALL role_only_ai_drop_column('ai_knowledge_document', 'tenant_id');
    CALL role_only_ai_add_index('ai_knowledge_document', 'idx_ai_knowledge_document_base',
        'ALTER TABLE `ai_knowledge_document` ADD INDEX `idx_ai_knowledge_document_base` (`knowledge_base_id`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_knowledge_document', 'idx_ai_knowledge_document_file',
        'ALTER TABLE `ai_knowledge_document` ADD INDEX `idx_ai_knowledge_document_file` (`file_id`)');
    CALL role_only_ai_add_index('ai_knowledge_document', 'idx_ai_knowledge_document_status',
        'ALTER TABLE `ai_knowledge_document` ADD INDEX `idx_ai_knowledge_document_status` (`knowledge_base_id`, `status`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_llm_model', 'tenant_id');
    CALL role_only_ai_drop_column('ai_llm_model', 'tenant_id');
    CALL role_only_ai_add_index('ai_llm_model', 'uk_ai_llm_model_code',
        'ALTER TABLE `ai_llm_model` ADD UNIQUE INDEX `uk_ai_llm_model_code` (`llm_service_id`, `model_code`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_llm_model', 'idx_ai_llm_model_service',
        'ALTER TABLE `ai_llm_model` ADD INDEX `idx_ai_llm_model_service` (`llm_service_id`, `enabled`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_llm_service', 'tenant_id');
    CALL role_only_ai_drop_column('ai_llm_service', 'tenant_id');
    CALL role_only_ai_add_index('ai_llm_service', 'uk_ai_llm_service_code',
        'ALTER TABLE `ai_llm_service` ADD UNIQUE INDEX `uk_ai_llm_service_code` (`code`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_llm_service', 'idx_ai_llm_service_provider',
        'ALTER TABLE `ai_llm_service` ADD INDEX `idx_ai_llm_service_provider` (`provider`, `enabled`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_message', 'tenant_id');
    CALL role_only_ai_drop_column('ai_message', 'tenant_id');
    CALL role_only_ai_add_index('ai_message', 'idx_ai_message_conversation',
        'ALTER TABLE `ai_message` ADD INDEX `idx_ai_message_conversation` (`conversation_id`, `create_time`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_message_attachment', 'tenant_id');
    CALL role_only_ai_drop_column('ai_message_attachment', 'tenant_id');
    CALL role_only_ai_add_index('ai_message_attachment', 'uk_ai_message_attachment_file',
        'ALTER TABLE `ai_message_attachment` ADD UNIQUE INDEX `uk_ai_message_attachment_file` (`message_id`, `file_id`)');
    CALL role_only_ai_add_index('ai_message_attachment', 'idx_ai_message_attachment_message',
        'ALTER TABLE `ai_message_attachment` ADD INDEX `idx_ai_message_attachment_message` (`message_id`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_message_attachment', 'idx_ai_message_attachment_conversation',
        'ALTER TABLE `ai_message_attachment` ADD INDEX `idx_ai_message_attachment_conversation` (`conversation_id`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_conversation_share', 'tenant_id');
    CALL role_only_ai_drop_column('ai_conversation_share', 'tenant_id');
    CALL role_only_ai_add_index('ai_conversation_share', 'uk_ai_conversation_share_token',
        'ALTER TABLE `ai_conversation_share` ADD UNIQUE INDEX `uk_ai_conversation_share_token` (`share_token`)');
    CALL role_only_ai_add_index('ai_conversation_share', 'idx_ai_conversation_share_conversation',
        'ALTER TABLE `ai_conversation_share` ADD INDEX `idx_ai_conversation_share_conversation` (`conversation_id`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_conversation_share', 'idx_ai_conversation_share_status',
        'ALTER TABLE `ai_conversation_share` ADD INDEX `idx_ai_conversation_share_status` (`status`, `expires_at`, `is_deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_tool_audit_log', 'tenant_id');
    CALL role_only_ai_drop_column('ai_tool_audit_log', 'tenant_id');
    CALL role_only_ai_add_index('ai_tool_audit_log', 'idx_ai_tool_audit_created',
        'ALTER TABLE `ai_tool_audit_log` ADD INDEX `idx_ai_tool_audit_created` (`create_time`)');
    CALL role_only_ai_add_index('ai_tool_audit_log', 'idx_ai_tool_audit_employee',
        'ALTER TABLE `ai_tool_audit_log` ADD INDEX `idx_ai_tool_audit_employee` (`employee_id`, `create_time`)');
    CALL role_only_ai_add_index('ai_tool_audit_log', 'idx_ai_tool_audit_skill',
        'ALTER TABLE `ai_tool_audit_log` ADD INDEX `idx_ai_tool_audit_skill` (`skill_code`, `result_status`, `create_time`)');

    CALL role_only_ai_drop_indexes_with_column('ai_tool_call_plan', 'tenant_id');
    CALL role_only_ai_drop_column('ai_tool_call_plan', 'tenant_id');
    CALL role_only_ai_add_index('ai_tool_call_plan', 'idx_ai_tool_plan_owner',
        'ALTER TABLE `ai_tool_call_plan` ADD INDEX `idx_ai_tool_plan_owner` (`owner_user_id`, `status`, `expires_at`)');
    CALL role_only_ai_add_index('ai_tool_call_plan', 'idx_ai_tool_plan_conversation',
        'ALTER TABLE `ai_tool_call_plan` ADD INDEX `idx_ai_tool_plan_conversation` (`conversation_id`, `create_time`)');

    CALL role_only_ai_drop_indexes_with_column('ai_tool_policy', 'tenant_id');
    CALL role_only_ai_drop_column('ai_tool_policy', 'tenant_id');
    CALL role_only_ai_add_index('ai_tool_policy', 'idx_ai_tool_policy_enabled',
        'ALTER TABLE `ai_tool_policy` ADD INDEX `idx_ai_tool_policy_enabled` (`enabled`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_tool_policy', 'idx_ai_tool_policy_tool',
        'ALTER TABLE `ai_tool_policy` ADD INDEX `idx_ai_tool_policy_tool` (`tool_code`, `enabled`, `is_deleted`)');
    CALL role_only_ai_add_index('ai_tool_policy', 'idx_ai_tool_policy_runtime',
        'ALTER TABLE `ai_tool_policy` ADD INDEX `idx_ai_tool_policy_runtime` (`enabled`, `is_deleted`, `tool_code`, `action_type`, `risk_level`)');

    CALL role_only_ai_drop_indexes_with_column('ai_employee_tool_grant', 'tenant_id');
    CALL role_only_ai_drop_column('ai_employee_tool_grant', 'tenant_id');
    CALL role_only_ai_add_index('ai_employee_tool_grant', 'uk_ai_employee_tool_grant',
        'ALTER TABLE `ai_employee_tool_grant` ADD UNIQUE INDEX `uk_ai_employee_tool_grant` (`employee_id`, `tool_code`, `deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_employee_tool_grant_dept', 'tenant_id');
    CALL role_only_ai_drop_column('ai_employee_tool_grant_dept', 'tenant_id');
    CALL role_only_ai_add_index('ai_employee_tool_grant_dept', 'idx_ai_employee_tool_grant_dept',
        'ALTER TABLE `ai_employee_tool_grant_dept` ADD INDEX `idx_ai_employee_tool_grant_dept` (`grant_id`, `deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_employee_tool_grant_user', 'tenant_id');
    CALL role_only_ai_drop_column('ai_employee_tool_grant_user', 'tenant_id');
    CALL role_only_ai_add_index('ai_employee_tool_grant_user', 'idx_ai_employee_tool_grant_user',
        'ALTER TABLE `ai_employee_tool_grant_user` ADD INDEX `idx_ai_employee_tool_grant_user` (`grant_id`, `deleted`)');

    CALL role_only_ai_drop_indexes_with_column('ai_tool_execution_audit', 'tenant_id');
    CALL role_only_ai_drop_column('ai_tool_execution_audit', 'tenant_id');
    CALL role_only_ai_add_index('ai_tool_execution_audit', 'idx_ai_tool_execution_audit_created',
        'ALTER TABLE `ai_tool_execution_audit` ADD INDEX `idx_ai_tool_execution_audit_created` (`created_at`)');
    CALL role_only_ai_add_index('ai_tool_execution_audit', 'idx_ai_tool_execution_audit_employee',
        'ALTER TABLE `ai_tool_execution_audit` ADD INDEX `idx_ai_tool_execution_audit_employee` (`employee_id`, `created_at`)');
END $$

CALL role_only_ai_schema_refresh() $$

DROP PROCEDURE IF EXISTS role_only_ai_schema_refresh $$
DROP PROCEDURE IF EXISTS role_only_ai_add_index $$
DROP PROCEDURE IF EXISTS role_only_ai_drop_column $$
DROP PROCEDURE IF EXISTS role_only_ai_drop_primary_with_column $$
DROP PROCEDURE IF EXISTS role_only_ai_drop_indexes_with_column $$
DROP PROCEDURE IF EXISTS role_only_ai_drop_index $$

DELIMITER ;
