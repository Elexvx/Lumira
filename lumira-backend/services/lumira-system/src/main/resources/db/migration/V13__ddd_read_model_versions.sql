CREATE TABLE IF NOT EXISTS `ddd_read_model_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户 ID，NULL 表示全局',
  `context_name` varchar(64) NOT NULL COMMENT '限界上下文',
  `scope` varchar(128) NOT NULL COMMENT '读模型范围',
  `version` bigint NOT NULL DEFAULT 1 COMMENT '版本号',
  `last_event_key` varchar(255) DEFAULT NULL COMMENT '最后应用的事件幂等键',
  `rebuilt_at` datetime DEFAULT NULL COMMENT '最后重建时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ddd_read_model_version_scope` (`tenant_id`,`context_name`,`scope`),
  KEY `idx_ddd_read_model_version_context` (`context_name`,`updated_at`),
  KEY `idx_ddd_read_model_version_event_key` (`last_event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DDD 读模型版本与缓存失效元数据';
