-- Consolidated baseline generated from previous V1,V2,V45-V63 migrations.
-- Future schema changes should start at V2.
-- Tenant feature tables and unused scaffold tables were removed during consolidation.

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned NOT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
  `conversation_code` varchar(64) NOT NULL,
  `title` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `is_pinned` tinyint unsigned NOT NULL DEFAULT '0',
  `latest_message_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_code` (`tenant_id`,`conversation_code`),
  KEY `idx_ai_conversation_tenant_employee` (`tenant_id`,`employee_id`),
  KEY `idx_ai_conversation_owner` (`tenant_id`,`owner_user_id`,`employee_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_conversation_share` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `share_token` varchar(128) NOT NULL,
  `title` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` datetime DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_share_token` (`tenant_id`,`share_token`),
  KEY `idx_ai_conversation_share_tenant_conversation` (`tenant_id`,`conversation_id`),
  KEY `idx_ai_conversation_share_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(64) NOT NULL,
  `position` varchar(128) DEFAULT NULL,
  `avatar_key` varchar(128) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `greeting` varchar(1024) DEFAULT NULL,
  `system_prompt` text,
  `default_llm_service_id` bigint unsigned DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_tenant_username` (`tenant_id`,`username`),
  KEY `idx_ai_employee_tenant_deleted` (`tenant_id`,`is_deleted`),
  KEY `idx_ai_employee_tenant_llm` (`tenant_id`,`default_llm_service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned NOT NULL,
  `skill_code` varchar(128) NOT NULL,
  `permission_mode` varchar(32) NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_skill_rel` (`tenant_id`,`employee_id`,`skill_code`),
  KEY `idx_ai_employee_skill_tenant_employee` (`tenant_id`,`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_llm_model` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `llm_service_id` bigint unsigned NOT NULL,
  `model_code` varchar(128) NOT NULL,
  `model_name` varchar(128) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_model_service_code` (`tenant_id`,`llm_service_id`,`model_code`),
  KEY `idx_ai_llm_model_tenant_deleted` (`tenant_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_llm_service` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `provider` varchar(64) NOT NULL,
  `code` varchar(64) NOT NULL,
  `title` varchar(128) NOT NULL,
  `base_url` varchar(512) DEFAULT NULL,
  `api_key_encrypted` text,
  `default_model` varchar(128) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `timeout_ms` int NOT NULL DEFAULT '60000',
  `temperature` decimal(4,2) DEFAULT '0.70',
  `max_tokens` int DEFAULT '2048',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_service_tenant_code` (`tenant_id`,`code`),
  KEY `idx_ai_llm_service_tenant_deleted` (`tenant_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `role` varchar(32) NOT NULL,
  `content` longtext,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_tenant_conversation` (`tenant_id`,`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message_attachment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `message_id` bigint unsigned NOT NULL,
  `file_id` bigint unsigned NOT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `file_size_bytes` bigint unsigned DEFAULT NULL,
  `public_url` varchar(1024) DEFAULT NULL,
  `preview_url` varchar(1024) DEFAULT NULL,
  `download_url` varchar(1024) DEFAULT NULL,
  `preview_mode` varchar(32) DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_message_attachment_rel` (`tenant_id`,`message_id`,`file_id`),
  KEY `idx_ai_message_attachment_tenant_conversation` (`tenant_id`,`conversation_id`),
  KEY `idx_ai_message_attachment_tenant_message` (`tenant_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `skill_code` varchar(128) NOT NULL,
  `skill_name` varchar(128) NOT NULL,
  `category` varchar(64) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `risk_level` varchar(32) DEFAULT NULL,
  `read_only` tinyint unsigned NOT NULL DEFAULT '0',
  `need_confirm` tinyint unsigned NOT NULL DEFAULT '0',
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_code` (`skill_code`),
  KEY `idx_ai_skill_category_enabled` (`category`,`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `ai_skill` (`id`, `skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`, `create_time`, `update_time`) VALUES (1,'knowledge.search','知识检索','COMMON','从已授权知识库中查找相关内容并返回参考结论。','LOW',1,0,1,0,'2026-05-15 00:52:27','2026-05-15 00:52:27'),(2,'content.summary','内容总结','COMMON','对文本、列表或对话内容进行结构化摘要。','LOW',1,0,1,0,'2026-05-15 00:52:27','2026-05-15 00:52:27'),(3,'customer.reply','客户回复','CUSTOM','基于上下文生成面向客户的正式回复话术。','MEDIUM',0,0,1,0,'2026-05-15 00:52:27','2026-05-15 00:52:27'),(4,'data.query','数据查询','CUSTOM','访问已授权业务数据并生成查询结果说明。','MEDIUM',0,0,1,0,'2026-05-15 00:52:27','2026-05-15 00:52:27'),(5,'data.export','数据导出','CUSTOM','将查询结果导出为可下载的结构化数据。','HIGH',0,1,1,0,'2026-05-15 00:52:27','2026-05-15 00:52:27'),(6,'workflow.request','流程发起','CUSTOM','发起需要确认的业务流程。','HIGH',0,1,1,0,'2026-05-15 00:52:27','2026-05-15 00:52:27');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_tool_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `skill_code` varchar(128) DEFAULT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `permission_mode` varchar(32) DEFAULT NULL,
  `confirm_required` tinyint unsigned NOT NULL DEFAULT '0',
  `confirm_result` tinyint unsigned NOT NULL DEFAULT '0',
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `request_payload_json` longtext,
  `response_payload_json` longtext,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_audit_log_tenant_employee_created` (`tenant_id`,`employee_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `login_type` varchar(32) NOT NULL DEFAULT 'PASSWORD',
  `login_result` varchar(32) NOT NULL,
  `fail_reason` varchar(255) DEFAULT NULL,
  `login_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `request_id` varchar(64) NOT NULL,
  `trace_id` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_login_log_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `module_name` varchar(64) DEFAULT NULL,
  `action_name` varchar(128) DEFAULT NULL,
  `operation_type` varchar(32) NOT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `request_id` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operation_log_module_created` (`module_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_object` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `uploaded_by_name` varchar(128) DEFAULT NULL,
  `storage_type` varchar(32) NOT NULL,
  `bucket` varchar(128) DEFAULT NULL,
  `object_key` varchar(255) NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT '0',
  `checksum` varchar(128) DEFAULT NULL,
  `public_url` varchar(512) DEFAULT NULL,
  `preview_mode` varchar(32) NOT NULL DEFAULT 'UNSUPPORTED',
  `previewable_flag` tinyint NOT NULL DEFAULT '0',
  `category` varchar(128) DEFAULT NULL,
  `tags` varchar(512) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`tenant_id`,`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `msg_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_type` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_role_id` bigint DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `publish_status` varchar(32) NOT NULL DEFAULT 'PUBLISHED',
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_notice_tenant_type_status_created` (`tenant_id`,`notice_type`,`publish_status`,`created_at`),
  KEY `idx_msg_notice_tenant_target_created` (`tenant_id`,`target_user_id`,`created_at`),
  KEY `idx_msg_notice_tenant_target_role_created` (`tenant_id`,`target_role_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `msg_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `read_at` datetime NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_notice_read` (`tenant_id`,`notice_id`,`user_id`),
  KEY `idx_msg_notice_read_user_created` (`tenant_id`,`user_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `source_type` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_key` varchar(128) NOT NULL,
  `payload_json` longtext NOT NULL,
  `dispatch_status` varchar(32) NOT NULL DEFAULT 'RECORDED',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_platform_event_outbox_tenant_status` (`tenant_id`,`dispatch_status`),
  KEY `idx_platform_event_outbox_retry` (`dispatch_status`,`next_retry_at`),
  KEY `idx_platform_event_outbox_created_at` (`created_at`),
  KEY `idx_platform_event_outbox_event_key` (`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_content` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `title` varchar(220) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `cover_file_id` bigint DEFAULT NULL,
  `body_type` varchar(32) NOT NULL DEFAULT 'RICH_TEXT',
  `body_text` mediumtext,
  `body_json` json DEFAULT NULL,
  `seo_json` json DEFAULT NULL,
  `tags_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_content_slug` (`tenant_id`,`site_id`,`slug`,`deleted`),
  KEY `idx_site_content_list` (`tenant_id`,`site_id`,`category_id`,`status`,`published_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_content_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_content_category_code` (`tenant_id`,`site_id`,`code`,`deleted`),
  KEY `idx_site_content_category_tree` (`tenant_id`,`site_id`,`parent_id`,`sort_order`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_form` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `submit_policy` varchar(32) NOT NULL DEFAULT 'ANONYMOUS',
  `schema_json` json NOT NULL,
  `notification_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_form_code` (`tenant_id`,`site_id`,`code`,`deleted`),
  KEY `idx_site_form_status` (`tenant_id`,`site_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_form_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `form_id` bigint NOT NULL,
  `submitter_user_id` bigint DEFAULT NULL,
  `submitter_ip` varchar(64) DEFAULT NULL,
  `data_json` json NOT NULL,
  `attachment_file_ids_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_remark` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_site_form_submission_list` (`tenant_id`,`site_id`,`form_id`,`status`,`created_at`,`deleted`),
  KEY `idx_site_form_submission_user` (`tenant_id`,`submitter_user_id`,`created_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_navigation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `link_type` varchar(32) NOT NULL,
  `link_target` varchar(512) NOT NULL,
  `open_type` varchar(32) NOT NULL DEFAULT 'SELF',
  `sort_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'VISIBLE',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_site_navigation_tree` (`tenant_id`,`site_id`,`parent_id`,`sort_order`,`deleted`),
  KEY `idx_site_navigation_status` (`tenant_id`,`site_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_page` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `title` varchar(180) NOT NULL,
  `slug` varchar(255) NOT NULL,
  `page_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `seo_json` json DEFAULT NULL,
  `current_draft_version` bigint DEFAULT NULL,
  `current_published_version` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_page_slug` (`tenant_id`,`site_id`,`slug`,`deleted`),
  KEY `idx_site_page_status` (`tenant_id`,`site_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_page_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `site_id` bigint NOT NULL,
  `page_id` bigint NOT NULL,
  `version_no` bigint NOT NULL,
  `blocks_json` json NOT NULL,
  `snapshot_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_page_version_no` (`tenant_id`,`page_id`,`version_no`),
  KEY `idx_site_page_version_status` (`tenant_id`,`page_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_site` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `primary_domain` varchar(255) DEFAULT NULL,
  `logo_file_id` bigint DEFAULT NULL,
  `favicon_file_id` bigint DEFAULT NULL,
  `theme_json` json DEFAULT NULL,
  `seo_json` json DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_site_site_tenant_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_site_site_tenant_status` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `config_key` varchar(128) NOT NULL,
  `config_name` varchar(128) NOT NULL DEFAULT '',
  `config_value` varchar(2000) NOT NULL,
  `config_scope` varchar(32) NOT NULL DEFAULT 'PLATFORM',
  `is_system` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`tenant_id`,`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=7385 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7001,1001,'platform.name','平台名称','SaaS Foundation','PLATFORM',1,'平台展示名称',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(7003,1001,'security.idle-timeout-seconds','空闲超时时间','1800','PLATFORM',1,'会话在无操作状态下允许保持的秒数',0,'2026-03-30 17:42:45',0,'2026-04-15 01:20:52',0),(7004,1001,'security.access-token-expire-seconds','Access Token 过期时间','1800','PLATFORM',1,'Access Token 的有效秒数',0,'2026-03-30 17:42:45',0,'2026-03-30 17:42:45',0),(7005,1001,'security.refresh-token-expire-seconds','Refresh Token 刷新时限','604800','PLATFORM',1,'Refresh Token 的有效秒数',0,'2026-03-30 17:42:45',0,'2026-03-30 17:42:45',0),(7006,1001,'branding.website-name','站点名称','宏翔商道','PLATFORM',0,'控制台顶部与浏览器标题展示名称',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7007,1001,'branding.website-favicon-url','站点图标地址','','PLATFORM',0,'浏览器标签页 icon 地址',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7008,1001,'branding.website-logo-url','站点 Logo 地址','/api/uploads/2026/04/06/b95bb9702acf4bf9beb9a9d0056f8cb9.svg','PLATFORM',0,'控制台左上角品牌 Logo 地址',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7009,1001,'branding.footer-icp','页脚 ICP 备案','','PLATFORM',0,'页脚备案信息',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7010,1001,'branding.footer-copyright','页脚版权声明','','PLATFORM',0,'页脚版权声明',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7013,1001,'agreement.user-agreement-markdown','用户协议','欢迎使用宏翔商道后台管理系统。\n\n在使用本系统前，请仔细阅读并理解以下内容：\n\n1. 您在登录、访问和使用本系统相关功能时，应遵守国家法律法规以及平台规则。\n2. 您应妥善保管账号、密码及相关身份信息，不得将账号转借、共享或提供给无关第三方。\n3. 平台可能会在提供服务所必需的范围内处理您的账号、日志与业务数据。\n4. 如您不同意本协议内容，请停止使用本系统。\n\n本协议自发布或更新之日起生效。','PLATFORM',0,'用户协议 Markdown',0,'2026-04-07 04:32:25',1001,'2026-05-04 13:44:05',0),(7014,1001,'agreement.privacy-agreement-markdown','隐私协议','我们重视并保护您的个人信息。\n\n在提供服务所必需的范围内，我们可能会收集、使用、存储和传输您的账号信息、操作日志和业务数据。\n\n我们不会在未经授权的情况下向无关第三方披露您的个人信息，除非法律法规或监管要求另有规定。\n\n如您对隐私保护有任何疑问，请联系系统管理员。','PLATFORM',0,'隐私协议 Markdown',0,'2026-04-07 04:32:25',1001,'2026-05-04 13:44:05',0),(7111,1001,'watermark.enabled','水印开关','true','PLATFORM',0,'全局水印开关',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7112,1001,'watermark.mode','水印模式','TEXT','PLATFORM',0,'TEXT/IMAGE',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7113,1001,'watermark.text-lines','水印文本','宏翔商道\n后台管理系统','PLATFORM',0,'多行文本水印',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7114,1001,'watermark.image-url','水印图片','','PLATFORM',0,'图片水印 URL',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7115,1001,'watermark.font-color','字体颜色','rgba(0,0,0,0.15)','PLATFORM',0,'字体颜色',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7116,1001,'watermark.font-size','字体大小','14','PLATFORM',0,'字体大小',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7117,1001,'watermark.font-weight','字体粗细','normal','PLATFORM',0,'字体粗细',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7118,1001,'watermark.rotate','旋转角度','-22','PLATFORM',0,'旋转角度',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7119,1001,'watermark.gap-x','横向间距','300','PLATFORM',0,'横向间距',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7120,1001,'watermark.gap-y','纵向间距','200','PLATFORM',0,'纵向间距',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7121,1001,'watermark.offset-x','横向偏移','0','PLATFORM',0,'横向偏移',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7122,1001,'watermark.offset-y','纵向偏移','0','PLATFORM',0,'纵向偏移',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7123,1001,'watermark.z-index','层级','9','PLATFORM',0,'z-index',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7124,1001,'watermark.opacity','透明度','0.15','PLATFORM',0,'透明度',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7137,1001,'security.allow-multi-device-login','多设备登录','0','PLATFORM',1,'是否允许同一账号在多个设备同时在线',0,'2026-04-06 23:53:20',0,'2026-04-06 23:53:20',0),(7201,1001,'branding.company-name','公司名称','宏翔商道','PLATFORM',0,'页脚版权主体名称',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7202,1001,'branding.copyright-start-year','版权起始年份','2025','PLATFORM',0,'页脚版权起始年份',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7205,1001,'branding.github-link-url','GitHub 链接','https://github.com/Elexvx/legendary-invention','PLATFORM',0,'顶部 GitHub 图标跳转地址',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7206,1001,'branding.help-link-url','帮助链接','https://github.com/Elexvx/legendary-invention/blob/main/README.md','PLATFORM',0,'顶部帮助图标跳转地址',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7213,1001,'security.captcha-enabled','验证码开关','0','PLATFORM',1,'是否开启登录时的人机验证码',0,'2026-04-07 05:42:47',0,'2026-05-04 01:48:47',0),(7214,1001,'security.captcha-type','验证码类型','IMAGE','PLATFORM',1,'验证码类型：IMAGE=图片验证码',0,'2026-04-07 05:42:47',0,'2026-04-13 17:18:29',0),(7215,1001,'security.login-defense-window-minutes','登录防御统计窗口','5','PLATFORM',1,'统计登录尝试与错误次数的时间窗口（分钟）',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7216,1001,'security.login-max-validation-attempts','最大验证次数','100','PLATFORM',1,'统计窗口内允许的最大验证码/登录验证尝试次数',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7217,1001,'security.login-max-failure-count','最大错误次数','10','PLATFORM',1,'统计窗口内允许的最大登录失败次数',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7218,1001,'security.password-min-length','密码最短长度','6','PLATFORM',1,'用户密码允许的最少字符数',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7219,1001,'security.password-require-uppercase','密码必须包含大写字母','0','PLATFORM',1,'强制密码包含 A-Z',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7220,1001,'security.password-require-lowercase','密码必须包含小写字母','0','PLATFORM',1,'强制密码包含 a-z',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7221,1001,'security.password-require-special-character','密码必须包含特殊字符','0','PLATFORM',1,'强制密码包含特殊字符',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7222,1001,'security.password-allow-consecutive-characters','允许连续字符','1','PLATFORM',1,'是否允许密码中出现连续字符',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7265,1001,'profile.field.avatar.visible','头像展示开关','true','PLATFORM',0,'控制个人中心是否展示头像上传与预览区域',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7266,1001,'profile.field.real-name.visible','姓名展示开关','true','PLATFORM',0,'控制个人中心是否展示姓名字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7267,1001,'profile.field.mobile.visible','手机号展示开关','true','PLATFORM',0,'控制个人中心是否展示手机号字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7268,1001,'profile.field.email.visible','邮箱展示开关','true','PLATFORM',0,'控制个人中心是否展示邮箱字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7269,1001,'profile.field.birth-month.visible','出生年月展示开关','true','PLATFORM',0,'控制个人中心是否展示出生年月字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7270,1001,'profile.field.gender.visible','性别展示开关','true','PLATFORM',0,'控制个人中心是否展示性别字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7271,1001,'profile.field.region.visible','所在地区展示开关','true','PLATFORM',0,'控制个人中心是否展示所在地区字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7272,1001,'profile.field.available-time.visible','可工作时间展示开关','true','PLATFORM',0,'控制个人中心是否展示可工作时间字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7273,1001,'profile.field.id-card-number.visible','身份证号码展示开关','true','PLATFORM',0,'控制个人中心是否展示身份证号码字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7372,1001,'verification.totp.enabled','2FA 启用','true','PLATFORM',0,'是否启用 2FA 登录方式',1001,'2026-04-25 09:24:01',1001,'2026-05-04 01:31:01',0),(7373,1001,'verification.email-login.enabled','邮箱验证码登录','false','PLATFORM',0,'是否启用邮箱验证码登录',1001,'2026-04-25 09:24:01',1001,'2026-05-04 01:31:01',0),(7374,1001,'branding.github-link-enabled','GitHub 链接开关','true','PLATFORM',0,'是否显示顶部 GitHub 图标',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(7377,1001,'branding.help-link-enabled','帮助链接开关','true','PLATFORM',0,'是否显示顶部帮助图标',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(7380,1001,'verification.wechat-login.enabled','微信登录启用','false','PLATFORM',0,'是否启用微信扫码登录',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7381,1001,'verification.wechat-login.app-id','微信 AppID','','PLATFORM',0,'微信开放平台网站应用 AppID',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7382,1001,'verification.wechat-login.app-secret','微信 AppSecret','','PLATFORM',0,'微信开放平台网站应用 AppSecret',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7383,1001,'verification.wechat-login.redirect-uri','微信登录回调地址','','PLATFORM',0,'微信开放平台授权回调地址',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7384,1001,'verification.wechat-login.state-expire-minutes','微信登录状态有效期','10','PLATFORM',0,'微信登录 state 缓存有效期，单位分钟',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `dict_type_id` bigint NOT NULL,
  `item_value` varchar(64) NOT NULL,
  `item_label` varchar(128) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item_value` (`tenant_id`,`dict_type_id`,`item_value`)
) ENGINE=InnoDB AUTO_INCREMENT=6005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_dict_item` (`id`, `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `remark`) VALUES (6001,1001,5001,'ENABLED','启用',1,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0,'ENABLED',NULL),(6002,1001,5001,'DISABLED','停用',2,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0,'ENABLED',NULL),(6003,1001,5002,'SYSTEM','系统角色',1,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0,'ENABLED',NULL),(6004,1001,5002,'CUSTOM','自定义角色',2,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0,'ENABLED',NULL);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `dict_code` varchar(64) NOT NULL,
  `dict_name` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `is_system` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`tenant_id`,`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=5003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `is_system`, `remark`) VALUES (5001,1001,'user_status','用户状态',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0,'ENABLED',1,'系统用户状态字典'),(5002,1001,'role_type','角色类型',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0,'ENABLED',1,'系统角色类型字典');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL,
  `message_key` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_locale` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_entry_namespace_key` (`namespace_id`,`message_key`),
  KEY `idx_sys_localization_entry_status` (`status`,`updated_at`),
  KEY `idx_sys_localization_entry_source` (`source_type`,`source_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_language` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `language_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `native_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fallback_locale` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_language_locale` (`locale_code`),
  KEY `idx_sys_localization_language_status` (`status`,`sort_no`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_localization_language` (`id`, `locale_code`, `language_name`, `native_name`, `fallback_locale`, `sort_no`, `is_default`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1,'zh-CN','Chinese','简体中文',NULL,1,1,'ENABLED',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(2,'en-US','English','English','zh-CN',2,0,'ENABLED',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_namespace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `namespace_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_namespace_code` (`namespace_code`),
  KEY `idx_sys_localization_namespace_status` (`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_release` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_version` bigint NOT NULL,
  `fallback_locale` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bundle_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_flag` tinyint(1) NOT NULL DEFAULT '1',
  `published_by` bigint DEFAULT NULL,
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_release_locale_version` (`locale_code`,`release_version`),
  KEY `idx_sys_localization_release_locale_active` (`locale_code`,`active_flag`,`release_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_translation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `translated_message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `translation_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TRANSLATED',
  `machine_generated` tinyint(1) NOT NULL DEFAULT '0',
  `review_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `translated_by` bigint DEFAULT NULL,
  `translated_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_translation_entry_locale` (`entry_id`,`locale_code`),
  KEY `idx_sys_localization_translation_locale_status` (`locale_code`,`translation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_usage_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_line` int DEFAULT NULL,
  `source_text` text COLLATE utf8mb4_unicode_ci,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_usage_ref` (`entry_id`,`source_type`,`source_ref`,`source_line`),
  KEY `idx_sys_localization_usage_ref_entry` (`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT '0',
  `menu_code` varchar(64) NOT NULL,
  `menu_name` varchar(128) NOT NULL,
  `menu_type` varchar(32) NOT NULL,
  `path` varchar(255) DEFAULT NULL,
  `component` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `icon` varchar(64) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `permission_key` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_code` (`tenant_id`,`menu_code`),
  KEY `idx_sys_menu_tenant_status` (`tenant_id`,`status`,`sort_no`)
) ENGINE=InnoDB AUTO_INCREMENT=4059 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3001,1001,0,'dashboard.home','首页','MENU','/dashboard/home','@/pages/dashboard/Home',0,'2026-03-29 17:20:41',1001,'2026-04-07 04:07:16',0,'DashboardOutlined',0,'dashboard:view','ENABLED'),(3002,1001,0,'settings.root','系统设置','CATALOG','/settings',NULL,0,'2026-03-29 17:20:41',0,'2026-05-15 00:52:27',0,'SettingOutlined',20,'system:view','ENABLED'),(3003,1001,3002,'settings.plugins','插件管理中心','MENU','/settings/plugins','@/pages/settings/plugins',0,'2026-03-29 17:20:41',1001,'2026-05-15 00:52:27',0,'ApiOutlined',8,'plugin:management:view','ENABLED'),(3004,1001,4032,'profile.center','个人资料','MENU','/user-center/personal-center/profile','@/pages/profile/Center',0,'2026-03-29 17:20:41',0,'2026-05-15 00:52:27',0,'UserOutlined',1,'profile:view','ENABLED'),(3006,1001,3020,'user.center.permissions','权限管理','MENU','/user-center/permissions','@/pages/iam/Overview',0,'2026-03-29 20:37:31',0,'2026-04-14 02:28:36',0,'SafetyCertificateOutlined',24,'iam:view','ENABLED'),(3007,1001,3002,'settings.monitoring.audit','审计中心','MENU','/settings/audit','@/pages/settings/monitoring/Audit',0,'2026-03-29 20:37:31',0,'2026-05-15 00:52:27',0,'AuditOutlined',12,'audit:view','ENABLED'),(3008,1001,3020,'system.users','用户管理','MENU','/user-center/users','@/pages/system/users',0,'2026-03-29 20:37:31',0,'2026-04-14 02:28:36',0,'UserOutlined',21,'system:user:view','ENABLED'),(3009,1001,3020,'system.roles','角色管理','MENU','/user-center/roles','@/pages/system/roles',0,'2026-03-29 20:37:31',0,'2026-04-14 02:28:36',0,'SafetyOutlined',23,'system:role:view','ENABLED'),(3010,1001,3002,'settings.menus','菜单管理','MENU','/settings/menus','@/pages/settings/menus',0,'2026-03-29 20:37:31',1001,'2026-05-15 00:52:27',0,'MenuOutlined',1,'system:menu:view','ENABLED'),(3011,1001,3002,'settings.dicts','字典管理','MENU','/settings/dicts','@/pages/settings/dicts',0,'2026-03-29 20:37:31',1001,'2026-05-15 00:52:27',0,'DatabaseOutlined',2,'system:dict:view','ENABLED'),(3013,1001,3002,'settings.security','安全设置','MENU','/settings/security','@/pages/settings/security',0,'2026-03-30 17:42:45',1001,'2026-05-15 00:52:27',0,'SafetyOutlined',5,'system:config:view','ENABLED'),(3014,1001,3002,'settings.personalization','个性化设置','MENU','/settings/personalization','@/pages/settings/personalization',0,'2026-04-03 16:25:38',1001,'2026-05-15 00:52:27',0,'SkinOutlined',4,'system:config:view','ENABLED'),(3015,1001,3020,'system.online-users','在线用户','MENU','/user-center/online-users','@/pages/system/online-users',0,'2026-04-05 22:53:05',0,'2026-04-14 02:28:36',0,'UserSwitchOutlined',22,'system:online-user:view','ENABLED'),(3016,1001,3002,'settings.monitoring.root','系统监控','CATALOG','/settings/monitoring','@/pages/settings/monitoring/index',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'FundOutlined',10,'system:monitor:view','ENABLED'),(3017,1001,3016,'settings.monitoring.service','服务监控','MENU','/settings/monitoring/service','redirect:/settings/monitoring?tab=service',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'RadarChartOutlined',22,'system:monitor:service:view','ENABLED'),(3018,1001,3016,'settings.monitoring.redis','Redis监控','MENU','/settings/monitoring/redis','redirect:/settings/monitoring?tab=redis',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'DatabaseOutlined',23,'system:monitor:redis:view','ENABLED'),(3019,1001,3002,'settings.monitoring.api-docs','接口文档','MENU','/settings/api-docs','@/pages/settings/monitoring/ApiDocs',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'FileTextOutlined',11,'system:monitor:docs:view','ENABLED'),(3020,1001,0,'user.center.root','用户中心','CATALOG','/user-center','@/pages/user-center/index',0,'2026-04-07 04:02:31',0,'2026-04-14 02:31:58',0,'TeamOutlined',18,'user:center:view','ENABLED'),(3024,1001,3002,'settings.ai-employees','数字员工','MENU','/settings/ai-employees','@/pages/settings/ai-employees',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'RobotOutlined',24,'ai:view','ENABLED'),(3025,1001,3002,'settings.profile-fields','字段管理','MENU','/settings/profile-fields','@/pages/settings/profile-fields',0,'2026-04-11 15:36:20',0,'2026-05-15 00:52:27',0,'FormOutlined',3,'system:config:view','ENABLED'),(3026,1001,3002,'settings.notifications','站内信归档','MENU','/settings/notifications','@/pages/settings/notifications/index',0,'2026-04-14 01:30:39',0,'2026-05-15 00:52:27',0,'NotificationOutlined',7,'system:notification:view','ENABLED'),(3027,1001,3002,'settings.verification','验证管理','MENU','/settings/verification','@/pages/settings/verification',0,'2026-04-22 21:55:16',0,'2026-05-15 00:52:27',0,'SafetyOutlined',6,'system:verification:view','ENABLED'),(3029,1001,4032,'files.my','我的文件','MENU','/user-center/files','@/pages/files/Center',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:27',0,'FileOutlined',2,'system:file:view','ENABLED'),(3030,1001,3002,'settings.files','全站文件管理','MENU','/settings/files/all','@/pages/settings/files/Center',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:27',0,'FolderOpenOutlined',9,'system:file:manage','ENABLED'),(3031,1001,3002,'localization.root','本地化中心','MENU','/settings/localization','@/pages/settings/localization',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'TranslationOutlined',29,'localization:view','ENABLED'),(4032,1001,0,'user.center.personal','个人中心','CATALOG',NULL,NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'IdcardOutlined',19,'profile:view','ENABLED'),(4041,1001,0,'site.root','官网管理','CATALOG','/site','redirect:/site/settings',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'GlobalOutlined',7,'site:view','ENABLED'),(4045,1001,4041,'site.settings','站点设置','MENU','/site/settings','@/pages/site/settings',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'SettingOutlined',1,'site:settings','ENABLED'),(4047,1001,4041,'site.navigation','导航管理','MENU','/site/navigation','@/pages/site/navigation',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'MenuOutlined',2,'site:navigation','ENABLED'),(4049,1001,4041,'site.pages','页面管理','MENU','/site/pages','@/pages/site/pages',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'LayoutOutlined',3,'site:page','ENABLED'),(4051,1001,4041,'site.contents','内容管理','MENU','/site/contents','@/pages/site/contents',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'ReadOutlined',4,'site:content','ENABLED'),(4053,1001,4041,'site.forms','表单管理','MENU','/site/forms','@/pages/site/forms',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'FormOutlined',5,'site:form','ENABLED'),(4055,1001,4041,'site.submissions','提交记录','MENU','/site/submissions','@/pages/site/submissions',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'InboxOutlined',6,'site:submission','ENABLED');
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_group` varchar(64) DEFAULT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'CORE',
  `plugin_code` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_key` (`tenant_id`,`permission_key`)
) ENGINE=InnoDB AUTO_INCREMENT=269 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1,1001,'dashboard:view','查看首页','dashboard','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(2,1001,'system:view','查看系统管理','system','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(3,1001,'profile:view','查看个人中心','profile','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(4,1001,'plugin:management:view','查看插件管理','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(5,1001,'plugin:management:upload','上传插件','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(6,1001,'plugin:management:install','安装插件','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(7,1001,'plugin:management:upgrade','升级插件','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(8,1001,'plugin:management:rollback','回滚插件','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(9,1001,'plugin:management:enable','启用插件','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(10,1001,'plugin:management:disable','停用插件','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(11,1001,'plugin:management:logs','查看插件日志','plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(28,1001,'audit:view','查看审计中心','audit','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(29,1001,'audit:login:view','查看登录日志','audit','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(30,1001,'audit:operation:view','查看操作日志','audit','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(31,1001,'iam:view','查看权限中心','iam','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(32,1001,'system:user:view','查看用户管理','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(33,1001,'system:user:create','创建用户','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(34,1001,'system:user:update','编辑用户','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(35,1001,'system:user:status','启停用户','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(36,1001,'system:role:view','查看角色管理','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(37,1001,'system:role:create','创建角色','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(38,1001,'system:role:update','编辑角色','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(39,1001,'system:role:permissions','分配角色权限','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(40,1001,'system:menu:view','查看菜单管理','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(41,1001,'system:menu:create','创建菜单','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(42,1001,'system:menu:update','编辑菜单','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(43,1001,'system:menu:status','启停菜单','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(44,1001,'system:dict:view','查看字典管理','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(45,1001,'system:dict:create','创建字典','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(46,1001,'system:dict:update','编辑字典','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(47,1001,'system:config:view','查看参数配置','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(48,1001,'system:config:update','编辑参数配置','system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(80,1001,'system:online-user:view','查看在线用户','system','CORE',NULL,0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(81,1001,'system:online-user:kick','踢出在线会话','system','CORE',NULL,0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(82,1001,'system:online-user:ban','封禁在线用户','system','CORE',NULL,0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(86,1001,'system:monitor:view','查看系统监控','system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(87,1001,'system:monitor:service:view','查看服务监控','system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(88,1001,'system:monitor:redis:view','查看Redis监控','system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(89,1001,'system:monitor:docs:view','查看接口文档','system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(97,1001,'plugin:2fa:view','查看 2FA 验证','2fa','PLUGIN','2fa',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(98,1001,'plugin:2fa:manage','管理 2FA 验证','2fa','PLUGIN','2fa',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(101,1001,'plugin:sms:view','查看短信验证','sms','PLUGIN','sms',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(102,1001,'plugin:sms:manage','管理短信验证','sms','PLUGIN','sms',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(109,1001,'user:center:view','查看用户中心','user-center','CORE',NULL,0,'2026-04-11 12:00:32',0,'2026-04-11 12:00:32',0),(113,1001,'plugin:announcement:view','查看公告','announcement','PLUGIN','announcement',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(114,1001,'plugin:announcement:write','维护公告','announcement','PLUGIN','announcement',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(115,1001,'system:notification:view','查看站内信归档','system','CORE',NULL,0,'2026-04-14 01:30:39',0,'2026-04-23 01:00:47',0),(116,1001,'system:notification:write','手动发布站内信','system','CORE',NULL,0,'2026-04-14 01:30:39',0,'2026-04-22 23:54:15',0),(123,1001,'message:message:view','查看站内信','message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(124,1001,'message:message:write','发送站内信','message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(125,1001,'message:message:read','标记站内信已读','message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(126,1001,'message:message:retract','撤回站内信','message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(142,1001,'system:verification:view','查看验证管理','system','CORE',NULL,0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(143,1001,'system:verification:manage','管理验证方式','system','CORE',NULL,0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(146,1001,'system:file:view','查看文件中心','system','CORE',NULL,0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(147,1001,'system:file:upload','上传文档','system','CORE',NULL,0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(148,1001,'system:file:delete','删除文档','system','CORE',NULL,0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(152,1001,'system:file:manage','查看全站文件管理','system','CORE',NULL,0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(153,1001,'system:file:manage:delete','删除全站文件','system','CORE',NULL,0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(156,1001,'localization:view','查看本地化中心','system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(157,1001,'localization:create','新增翻译词条','system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(158,1001,'localization:update','编辑翻译词条','system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(159,1001,'localization:delete','删除翻译词条','system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(160,1001,'localization:sync','同步翻译词条','system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(161,1001,'localization:publish','发布翻译版本','system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(162,1001,'localization:rollback','回滚翻译版本','system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(180,1001,'ai:view','查看数字员工','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(181,1001,'ai:employee:create','创建数字员工','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(182,1001,'ai:employee:update','编辑数字员工','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(183,1001,'ai:employee:delete','删除数字员工','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(184,1001,'ai:employee:status','启停数字员工','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(185,1001,'ai:employee:skills','配置数字员工技能','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(186,1001,'ai:llm:create','创建 LLM 服务','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(187,1001,'ai:llm:update','编辑 LLM 服务','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(188,1001,'ai:llm:delete','删除 LLM 服务','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(189,1001,'ai:llm:status','启停 LLM 服务','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(190,1001,'ai:skill:view','查看技能列表','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(191,1001,'ai:chat:send','发送 AI 对话','ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(224,1001,'site:view','查看官网管理','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(226,1001,'site:settings','管理官网设置','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(228,1001,'site:navigation','管理官网导航','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(230,1001,'site:page','查看官网页面','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(232,1001,'site:page:create','新增官网页面','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(234,1001,'site:page:update','编辑官网页面','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(236,1001,'site:page:publish','发布官网页面','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(238,1001,'site:content','查看官网内容','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(240,1001,'site:content:create','新增官网内容','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(242,1001,'site:content:update','编辑官网内容','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(244,1001,'site:content:publish','发布官网内容','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(246,1001,'site:form','管理官网表单','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(248,1001,'site:submission','查看官网提交记录','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(250,1001,'site:submission:review','审核官网提交记录','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(255,1001,'site:settings:update','更新官网设置','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(257,1001,'site:navigation:create','新增官网导航','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(259,1001,'site:navigation:update','编辑官网导航','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(261,1001,'site:navigation:delete','删除官网导航','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(263,1001,'site:form:create','新增官网表单','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(265,1001,'site:form:update','编辑官网表单','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(267,1001,'site:form:delete','删除官网表单','site','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_name` varchar(128) NOT NULL,
  `plugin_type` varchar(32) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `author` varchar(128) DEFAULT NULL,
  `plugin_api_version` varchar(32) NOT NULL,
  `builtin_flag` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_definition_code` (`plugin_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `depends_on_plugin_code` varchar(64) NOT NULL,
  `min_version` varchar(32) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_dependency_rel` (`plugin_code`,`depends_on_plugin_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_menu_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `menu_code` varchar(64) NOT NULL,
  `menu_name` varchar(128) NOT NULL,
  `route_path` varchar(255) NOT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `parent_menu_code` varchar(64) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_menu_rel` (`plugin_code`,`plugin_version`,`menu_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_permission_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_group` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_permission_rel` (`plugin_code`,`plugin_version`,`permission_key`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_runtime_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) DEFAULT NULL,
  `operation_type` varchar(32) NOT NULL,
  `lifecycle_status` varchar(32) NOT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `request_id` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `failure_stack` text,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_plugin_runtime_log_plugin_created` (`plugin_code`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `config_json` json DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_tenant_rel` (`tenant_id`,`plugin_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `version` varchar(32) NOT NULL,
  `package_path` varchar(512) DEFAULT NULL,
  `artifact_path` varchar(512) DEFAULT NULL,
  `frontend_manifest_path` varchar(512) DEFAULT NULL,
  `backend_jar_path` varchar(512) DEFAULT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `signature_path` varchar(512) DEFAULT NULL,
  `min_platform_version` varchar(32) NOT NULL,
  `install_status` varchar(32) NOT NULL DEFAULT 'UPLOADED',
  `load_status` varchar(32) NOT NULL DEFAULT 'UNLOADED',
  `health_status` varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  `is_active` tinyint NOT NULL DEFAULT '0',
  `rollbackable` tinyint NOT NULL DEFAULT '0',
  `metadata_json` json DEFAULT NULL,
  `validation_report_json` json DEFAULT NULL,
  `staged_path` varchar(512) DEFAULT NULL,
  `installed_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_version_code_version` (`plugin_code`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_code` varchar(64) NOT NULL,
  `role_name` varchar(128) NOT NULL,
  `role_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`,`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2001,1001,'ADMIN','平台管理员','BUILTIN',0,'2026-03-30 14:28:54',0,'2026-03-30 14:28:54',0),(2003,1001,'commonuser','普通用户','CUSTOM',1001,'2026-04-23 02:09:41',1001,'2026-04-25 09:04:23',0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission_rel` (`tenant_id`,`role_id`,`permission_key`)
) ENGINE=InnoDB AUTO_INCREMENT=302 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1,1001,2001,'dashboard:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(2,1001,2001,'plugin:management:disable',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(3,1001,2001,'plugin:management:enable',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(4,1001,2001,'plugin:management:install',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(5,1001,2001,'plugin:management:logs',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(6,1001,2001,'plugin:management:rollback',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(7,1001,2001,'plugin:management:upgrade',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(8,1001,2001,'plugin:management:upload',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(9,1001,2001,'plugin:management:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(10,1001,2001,'profile:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(11,1001,2001,'system:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(31,1001,2001,'audit:login:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(32,1001,2001,'audit:operation:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(33,1001,2001,'audit:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(34,1001,2001,'iam:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(35,1001,2001,'system:config:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(36,1001,2001,'system:config:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(37,1001,2001,'system:dict:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(38,1001,2001,'system:dict:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(39,1001,2001,'system:dict:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(40,1001,2001,'system:menu:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(41,1001,2001,'system:menu:status',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(42,1001,2001,'system:menu:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(43,1001,2001,'system:menu:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(44,1001,2001,'system:role:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(45,1001,2001,'system:role:permissions',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(46,1001,2001,'system:role:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(47,1001,2001,'system:role:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(48,1001,2001,'system:user:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(49,1001,2001,'system:user:status',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(50,1001,2001,'system:user:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(51,1001,2001,'system:user:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(93,1001,2001,'system:online-user:ban',0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(94,1001,2001,'system:online-user:kick',0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(95,1001,2001,'system:online-user:view',0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(99,1001,2001,'system:monitor:docs:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(100,1001,2001,'system:monitor:redis:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(101,1001,2001,'system:monitor:service:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(102,1001,2001,'system:monitor:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(113,1001,2001,'plugin:2fa:view',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(114,1001,2001,'plugin:2fa:manage',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(117,1001,2001,'plugin:sms:view',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(118,1001,2001,'plugin:sms:manage',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(125,1001,2001,'user:center:view',0,'2026-04-11 12:00:32',0,'2026-04-11 12:00:32',0),(129,1001,2001,'plugin:announcement:view',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(130,1001,2001,'plugin:announcement:write',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(131,1001,2001,'system:notification:view',0,'2026-04-14 01:30:39',0,'2026-04-14 01:30:39',0),(132,1001,2001,'system:notification:write',0,'2026-04-14 01:30:39',0,'2026-04-14 01:30:39',0),(140,1001,2001,'message:message:read',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(141,1001,2001,'message:message:retract',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(142,1001,2001,'message:message:view',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(143,1001,2001,'message:message:write',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(166,1001,2001,'system:verification:manage',0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(167,1001,2001,'system:verification:view',0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(177,1001,2003,'dashboard:view',1001,'2026-04-25 09:04:22',1001,'2026-04-25 09:04:22',0),(178,1001,2003,'profile:view',1001,'2026-04-25 09:04:22',1001,'2026-04-25 09:04:22',0),(179,1001,2001,'system:file:delete',0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(180,1001,2001,'system:file:upload',0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(181,1001,2001,'system:file:view',0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(185,1001,2001,'system:file:manage',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(186,1001,2001,'system:file:manage:delete',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(189,1001,2001,'localization:view',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(190,1001,2001,'localization:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(191,1001,2001,'localization:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(192,1001,2001,'localization:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(193,1001,2001,'localization:sync',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(194,1001,2001,'localization:publish',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(195,1001,2001,'localization:rollback',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(213,1001,2001,'ai:view',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(214,1001,2001,'ai:employee:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(215,1001,2001,'ai:employee:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(216,1001,2001,'ai:employee:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(217,1001,2001,'ai:employee:status',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(218,1001,2001,'ai:employee:skills',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(219,1001,2001,'ai:llm:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(220,1001,2001,'ai:llm:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(221,1001,2001,'ai:llm:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(222,1001,2001,'ai:llm:status',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(223,1001,2001,'ai:skill:view',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(224,1001,2001,'ai:chat:send',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(257,1001,2001,'site:view',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(259,1001,2001,'site:settings',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(261,1001,2001,'site:navigation',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(263,1001,2001,'site:page',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(265,1001,2001,'site:page:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(267,1001,2001,'site:page:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(269,1001,2001,'site:page:publish',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(271,1001,2001,'site:content',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(273,1001,2001,'site:content:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(275,1001,2001,'site:content:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(277,1001,2001,'site:content:publish',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(279,1001,2001,'site:form',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(281,1001,2001,'site:submission',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(283,1001,2001,'site:submission:review',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(288,1001,2001,'site:settings:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(290,1001,2001,'site:navigation:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(292,1001,2001,'site:navigation:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(294,1001,2001,'site:navigation:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(296,1001,2001,'site:form:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(298,1001,2001,'site:form:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(300,1001,2001,'site:form:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0);
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `real_name` varchar(64) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `birth_month` varchar(16) DEFAULT NULL,
  `gender` varchar(16) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `available_time` varchar(255) DEFAULT NULL,
  `id_card_number` varchar(64) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `mobile` varchar(32) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_mobile` (`mobile`)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role_rel` (`tenant_id`,`user_id`,`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_rel` (`tenant_id`,`user_id`),
  KEY `idx_sys_user_tenant_user_status` (`user_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_tenant_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `display_name` varchar(128) NOT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `locale` varchar(32) DEFAULT 'zh-CN',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_profile` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_wechat_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `openid` varchar(128) NOT NULL,
  `unionid` varchar(128) DEFAULT NULL,
  `scope` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_wechat_openid` (`openid`),
  UNIQUE KEY `uk_sys_user_wechat_unionid` (`unionid`),
  KEY `idx_sys_user_wechat_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_verification_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_code` varchar(32) NOT NULL,
  `factor_name` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `bound` tinyint NOT NULL DEFAULT '0',
  `email_required` tinyint NOT NULL DEFAULT '0',
  `masked_contact` varchar(255) DEFAULT NULL,
  `secret_key` varchar(255) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `verified_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_binding` (`tenant_id`,`user_id`,`factor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_verification_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_code` varchar(32) NOT NULL,
  `challenge_type` varchar(16) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_flag` tinyint NOT NULL DEFAULT '0',
  `setup_secret` varchar(255) DEFAULT NULL,
  `setup_uri` varchar(512) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `code_hash` varchar(128) DEFAULT NULL,
  `masked_contact` varchar(255) DEFAULT NULL,
  `debug_code` varchar(32) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_challenge` (`challenge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
