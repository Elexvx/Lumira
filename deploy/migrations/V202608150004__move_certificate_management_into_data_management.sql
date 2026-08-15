-- Move certificate administration into the shared data-management catalog.
-- Keep the certificate URLs and route catalog for compatibility, but remove the
-- standalone certificate catalog from the authenticated main menu.
SET NAMES utf8mb4;

UPDATE `sys_menu` AS certificate_menu
JOIN `sys_menu` AS data_root
  ON data_root.`menu_code` = 'data.management.root'
 AND data_root.`deleted` = 0
SET certificate_menu.`parent_id` = data_root.`id`,
    certificate_menu.`sort_no` = CASE certificate_menu.`menu_code`
      WHEN 'certificate.templates' THEN 6
      WHEN 'certificate.generate' THEN 7
      WHEN 'certificate.records' THEN 8
      ELSE certificate_menu.`sort_no`
    END,
    certificate_menu.`status` = 'ENABLED',
    certificate_menu.`deleted` = 0,
    certificate_menu.`updated_by` = 0
WHERE certificate_menu.`menu_code` IN (
    'certificate.templates',
    'certificate.generate',
    'certificate.records'
);

UPDATE `sys_menu`
SET `status` = 'DISABLED',
    `deleted` = 1,
    `updated_by` = 0
WHERE `menu_code` = 'certificate.root'
  AND `deleted` = 0;

UPDATE `sys_menu` AS download_menu
JOIN `sys_menu` AS data_root
  ON data_root.`menu_code` = 'data.management.root'
 AND data_root.`deleted` = 0
SET download_menu.`sort_no` = 9,
    download_menu.`updated_by` = 0
WHERE download_menu.`menu_code` = 'files.download-center'
  AND download_menu.`parent_id` = data_root.`id`;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'platform', 'menu-tree', 1, 'migration:V202608150004:certificate-data-management', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
