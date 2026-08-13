-- Expand-only migration for profile fields that are editable through Profile field management.
-- These fields are rendered in the user editor's personal-information tab.

INSERT INTO `sys_config` (`config_key`,`config_name`,`config_value`,`config_scope`,`is_system`,`remark`,`created_by`,`updated_by`,`deleted`)
VALUES
('profile.field.nickname.visible', 'Nickname visible', 'true', 'PLATFORM', 0, 'Profile nickname visible flag', 0, 0, 0),
('profile.field.nickname.weight', 'Nickname weight', '10', 'PLATFORM', 0, 'Profile nickname field score weight', 0, 0, 0),
('profile.field.nickname.required', 'Nickname required', 'false', 'PLATFORM', 0, 'Profile nickname required flag', 0, 0, 0),
('profile.field.nickname.sort', 'Nickname sort', '15', 'PLATFORM', 0, 'Profile nickname field sort order', 0, 0, 0),
('profile.field.available-time.visible', 'Available time visible', 'true', 'PLATFORM', 0, 'Profile available time visible flag', 0, 0, 0),
('profile.field.available-time.weight', 'Available time weight', '10', 'PLATFORM', 0, 'Profile available time field score weight', 0, 0, 0),
('profile.field.available-time.required', 'Available time required', 'false', 'PLATFORM', 0, 'Profile available time required flag', 0, 0, 0),
('profile.field.available-time.sort', 'Available time sort', '90', 'PLATFORM', 0, 'Profile available time field sort order', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `config_name` = VALUES(`config_name`),
  `config_scope` = 'PLATFORM',
  `is_system` = 0,
  `remark` = VALUES(`remark`),
  `deleted` = 0;

INSERT INTO `sys_profile_field_definition` (`page_key`,`field_key`,`field_label`,`field_description`,`group_key`,`group_label`,`visible_config_key`,`weight_config_key`,`default_visible`,`default_weight`,`field_type`,`required_flag`,`placeholder`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
VALUES
('PROFILE','nickname','Nickname','Controls whether the nickname profile field is shown','basic','Basic profile','profile.field.nickname.visible','profile.field.nickname.weight',1,10,'TEXT',0,'Enter nickname',15,'ENABLED',0,0,0),
('PROFILE','availableTime','Available time','Controls whether the available-time profile field is shown','basic','Basic profile','profile.field.available-time.visible','profile.field.available-time.weight',1,10,'TEXTAREA',0,'Enter available time',90,'ENABLED',0,0,0)
ON DUPLICATE KEY UPDATE
  `field_label` = VALUES(`field_label`),
  `field_description` = VALUES(`field_description`),
  `group_key` = VALUES(`group_key`),
  `group_label` = VALUES(`group_label`),
  `visible_config_key` = VALUES(`visible_config_key`),
  `weight_config_key` = VALUES(`weight_config_key`),
  `default_visible` = VALUES(`default_visible`),
  `default_weight` = VALUES(`default_weight`),
  `field_type` = VALUES(`field_type`),
  `required_flag` = VALUES(`required_flag`),
  `placeholder` = VALUES(`placeholder`),
  `sort_no` = VALUES(`sort_no`),
  `status` = 'ENABLED',
  `deleted` = 0;
