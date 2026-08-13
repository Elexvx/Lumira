-- Consolidate competition operations around the selected competition workspace.
-- Remove the retired standalone registration dossier from the menu read model.
SET NAMES utf8mb4;

DELETE FROM `sys_menu`
WHERE `menu_code` = 'competition.registrations';

UPDATE `sys_menu`
SET `menu_name` = CASE `menu_code`
      WHEN 'payment.management' THEN '全局支付流水'
      WHEN 'certificate.root' THEN '证书中心'
      WHEN 'certificate.generate' THEN '跨赛事证书生成'
      WHEN 'certificate.records' THEN '全局证书记录'
      WHEN 'expert.review.tasks' THEN '跨赛事评审工作台'
      ELSE `menu_name`
    END,
    `updated_by` = 0
WHERE `menu_code` IN (
    'payment.management',
    'certificate.root',
    'certificate.generate',
    'certificate.records',
    'expert.review.tasks'
)
  AND `deleted` = 0;

UPDATE `sys_localization_entry`
SET `default_message` = CASE `message_key`
      WHEN 'nav.expertReview.reviews' THEN '跨赛事评审工作台'
      WHEN 'nav.payments.management' THEN '全局支付流水'
      WHEN 'nav.certificates.root' THEN '证书中心'
      WHEN 'nav.certificates.generate' THEN '跨赛事证书生成'
      WHEN 'nav.certificates.records' THEN '全局证书记录'
      ELSE `default_message`
    END,
    `updated_by` = 0
WHERE `message_key` IN (
    'nav.expertReview.reviews',
    'nav.payments.management',
    'nav.certificates.root',
    'nav.certificates.generate',
    'nav.certificates.records'
)
  AND `source_locale` = 'zh-CN'
  AND `deleted` = 0
  AND (
      (`message_key` = 'nav.expertReview.reviews' AND `default_message` = '评审与晋级')
      OR (`message_key` = 'nav.payments.management' AND `default_message` = '支付管理')
      OR (`message_key` = 'nav.certificates.root' AND `default_message` = '证书管理')
      OR (`message_key` = 'nav.certificates.generate' AND `default_message` = '证书生成')
      OR (`message_key` = 'nav.certificates.records' AND `default_message` = '证书记录')
  );

UPDATE `sys_localization_translation` AS translation
JOIN `sys_localization_entry` AS entry
  ON entry.`id` = translation.`entry_id`
 AND entry.`deleted` = 0
SET translation.`translated_message` = CASE entry.`message_key`
      WHEN 'nav.expertReview.reviews' THEN '跨赛事评审工作台'
      WHEN 'nav.payments.management' THEN '全局支付流水'
      WHEN 'nav.certificates.root' THEN '证书中心'
      WHEN 'nav.certificates.generate' THEN '跨赛事证书生成'
      WHEN 'nav.certificates.records' THEN '全局证书记录'
      ELSE translation.`translated_message`
    END,
    translation.`updated_by` = 0
WHERE translation.`locale_code` = 'zh-CN'
  AND translation.`deleted` = 0
  AND (
      (entry.`message_key` = 'nav.expertReview.reviews' AND translation.`translated_message` = '评审与晋级')
      OR (entry.`message_key` = 'nav.payments.management' AND translation.`translated_message` = '支付管理')
      OR (entry.`message_key` = 'nav.certificates.root' AND translation.`translated_message` = '证书管理')
      OR (entry.`message_key` = 'nav.certificates.generate' AND translation.`translated_message` = '证书生成')
      OR (entry.`message_key` = 'nav.certificates.records' AND translation.`translated_message` = '证书记录')
  );

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES ('platform', 'menu-tree', 1, 'migration:V202608130002:competition-navigation', NOW())
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
