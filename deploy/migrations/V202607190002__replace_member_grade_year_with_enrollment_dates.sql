-- Replace the ambiguous member grade year field with explicit enrollment and graduation dates.
-- Existing registration snapshots remain unchanged; only active competition configuration is migrated.

SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS `_competition_member_grade_year_migration`;
CREATE TEMPORARY TABLE `_competition_member_grade_year_migration` AS
SELECT
  grade_year.`id`,
  grade_year.`competition_uuid`,
  grade_year.`config_set_id`,
  grade_year.`sort_order`,
  grade_year.`required_flag`,
  grade_year.`created_by`,
  grade_year.`created_by_uuid`,
  grade_year.`updated_by`,
  grade_year.`updated_by_uuid`,
  NOT EXISTS (
    SELECT 1
    FROM `competition_config_item` graduation
    WHERE graduation.`config_set_id` = grade_year.`config_set_id`
      AND graduation.`item_type` = 'MEMBER_FIELD'
      AND graduation.`item_key` = 'graduationDate'
      AND graduation.`deleted` = 0
  ) AS `needs_shift`
FROM `competition_config_item` grade_year
WHERE grade_year.`item_type` = 'MEMBER_FIELD'
  AND grade_year.`item_key` = 'gradeYear'
  AND grade_year.`deleted` = 0;

-- Make room for the additional field only when graduationDate has not already
-- been created. This keeps the migration safe if it is applied more than once.
UPDATE `competition_config_item` item
JOIN `_competition_member_grade_year_migration` grade_year
  ON grade_year.`config_set_id` = item.`config_set_id`
SET item.`sort_order` = item.`sort_order` + 10,
    item.`updated_at` = CURRENT_TIMESTAMP
WHERE item.`item_type` = 'MEMBER_FIELD'
  AND item.`sort_order` > grade_year.`sort_order`
  AND item.`deleted` = 0
  AND grade_year.`needs_shift` = 1;

INSERT INTO `competition_config_item`
(`competition_uuid`,`config_set_id`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`created_by`,`created_by_uuid`,`updated_by`,`updated_by_uuid`,`deleted`)
SELECT grade_year.`competition_uuid`, grade_year.`config_set_id`, 'MEMBER_FIELD', 'enrollmentDate', '入学时间',
       '{"fieldType":"DATE","validationRule":"NONE","placeholder":"请选择入学时间"}', NULL,
       grade_year.`sort_order`, grade_year.`required_flag`, 1,
       grade_year.`created_by`, grade_year.`created_by_uuid`, grade_year.`updated_by`, grade_year.`updated_by_uuid`, 0
FROM `_competition_member_grade_year_migration` grade_year
WHERE NOT EXISTS (
  SELECT 1
  FROM `competition_config_item` enrollment
  WHERE enrollment.`config_set_id` = grade_year.`config_set_id`
    AND enrollment.`item_type` = 'MEMBER_FIELD'
    AND enrollment.`item_key` = 'enrollmentDate'
    AND enrollment.`deleted` = 0
);

INSERT INTO `competition_config_item`
(`competition_uuid`,`config_set_id`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`created_by`,`created_by_uuid`,`updated_by`,`updated_by_uuid`,`deleted`)
SELECT grade_year.`competition_uuid`, grade_year.`config_set_id`, 'MEMBER_FIELD', 'graduationDate', '毕业时间',
       '{"fieldType":"DATE","validationRule":"NONE","placeholder":"请选择毕业时间"}', NULL,
       grade_year.`sort_order` + 10, grade_year.`required_flag`, 1,
       grade_year.`created_by`, grade_year.`created_by_uuid`, grade_year.`updated_by`, grade_year.`updated_by_uuid`, 0
FROM `_competition_member_grade_year_migration` grade_year
WHERE NOT EXISTS (
  SELECT 1
  FROM `competition_config_item` graduation
  WHERE graduation.`config_set_id` = grade_year.`config_set_id`
    AND graduation.`item_type` = 'MEMBER_FIELD'
    AND graduation.`item_key` = 'graduationDate'
    AND graduation.`deleted` = 0
);

UPDATE `competition_config_item` item
JOIN `_competition_member_grade_year_migration` grade_year ON grade_year.`id` = item.`id`
SET item.`enabled` = 0,
    item.`deleted` = 1,
    item.`updated_at` = CURRENT_TIMESTAMP;

DROP TEMPORARY TABLE IF EXISTS `_competition_member_grade_year_migration`;
