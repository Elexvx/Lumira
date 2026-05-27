INSERT INTO sys_dict_type (tenant_id, dict_code, dict_name, status, is_system, remark, created_by, updated_by, deleted)
VALUES (1001, 'menu_icon', '菜单图标', 'ENABLED', 1, '菜单管理图标下拉选项', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  dict_name = VALUES(dict_name),
  status = VALUES(status),
  is_system = VALUES(is_system),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_at = CURRENT_TIMESTAMP,
  deleted = VALUES(deleted);

INSERT INTO sys_dict_item (tenant_id, dict_type_id, item_value, item_label, sort_no, created_by, updated_by, deleted, status, remark)
SELECT 1001, t.id, v.item_value, v.item_label, v.sort_no, 0, 0, 0, 'ENABLED', v.remark
FROM sys_dict_type t
JOIN (
  SELECT 'menu_icon' AS dict_code, 'DashboardOutlined' AS item_value, '首页 / DashboardOutlined' AS item_label, 1 AS sort_no, '首页、仪表盘' AS remark
  UNION ALL SELECT 'menu_icon', 'AppstoreOutlined', '应用 / AppstoreOutlined', 2, '应用入口'
  UNION ALL SELECT 'menu_icon', 'SettingOutlined', '设置 / SettingOutlined', 3, '系统设置'
  UNION ALL SELECT 'menu_icon', 'UserOutlined', '用户 / UserOutlined', 4, '用户中心'
  UNION ALL SELECT 'menu_icon', 'TeamOutlined', '团队 / TeamOutlined', 5, '组织、团队'
  UNION ALL SELECT 'menu_icon', 'UserSwitchOutlined', '用户切换 / UserSwitchOutlined', 6, '用户切换'
  UNION ALL SELECT 'menu_icon', 'IdcardOutlined', '身份 / IdcardOutlined', 7, '身份资料'
  UNION ALL SELECT 'menu_icon', 'ApartmentOutlined', '组织 / ApartmentOutlined', 8, '组织架构'
  UNION ALL SELECT 'menu_icon', 'SafetyOutlined', '安全 / SafetyOutlined', 9, '安全配置'
  UNION ALL SELECT 'menu_icon', 'MenuOutlined', '菜单 / MenuOutlined', 10, '菜单管理'
  UNION ALL SELECT 'menu_icon', 'DatabaseOutlined', '字典 / DatabaseOutlined', 11, '数据字典'
  UNION ALL SELECT 'menu_icon', 'FormOutlined', '表单 / FormOutlined', 12, '表单字段'
  UNION ALL SELECT 'menu_icon', 'SkinOutlined', '个性化 / SkinOutlined', 13, '主题外观'
  UNION ALL SELECT 'menu_icon', 'NotificationOutlined', '通知 / NotificationOutlined', 14, '消息通知'
  UNION ALL SELECT 'menu_icon', 'RobotOutlined', '机器人 / RobotOutlined', 15, 'AI 员工'
  UNION ALL SELECT 'menu_icon', 'ApiOutlined', '接口 / ApiOutlined', 16, '接口、插件'
  UNION ALL SELECT 'menu_icon', 'FolderOpenOutlined', '文件 / FolderOpenOutlined', 17, '文件管理'
  UNION ALL SELECT 'menu_icon', 'TranslationOutlined', '本地化 / TranslationOutlined', 18, '多语言'
  UNION ALL SELECT 'menu_icon', 'FundOutlined', '监控 / FundOutlined', 19, '监控指标'
  UNION ALL SELECT 'menu_icon', 'FileTextOutlined', '文档 / FileTextOutlined', 20, '文档页面'
  UNION ALL SELECT 'menu_icon', 'FileSearchOutlined', '检索 / FileSearchOutlined', 21, '检索、审阅'
  UNION ALL SELECT 'menu_icon', 'FileOutlined', '文件 / FileOutlined', 22, '通用文件'
  UNION ALL SELECT 'menu_icon', 'AuditOutlined', '审计 / AuditOutlined', 23, '审计日志'
  UNION ALL SELECT 'menu_icon', 'RadarChartOutlined', '雷达 / RadarChartOutlined', 24, '分析图表'
) v ON v.dict_code = t.dict_code
WHERE t.tenant_id = 1001
  AND t.dict_code = 'menu_icon'
  AND t.deleted = 0
ON DUPLICATE KEY UPDATE
  item_label = VALUES(item_label),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_at = CURRENT_TIMESTAMP,
  deleted = VALUES(deleted);

UPDATE sys_dict_item
SET item_value = concat(left(item_value, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 1
  AND locate('__deleted_', item_value) = 0;

UPDATE sys_dict_type
SET dict_code = concat(left(dict_code, greatest(0, 64 - char_length(concat('__deleted_', id)))), '__deleted_', id),
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 1
  AND locate('__deleted_', dict_code) = 0;
