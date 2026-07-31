UPDATE sys_platform_setting_definition
SET default_value = '{"page":{"width":3508,"height":2480,"dpi":300,"orientation":"LANDSCAPE"},"elements":[{"id":"el_title","type":"text","text":"获奖证书","x":1200,"y":430,"width":1100,"height":160,"fontFamily":"Microsoft YaHei","fontSize":96,"fontWeight":"bold","color":"#222222","textAlign":"center"},{"id":"el_name","type":"text","fieldKey":"recipientName","x":1200,"y":800,"width":1100,"height":120,"fontFamily":"Microsoft YaHei","fontSize":72,"fontWeight":"bold","color":"#222222","textAlign":"center","placeholder":"${recipientName}"},{"id":"el_award","type":"text","fieldKey":"awardName","x":1200,"y":1040,"width":1100,"height":100,"fontFamily":"Microsoft YaHei","fontSize":56,"fontWeight":"normal","color":"#222222","textAlign":"center","placeholder":"${awardName}"},{"id":"el_competition","type":"text","fieldKey":"competitionTitle","x":1000,"y":1280,"width":1500,"height":90,"fontFamily":"Microsoft YaHei","fontSize":44,"fontWeight":"normal","color":"#333333","textAlign":"center","placeholder":"${competitionTitle}"},{"id":"el_date","type":"text","fieldKey":"issueDate","x":2450,"y":2080,"width":500,"height":70,"fontFamily":"Microsoft YaHei","fontSize":32,"fontWeight":"normal","color":"#444444","textAlign":"center","placeholder":"${issueDate}"},{"id":"el_qr","type":"qrcode","fieldKey":"verificationUrl","x":2920,"y":1800,"width":220,"height":220}]}'
WHERE group_code = 'CERTIFICATE'
  AND config_key = 'certificate.canvas.default-json'
  AND deleted = 0;

UPDATE sys_platform_setting_definition
SET default_value = 'GENERATING,ISSUED,FAILED,REVOKED'
WHERE group_code = 'CERTIFICATE'
  AND config_key = 'certificate.rule.record-statuses'
  AND deleted = 0;
