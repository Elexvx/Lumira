-- Keep dictionary management focused on business reference data.
-- Role type and user status are platform internals and are configured by their own modules.

UPDATE sys_dict_item i
JOIN sys_dict_type t ON t.tenant_id = i.tenant_id AND t.id = i.dict_type_id
SET i.deleted = 1,
    i.updated_by = 0,
    i.updated_at = CURRENT_TIMESTAMP
WHERE t.tenant_id = 1001
  AND t.dict_code IN ('user_status', 'role_type')
  AND i.deleted = 0;

UPDATE sys_dict_type
SET deleted = 1,
    updated_by = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 1001
  AND dict_code IN ('user_status', 'role_type')
  AND deleted = 0;

INSERT INTO sys_dict_type (tenant_id, dict_code, dict_name, status, is_system, remark, created_by, updated_by, deleted)
VALUES
(1001, 'gender', '性别', 'ENABLED', 1, '用户基础资料性别选项', 0, 0, 0),
(1001, 'region_province', '省份', 'ENABLED', 1, '所在地区省份选项', 0, 0, 0),
(1001, 'region_city', '城市', 'ENABLED', 1, '所在地区城市选项', 0, 0, 0),
(1001, 'region_district', '区县', 'ENABLED', 1, '所在地区区县选项', 0, 0, 0),
(1001, 'postal_code', '邮政编码', 'ENABLED', 1, '常用地区邮政编码选项', 0, 0, 0)
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
  SELECT 'gender' AS dict_code, 'MALE' AS item_value, '男' AS item_label, 1 AS sort_no, '男性' AS remark
  UNION ALL SELECT 'gender', 'FEMALE', '女', 2, '女性'
  UNION ALL SELECT 'gender', 'OTHER', '其他', 3, '其他或不便透露'
) v ON v.dict_code = t.dict_code
WHERE t.tenant_id = 1001
  AND t.deleted = 0
ON DUPLICATE KEY UPDATE
  item_label = VALUES(item_label),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_at = CURRENT_TIMESTAMP,
  deleted = VALUES(deleted);

INSERT INTO sys_dict_item (tenant_id, dict_type_id, item_value, item_label, sort_no, created_by, updated_by, deleted, status, remark)
SELECT 1001, t.id, v.item_value, v.item_label, v.sort_no, 0, 0, 0, 'ENABLED', v.remark
FROM sys_dict_type t
JOIN (
  SELECT 'region_province' AS dict_code, 'BEIJING' AS item_value, '北京市' AS item_label, 1 AS sort_no, '直辖市' AS remark
  UNION ALL SELECT 'region_province', 'SHANGHAI', '上海市', 2, '直辖市'
  UNION ALL SELECT 'region_province', 'TIANJIN', '天津市', 3, '直辖市'
  UNION ALL SELECT 'region_province', 'CHONGQING', '重庆市', 4, '直辖市'
  UNION ALL SELECT 'region_province', 'HEBEI', '河北省', 5, '省'
  UNION ALL SELECT 'region_province', 'SHANXI', '山西省', 6, '省'
  UNION ALL SELECT 'region_province', 'LIAONING', '辽宁省', 7, '省'
  UNION ALL SELECT 'region_province', 'JILIN', '吉林省', 8, '省'
  UNION ALL SELECT 'region_province', 'HEILONGJIANG', '黑龙江省', 9, '省'
  UNION ALL SELECT 'region_province', 'JIANGSU', '江苏省', 10, '省'
  UNION ALL SELECT 'region_province', 'ZHEJIANG', '浙江省', 11, '省'
  UNION ALL SELECT 'region_province', 'ANHUI', '安徽省', 12, '省'
  UNION ALL SELECT 'region_province', 'FUJIAN', '福建省', 13, '省'
  UNION ALL SELECT 'region_province', 'JIANGXI', '江西省', 14, '省'
  UNION ALL SELECT 'region_province', 'SHANDONG', '山东省', 15, '省'
  UNION ALL SELECT 'region_province', 'HENAN', '河南省', 16, '省'
  UNION ALL SELECT 'region_province', 'HUBEI', '湖北省', 17, '省'
  UNION ALL SELECT 'region_province', 'HUNAN', '湖南省', 18, '省'
  UNION ALL SELECT 'region_province', 'GUANGDONG', '广东省', 19, '省'
  UNION ALL SELECT 'region_province', 'HAINAN', '海南省', 20, '省'
  UNION ALL SELECT 'region_province', 'SICHUAN', '四川省', 21, '省'
  UNION ALL SELECT 'region_province', 'GUIZHOU', '贵州省', 22, '省'
  UNION ALL SELECT 'region_province', 'YUNNAN', '云南省', 23, '省'
  UNION ALL SELECT 'region_province', 'SHAANXI', '陕西省', 24, '省'
  UNION ALL SELECT 'region_province', 'GANSU', '甘肃省', 25, '省'
  UNION ALL SELECT 'region_province', 'QINGHAI', '青海省', 26, '省'
  UNION ALL SELECT 'region_province', 'TAIWAN', '台湾省', 27, '省'
  UNION ALL SELECT 'region_province', 'NEIMENGGU', '内蒙古自治区', 28, '自治区'
  UNION ALL SELECT 'region_province', 'GUANGXI', '广西壮族自治区', 29, '自治区'
  UNION ALL SELECT 'region_province', 'XIZANG', '西藏自治区', 30, '自治区'
  UNION ALL SELECT 'region_province', 'NINGXIA', '宁夏回族自治区', 31, '自治区'
  UNION ALL SELECT 'region_province', 'XINJIANG', '新疆维吾尔自治区', 32, '自治区'
  UNION ALL SELECT 'region_province', 'HONGKONG', '香港特别行政区', 33, '特别行政区'
  UNION ALL SELECT 'region_province', 'MACAO', '澳门特别行政区', 34, '特别行政区'
) v ON v.dict_code = t.dict_code
WHERE t.tenant_id = 1001
  AND t.deleted = 0
