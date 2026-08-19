-- Adds per-activity registration schemas and immutable submitted-answer snapshots.
-- This manual upgrade is idempotent and preserves all legacy scalar registration columns.

SET NAMES utf8mb4;

SET @has_activity_registration_form_json := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'aiadc_activity'
      AND column_name = 'registration_form_json'
);

SET @add_activity_registration_form_json_sql := IF(
    @has_activity_registration_form_json = 0,
    'ALTER TABLE `aiadc_activity` ADD COLUMN `registration_form_json` longtext NULL AFTER `featured`',
    'SELECT 1'
);

PREPARE add_activity_registration_form_json_statement FROM @add_activity_registration_form_json_sql;
EXECUTE add_activity_registration_form_json_statement;
DEALLOCATE PREPARE add_activity_registration_form_json_statement;

SET @has_activity_registration_form_data_json := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'aiadc_activity_registration'
      AND column_name = 'form_data_json'
);

SET @add_activity_registration_form_data_json_sql := IF(
    @has_activity_registration_form_data_json = 0,
    'ALTER TABLE `aiadc_activity_registration` ADD COLUMN `form_data_json` longtext NULL AFTER `remark`',
    'SELECT 1'
);

PREPARE add_activity_registration_form_data_json_statement FROM @add_activity_registration_form_data_json_sql;
EXECUTE add_activity_registration_form_data_json_statement;
DEALLOCATE PREPARE add_activity_registration_form_data_json_statement;

UPDATE `aiadc_activity`
SET `registration_form_json` = '[{"fieldKey":"name","label":"姓名","fieldType":"TEXT","placeholder":"请输入姓名","required":true,"options":[]},{"fieldKey":"mobile","label":"手机号","fieldType":"MOBILE","placeholder":"请输入手机号","required":true,"options":[]},{"fieldKey":"email","label":"邮箱","fieldType":"EMAIL","placeholder":"请输入邮箱","required":false,"options":[]},{"fieldKey":"organization","label":"单位","fieldType":"TEXT","placeholder":"请输入单位","required":false,"options":[]},{"fieldKey":"position","label":"职务","fieldType":"TEXT","placeholder":"请输入职务","required":false,"options":[]},{"fieldKey":"remark","label":"备注","fieldType":"TEXTAREA","placeholder":"请输入备注","required":false,"options":[]}]'
WHERE `registration_form_json` IS NULL OR TRIM(`registration_form_json`) = '';
