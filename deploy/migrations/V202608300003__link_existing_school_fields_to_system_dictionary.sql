UPDATE competition_config_item
SET content_json = JSON_REMOVE(
        JSON_SET(
            CASE
                WHEN JSON_VALID(content_json) THEN content_json
                ELSE JSON_OBJECT()
            END,
            '$.fieldType', 'SELECT',
            '$.optionSource', 'DICTIONARY',
            '$.dictCode', 'sys_school'
        ),
        '$.options',
        '$.placeholder'
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND item_type IN (
      'REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD',
      'TEACHER_FIELD', 'PROJECT_FIELD', 'EXPERT_FIELD'
  )
  AND LOWER(REPLACE(REPLACE(item_key, '_', ''), '-', '')) IN (
      'school', 'schoolname', 'college', 'university'
  )
  AND (
      NOT JSON_VALID(content_json)
      OR UPPER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.fieldType')), 'TEXT')) IN ('TEXT', 'SELECT')
  );
