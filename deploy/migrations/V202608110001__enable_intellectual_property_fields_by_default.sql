UPDATE `competition_config_item_template`
SET `enabled` = 1,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `template_code` = 'DEFAULT'
  AND `item_type` = 'PROJECT_FIELD'
  AND `item_key` IN (
    'intellectualPropertyType',
    'intellectualPropertyName',
    'registrationNumber',
    'rightsHolder',
    'legalStatus',
    'grantDate',
    'distributionRegions'
  )
  AND `deleted` = 0;
