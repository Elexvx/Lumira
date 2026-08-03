-- Repair the payment translations that were corrupted while the frontend
-- catalog was moved into the database. Only the known question-mark payloads
-- are replaced so database-managed edits remain authoritative.

UPDATE `sys_localization_translation` translation
JOIN `sys_localization_entry` entry
  ON entry.`id` = translation.`entry_id`
 AND entry.`deleted` = 0
SET translation.`translated_message` = CASE entry.`message_key`
      WHEN 'payment.connectivity.available' THEN '可用'
      WHEN 'payment.connectivity.notTested' THEN '未测试'
      WHEN 'payment.connectivity.unavailable' THEN '不可用'
      WHEN 'payment.environment.production' THEN '正式'
      WHEN 'payment.environment.sandbox' THEN '测试'
      WHEN 'payment.message.connectivityFailedWithReason' THEN '支付连通性测试失败：{reason}'
      WHEN 'payment.message.connectivityPassed' THEN '支付连通性测试通过'
      WHEN 'payment.message.missingFields' THEN '缺少必填支付字段：{reason}'
      WHEN 'payment.message.providerDisabled' THEN '支付服务商已停用'
      WHEN 'payment.message.providerNotConfigured' THEN '支付服务商尚未完成配置'
      WHEN 'payment.message.providerReady' THEN '支付服务商配置可用'
      WHEN 'payment.message.providerTestFailed' THEN '支付服务商测试失败'
      WHEN 'payment.provider.alipay' THEN '支付宝'
      WHEN 'payment.provider.wechatPay' THEN '微信支付'
      ELSE translation.`translated_message`
    END,
    translation.`updated_by` = 0
WHERE translation.`locale_code` = 'zh-CN'
  AND translation.`deleted` = 0
  AND translation.`translated_message` REGEXP '^[?]+(\\{reason\\})?$'
  AND entry.`message_key` IN (
    'payment.connectivity.available',
    'payment.connectivity.notTested',
    'payment.connectivity.unavailable',
    'payment.environment.production',
    'payment.environment.sandbox',
    'payment.message.connectivityFailedWithReason',
    'payment.message.connectivityPassed',
    'payment.message.missingFields',
    'payment.message.providerDisabled',
    'payment.message.providerNotConfigured',
    'payment.message.providerReady',
    'payment.message.providerTestFailed',
    'payment.provider.alipay',
    'payment.provider.wechatPay'
  );

UPDATE `sys_localization_entry`
SET `default_message` = CASE `message_key`
      WHEN 'payment.connectivity.available' THEN '可用'
      WHEN 'payment.connectivity.notTested' THEN '未测试'
      WHEN 'payment.connectivity.unavailable' THEN '不可用'
      WHEN 'payment.environment.production' THEN '正式'
      WHEN 'payment.environment.sandbox' THEN '测试'
      WHEN 'payment.message.connectivityFailedWithReason' THEN '支付连通性测试失败：{reason}'
      WHEN 'payment.message.connectivityPassed' THEN '支付连通性测试通过'
      WHEN 'payment.message.missingFields' THEN '缺少必填支付字段：{reason}'
      WHEN 'payment.message.providerDisabled' THEN '支付服务商已停用'
      WHEN 'payment.message.providerNotConfigured' THEN '支付服务商尚未完成配置'
      WHEN 'payment.message.providerReady' THEN '支付服务商配置可用'
      WHEN 'payment.message.providerTestFailed' THEN '支付服务商测试失败'
      WHEN 'payment.provider.alipay' THEN '支付宝'
      WHEN 'payment.provider.wechatPay' THEN '微信支付'
      ELSE `default_message`
    END,
    `updated_by` = 0
WHERE `source_locale` = 'zh-CN'
  AND `deleted` = 0
  AND `default_message` REGEXP '^[?]+(\\{reason\\})?$'
  AND `message_key` IN (
    'payment.connectivity.available',
    'payment.connectivity.notTested',
    'payment.connectivity.unavailable',
    'payment.environment.production',
    'payment.environment.sandbox',
    'payment.message.connectivityFailedWithReason',
    'payment.message.connectivityPassed',
    'payment.message.missingFields',
    'payment.message.providerDisabled',
    'payment.message.providerNotConfigured',
    'payment.message.providerReady',
    'payment.message.providerTestFailed',
    'payment.provider.alipay',
    'payment.provider.wechatPay'
  );
