-- Clarify that enrollment and graduation collection fields are year-only in the UI.
-- Stored values remain ISO dates so existing registration validation and snapshots stay compatible.

SET NAMES utf8mb4;

UPDATE `competition_config_item`
SET `title` = '入学年份',
    `content_json` = JSON_SET(
      COALESCE(NULLIF(`content_json`, ''), '{}'),
      '$.placeholder',
      '请选择入学年份'
    ),
    `updated_at` = CURRENT_TIMESTAMP
WHERE `item_type` = 'MEMBER_FIELD'
  AND `item_key` = 'enrollmentDate'
  AND `deleted` = 0;

UPDATE `competition_config_item`
SET `title` = '毕业年份',
    `content_json` = JSON_SET(
      COALESCE(NULLIF(`content_json`, ''), '{}'),
      '$.placeholder',
      '请选择毕业年份'
    ),
    `updated_at` = CURRENT_TIMESTAMP
WHERE `item_type` = 'MEMBER_FIELD'
  AND `item_key` = 'graduationDate'
  AND `deleted` = 0;
