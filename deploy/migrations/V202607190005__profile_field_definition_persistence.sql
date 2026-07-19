-- Expand-only migration for existing databases.
-- Fresh databases receive the same isolated PROFILE and TEAM_MEMBER definitions
-- from lumira-backend/sql/saas.sql.

CREATE TABLE IF NOT EXISTS `sys_profile_field_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `page_key` varchar(64) NOT NULL,
  `field_key` varchar(64) NOT NULL,
  `field_label` varchar(128) NOT NULL,
  `field_description` varchar(512) DEFAULT NULL,
  `group_key` varchar(64) NOT NULL,
  `group_label` varchar(128) NOT NULL,
  `visible_config_key` varchar(128) NOT NULL,
  `weight_config_key` varchar(128) NOT NULL,
  `default_visible` tinyint NOT NULL DEFAULT 1,
  `default_weight` int NOT NULL DEFAULT 0,
  `field_type` varchar(32) NOT NULL,
  `required_flag` tinyint NOT NULL DEFAULT 0,
  `placeholder` varchar(255) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT 0,
  `updated_by` bigint DEFAULT 0,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile_field_page_key` (`page_key`,`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_dict_type` (`dict_code`,`dict_name`,`status`,`is_system`,`remark`,`created_by`,`updated_by`,`deleted`)
VALUES ('profile_settings_page_key','资料字段页面','ENABLED',1,'Profile settings supported pages',0,0,0),
       ('profile_custom_field_type','资料自定义字段类型','ENABLED',1,'Profile settings custom field types',0,0,0)
ON DUPLICATE KEY UPDATE `dict_name`=VALUES(`dict_name`),`status`='ENABLED',`is_system`=1,`remark`=VALUES(`remark`),`deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`,`item_value`,`item_label`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
SELECT `id`,'PROFILE','个人资料',10,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_settings_page_key'
UNION ALL SELECT `id`,'TEAM_MEMBER','团队成员',20,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_settings_page_key'
UNION ALL SELECT `id`,'TEXT','文本',10,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'NUMBER','数字',20,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'DATE','日期',30,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'SELECT','下拉选择',40,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'TEXTAREA','多行文本',50,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`),`sort_no`=VALUES(`sort_no`),`status`='ENABLED',`deleted`=0;

INSERT INTO `sys_profile_field_definition` (`page_key`,`field_key`,`field_label`,`field_description`,`group_key`,`group_label`,`visible_config_key`,`weight_config_key`,`default_visible`,`default_weight`,`field_type`,`required_flag`,`placeholder`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
VALUES
('PROFILE','avatarUrl','Avatar','Controls whether profile avatar upload and preview are shown','basic','Basic profile','profile.field.avatar.visible','profile.field.avatar.weight',1,10,'IMAGE',0,NULL,10,'ENABLED',0,0,0),
('PROFILE','realName','Real name','Controls whether the real-name profile field is shown','basic','Basic profile','profile.field.real-name.visible','profile.field.real-name.weight',1,15,'TEXT',0,'Enter real name',20,'ENABLED',0,0,0),
('PROFILE','mobile','Mobile','Controls whether the mobile profile field is shown','contact','Contact','profile.field.mobile.visible','profile.field.mobile.weight',1,15,'MOBILE',0,'Enter mobile number',30,'ENABLED',0,0,0),
('PROFILE','email','Email','Controls whether the email profile field is shown','contact','Contact','profile.field.email.visible','profile.field.email.weight',1,15,'EMAIL',0,'Enter email address',40,'ENABLED',0,0,0),
('PROFILE','birthMonth','Birth month','Controls whether the birth-month profile field is shown','basic','Basic profile','profile.field.birth-month.visible','profile.field.birth-month.weight',1,10,'MONTH',0,'Select birth month',50,'ENABLED',0,0,0),
('PROFILE','gender','Gender','Controls whether the gender profile field is shown','basic','Basic profile','profile.field.gender.visible','profile.field.gender.weight',1,10,'SELECT',0,'Select gender',60,'ENABLED',0,0,0),
('PROFILE','region','Region','Controls whether the region profile field is shown','basic','Basic profile','profile.field.region.visible','profile.field.region.weight',1,10,'TEXT',0,'Enter region',70,'ENABLED',0,0,0),
('PROFILE','idCardNumber','ID card number','Controls whether the ID-card profile field is shown','identity','Identity','profile.field.id-card-number.visible','profile.field.id-card-number.weight',1,5,'ID_CARD',0,'Enter ID card number',80,'ENABLED',0,0,0),
('TEAM_MEMBER','memberName','Member name','Team member name','teamMember','Team member','team.member.field.member-name.visible','team.member.field.member-name.weight',1,10,'TEXT',1,'Enter member name',10,'ENABLED',0,0,0),
('TEAM_MEMBER','employeeNo','Employee number','Team member employee or student number','teamMember','Team member','team.member.field.employee-no.visible','team.member.field.employee-no.weight',1,5,'TEXT',0,'Enter employee or student number',20,'ENABLED',0,0,0),
('TEAM_MEMBER','departmentName','Department','Team member department','teamMember','Team member','team.member.field.department-name.visible','team.member.field.department-name.weight',1,5,'TEXT',0,'Enter department',30,'ENABLED',0,0,0),
('TEAM_MEMBER','role','Role','Team member role','teamMember','Team member','team.member.field.role.visible','team.member.field.role.weight',1,5,'SELECT',0,'Select role',40,'ENABLED',0,0,0),
('TEAM_MEMBER','remark','Remark','Team member remark','teamMember','Team member','team.member.field.remark.visible','team.member.field.remark.weight',1,5,'TEXTAREA',0,'Enter remark',50,'ENABLED',0,0,0)
ON DUPLICATE KEY UPDATE `field_label`=VALUES(`field_label`),`field_description`=VALUES(`field_description`),`group_key`=VALUES(`group_key`),`group_label`=VALUES(`group_label`),`visible_config_key`=VALUES(`visible_config_key`),`weight_config_key`=VALUES(`weight_config_key`),`default_visible`=VALUES(`default_visible`),`default_weight`=VALUES(`default_weight`),`field_type`=VALUES(`field_type`),`required_flag`=VALUES(`required_flag`),`placeholder`=VALUES(`placeholder`),`sort_no`=VALUES(`sort_no`),`status`='ENABLED',`deleted`=0;
