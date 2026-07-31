CREATE TABLE IF NOT EXISTS `competition_config_item_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_code` varchar(64) NOT NULL DEFAULT 'DEFAULT',
  `item_type` varchar(64) NOT NULL,
  `item_key` varchar(128) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content_json` longtext,
  `content_text` longtext,
  `sort_order` int NOT NULL DEFAULT '100',
  `required_flag` tinyint NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_config_item_template_key` (`template_code`,`item_type`,`item_key`,`deleted`),
  KEY `idx_competition_config_item_template_lookup` (`template_code`,`enabled`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `competition_config_item_template`
(`template_code`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`deleted`)
VALUES
('DEFAULT','AGREEMENT','commitment','赛事承诺书','{}','请配置赛事承诺书内容。',10,1,1,0),
('DEFAULT','CONSENT','informed-consent','知情同意书','{}','请配置知情同意书内容。',20,1,1,0),
('DEFAULT','REQUIRED_FILE','work-file','作品文件','{"fileFormat":"ANY","accept":"*","maxSizeMb":100,"maxCount":1}',NULL,10,1,1,0),
('DEFAULT','TEAM_SETTINGS','team-size-limits','团队人数限制','{"teamMinMembers":1,"teamMaxMembers":20,"standardField":true}',NULL,0,0,1,0),
('DEFAULT','TEAM_FIELD','teamName','团队名称','{"fieldType":"TEXT","placeholder":"请输入团队名称","validationRule":"NONE","standardField":true}',NULL,10,1,1,0),
('DEFAULT','TEAM_FIELD','avatarUrl','团队头像','{"fieldType":"IMAGE","placeholder":"请上传团队头像","validationRule":"NONE","standardField":true}',NULL,20,0,1,0),
('DEFAULT','TEAM_FIELD','description','团队简介','{"fieldType":"TEXTAREA","placeholder":"请输入团队简介","validationRule":"NONE","standardField":true}',NULL,30,0,1,0),
('DEFAULT','MEMBER_FIELD','memberName','成员姓名','{"fieldType":"TEXT","placeholder":"请输入成员姓名","validationRule":"NONE","standardField":true}',NULL,110,1,1,0),
('DEFAULT','PROJECT_FIELD','title','项目名称','{"fieldType":"TEXT","placeholder":"请输入项目名称","validationRule":"NONE","standardField":true}',NULL,210,1,1,0),
('DEFAULT','PROJECT_FIELD','imageUrl','项目头像','{"fieldType":"IMAGE","placeholder":"请上传项目头像","validationRule":"NONE","standardField":true}',NULL,220,0,1,0),
('DEFAULT','PROJECT_FIELD','description','项目简介','{"fieldType":"TEXTAREA","placeholder":"请输入项目简介","validationRule":"NONE","standardField":true}',NULL,230,0,1,0),
('DEFAULT','PROJECT_FIELD','intellectualPropertyType','知识产权类型','{"fieldType":"SELECT","placeholder":"请选择知识产权类型","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true,"options":"发明专利\\n实用新型专利\\n外观设计专利\\n软件著作权\\n作品著作权\\n商标\\n其他"}',NULL,310,1,0,0),
('DEFAULT','PROJECT_FIELD','intellectualPropertyName','知识产权名称','{"fieldType":"TEXT","placeholder":"请输入知识产权名称","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,320,1,0,0),
('DEFAULT','PROJECT_FIELD','registrationNumber','申请号/登记号','{"fieldType":"TEXT","placeholder":"请输入申请号或登记号","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,330,0,0,0),
('DEFAULT','PROJECT_FIELD','rightsHolder','权利人','{"fieldType":"TEXT","placeholder":"请输入权利人","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,340,1,0,0),
('DEFAULT','PROJECT_FIELD','legalStatus','法律状态','{"fieldType":"SELECT","placeholder":"请选择法律状态","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true,"options":"申请中\\n已受理\\n已授权\\n已登记\\n已失效\\n其他"}',NULL,350,0,0,0),
('DEFAULT','PROJECT_FIELD','grantDate','授权/登记日期','{"fieldType":"DATE","placeholder":"请选择授权或登记日期","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,360,0,0,0),
('DEFAULT','PROJECT_FIELD','distributionRegions','知识产权分布区域','{"fieldType":"MULTI_SELECT","placeholder":"请选择知识产权分布区域","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true,"options":"中国大陆\\n中国香港\\n中国澳门\\n中国台湾\\n海外"}',NULL,370,1,0,0)
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `content_json` = VALUES(`content_json`),
  `content_text` = VALUES(`content_text`),
  `sort_order` = VALUES(`sort_order`),
  `required_flag` = VALUES(`required_flag`),
  `enabled` = VALUES(`enabled`),
  `updated_at` = CURRENT_TIMESTAMP;

DELETE FROM `competition_config_item`
WHERE `item_type` = 'MEMBER_FIELD'
  AND `item_key` IN ('employeeNo','departmentName','role','remark');

INSERT INTO `competition_config_item`
(`competition_uuid`,`config_set_id`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`created_by`,`created_by_uuid`,`updated_by`,`updated_by_uuid`,`deleted`)
SELECT ccs.`competition_uuid`, ccs.`id`, template.`item_type`, template.`item_key`, template.`title`, template.`content_json`, template.`content_text`,
       template.`sort_order`, template.`required_flag`, template.`enabled`, 0, NULL, 0, NULL, 0
FROM `competition_config_set` ccs
JOIN `competition_config_item_template` template
  ON template.`template_code` = 'DEFAULT' AND template.`enabled` = 1 AND template.`deleted` = 0
WHERE ccs.`deleted` = 0
  AND ccs.`status` IN ('DRAFT','PUBLISHED')
  AND NOT EXISTS (
    SELECT 1
    FROM `competition_config_item` item
    WHERE item.`config_set_id` = ccs.`id`
      AND item.`item_type` = template.`item_type`
      AND item.`item_key` = template.`item_key`
      AND item.`deleted` = 0
  );

UPDATE `competition_config_item` item
JOIN `competition_config_item_template` template
  ON template.`template_code` = 'DEFAULT'
 AND template.`item_type` = item.`item_type`
 AND template.`item_key` = item.`item_key`
 AND template.`deleted` = 0
SET item.`content_json` = JSON_MERGE_PATCH(
      CASE WHEN JSON_VALID(template.`content_json`) THEN template.`content_json` ELSE '{}' END,
      CASE WHEN JSON_VALID(item.`content_json`) THEN item.`content_json` ELSE '{}' END
    ),
    item.`updated_at` = CURRENT_TIMESTAMP
WHERE item.`deleted` = 0
  AND JSON_EXTRACT(template.`content_json`, '$.standardField') = TRUE;
