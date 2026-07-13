-- Database-owned defaults for new certificate template versions.
INSERT INTO `sys_platform_setting_definition`
    (`group_code`,`config_key`,`default_value`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
VALUES
    ('CERTIFICATE','certificate.canvas.default-width','3508',10,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-height','2480',20,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-orientation','LANDSCAPE',30,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-unit','PX',40,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-dpi','300',50,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-json','{"page":{"width":3508,"height":2480,"dpi":300,"orientation":"LANDSCAPE"},"elements":[{"id":"el_name","type":"text","fieldKey":"recipientName","x":1200,"y":920,"width":1100,"height":120,"fontFamily":"Microsoft YaHei","fontSize":72,"fontWeight":"bold","color":"#222222","textAlign":"center","placeholder":"${recipientName}"},{"id":"el_award","type":"text","fieldKey":"awardName","x":1200,"y":1200,"width":1100,"height":100,"fontFamily":"Microsoft YaHei","fontSize":56,"fontWeight":"normal","color":"#222222","textAlign":"center","placeholder":"${awardName}"},{"id":"el_qr","type":"qrcode","fieldKey":"verificationUrl","x":2920,"y":1900,"width":220,"height":220}]}',60,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-variable-schema-json','{"variables":[{"key":"recipientName","label":"Recipient","type":"text","required":true},{"key":"competitionTitle","label":"Competition","type":"text","required":true},{"key":"projectName","label":"Project","type":"text","required":false},{"key":"teamName","label":"Team","type":"text","required":false},{"key":"awardName","label":"Award","type":"text","required":true},{"key":"certificateNo","label":"Certificate No","type":"text","required":true},{"key":"issueDate","label":"Issue Date","type":"date","required":true},{"key":"verificationUrl","label":"Verification URL","type":"qrcode","required":true}]}',70,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.public.organizer','Lumira',80,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.template-statuses','DRAFT,PUBLISHED,ARCHIVED',90,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.scene-types','COMPETITION_AWARD,PARTICIPATION,CUSTOM',100,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.source-types','MANUAL,IMPORT,REGISTRATION,AWARD_RESULT',110,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.recipient-types','USER,TEAM,PROJECT,CUSTOM',120,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.record-statuses','ISSUED,REVOKED',130,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.default-scene-type','COMPETITION_AWARD',140,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.default-source-type','MANUAL',150,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.default-recipient-type','CUSTOM',160,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.template-prefix','CTPL-',170,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.batch-prefix','CB-',180,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.certificate-prefix','CERT-',190,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.timestamp-format','yyyyMMddHHmmssSSS',200,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.verification-code-length','6',210,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.preview.batch-no','PREVIEW',220,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.preview.batch-name','Preview',230,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.preview.status','PREVIEW',240,'ENABLED',0,0,0)
ON DUPLICATE KEY UPDATE `group_code`=VALUES(`group_code`),`default_value`=VALUES(`default_value`),
    `sort_no`=VALUES(`sort_no`),`status`='ENABLED',`deleted`=0;

UPDATE `sys_platform_setting_definition`
SET `config_name` = `config_key`
WHERE `group_code`='CERTIFICATE' AND (`config_name` IS NULL OR `config_name`='') AND `deleted`=0;