ON DUPLICATE KEY UPDATE
  item_label = VALUES(item_label),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_at = CURRENT_TIMESTAMP,
  deleted = VALUES(deleted);

INSERT INTO sys_dict_item (tenant_id, dict_type_id, item_value, item_label, sort_no, created_by, updated_by, deleted, status, remark)
SELECT 1001, t.id, v.item_value, v.item_label, v.sort_no, 0, 0, 0, 'ENABLED', v.remark
FROM sys_dict_type t
JOIN (
  SELECT 'region_city' AS dict_code, 'BEIJING' AS item_value, '北京市' AS item_label, 1 AS sort_no, '北京' AS remark
  UNION ALL SELECT 'region_city', 'SHANGHAI', '上海市', 2, '上海'
  UNION ALL SELECT 'region_city', 'GUANGZHOU', '广州市', 3, '广东省'
  UNION ALL SELECT 'region_city', 'SHENZHEN', '深圳市', 4, '广东省'
  UNION ALL SELECT 'region_city', 'HANGZHOU', '杭州市', 5, '浙江省'
  UNION ALL SELECT 'region_city', 'NANJING', '南京市', 6, '江苏省'
  UNION ALL SELECT 'region_city', 'CHENGDU', '成都市', 7, '四川省'
  UNION ALL SELECT 'region_city', 'WUHAN', '武汉市', 8, '湖北省'
  UNION ALL SELECT 'region_city', 'XIAN', '西安市', 9, '陕西省'
  UNION ALL SELECT 'region_city', 'TIANJIN', '天津市', 10, '天津'
  UNION ALL SELECT 'region_city', 'CHONGQING', '重庆市', 11, '重庆'
) v ON v.dict_code = t.dict_code
WHERE t.tenant_id = 1001
  AND t.deleted = 0
ON DUPLICATE KEY UPDATE
  item_label = VALUES(item_label),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_at = CURRENT_TIMESTAMP,
  deleted = VALUES(deleted);

INSERT INTO sys_dict_item (tenant_id, dict_type_id, item_value, item_label, sort_no, created_by, updated_by, deleted, status, remark)
SELECT 1001, t.id, v.item_value, v.item_label, v.sort_no, 0, 0, 0, 'ENABLED', v.remark
FROM sys_dict_type t
JOIN (
  SELECT 'region_district' AS dict_code, 'CHAOYANG' AS item_value, '朝阳区' AS item_label, 1 AS sort_no, '北京市' AS remark
  UNION ALL SELECT 'region_district', 'HAIDIAN', '海淀区', 2, '北京市'
  UNION ALL SELECT 'region_district', 'PUDONG', '浦东新区', 3, '上海市'
  UNION ALL SELECT 'region_district', 'HUANGPU', '黄浦区', 4, '上海市'
  UNION ALL SELECT 'region_district', 'TIANHE', '天河区', 5, '广州市'
  UNION ALL SELECT 'region_district', 'NANSHAN', '南山区', 6, '深圳市'
  UNION ALL SELECT 'region_district', 'XIHU', '西湖区', 7, '杭州市'
  UNION ALL SELECT 'region_district', 'WUHOU', '武侯区', 8, '成都市'
) v ON v.dict_code = t.dict_code
WHERE t.tenant_id = 1001
  AND t.deleted = 0
ON DUPLICATE KEY UPDATE
  item_label = VALUES(item_label),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_at = CURRENT_TIMESTAMP,
  deleted = VALUES(deleted);

INSERT INTO sys_dict_item (tenant_id, dict_type_id, item_value, item_label, sort_no, created_by, updated_by, deleted, status, remark)
SELECT 1001, t.id, v.item_value, v.item_label, v.sort_no, 0, 0, 0, 'ENABLED', v.remark
FROM sys_dict_type t
JOIN (
  SELECT 'postal_code' AS dict_code, '100000' AS item_value, '北京市 100000' AS item_label, 1 AS sort_no, '北京市常用邮编' AS remark
  UNION ALL SELECT 'postal_code', '200000', '上海市 200000', 2, '上海市常用邮编'
  UNION ALL SELECT 'postal_code', '510000', '广州市 510000', 3, '广州市常用邮编'
  UNION ALL SELECT 'postal_code', '518000', '深圳市 518000', 4, '深圳市常用邮编'
  UNION ALL SELECT 'postal_code', '310000', '杭州市 310000', 5, '杭州市常用邮编'
  UNION ALL SELECT 'postal_code', '210000', '南京市 210000', 6, '南京市常用邮编'
  UNION ALL SELECT 'postal_code', '610000', '成都市 610000', 7, '成都市常用邮编'
  UNION ALL SELECT 'postal_code', '430000', '武汉市 430000', 8, '武汉市常用邮编'
  UNION ALL SELECT 'postal_code', '710000', '西安市 710000', 9, '西安市常用邮编'
) v ON v.dict_code = t.dict_code
WHERE t.tenant_id = 1001
  AND t.deleted = 0
ON DUPLICATE KEY UPDATE
  item_label = VALUES(item_label),
  sort_no = VALUES(sort_no),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_at = CURRENT_TIMESTAMP,
  deleted = VALUES(deleted);
