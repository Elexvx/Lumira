-- Refresh remaining legacy tenant-scoped tables to role-only/global storage.
-- Safe to re-run: helpers check existing tables, columns, and indexes.

DELIMITER $$

DROP PROCEDURE IF EXISTS role_only_remaining_backup_table $$
CREATE PROCEDURE role_only_remaining_backup_table(IN p_table VARCHAR(64), IN p_backup VARCHAR(96))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table
    ) THEN
        SET @role_only_ddl = CONCAT(
            'CREATE TABLE IF NOT EXISTS `', REPLACE(p_backup, '`', '``'), '` AS SELECT * FROM `',
            REPLACE(p_table, '`', '``'), '`'
        );
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_remaining_drop_index $$
CREATE PROCEDURE role_only_remaining_drop_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND index_name = p_index
    ) THEN
        SET @role_only_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table, '`', '``'), '` DROP INDEX `', REPLACE(p_index, '`', '``'), '`'
        );
        PREPARE role_only_stmt FROM @role_only_ddl;
        EXECUTE role_only_stmt;
        DEALLOCATE PREPARE role_only_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS role_only_remaining_drop_indexes_with_column $$
CREATE PROCEDURE role_only_remaining_drop_indexes_with_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
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
        CALL role_only_remaining_drop_index(p_table, v_index);
    END LOOP;
    CLOSE index_cursor;
END $$

DROP PROCEDURE IF EXISTS role_only_remaining_drop_column $$
CREATE PROCEDURE role_only_remaining_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
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

DROP PROCEDURE IF EXISTS role_only_remaining_add_index $$
CREATE PROCEDURE role_only_remaining_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_sql TEXT)
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

DROP PROCEDURE IF EXISTS role_only_remaining_drop_tenant_column $$
CREATE PROCEDURE role_only_remaining_drop_tenant_column(IN p_table VARCHAR(64))
BEGIN
    CALL role_only_remaining_drop_indexes_with_column(p_table, 'tenant_id');
    CALL role_only_remaining_drop_column(p_table, 'tenant_id');
END $$

