-- Make expert applications part of the selected competition's versioned form.
-- Keep the submitted dynamic values on the expert application snapshot so later
-- edits to a competition form do not rewrite an existing application.

SET NAMES utf8mb4;
SET @schema_name = DATABASE();

SET @ddl = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'aiadc_expert'
      AND column_name = 'competition_uuid'
  ),
  'SELECT 1',
  'ALTER TABLE aiadc_expert ADD COLUMN competition_uuid char(36) DEFAULT NULL AFTER code'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'aiadc_expert'
      AND column_name = 'extra_values_json'
  ),
  'SELECT 1',
  'ALTER TABLE aiadc_expert ADD COLUMN extra_values_json longtext NULL AFTER tags'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  EXISTS(
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'aiadc_expert'
      AND index_name = 'idx_aiadc_expert_competition'
  ),
  'SELECT 1',
  'ALTER TABLE aiadc_expert ADD INDEX idx_aiadc_expert_competition (competition_uuid, deleted, updated_at)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `competition_config_item_template`
(`template_code`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`deleted`)
VALUES
('DEFAULT','EXPERT_FIELD','name','专家姓名','{"fieldType":"TEXT","placeholder":"请输入专家姓名","validationRule":"PERSON_NAME","standardField":true}',NULL,410,1,1,0),
('DEFAULT','EXPERT_FIELD','title','专家头衔','{"fieldType":"TEXT","placeholder":"请输入专家头衔","validationRule":"NONE","standardField":true}',NULL,420,0,1,0),
('DEFAULT','EXPERT_FIELD','organization','所属机构','{"fieldType":"TEXT","placeholder":"请输入所属机构","validationRule":"NONE","standardField":true}',NULL,430,0,1,0),
('DEFAULT','EXPERT_FIELD','position','职务','{"fieldType":"TEXT","placeholder":"请输入职务","validationRule":"NONE","standardField":true}',NULL,440,0,1,0),
('DEFAULT','EXPERT_FIELD','expertise','专业领域','{"fieldType":"TEXT","placeholder":"请输入专业领域","validationRule":"NONE","standardField":true}',NULL,450,1,1,0),
('DEFAULT','EXPERT_FIELD','mobile','手机号码','{"fieldType":"MOBILE","placeholder":"请输入 11 位手机号","validationRule":"CHINA_MOBILE","standardField":true}',NULL,460,0,1,0),
('DEFAULT','EXPERT_FIELD','email','邮箱','{"fieldType":"EMAIL","placeholder":"请输入邮箱","validationRule":"EMAIL","standardField":true}',NULL,470,0,1,0),
('DEFAULT','EXPERT_FIELD','idCardNumber','身份证号码','{"fieldType":"TEXT","placeholder":"请输入身份证号码","validationRule":"ID_CARD","standardField":true}',NULL,480,0,1,0),
('DEFAULT','EXPERT_FIELD','avatarUrl','头像','{"fieldType":"IMAGE","placeholder":"请上传头像","validationRule":"NONE","standardField":true}',NULL,490,0,1,0),
('DEFAULT','EXPERT_FIELD','bio','专家简介','{"fieldType":"TEXTAREA","placeholder":"请输入专家简介","validationRule":"NONE","standardField":true}',NULL,500,0,1,0),
('DEFAULT','EXPERT_FIELD','tags','专家标签','{"fieldType":"TEXT","placeholder":"请输入专家标签","validationRule":"NONE","standardField":true}',NULL,510,0,1,0)
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `content_json` = VALUES(`content_json`),
  `sort_order` = VALUES(`sort_order`),
  `required_flag` = VALUES(`required_flag`),
  `enabled` = VALUES(`enabled`),
  `deleted` = 0;

INSERT INTO `competition_config_item`
(`competition_uuid`,`config_set_id`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`created_by`,`created_by_uuid`,`updated_by`,`updated_by_uuid`,`deleted`)
SELECT config_set.`competition_uuid`, config_set.`id`, template.`item_type`, template.`item_key`, template.`title`, template.`content_json`, template.`content_text`,
       template.`sort_order`, template.`required_flag`, template.`enabled`, 0, NULL, 0, NULL, 0
FROM `competition_config_set` config_set
JOIN `competition_config_item_template` template
  ON template.`template_code` = 'DEFAULT'
 AND template.`item_type` = 'EXPERT_FIELD'
 AND template.`deleted` = 0
WHERE config_set.`status` IN ('DRAFT', 'PUBLISHED')
  AND config_set.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1
    FROM `competition_config_item` existing
    WHERE existing.`config_set_id` = config_set.`id`
      AND existing.`item_type` = template.`item_type`
      AND existing.`item_key` = template.`item_key`
      AND existing.`deleted` = 0
  );
