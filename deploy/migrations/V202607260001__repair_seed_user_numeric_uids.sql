-- Repair bootstrap users whose UIDs were generated through floating-point
-- arithmetic and persisted as scientific-notation strings. Keep the entire
-- user-reference rewrite transactional so no relation can observe a mixed UID.

DELIMITER $$

CREATE PROCEDURE `repair_seed_user_numeric_uids`()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE reference_table VARCHAR(64);
    DECLARE reference_column VARCHAR(64);
    DECLARE old_admin_uid VARCHAR(36);
    DECLARE old_user_uid VARCHAR(36);
    DECLARE new_admin_uid CHAR(18);
    DECLARE new_user_uid CHAR(18);

    DECLARE user_uuid_references CURSOR FOR
        SELECT `table_name`, `column_name`
        FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND (
              (`table_name` = 'sys_user' AND `column_name` = 'uuid')
              OR `column_name` IN (
                  'user_uuid',
                  'owner_user_uuid',
                  'target_user_uuid',
                  'operator_user_uuid',
                  'submitter_user_uuid',
                  'created_by_uuid',
                  'updated_by_uuid',
                  'uploaded_by_uuid',
                  'confirmed_by_uuid',
                  'operator_uuid',
                  'published_by_uuid',
                  'submitter_uuid',
                  'decided_by_uuid',
                  'changed_by_uuid',
                  'invited_by_uuid',
                  'reviewed_by_uuid'
              )
          )
          AND `data_type` IN ('char', 'varchar')
        ORDER BY
            CASE WHEN `table_name` = 'sys_user' AND `column_name` = 'uuid' THEN 1 ELSE 0 END,
            `table_name`,
            `ordinal_position`;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET old_admin_uid = (SELECT `uuid` FROM `sys_user` WHERE `id` = 1001 LIMIT 1);
    SET old_user_uid = (SELECT `uuid` FROM `sys_user` WHERE `id` = 1002 LIMIT 1);
    SET new_admin_uid = IF(
        old_admin_uid REGEXP '^[1-9][0-9]{17}$',
        old_admin_uid,
        '900000000000001001'
    );
    SET new_user_uid = IF(
        old_user_uid REGEXP '^[1-9][0-9]{17}$',
        old_user_uid,
        '900000000000001002'
    );

    IF old_admin_uid IS NOT NULL
       AND old_admin_uid <> new_admin_uid
       AND EXISTS (
           SELECT 1 FROM `sys_user`
           WHERE BINARY `uuid` = BINARY new_admin_uid AND `id` <> 1001
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot repair admin UID because the target UID is already in use';
    END IF;

    IF old_user_uid IS NOT NULL
       AND old_user_uid <> new_user_uid
       AND EXISTS (
           SELECT 1 FROM `sys_user`
           WHERE BINARY `uuid` = BINARY new_user_uid AND `id` <> 1002
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot repair common-user UID because the target UID is already in use';
    END IF;

    START TRANSACTION;
    OPEN user_uuid_references;

    reference_loop: LOOP
        FETCH user_uuid_references INTO reference_table, reference_column;
        IF done THEN
            LEAVE reference_loop;
        END IF;

        SET @repair_uid_sql = CONCAT(
            'UPDATE `',
            REPLACE(reference_table, '`', '``'),
            '` SET `',
            REPLACE(reference_column, '`', '``'),
            '` = CASE BINARY `',
            REPLACE(reference_column, '`', '``'),
            '` WHEN BINARY ? THEN ? WHEN BINARY ? THEN ? ELSE `',
            REPLACE(reference_column, '`', '``'),
            '` END WHERE BINARY `',
            REPLACE(reference_column, '`', '``'),
            '` IN (BINARY ?, BINARY ?)'
        );
        SET @old_admin_uid = old_admin_uid;
        SET @new_admin_uid = new_admin_uid;
        SET @old_user_uid = old_user_uid;
        SET @new_user_uid = new_user_uid;

        PREPARE repair_uid_statement FROM @repair_uid_sql;
        EXECUTE repair_uid_statement USING
            @old_admin_uid,
            @new_admin_uid,
            @old_user_uid,
            @new_user_uid,
            @old_admin_uid,
            @old_user_uid;
        DEALLOCATE PREPARE repair_uid_statement;
    END LOOP;

    CLOSE user_uuid_references;
    COMMIT;
END$$

DELIMITER ;

CALL `repair_seed_user_numeric_uids`();
DROP PROCEDURE `repair_seed_user_numeric_uids`;