DROP PROCEDURE IF EXISTS role_only_remaining_schema_refresh $$
CREATE PROCEDURE role_only_remaining_schema_refresh()
BEGIN
    CALL role_only_remaining_backup_table('iam_delegation_grant', 'role_only_remaining_backup_iam_delegation_grant_20260625');
    CALL role_only_remaining_backup_table('iam_permission', 'role_only_remaining_backup_iam_permission_20260625');
    CALL role_only_remaining_backup_table('iam_subject', 'role_only_remaining_backup_iam_subject_20260625');
    CALL role_only_remaining_backup_table('iam_subject_role', 'role_only_remaining_backup_iam_subject_role_20260625');
    CALL role_only_remaining_backup_table('plugin_event_outbox', 'role_only_remaining_backup_plugin_event_outbox_20260625');
    CALL role_only_remaining_backup_table('sys_config', 'role_only_remaining_backup_sys_config_20260625');
    CALL role_only_remaining_backup_table('sys_department', 'role_only_remaining_backup_sys_department_20260625');
    CALL role_only_remaining_backup_table('sys_department_closure', 'role_only_remaining_backup_sys_department_closure_20260625');
    CALL role_only_remaining_backup_table('sys_dict_item', 'role_only_remaining_backup_sys_dict_item_20260625');
    CALL role_only_remaining_backup_table('sys_dict_type', 'role_only_remaining_backup_sys_dict_type_20260625');
    CALL role_only_remaining_backup_table('sys_plugin_runtime_log', 'role_only_remaining_backup_sys_plugin_runtime_log_20260625');
    CALL role_only_remaining_backup_table('sys_plugin_tenant', 'role_only_remaining_backup_sys_plugin_tenant_20260625');
    CALL role_only_remaining_backup_table('sys_sensitive_word', 'role_only_remaining_backup_sys_sensitive_word_20260625');
    CALL role_only_remaining_backup_table('sys_user_passkey_credential', 'role_only_remaining_backup_sys_user_passkey_credential_20260625');
    CALL role_only_remaining_backup_table('sys_user_tenant', 'role_only_remaining_backup_sys_user_tenant_20260625');
    CALL role_only_remaining_backup_table('sys_user_tenant_profile', 'role_only_remaining_backup_sys_user_tenant_profile_20260625');
    CALL role_only_remaining_backup_table('sys_verification_binding', 'role_only_remaining_backup_sys_verification_binding_20260625');
    CALL role_only_remaining_backup_table('sys_verification_challenge', 'role_only_remaining_backup_sys_verification_challenge_20260625');
    CALL role_only_remaining_backup_table('team', 'role_only_remaining_backup_team_20260625');
    CALL role_only_remaining_backup_table('team_invite', 'role_only_remaining_backup_team_invite_20260625');
    CALL role_only_remaining_backup_table('team_join_request', 'role_only_remaining_backup_team_join_request_20260625');
    CALL role_only_remaining_backup_table('team_member', 'role_only_remaining_backup_team_member_20260625');

    DROP TABLE IF EXISTS sys_plugin_tenant;
    DROP TABLE IF EXISTS sys_user_tenant_profile;
    DROP TABLE IF EXISTS sys_user_tenant;

    CALL role_only_remaining_drop_tenant_column('iam_delegation_grant');
    CALL role_only_remaining_add_index('iam_delegation_grant', 'idx_delegation_delegate',
        'ALTER TABLE `iam_delegation_grant` ADD INDEX `idx_delegation_delegate` (`delegate_subject_id`, `deleted`)');
    CALL role_only_remaining_add_index('iam_delegation_grant', 'idx_delegation_delegator',
        'ALTER TABLE `iam_delegation_grant` ADD INDEX `idx_delegation_delegator` (`delegator_subject_id`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('iam_permission');
    CALL role_only_remaining_add_index('iam_permission', 'uk_iam_permission_key',
        'ALTER TABLE `iam_permission` ADD UNIQUE INDEX `uk_iam_permission_key` (`permission_key`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('iam_subject');
    CALL role_only_remaining_add_index('iam_subject', 'uk_iam_subject_type_ref',
        'ALTER TABLE `iam_subject` ADD UNIQUE INDEX `uk_iam_subject_type_ref` (`subject_type`, `ref_id`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('iam_subject_role');
    CALL role_only_remaining_add_index('iam_subject_role', 'uk_iam_subject_role',
        'ALTER TABLE `iam_subject_role` ADD UNIQUE INDEX `uk_iam_subject_role` (`subject_id`, `role_id`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('plugin_event_outbox');
    CALL role_only_remaining_add_index('plugin_event_outbox', 'uk_plugin_event_outbox_event',
        'ALTER TABLE `plugin_event_outbox` ADD UNIQUE INDEX `uk_plugin_event_outbox_event` (`event_type`, `event_key`)');
    CALL role_only_remaining_add_index('plugin_event_outbox', 'idx_plugin_event_outbox_status',
        'ALTER TABLE `plugin_event_outbox` ADD INDEX `idx_plugin_event_outbox_status` (`status`, `next_retry_at`)');
    CALL role_only_remaining_add_index('plugin_event_outbox', 'idx_plugin_event_outbox_created_at',
        'ALTER TABLE `plugin_event_outbox` ADD INDEX `idx_plugin_event_outbox_created_at` (`created_at`)');

    CALL role_only_remaining_drop_tenant_column('sys_config');
    CALL role_only_remaining_add_index('sys_config', 'uk_sys_config_key',
        'ALTER TABLE `sys_config` ADD UNIQUE INDEX `uk_sys_config_key` (`config_key`)');
    CALL role_only_remaining_add_index('sys_config', 'idx_sys_config_scope_key_deleted',
        'ALTER TABLE `sys_config` ADD INDEX `idx_sys_config_scope_key_deleted` (`config_scope`, `config_key`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('sys_department');
    CALL role_only_remaining_add_index('sys_department', 'uk_sys_department_code',
        'ALTER TABLE `sys_department` ADD UNIQUE INDEX `uk_sys_department_code` (`dept_code`)');
    CALL role_only_remaining_add_index('sys_department', 'idx_sys_department_parent',
        'ALTER TABLE `sys_department` ADD INDEX `idx_sys_department_parent` (`parent_id`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('sys_department_closure');
    CALL role_only_remaining_add_index('sys_department_closure', 'uk_dept_closure',
        'ALTER TABLE `sys_department_closure` ADD UNIQUE INDEX `uk_dept_closure` (`ancestor_id`, `descendant_id`)');
    CALL role_only_remaining_add_index('sys_department_closure', 'idx_dept_closure_descendant',
        'ALTER TABLE `sys_department_closure` ADD INDEX `idx_dept_closure_descendant` (`descendant_id`)');
    CALL role_only_remaining_add_index('sys_department_closure', 'idx_dept_closure_ancestor_depth',
        'ALTER TABLE `sys_department_closure` ADD INDEX `idx_dept_closure_ancestor_depth` (`ancestor_id`, `depth`)');

    CALL role_only_remaining_drop_tenant_column('sys_dict_item');
    CALL role_only_remaining_add_index('sys_dict_item', 'uk_sys_dict_item_value',
        'ALTER TABLE `sys_dict_item` ADD UNIQUE INDEX `uk_sys_dict_item_value` (`dict_type_id`, `item_value`)');

    CALL role_only_remaining_drop_tenant_column('sys_dict_type');
    CALL role_only_remaining_add_index('sys_dict_type', 'uk_sys_dict_type_code',
        'ALTER TABLE `sys_dict_type` ADD UNIQUE INDEX `uk_sys_dict_type_code` (`dict_code`)');

    CALL role_only_remaining_drop_tenant_column('sys_plugin_runtime_log');
    CALL role_only_remaining_add_index('sys_plugin_runtime_log', 'idx_sys_plugin_runtime_log_plugin_created',
        'ALTER TABLE `sys_plugin_runtime_log` ADD INDEX `idx_sys_plugin_runtime_log_plugin_created` (`plugin_code`, `created_at`)');

    CALL role_only_remaining_drop_tenant_column('sys_sensitive_word');
    CALL role_only_remaining_add_index('sys_sensitive_word', 'uk_sys_sensitive_word_normalized',
        'ALTER TABLE `sys_sensitive_word` ADD UNIQUE INDEX `uk_sys_sensitive_word_normalized` (`normalized_word`, `deleted`)');
    CALL role_only_remaining_add_index('sys_sensitive_word', 'idx_sys_sensitive_word_enabled',
        'ALTER TABLE `sys_sensitive_word` ADD INDEX `idx_sys_sensitive_word_enabled` (`enabled`, `deleted`)');
    CALL role_only_remaining_add_index('sys_sensitive_word', 'idx_sensitive_word_enabled',
        'ALTER TABLE `sys_sensitive_word` ADD INDEX `idx_sensitive_word_enabled` (`enabled`, `deleted`, `normalized_word`)');

    CALL role_only_remaining_drop_tenant_column('sys_user_passkey_credential');
    CALL role_only_remaining_add_index('sys_user_passkey_credential', 'uk_passkey_credential_id',
        'ALTER TABLE `sys_user_passkey_credential` ADD UNIQUE INDEX `uk_passkey_credential_id` (`credential_id`)');
    CALL role_only_remaining_add_index('sys_user_passkey_credential', 'idx_passkey_user',
        'ALTER TABLE `sys_user_passkey_credential` ADD INDEX `idx_passkey_user` (`user_id`, `deleted`)');
    CALL role_only_remaining_add_index('sys_user_passkey_credential', 'idx_passkey_user_handle',
        'ALTER TABLE `sys_user_passkey_credential` ADD INDEX `idx_passkey_user_handle` (`user_handle`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('sys_verification_binding');
    CALL role_only_remaining_add_index('sys_verification_binding', 'uk_sys_verification_binding',
        'ALTER TABLE `sys_verification_binding` ADD UNIQUE INDEX `uk_sys_verification_binding` (`user_id`, `factor_code`)');

    CALL role_only_remaining_drop_tenant_column('sys_verification_challenge');
    CALL role_only_remaining_add_index('sys_verification_challenge', 'uk_sys_verification_challenge',
        'ALTER TABLE `sys_verification_challenge` ADD UNIQUE INDEX `uk_sys_verification_challenge` (`challenge_id`)');

    CALL role_only_remaining_drop_tenant_column('team');
    CALL role_only_remaining_add_index('team', 'uk_team_code',
        'ALTER TABLE `team` ADD UNIQUE INDEX `uk_team_code` (`team_code`, `deleted`)');
    CALL role_only_remaining_add_index('team', 'idx_team_owner',
        'ALTER TABLE `team` ADD INDEX `idx_team_owner` (`owner_user_id`, `deleted`)');
    CALL role_only_remaining_add_index('team', 'idx_team_status',
        'ALTER TABLE `team` ADD INDEX `idx_team_status` (`status`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('team_member');
    CALL role_only_remaining_add_index('team_member', 'uk_team_member',
        'ALTER TABLE `team_member` ADD UNIQUE INDEX `uk_team_member` (`team_id`, `user_id`, `deleted`)');
    CALL role_only_remaining_add_index('team_member', 'idx_team_member_user',
        'ALTER TABLE `team_member` ADD INDEX `idx_team_member_user` (`user_id`, `status`, `deleted`)');
    CALL role_only_remaining_add_index('team_member', 'idx_team_member_team',
        'ALTER TABLE `team_member` ADD INDEX `idx_team_member_team` (`team_id`, `status`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('team_invite');
    CALL role_only_remaining_add_index('team_invite', 'uk_team_invite_token',
        'ALTER TABLE `team_invite` ADD UNIQUE INDEX `uk_team_invite_token` (`invite_token_hash`, `deleted`)');
    CALL role_only_remaining_add_index('team_invite', 'uk_team_invite_code',
        'ALTER TABLE `team_invite` ADD UNIQUE INDEX `uk_team_invite_code` (`invite_code`, `deleted`)');
    CALL role_only_remaining_add_index('team_invite', 'idx_team_invite_team',
        'ALTER TABLE `team_invite` ADD INDEX `idx_team_invite_team` (`team_id`, `status`, `deleted`)');

    CALL role_only_remaining_drop_tenant_column('team_join_request');
    CALL role_only_remaining_add_index('team_join_request', 'uk_team_join_pending',
        'ALTER TABLE `team_join_request` ADD UNIQUE INDEX `uk_team_join_pending` (`team_id`, `user_id`, `status`, `deleted`)');
    CALL role_only_remaining_add_index('team_join_request', 'idx_team_join_team',
        'ALTER TABLE `team_join_request` ADD INDEX `idx_team_join_team` (`team_id`, `status`, `deleted`)');
    CALL role_only_remaining_add_index('team_join_request', 'idx_team_join_user',
        'ALTER TABLE `team_join_request` ADD INDEX `idx_team_join_user` (`user_id`, `status`, `deleted`)');
END $$

CALL role_only_remaining_schema_refresh() $$

DROP PROCEDURE IF EXISTS role_only_remaining_schema_refresh $$
DROP PROCEDURE IF EXISTS role_only_remaining_drop_tenant_column $$
DROP PROCEDURE IF EXISTS role_only_remaining_add_index $$
DROP PROCEDURE IF EXISTS role_only_remaining_drop_column $$
DROP PROCEDURE IF EXISTS role_only_remaining_drop_indexes_with_column $$
DROP PROCEDURE IF EXISTS role_only_remaining_drop_index $$
DROP PROCEDURE IF EXISTS role_only_remaining_backup_table $$

DELIMITER ;
