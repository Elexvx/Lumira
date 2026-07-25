-- Replace the temporary deterministic repair UIDs with random 18-digit UIDs.
-- Fresh databases already receive random UIDs from saas.sql, so this migration
-- only rotates the two deterministic values released in V202607260001.

DELIMITER $$

CREATE PROCEDURE `randomize_fixed_seed_user_uids`()
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
    SET new_admin_uid = old_admin_uid;
    SET new_user_uid = old_user_uid;

    IF CAST(old_admin_uid AS BINARY) = CAST('900000000000001001' AS BINARY) THEN
        REPEAT
            SET new_admin_uid = CONCAT(
                CAST(MOD(CONV(HEX(RANDOM_BYTES(1)), 16, 10), 9) + 1 AS CHAR),
                LPAD(CAST(MOD(CONV(HEX(RANDOM_BYTES(4)), 16, 10), 1000000000) AS CHAR), 9, '0'),
                LPAD(CAST(MOD(CONV(HEX(RANDOM_BYTES(4)), 16, 10), 100000000) AS CHAR), 8, '0')
            );
        UNTIL NOT EXISTS (
            SELECT 1
            FROM `sys_user`
            WHERE CAST(`uuid` AS BINARY) = CAST(new_admin_uid AS BINARY)
              AND `id` <> 1001
        )
        END REPEAT;
    END IF;

    IF CAST(old_user_uid AS BINARY) = CAST('900000000000001002' AS BINARY) THEN
        REPEAT
            SET new_user_uid = CONCAT(
                CAST(MOD(CONV(HEX(RANDOM_BYTES(1)), 16, 10), 9) + 1 AS CHAR),
                LPAD(CAST(MOD(CONV(HEX(RANDOM_BYTES(4)), 16, 10), 1000000000) AS CHAR), 9, '0'),
                LPAD(CAST(MOD(CONV(HEX(RANDOM_BYTES(4)), 16, 10), 100000000) AS CHAR), 8, '0')
            );
        UNTIL CAST(new_user_uid AS BINARY) <> CAST(new_admin_uid AS BINARY)
          AND NOT EXISTS (
              SELECT 1
              FROM `sys_user`
              WHERE CAST(`uuid` AS BINARY) = CAST(new_user_uid AS BINARY)
                AND `id` <> 1002
          )
        END REPEAT;
    END IF;

    START TRANSACTION;
    OPEN user_uuid_references;

    reference_loop: LOOP
        FETCH user_uuid_references INTO reference_table, reference_column;
        IF done THEN
            LEAVE reference_loop;
        END IF;

        SET @randomize_uid_sql = CONCAT(
            'UPDATE `',
            REPLACE(reference_table, '`', '``'),
            '` SET `',
            REPLACE(reference_column, '`', '``'),
            '` = CASE CAST(`',
            REPLACE(reference_column, '`', '``'),
            '` AS BINARY) WHEN CAST(? AS BINARY) THEN ? WHEN CAST(? AS BINARY) THEN ? ELSE `',
            REPLACE(reference_column, '`', '``'),
            '` END WHERE CAST(`',
            REPLACE(reference_column, '`', '``'),
            '` AS BINARY) IN (CAST(? AS BINARY), CAST(? AS BINARY))'
        );
        SET @old_admin_uid = old_admin_uid;
        SET @new_admin_uid = new_admin_uid;
        SET @old_user_uid = old_user_uid;
        SET @new_user_uid = new_user_uid;

        PREPARE randomize_uid_statement FROM @randomize_uid_sql;
        EXECUTE randomize_uid_statement USING
            @old_admin_uid,
            @new_admin_uid,
            @old_user_uid,
            @new_user_uid,
            @old_admin_uid,
            @old_user_uid;
        DEALLOCATE PREPARE randomize_uid_statement;
    END LOOP;

    CLOSE user_uuid_references;
    COMMIT;
END$$

DELIMITER ;

CALL `randomize_fixed_seed_user_uids`();
DROP PROCEDURE `randomize_fixed_seed_user_uids`;
