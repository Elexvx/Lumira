-- Manual equivalent of V202608190002__add_competition_participant_role_settings.sql.
-- Split competition participants into students and teachers without overwriting
-- existing competition-specific team-size choices.
SET NAMES utf8mb4;

INSERT INTO `competition_config_item_template`
(`template_code`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`deleted`)
VALUES
('DEFAULT','TEAM_SETTINGS','team-size-limits','参赛人员数量限制','{"studentMinMembers":1,"studentMaxMembers":15,"teacherMinMembers":0,"teacherMaxMembers":3,"teamMinMembers":1,"teamMaxMembers":15,"standardField":true}',NULL,0,0,1,0),
('DEFAULT','MEMBER_FIELD','memberName','学生姓名','{"fieldType":"TEXT","placeholder":"请输入学生姓名","validationRule":"PERSON_NAME","standardField":true}',NULL,110,1,1,0),
('DEFAULT','TEACHER_FIELD','memberName','指导老师姓名','{"fieldType":"TEXT","placeholder":"请输入指导老师姓名","validationRule":"PERSON_NAME","standardField":true}',NULL,120,1,1,0)
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `content_json` = VALUES(`content_json`),
  `content_text` = VALUES(`content_text`),
  `sort_order` = VALUES(`sort_order`),
  `required_flag` = VALUES(`required_flag`),
  `enabled` = VALUES(`enabled`),
  `deleted` = 0,
  `updated_at` = CURRENT_TIMESTAMP;

UPDATE `competition_config_item`
SET `content_json` = '{"studentMinMembers":1,"studentMaxMembers":15,"teacherMinMembers":0,"teacherMaxMembers":3,"teamMinMembers":1,"teamMaxMembers":15,"standardField":true}'
WHERE `item_type` = 'TEAM_SETTINGS'
  AND `item_key` = 'team-size-limits'
  AND `deleted` = 0
  AND (`content_json` IS NULL OR JSON_VALID(`content_json`) = 0);

UPDATE `competition_config_item`
SET `title` = CASE WHEN `title` = '团队人数限制' THEN '参赛人员数量限制' ELSE `title` END,
    `content_json` = JSON_SET(
      `content_json`,
      '$.studentMinMembers', COALESCE(
        JSON_EXTRACT(`content_json`, '$.studentMinMembers'),
        JSON_EXTRACT(`content_json`, '$.teamMinMembers'),
        1
      ),
      '$.studentMaxMembers', COALESCE(
        JSON_EXTRACT(`content_json`, '$.studentMaxMembers'),
        JSON_EXTRACT(`content_json`, '$.teamMaxMembers'),
        15
      ),
      '$.teacherMinMembers', COALESCE(JSON_EXTRACT(`content_json`, '$.teacherMinMembers'), 0),
      '$.teacherMaxMembers', COALESCE(JSON_EXTRACT(`content_json`, '$.teacherMaxMembers'), 3),
      '$.standardField', TRUE
    ),
    `updated_at` = CURRENT_TIMESTAMP
WHERE `item_type` = 'TEAM_SETTINGS'
  AND `item_key` = 'team-size-limits'
  AND `deleted` = 0
  AND JSON_VALID(`content_json`) = 1;

UPDATE `competition_config_item`
SET `title` = CASE WHEN `title` = '成员姓名' THEN '学生姓名' ELSE `title` END,
    `content_json` = CASE
      WHEN JSON_VALID(`content_json`) = 1 THEN JSON_SET(
        `content_json`,
        '$.placeholder', CASE
          WHEN JSON_UNQUOTE(JSON_EXTRACT(`content_json`, '$.placeholder')) = '请输入成员姓名'
            THEN '请输入学生姓名'
          ELSE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(`content_json`, '$.placeholder')), '请输入学生姓名')
        END,
        '$.standardField', TRUE
      )
      ELSE '{"fieldType":"TEXT","placeholder":"请输入学生姓名","validationRule":"PERSON_NAME","standardField":true}'
    END,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `item_type` = 'MEMBER_FIELD'
  AND `item_key` = 'memberName'
  AND `deleted` = 0;

INSERT INTO `competition_config_item`
(`competition_uuid`,`config_set_id`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`created_by`,`created_by_uuid`,`updated_by`,`updated_by_uuid`,`deleted`)
SELECT config_set.`competition_uuid`, config_set.`id`, template.`item_type`, template.`item_key`,
       template.`title`, template.`content_json`, template.`content_text`, template.`sort_order`,
       template.`required_flag`, template.`enabled`, 0, NULL, 0, NULL, 0
FROM `competition_config_set` AS config_set
JOIN `competition_config_item_template` AS template
  ON template.`template_code` = 'DEFAULT'
 AND template.`item_type` = 'TEACHER_FIELD'
 AND template.`item_key` = 'memberName'
 AND template.`deleted` = 0
WHERE config_set.`status` IN ('DRAFT', 'PUBLISHED')
  AND config_set.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `competition_config_item` AS item
    WHERE item.`config_set_id` = config_set.`id`
      AND item.`item_type` = 'TEACHER_FIELD'
      AND item.`item_key` = 'memberName'
      AND item.`deleted` = 0
  );
