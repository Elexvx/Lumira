INSERT INTO `sys_config` (
  `tenant_id`,
  `config_key`,
  `config_name`,
  `config_value`,
  `config_scope`,
  `is_system`,
  `remark`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`
)
VALUES
  (1001, 'notification.wechat-official.enabled', '微信公众号通知启用', 'false', 'PLATFORM', 0, '是否启用微信公众号/服务号模板消息通知', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0),
  (1001, 'notification.wechat-official.app-id', '微信公众号 AppID', '', 'PLATFORM', 0, '微信公众号或服务号 AppID', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0),
  (1001, 'notification.wechat-official.app-secret', '微信公众号 AppSecret', '', 'PLATFORM', 0, '微信公众号或服务号 AppSecret', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0),
  (1001, 'notification.wechat-official.template-id', '微信公众号模板 ID', '', 'PLATFORM', 0, '用于系统通知的公众号模板消息 ID', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0),
  (1001, 'notification.wechat-official.detail-url', '微信公众号通知详情链接', '', 'PLATFORM', 0, '模板消息点击后打开的系统链接，可留空', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
  `config_name` = VALUES(`config_name`),
  `config_scope` = VALUES(`config_scope`),
  `is_system` = VALUES(`is_system`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
