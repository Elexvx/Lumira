

-- Database-owned platform setting groups and defaults.
CREATE TABLE IF NOT EXISTS `sys_platform_setting_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_code` varchar(64) NOT NULL,
  `config_key` varchar(128) NOT NULL,
  `config_name` varchar(128) NOT NULL DEFAULT '',
  `remark` varchar(512) DEFAULT NULL,
  `default_value` text,
  `reset_value` text,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT 0,
  `updated_by` bigint DEFAULT 0,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_setting_config_key` (`config_key`),
  KEY `idx_platform_setting_group` (`group_code`,`status`,`deleted`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `sys_platform_setting_definition`
  ADD COLUMN IF NOT EXISTS `config_name` varchar(128) NOT NULL DEFAULT '' AFTER `config_key`,
  ADD COLUMN IF NOT EXISTS `remark` varchar(512) DEFAULT NULL AFTER `config_name`,
  ADD COLUMN IF NOT EXISTS `reset_value` text AFTER `default_value`;

INSERT INTO `sys_platform_setting_definition` (`group_code`,`config_key`,`default_value`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
VALUES
    ('BRANDING','branding.website-name','Lumira',10,'ENABLED',0,0,0),
    ('BRANDING','branding.website-favicon-url','',20,'ENABLED',0,0,0),
    ('BRANDING','branding.website-logo-url','',30,'ENABLED',0,0,0),
    ('BRANDING','branding.login-background-url','',40,'ENABLED',0,0,0),
    ('BRANDING','branding.github-link-enabled','true',50,'ENABLED',0,0,0),
    ('BRANDING','branding.github-link-url','',60,'ENABLED',0,0,0),
    ('BRANDING','branding.help-link-enabled','true',70,'ENABLED',0,0,0),
    ('BRANDING','branding.help-link-url','',80,'ENABLED',0,0,0),
    ('BRANDING','branding.company-name','',90,'ENABLED',0,0,0),
    ('BRANDING','branding.copyright-start-year','',100,'ENABLED',0,0,0),
    ('BRANDING','branding.footer-icp','',110,'ENABLED',0,0,0),
    ('BRANDING','branding.footer-police-beian','',120,'ENABLED',0,0,0),
    ('BRANDING','branding.footer-copyright','',130,'ENABLED',0,0,0),
    ('AGREEMENT','agreement.user-agreement-markdown','',10,'ENABLED',0,0,0),
    ('AGREEMENT','agreement.privacy-agreement-markdown','',20,'ENABLED',0,0,0),
    ('SMTP','smtp.enabled','true',10,'ENABLED',0,0,0),
    ('SMTP','smtp.host','',20,'ENABLED',0,0,0),
    ('SMTP','smtp.port','25',30,'ENABLED',0,0,0),
    ('SMTP','smtp.username','',40,'ENABLED',0,0,0),
    ('SMTP','smtp.password','',50,'ENABLED',0,0,0),
    ('SMTP','smtp.from','',60,'ENABLED',0,0,0),
    ('SMTP','smtp.auth-enabled','true',70,'ENABLED',0,0,0),
    ('SMTP','smtp.starttls-enabled','true',80,'ENABLED',0,0,0),
    ('SMTP','smtp.ssl-enabled','false',90,'ENABLED',0,0,0),
    ('SMTP','smtp.test-subject','SMTP test email',100,'ENABLED',0,0,0),
    ('SMTP','smtp.test-content','This is a test email sent from the system SMTP settings.',110,'ENABLED',0,0,0),
    ('SMTP','smtp.connection-timeout-ms','5000',120,'ENABLED',0,0,0),
    ('SMTP','smtp.read-timeout-ms','5000',130,'ENABLED',0,0,0),
    ('SMTP','smtp.write-timeout-ms','5000',140,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.enabled','false',10,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.app-id','',20,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.app-secret','',30,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.template-id','',40,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.detail-url','',50,'ENABLED',0,0,0),
    ('WATERMARK','watermark.enabled','false',10,'ENABLED',0,0,0),
    ('WATERMARK','watermark.mode','TEXT',20,'ENABLED',0,0,0),
    ('WATERMARK','watermark.text-lines','',30,'ENABLED',0,0,0),
    ('WATERMARK','watermark.image-url','',40,'ENABLED',0,0,0),
    ('WATERMARK','watermark.font-color','rgba(0,0,0,0.15)',50,'ENABLED',0,0,0),
    ('WATERMARK','watermark.font-size','14',60,'ENABLED',0,0,0),
    ('WATERMARK','watermark.font-weight','normal',70,'ENABLED',0,0,0),
    ('WATERMARK','watermark.rotate','-22',80,'ENABLED',0,0,0),
    ('WATERMARK','watermark.gap-x','100',90,'ENABLED',0,0,0),
    ('WATERMARK','watermark.gap-y','100',100,'ENABLED',0,0,0),
    ('WATERMARK','watermark.offset-x','0',110,'ENABLED',0,0,0),
    ('WATERMARK','watermark.offset-y','0',120,'ENABLED',0,0,0),
    ('WATERMARK','watermark.z-index','9',130,'ENABLED',0,0,0),
    ('WATERMARK','watermark.opacity','0.15',140,'ENABLED',0,0,0),
    ('FLOATING_WINDOW','floating-window.api-docs-qr-enabled','false',10,'ENABLED',0,0,0),
    ('FLOATING_WINDOW','floating-window.api-docs-qr-title','',20,'ENABLED',0,0,0),
    ('FLOATING_WINDOW','floating-window.api-docs-qr-image-url','',30,'ENABLED',0,0,0)
ON DUPLICATE KEY UPDATE `group_code`=VALUES(`group_code`),`default_value`=VALUES(`default_value`),
    `sort_no`=VALUES(`sort_no`),`status`='ENABLED',`deleted`=0;

UPDATE `sys_platform_setting_definition`
SET `config_name` = `config_key`
WHERE (`config_name` IS NULL OR `config_name` = '') AND `deleted`=0;

UPDATE `sys_platform_setting_definition`
SET `reset_value` = CASE `config_key`
    WHEN 'smtp.enabled' THEN 'false' WHEN 'smtp.host' THEN '' WHEN 'smtp.port' THEN '25'
    WHEN 'smtp.username' THEN '' WHEN 'smtp.password' THEN '' WHEN 'smtp.from' THEN ''
    WHEN 'smtp.auth-enabled' THEN 'true' WHEN 'smtp.starttls-enabled' THEN 'true'
    WHEN 'smtp.ssl-enabled' THEN 'false' ELSE `reset_value` END
WHERE `group_code`='SMTP' AND `deleted`=0;
