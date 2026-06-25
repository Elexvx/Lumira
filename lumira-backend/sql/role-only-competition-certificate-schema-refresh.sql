-- Refresh competition registration and certificate tables from tenant-scoped storage to role-only/global storage.
-- Safe to re-run: helpers check existing columns and indexes before changing them.

DELIMITER $$

DROP PROCEDURE IF EXISTS role_only_comp_drop_index $$
CREATE PROCEDURE role_only_comp_drop_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
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

DROP PROCEDURE IF EXISTS role_only_comp_drop_indexes_with_column $$
CREATE PROCEDURE role_only_comp_drop_indexes_with_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
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
        CALL role_only_comp_drop_index(p_table, v_index);
    END LOOP;
    CLOSE index_cursor;
END $$

DROP PROCEDURE IF EXISTS role_only_comp_drop_column $$
CREATE PROCEDURE role_only_comp_drop_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
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

DROP PROCEDURE IF EXISTS role_only_comp_add_index $$
CREATE PROCEDURE role_only_comp_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_sql TEXT)
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

DROP PROCEDURE IF EXISTS role_only_competition_certificate_schema_refresh $$
CREATE PROCEDURE role_only_competition_certificate_schema_refresh()
BEGIN
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_competition_registration_20260625 AS
        SELECT * FROM competition_registration;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_certificate_template_20260625 AS
        SELECT * FROM certificate_template;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_certificate_template_version_20260625 AS
        SELECT * FROM certificate_template_version;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_certificate_batch_20260625 AS
        SELECT * FROM certificate_batch;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_certificate_record_20260625 AS
        SELECT * FROM certificate_record;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_competition_stage_20260625 AS
        SELECT * FROM competition_stage;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_competition_stage_form_20260625 AS
        SELECT * FROM competition_stage_form;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_registration_material_submission_20260625 AS
        SELECT * FROM registration_material_submission;
    CREATE TABLE IF NOT EXISTS role_only_comp_backup_registration_material_value_20260625 AS
        SELECT * FROM registration_material_value;

    UPDATE competition_registration row_data
    JOIN (
        SELECT registration_no, MIN(id) AS keep_id
        FROM competition_registration
        GROUP BY registration_no
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.registration_no = row_data.registration_no
    SET row_data.registration_no = LEFT(CONCAT(LEFT(row_data.registration_no, 40), '__archived_', row_data.id), 64),
        row_data.deleted = 1,
        row_data.updated_at = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE competition_registration row_data
    JOIN (
        SELECT participant_no, MIN(id) AS keep_id
        FROM competition_registration
        WHERE participant_no IS NOT NULL
        GROUP BY participant_no
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.participant_no = row_data.participant_no
    SET row_data.participant_no = LEFT(CONCAT(LEFT(row_data.participant_no, 40), '__archived_', row_data.id), 64),
        row_data.deleted = 1,
        row_data.updated_at = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE certificate_template row_data
    JOIN (
        SELECT template_code, MIN(id) AS keep_id
        FROM certificate_template
        GROUP BY template_code
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.template_code = row_data.template_code
    SET row_data.template_code = LEFT(CONCAT(LEFT(row_data.template_code, 40), '__archived_', row_data.id), 64),
        row_data.deleted = 1,
        row_data.updated_at = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE certificate_batch row_data
    JOIN (
        SELECT batch_no, MIN(id) AS keep_id
        FROM certificate_batch
        GROUP BY batch_no
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.batch_no = row_data.batch_no
    SET row_data.batch_no = LEFT(CONCAT(LEFT(row_data.batch_no, 40), '__archived_', row_data.id), 64),
        row_data.deleted = 1,
        row_data.updated_at = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    UPDATE certificate_record row_data
    JOIN (
        SELECT certificate_no, MIN(id) AS keep_id
        FROM certificate_record
        GROUP BY certificate_no
        HAVING COUNT(*) > 1
    ) duplicate_row ON duplicate_row.certificate_no = row_data.certificate_no
    SET row_data.certificate_no = LEFT(CONCAT(LEFT(row_data.certificate_no, 40), '__archived_', row_data.id), 64),
        row_data.deleted = 1,
        row_data.updated_at = NOW()
    WHERE row_data.id <> duplicate_row.keep_id;

    CALL role_only_comp_drop_index('competition_registration', 'uk_competition_registration_no');
    CALL role_only_comp_drop_index('competition_registration', 'uk_competition_registration_participant');
    CALL role_only_comp_drop_index('competition_registration', 'idx_competition_registration_owner');
    CALL role_only_comp_drop_index('competition_registration', 'idx_competition_registration_competition');
    CALL role_only_comp_drop_index('competition_registration', 'idx_competition_registration_payment');
    CALL role_only_comp_drop_indexes_with_column('competition_registration', 'tenant_id');
    CALL role_only_comp_drop_column('competition_registration', 'tenant_id');
    CALL role_only_comp_add_index('competition_registration', 'uk_competition_registration_no',
        'ALTER TABLE `competition_registration` ADD UNIQUE INDEX `uk_competition_registration_no` (`registration_no`, `deleted`)');
    CALL role_only_comp_add_index('competition_registration', 'uk_competition_registration_participant',
        'ALTER TABLE `competition_registration` ADD UNIQUE INDEX `uk_competition_registration_participant` (`participant_no`, `deleted`)');
    CALL role_only_comp_add_index('competition_registration', 'idx_competition_registration_owner',
        'ALTER TABLE `competition_registration` ADD INDEX `idx_competition_registration_owner` (`owner_user_id`, `deleted`, `created_at`)');
    CALL role_only_comp_add_index('competition_registration', 'idx_competition_registration_competition',
        'ALTER TABLE `competition_registration` ADD INDEX `idx_competition_registration_competition` (`competition_id`, `status`, `deleted`)');
    CALL role_only_comp_add_index('competition_registration', 'idx_competition_registration_payment',
        'ALTER TABLE `competition_registration` ADD INDEX `idx_competition_registration_payment` (`payment_order_no`, `deleted`)');

    CALL role_only_comp_drop_index('certificate_template', 'uk_certificate_template_code');
    CALL role_only_comp_drop_index('certificate_template', 'idx_certificate_template_status');
    CALL role_only_comp_drop_indexes_with_column('certificate_template', 'tenant_id');
    CALL role_only_comp_drop_column('certificate_template', 'tenant_id');
    CALL role_only_comp_add_index('certificate_template', 'uk_certificate_template_code',
        'ALTER TABLE `certificate_template` ADD UNIQUE INDEX `uk_certificate_template_code` (`template_code`, `deleted`)');
    CALL role_only_comp_add_index('certificate_template', 'idx_certificate_template_status',
        'ALTER TABLE `certificate_template` ADD INDEX `idx_certificate_template_status` (`status`, `deleted`, `updated_at`)');

    CALL role_only_comp_drop_index('certificate_template_version', 'uk_certificate_template_version');
    CALL role_only_comp_drop_index('certificate_template_version', 'idx_certificate_template_version_status');
    CALL role_only_comp_drop_indexes_with_column('certificate_template_version', 'tenant_id');
    CALL role_only_comp_drop_column('certificate_template_version', 'tenant_id');
    CALL role_only_comp_add_index('certificate_template_version', 'uk_certificate_template_version',
        'ALTER TABLE `certificate_template_version` ADD UNIQUE INDEX `uk_certificate_template_version` (`template_id`, `version`, `deleted`)');
    CALL role_only_comp_add_index('certificate_template_version', 'idx_certificate_template_version_status',
        'ALTER TABLE `certificate_template_version` ADD INDEX `idx_certificate_template_version_status` (`template_id`, `status`, `deleted`)');

    CALL role_only_comp_drop_index('certificate_batch', 'uk_certificate_batch_no');
    CALL role_only_comp_drop_index('certificate_batch', 'idx_certificate_batch_template');
    CALL role_only_comp_drop_index('certificate_batch', 'idx_certificate_batch_status');
    CALL role_only_comp_drop_indexes_with_column('certificate_batch', 'tenant_id');
    CALL role_only_comp_drop_column('certificate_batch', 'tenant_id');
    CALL role_only_comp_add_index('certificate_batch', 'uk_certificate_batch_no',
        'ALTER TABLE `certificate_batch` ADD UNIQUE INDEX `uk_certificate_batch_no` (`batch_no`, `deleted`)');
    CALL role_only_comp_add_index('certificate_batch', 'idx_certificate_batch_template',
        'ALTER TABLE `certificate_batch` ADD INDEX `idx_certificate_batch_template` (`template_id`, `template_version_id`, `deleted`)');
    CALL role_only_comp_add_index('certificate_batch', 'idx_certificate_batch_status',
        'ALTER TABLE `certificate_batch` ADD INDEX `idx_certificate_batch_status` (`status`, `deleted`, `created_at`)');

    CALL role_only_comp_drop_index('certificate_record', 'uk_certificate_record_no');
    CALL role_only_comp_drop_index('certificate_record', 'idx_certificate_record_batch');
    CALL role_only_comp_drop_index('certificate_record', 'idx_certificate_record_template');
    CALL role_only_comp_drop_index('certificate_record', 'idx_certificate_record_status');
    CALL role_only_comp_drop_index('certificate_record', 'idx_certificate_record_recipient');
    CALL role_only_comp_drop_indexes_with_column('certificate_record', 'tenant_id');
    CALL role_only_comp_drop_column('certificate_record', 'tenant_id');
    CALL role_only_comp_add_index('certificate_record', 'uk_certificate_record_no',
        'ALTER TABLE `certificate_record` ADD UNIQUE INDEX `uk_certificate_record_no` (`certificate_no`, `deleted`)');
    CALL role_only_comp_add_index('certificate_record', 'idx_certificate_record_batch',
        'ALTER TABLE `certificate_record` ADD INDEX `idx_certificate_record_batch` (`batch_id`, `deleted`)');
    CALL role_only_comp_add_index('certificate_record', 'idx_certificate_record_template',
        'ALTER TABLE `certificate_record` ADD INDEX `idx_certificate_record_template` (`template_id`, `template_version_id`, `deleted`)');
    CALL role_only_comp_add_index('certificate_record', 'idx_certificate_record_status',
        'ALTER TABLE `certificate_record` ADD INDEX `idx_certificate_record_status` (`status`, `deleted`, `created_at`)');
    CALL role_only_comp_add_index('certificate_record', 'idx_certificate_record_recipient',
        'ALTER TABLE `certificate_record` ADD INDEX `idx_certificate_record_recipient` (`recipient_name`, `deleted`)');

    CALL role_only_comp_drop_index('competition_stage', 'uk_competition_stage_code');
    CALL role_only_comp_drop_index('competition_stage', 'idx_competition_stage_competition');
    CALL role_only_comp_drop_indexes_with_column('competition_stage', 'tenant_id');
    CALL role_only_comp_drop_column('competition_stage', 'tenant_id');
    CALL role_only_comp_add_index('competition_stage', 'uk_competition_stage_code',
        'ALTER TABLE `competition_stage` ADD UNIQUE INDEX `uk_competition_stage_code` (`competition_id`, `stage_code`, `deleted`)');
    CALL role_only_comp_add_index('competition_stage', 'idx_competition_stage_competition',
        'ALTER TABLE `competition_stage` ADD INDEX `idx_competition_stage_competition` (`competition_id`, `deleted`, `sort`)');

    CALL role_only_comp_drop_index('competition_stage_form', 'uk_competition_stage_form');
    CALL role_only_comp_drop_index('competition_stage_form', 'idx_competition_stage_form_competition');
    CALL role_only_comp_drop_indexes_with_column('competition_stage_form', 'tenant_id');
    CALL role_only_comp_drop_column('competition_stage_form', 'tenant_id');
    CALL role_only_comp_add_index('competition_stage_form', 'uk_competition_stage_form',
        'ALTER TABLE `competition_stage_form` ADD UNIQUE INDEX `uk_competition_stage_form` (`stage_id`, `version`, `deleted`)');
    CALL role_only_comp_add_index('competition_stage_form', 'idx_competition_stage_form_competition',
        'ALTER TABLE `competition_stage_form` ADD INDEX `idx_competition_stage_form_competition` (`competition_id`, `stage_id`, `deleted`)');

    CALL role_only_comp_drop_index('registration_material_submission', 'uk_registration_material_submission');
    CALL role_only_comp_drop_index('registration_material_submission', 'idx_registration_material_submission_competition');
    CALL role_only_comp_drop_indexes_with_column('registration_material_submission', 'tenant_id');
    CALL role_only_comp_drop_column('registration_material_submission', 'tenant_id');
    CALL role_only_comp_add_index('registration_material_submission', 'uk_registration_material_submission',
        'ALTER TABLE `registration_material_submission` ADD UNIQUE INDEX `uk_registration_material_submission` (`registration_id`, `stage_id`, `deleted`)');
    CALL role_only_comp_add_index('registration_material_submission', 'idx_registration_material_submission_competition',
        'ALTER TABLE `registration_material_submission` ADD INDEX `idx_registration_material_submission_competition` (`competition_id`, `stage_id`, `deleted`)');

    CALL role_only_comp_drop_index('registration_material_value', 'idx_registration_material_value_submission');
    CALL role_only_comp_drop_indexes_with_column('registration_material_value', 'tenant_id');
    CALL role_only_comp_drop_column('registration_material_value', 'tenant_id');
    CALL role_only_comp_add_index('registration_material_value', 'idx_registration_material_value_submission',
        'ALTER TABLE `registration_material_value` ADD INDEX `idx_registration_material_value_submission` (`submission_id`, `deleted`)');
END $$

CALL role_only_competition_certificate_schema_refresh() $$

DROP PROCEDURE IF EXISTS role_only_competition_certificate_schema_refresh $$
DROP PROCEDURE IF EXISTS role_only_comp_add_index $$
DROP PROCEDURE IF EXISTS role_only_comp_drop_column $$
DROP PROCEDURE IF EXISTS role_only_comp_drop_indexes_with_column $$
DROP PROCEDURE IF EXISTS role_only_comp_drop_index $$

DELIMITER ;
