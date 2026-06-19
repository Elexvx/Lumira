
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned NOT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
  `conversation_code` varchar(64) NOT NULL,
  `title` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `is_pinned` tinyint unsigned NOT NULL DEFAULT '0',
  `latest_message_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_code` (`tenant_id`,`conversation_code`),
  KEY `idx_ai_conversation_owner` (`tenant_id`,`owner_user_id`,`is_pinned`,`latest_message_at`,`is_deleted`),
  KEY `idx_ai_conversation_employee` (`tenant_id`,`employee_id`,`latest_message_at`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_conversation` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_conversation` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(128) NOT NULL,
  `position` varchar(128) DEFAULT NULL,
  `avatar_key` varchar(255) DEFAULT NULL,
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
  UNIQUE KEY `uk_ai_employee_username` (`tenant_id`,`username`,`is_deleted`),
  KEY `idx_ai_employee_enabled_sort` (`tenant_id`,`enabled`,`sort_order`,`id`,`is_deleted`),
  KEY `idx_ai_employee_llm` (`tenant_id`,`default_llm_service_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_employee` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_knowledge_base_rel` (`tenant_id`,`employee_id`,`knowledge_base_id`),
  KEY `idx_ai_employee_knowledge_base_employee` (`tenant_id`,`employee_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_employee_knowledge_base` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee_knowledge_base` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `skill_code` varchar(128) NOT NULL,
  `permission_mode` varchar(32) NOT NULL DEFAULT 'deny',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_skill` (`tenant_id`,`employee_id`,`skill_code`,`is_deleted`),
  KEY `idx_ai_employee_skill_code` (`tenant_id`,`skill_code`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_employee_skill` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee_skill` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `kb_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `visibility_scope` varchar(32) NOT NULL DEFAULT 'PERSONAL',
  `owner_user_id` bigint unsigned NOT NULL DEFAULT '0',
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_base_code` (`tenant_id`,`kb_code`),
  UNIQUE KEY `uk_ai_knowledge_base_owner_name` (`tenant_id`,`owner_user_id`,`name`,`is_deleted`),
  KEY `idx_ai_knowledge_base_tenant_status` (`tenant_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_owner` (`tenant_id`,`owner_user_id`,`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_knowledge_base` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_base` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_base_acl` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `subject_type` varchar(32) NOT NULL,
  `subject_id` bigint unsigned NOT NULL,
  `permission` varchar(32) NOT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_acl_subject` (`tenant_id`,`knowledge_base_id`,`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_subject` (`tenant_id`,`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_knowledge_base_acl` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_base_acl` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_chunk` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `document_id` bigint unsigned NOT NULL,
  `chunk_index` int unsigned NOT NULL,
  `content` text NOT NULL,
  `search_text` text NOT NULL,
  `token_count` int unsigned NOT NULL DEFAULT '0',
  `embedding_model` varchar(64) DEFAULT NULL,
  `embedding_dim` int unsigned NOT NULL DEFAULT '0',
  `embedding_vector_json` json DEFAULT NULL,
  `vector_indexed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_chunk_index` (`tenant_id`,`document_id`,`chunk_index`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_document` (`tenant_id`,`document_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_vector` (`tenant_id`,`knowledge_base_id`,`is_deleted`,`embedding_model`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_knowledge_chunk` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_chunk` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `file_id` bigint unsigned DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `file_size_bytes` bigint unsigned DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'INDEXING',
  `parse_error` varchar(1024) DEFAULT NULL,
  `extracted_text` longtext,
  `extracted_char_count` int unsigned NOT NULL DEFAULT '0',
  `chunk_count` int unsigned NOT NULL DEFAULT '0',
  `index_retry_count` int NOT NULL DEFAULT '0',
  `index_next_retry_at` datetime DEFAULT NULL,
  `index_last_error` varchar(512) DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_document_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_document_file` (`tenant_id`,`file_id`),
  KEY `idx_ai_knowledge_document_index_retry` (`status`,`is_deleted`,`index_next_retry_at`,`update_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_knowledge_document` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_document` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_llm_model` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `llm_service_id` bigint unsigned NOT NULL,
  `model_code` varchar(128) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_model_code` (`tenant_id`,`llm_service_id`,`model_code`,`is_deleted`),
  KEY `idx_ai_llm_model_service` (`tenant_id`,`llm_service_id`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_llm_model` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_llm_model` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_llm_service` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `provider` varchar(64) NOT NULL,
  `code` varchar(64) NOT NULL,
  `title` varchar(128) NOT NULL,
  `base_url` varchar(512) DEFAULT NULL,
  `api_key_encrypted` varchar(2048) DEFAULT NULL,
  `default_model` varchar(128) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `timeout_ms` int unsigned NOT NULL DEFAULT '60000',
  `temperature` decimal(4,2) DEFAULT NULL,
  `max_tokens` int unsigned DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_service_code` (`tenant_id`,`code`,`is_deleted`),
  KEY `idx_ai_llm_service_provider` (`tenant_id`,`provider`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_llm_service` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_llm_service` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `role` varchar(32) NOT NULL,
  `content` longtext NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation` (`tenant_id`,`conversation_id`,`create_time`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_message` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `skill_code` varchar(128) NOT NULL,
  `skill_name` varchar(128) NOT NULL,
  `category` varchar(64) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `read_only` tinyint unsigned NOT NULL DEFAULT '1',
  `need_confirm` tinyint unsigned NOT NULL DEFAULT '0',
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_code` (`skill_code`,`is_deleted`),
  KEY `idx_ai_skill_category_enabled` (`category`,`enabled`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_skill` DISABLE KEYS */;
INSERT INTO `ai_skill` VALUES (1,'system.permission.snapshot','读取当前权限上下�?,'system','返回当前登录用户、租户、角色、部门和权限集合，供 AI 判断可访问边界�?,'LOW',1,0,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(2,'system.menu.list','读取系统菜单与模块入�?,'system','按当前账号权限读取系统菜单、路由、权限键和状态，�?AI 理解平台能力地图�?,'LOW',1,0,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(3,'system.config.read','读取非敏感系统配�?,'system','按配置键读取非敏感平台配置；敏感配置会被拒绝�?,'MEDIUM',1,0,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(4,'system.user.search','检索系统用�?,'system','按关键词和状态检索当前租户用户，返回脱敏后的基础资料�?,'MEDIUM',1,0,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(5,'file.object.search','检索文件对�?,'file','按关键词、类型和状态检索文件中心对象�?,'MEDIUM',1,0,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(6,'audit.ai_call.search','检�?AI 工具审计','audit','按数字员工、技能编码和结果状态检�?AI 调用审计日志�?,'MEDIUM',1,0,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(7,'system.user.create','新增系统用户','system','在当前租户和当前账号权限范围内新增系统用户�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(8,'system.user.update','编辑系统用户','system','在当前租户和当前账号权限范围内编辑用户基础信息、角色和部门�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(9,'system.user.status','启停系统用户','system','在当前租户和当前账号权限范围内启用或禁用用户�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(10,'system.user.delete','删除系统用户','system','在当前租户和当前账号权限范围内删除用户�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(11,'profile.avatar.update','修改当前用户头像','profile','仅修改当前登录用户自己的头像�?,'MEDIUM',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(12,'system.role.create','新增角色','system','在当前租户新增角色�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(13,'system.role.update','编辑角色','system','在当前租户编辑角色基础信息�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(14,'system.role.permissions','配置角色权限','system','在当前租户更新角色权限集合�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(15,'system.role.delete','删除角色','system','在当前租户删除角色�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(16,'system.menu.create','新增菜单','system','新增当前租户自定义菜单�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(17,'system.menu.update','编辑菜单','system','编辑当前租户自定义菜单�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(18,'system.menu.status','启停菜单','system','更新当前租户菜单状态�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(19,'system.menu.delete','删除菜单','system','删除当前租户自定义菜单�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(20,'system.dict_type.create','新增字典类型','system','新增当前租户字典类型�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(21,'system.dict_type.update','编辑字典类型','system','编辑当前租户字典类型�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(22,'system.dict_type.delete','删除字典类型','system','删除当前租户非系统字典类型�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(23,'system.dict_item.create','新增字典�?,'system','新增当前租户字典项�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(24,'system.dict_item.update','编辑字典�?,'system','编辑当前租户字典项�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(25,'system.dict_item.delete','删除字典�?,'system','删除当前租户字典项�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(26,'system.config.create','新增系统配置','system','新增非敏感平台或租户配置�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(27,'system.config.update','编辑系统配置','system','编辑非敏感平台或租户配置�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(28,'platform.branding.update','更新品牌设置','system','更新网站名称、Logo、页脚等品牌设置�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(29,'platform.agreement.update','更新协议设置','system','更新用户协议与隐私协议设置�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(30,'platform.watermark.update','更新水印设置','system','更新平台水印设置�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(31,'platform.floating_window.update','更新浮窗设置','system','更新全局浮窗设置�?,'HIGH',0,1,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06');
/*!40000 ALTER TABLE `ai_skill` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_tool_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `skill_code` varchar(128) NOT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `permission_mode` varchar(32) DEFAULT NULL,
  `confirm_required` tinyint unsigned NOT NULL DEFAULT '0',
  `confirm_result` tinyint unsigned DEFAULT NULL,
  `supervisor_verdict` varchar(32) DEFAULT NULL,
  `supervisor_message` varchar(1024) DEFAULT NULL,
  `policy_match` varchar(1024) DEFAULT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `request_payload_json` longtext,
  `response_payload_json` longtext,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_audit_tenant_created` (`tenant_id`,`create_time`),
  KEY `idx_ai_tool_audit_employee` (`tenant_id`,`employee_id`,`create_time`),
  KEY `idx_ai_tool_audit_skill` (`tenant_id`,`skill_code`,`result_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_tool_audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_tool_audit_log` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_tool_call_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
  `tool_code` varchar(128) NOT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `action_type` varchar(64) DEFAULT NULL,
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `summary` varchar(1024) DEFAULT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `requires_confirm` tinyint unsigned NOT NULL DEFAULT '1',
  `supervisor_verdict` varchar(32) NOT NULL DEFAULT 'REQUIRE_CONFIRM',
  `supervisor_message` varchar(1024) DEFAULT NULL,
  `policy_verdict` varchar(32) NOT NULL DEFAULT 'ALLOW',
  `policy_message` varchar(1024) DEFAULT NULL,
  `arguments_json` longtext,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime NOT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_plan_owner` (`tenant_id`,`owner_user_id`,`status`,`expires_at`),
  KEY `idx_ai_tool_plan_conversation` (`tenant_id`,`conversation_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_tool_call_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_tool_call_plan` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_tool_policy` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `policy_name` varchar(128) NOT NULL,
  `tool_code` varchar(128) NOT NULL DEFAULT '*',
  `action_type` varchar(64) DEFAULT NULL,
  `risk_level` varchar(32) DEFAULT NULL,
  `match_type` varchar(32) NOT NULL DEFAULT 'KEYWORD',
  `match_value` varchar(512) DEFAULT NULL,
  `verdict` varchar(32) NOT NULL DEFAULT 'DENY',
  `message` varchar(1024) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_policy_tenant_enabled` (`tenant_id`,`enabled`,`is_deleted`),
  KEY `idx_ai_tool_policy_tool` (`tenant_id`,`tool_code`,`enabled`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ai_tool_policy` DISABLE KEYS */;
INSERT INTO `ai_tool_policy` VALUES (1,1001,'禁止读取或修改密钥类配置','*',NULL,NULL,'KEYWORD','password,secret,token,credential,private,api_key,密钥,密码,令牌','DENY','命中平台防护规则：敏感密钥、密码或令牌不允许由 AI 工具读取或修改�?,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(2,1001,'禁止直接执行 SQL 或脚�?,'*',NULL,NULL,'KEYWORD','drop table,truncate,delete from,update sys_,insert into,sql,脚本,命令�?shell','DENY','命中平台防护规则：AI 不允许执�?SQL、脚本或命令行�?,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(3,1001,'禁止跨租户操�?,'*',NULL,NULL,'KEYWORD','tenantId,currentTenantId,crossTenant,跨租�?,'DENY','命中平台防护规则：AI 工具只能操作当前登录租户和权限范围内的数据�?,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06'),(4,1001,'禁止修改默认管理员和自身关键状�?,'system.user.*',NULL,NULL,'KEYWORD','1001,admin,DISABLED,禁用自己,删除自己,默认管理�?,'DENY','命中平台防护规则：默认管理员和当前账号关键状态不允许通过 AI 工具修改�?,1,0,'2026-06-18 22:05:06','2026-06-18 22:05:06');
/*!40000 ALTER TABLE `ai_tool_policy` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `login_type` varchar(32) NOT NULL,
  `login_result` varchar(32) NOT NULL,
  `fail_reason` varchar(512) DEFAULT NULL,
  `login_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_login_tenant_created` (`tenant_id`,`created_at`),
  KEY `idx_audit_login_user_created` (`tenant_id`,`user_id`,`created_at`),
  KEY `idx_audit_login_result_created` (`tenant_id`,`login_result`,`created_at`),
  KEY `idx_audit_login_user_result_recent` (`tenant_id`,`user_id`,`login_result`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `audit_login_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_login_log` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `module_name` varchar(64) NOT NULL,
  `action_name` varchar(128) NOT NULL,
  `operation_type` varchar(32) NOT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operation_tenant_created` (`tenant_id`,`created_at`),
  KEY `idx_audit_operation_user_created` (`tenant_id`,`user_id`,`created_at`),
  KEY `idx_audit_operation_module_created` (`tenant_id`,`module_name`,`created_at`),
  KEY `idx_audit_operation_result_created` (`tenant_id`,`result_status`,`created_at`),
  KEY `idx_audit_operation_tenant_user_recent` (`tenant_id`,`username`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `audit_operation_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_operation_log` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ddd_read_model_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户 ID，NULL 表示全局',
  `context_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '限界上下�?,
  `scope` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '读模型范�?,
  `version` bigint NOT NULL DEFAULT '1' COMMENT '版本�?,
  `last_event_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后应用的事件幂等�?,
  `rebuilt_at` datetime DEFAULT NULL COMMENT '最后重建时�?,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ddd_read_model_version_scope` (`tenant_id`,`context_name`,`scope`),
  KEY `idx_ddd_read_model_version_context` (`context_name`,`updated_at`),
  KEY `idx_ddd_read_model_version_event_key` (`last_event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DDD 读模型版本与缓存失效元数�?;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `ddd_read_model_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `ddd_read_model_version` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_object` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `storage_type` varchar(32) NOT NULL,
  `bucket` varchar(128) DEFAULT NULL,
  `object_key` varchar(255) NOT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `uploaded_by_name` varchar(128) DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `visibility_scope` varchar(32) NOT NULL DEFAULT 'PERSONAL',
  `original_filename` varchar(255) NOT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `content_type` varchar(128) DEFAULT NULL,
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
  UNIQUE KEY `uk_file_object_key` (`tenant_id`,`object_key`),
  KEY `idx_file_object_department` (`tenant_id`,`department_id`,`deleted`),
  KEY `idx_file_object_visibility` (`tenant_id`,`visibility_scope`,`deleted`),
  KEY `idx_file_object_tenant_deleted_bucket` (`tenant_id`,`deleted`,`bucket`),
  KEY `idx_file_object_tenant_deleted_created_id` (`tenant_id`,`deleted`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `file_object` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_object` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_processing_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `file_id` bigint NOT NULL,
  `task_type` varchar(64) NOT NULL,
  `artifact_type` varchar(64) NOT NULL,
  `artifact_path` varchar(512) DEFAULT NULL,
  `content_text` mediumtext,
  `content_length` int NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_processing_artifact` (`tenant_id`,`file_id`,`artifact_type`),
  KEY `idx_file_processing_artifact_file` (`tenant_id`,`file_id`,`deleted`),
  KEY `idx_file_processing_artifact_type` (`tenant_id`,`artifact_type`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `file_processing_artifact` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_processing_artifact` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_processing_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `file_id` bigint NOT NULL,
  `task_type` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `priority` int NOT NULL DEFAULT '0',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `claimed_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_processing_task_file_type` (`tenant_id`,`file_id`,`task_type`),
  KEY `idx_file_processing_task_status_retry` (`status`,`next_retry_at`,`priority`,`created_at`),
  KEY `idx_file_processing_task_file` (`tenant_id`,`file_id`,`deleted`),
  KEY `idx_file_processing_task_queue` (`deleted`,`status`,`next_retry_at`,`priority`,`created_at`,`id`),
  KEY `idx_file_processing_task_tenant_created` (`tenant_id`,`deleted`,`status`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `file_processing_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_processing_task` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_storage_space` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `storage_key` varchar(64) NOT NULL,
  `provider` varchar(32) NOT NULL,
  `root_path` varchar(255) DEFAULT NULL,
  `bucket_name` varchar(128) DEFAULT NULL,
  `endpoint` varchar(255) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `access_key_id` varchar(255) DEFAULT NULL,
  `access_key_secret` varchar(2048) DEFAULT NULL,
  `rename_strategy` varchar(32) NOT NULL DEFAULT 'APPEND_RANDOM_ID',
  `max_file_size_mb` int NOT NULL DEFAULT '20',
  `allowed_mime_types` varchar(1024) NOT NULL DEFAULT '*',
  `default_flag` tinyint NOT NULL DEFAULT '0',
  `retain_file_on_record_delete` tinyint NOT NULL DEFAULT '0',
  `anonymous_access_allowed` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_storage_space_key` (`tenant_id`,`storage_key`),
  KEY `idx_file_storage_space_default` (`tenant_id`,`default_flag`,`deleted`),
  KEY `idx_file_storage_space_tenant_deleted_default_id` (`tenant_id`,`deleted`,`default_flag`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `file_storage_space` DISABLE KEYS */;
INSERT INTO `file_storage_space` VALUES (1,1001,'Local storage','local','LOCAL','storage/uploads/',NULL,NULL,NULL,NULL,NULL,'APPEND_RANDOM_ID',20,'*',1,0,0,'ENABLED',1,'2026-06-18 22:05:05',1,'2026-06-18 22:05:05',0),(2,1001,'AI 聊天附件','ai_chat','LOCAL','storage/uploads/ai_chat/',NULL,NULL,NULL,NULL,NULL,'APPEND_RANDOM_ID',20,'*',0,0,0,'ENABLED',1,'2026-06-18 22:05:05',1,'2026-06-18 22:05:05',0),(3,1001,'AI 知识库文�?,'ai_knowledge','LOCAL','storage/uploads/ai_knowledge/',NULL,NULL,NULL,NULL,NULL,'APPEND_RANDOM_ID',50,'*',0,0,0,'ENABLED',1,'2026-06-18 22:05:05',1,'2026-06-18 22:05:05',0),(4,1001,'下载中心文件','download_center','LOCAL','storage/uploads/download_center/',NULL,NULL,NULL,NULL,NULL,'APPEND_RANDOM_ID',100,'*',0,0,0,'ENABLED',1,'2026-06-18 22:05:05',1,'2026-06-18 22:05:05',0);
/*!40000 ALTER TABLE `file_storage_space` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','baseline','SQL','V1__baseline.sql',-1658795399,'root','2026-06-18 14:05:07',3227,1),(2,'2','cleanup unused schema','SQL','V2__cleanup_unused_schema.sql',-1828826651,'root','2026-06-18 14:05:07',12,1),(3,'3','role default home path','SQL','V3__role_default_home_path.sql',2097426457,'root','2026-06-18 14:05:07',6,1),(4,'4','grant payment view to default admin','SQL','V4__grant_payment_view_to_default_admin.sql',-187984587,'root','2026-06-18 14:05:07',4,1),(5,'5','baseline payment domain','SQL','V5__baseline_payment_domain.sql',663347399,'root','2026-06-18 14:05:07',26,1),(6,'6','grant payment config permissions to default admin','SQL','V6__grant_payment_config_permissions_to_default_admin.sql',1464343511,'root','2026-06-18 14:05:07',3,1),(7,'7','expand encrypted secret columns','SQL','V7__expand_encrypted_secret_columns.sql',-2043559453,'root','2026-06-18 14:05:07',38,1),(8,'8','seed download center','SQL','V8__seed_download_center.sql',-1901264400,'root','2026-06-18 14:05:07',9,1),(9,'9','download center action permissions','SQL','V9__download_center_action_permissions.sql',-297547728,'root','2026-06-18 14:05:07',8,1),(10,'10','sensitive words plugin','SQL','V10__sensitive_words_plugin.sql',-760528941,'root','2026-06-18 14:05:07',54,1),(11,'11','seed ai knowledge storage space','SQL','V11__seed_ai_knowledge_storage_space.sql',-759221837,'root','2026-06-18 14:05:07',4,1),(12,'12','performance hot path indexes','SQL','V12__performance_hot_path_indexes.sql',-89442455,'root','2026-06-18 14:05:08',219,1),(13,'13','ddd read model versions','SQL','V13__ddd_read_model_versions.sql',170983370,'root','2026-06-18 14:05:08',43,1),(14,'14','plugin event outbox','SQL','V14__plugin_event_outbox.sql',-453392564,'root','2026-06-18 14:05:08',45,1),(15,'15','ai knowledge index retry governance','SQL','V15__ai_knowledge_index_retry_governance.sql',-1262285339,'root','2026-06-18 14:05:08',181,1),(16,'16','ai knowledge chunk vector projection','SQL','V16__ai_knowledge_chunk_vector_projection.sql',-956927499,'root','2026-06-18 14:05:08',105,1),(17,'17','iam tenant v2 permissions','SQL','V17__iam_tenant_v2_permissions.sql',-1886514804,'root','2026-06-18 14:05:08',6,1),(18,'18','file processing runtime tables','SQL','V18__file_processing_runtime_tables.sql',-1280098032,'root','2026-06-18 14:05:08',90,1),(19,'19','platform event outbox owner queue index','SQL','V19__platform_event_outbox_owner_queue_index.sql',1358864838,'root','2026-06-18 14:05:08',24,1),(20,'20','message visible scope index','SQL','V20__message_visible_scope_index.sql',1114914321,'root','2026-06-18 14:05:08',39,1),(21,'21','message target scope indexes','SQL','V21__message_target_scope_indexes.sql',-506034575,'root','2026-06-18 14:05:08',81,1),(22,'22','iam permission hot path indexes','SQL','V22__iam_permission_hot_path_indexes.sql',1394161225,'root','2026-06-18 14:05:08',77,1),(23,'23','baseline auth domain','SQL','auth/V23__baseline_auth_domain.sql',371932663,'root','2026-06-18 14:05:08',19,1),(24,'24','baseline localization domain','SQL','localization/V24__baseline_localization_domain.sql',1763240415,'root','2026-06-18 14:05:08',36,1),(25,'25','localization hot path indexes','SQL','localization/V25__localization_hot_path_indexes.sql',-1068491604,'root','2026-06-18 14:05:09',65,1),(26,'49','aggregate file hot path indexes','SQL','V49__aggregate_file_hot_path_indexes.sql',1222922662,'root','2026-06-18 14:05:09',137,1),(27,'50','platform update tasks','SQL','V50__platform_update_tasks.sql',1794751747,'root','2026-06-18 14:08:49',125,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_user` (
  `id` bigint NOT NULL,
  `user_no` varchar(64) NOT NULL,
  `display_name` varchar(128) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `user_type` varchar(32) NOT NULL DEFAULT 'REGISTERED',
  `source` varchar(64) NOT NULL DEFAULT 'LEGACY_SYS_USER',
  `registered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_user_no` (`user_no`),
  KEY `idx_iam_user_status_created` (`status`,`created_at`,`id`,`deleted`),
  KEY `idx_iam_user_source_created` (`source`,`created_at`,`id`,`deleted`),
  KEY `idx_iam_user_last_login` (`last_login_at`,`id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `iam_user` DISABLE KEYS */;
INSERT INTO `iam_user` VALUES (1001,'admin','平台管理�?,NULL,'ENABLED','ADMIN','LEGACY_SYS_USER','2026-06-18 22:05:06',NULL,'2026-06-18 22:05:06','2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `iam_user` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_user_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `credential_type` varchar(32) NOT NULL,
  `credential_secret` varchar(512) NOT NULL,
  `algorithm` varchar(64) NOT NULL DEFAULT 'BCRYPT',
  `version` int NOT NULL DEFAULT '1',
  `expire_at` datetime DEFAULT NULL,
  `last_changed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_credential_user_type` (`user_id`,`credential_type`,`version`),
  KEY `idx_iam_credential_type_status` (`credential_type`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `iam_user_credential` DISABLE KEYS */;
INSERT INTO `iam_user_credential` VALUES (1,1001,'PASSWORD','$2a$10$OoeukQEfBNqpig.E0ZnA.e3wWxfEYg.WdWXPN5in.AfiH3BQTzHDu','BCRYPT',1,NULL,'2026-06-18 22:05:06','ENABLED','2026-06-18 22:05:06','2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `iam_user_credential` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_user_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `device_id` varchar(128) NOT NULL,
  `device_name` varchar(128) DEFAULT NULL,
  `device_type` varchar(32) DEFAULT NULL,
  `os` varchar(64) DEFAULT NULL,
  `browser` varchar(64) DEFAULT NULL,
  `last_ip` varchar(64) DEFAULT NULL,
  `last_active_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trusted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_device_user_device` (`user_id`,`device_id`),
  KEY `idx_iam_device_user_active` (`user_id`,`last_active_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `iam_user_device` DISABLE KEYS */;
/*!40000 ALTER TABLE `iam_user_device` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_user_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_source` varchar(64) NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_iam_event_user_created` (`user_id`,`created_at`),
  KEY `idx_iam_event_type_created` (`event_type`,`created_at`),
  KEY `idx_iam_event_ip_created` (`ip`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `iam_user_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `iam_user_event` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_user_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `identity_type` varchar(32) NOT NULL,
  `identifier` varchar(255) NOT NULL,
  `identifier_normalized` varchar(255) NOT NULL,
  `verified` tinyint NOT NULL DEFAULT '0',
  `primary_identity` tinyint NOT NULL DEFAULT '0',
  `bound_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used_at` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity` (`identity_type`,`identifier_normalized`),
  KEY `idx_iam_identity_user` (`user_id`,`identity_type`,`deleted`),
  KEY `idx_iam_identity_last_used` (`last_used_at`,`id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `iam_user_identity` DISABLE KEYS */;
INSERT INTO `iam_user_identity` VALUES (1,1001,'USERNAME','admin','admin',1,1,'2026-06-18 22:05:06',NULL,'ENABLED','2026-06-18 22:05:06','2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `iam_user_identity` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `nickname` varchar(128) DEFAULT NULL,
  `real_name` varchar(128) DEFAULT NULL,
  `gender` varchar(32) DEFAULT NULL,
  `birth_month` varchar(16) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `locale` varchar(32) DEFAULT NULL,
  `timezone` varchar(64) DEFAULT NULL,
  `bio` varchar(1000) DEFAULT NULL,
  `extra_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_profile_user` (`user_id`),
  KEY `idx_iam_profile_real_name` (`real_name`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `iam_user_profile` DISABLE KEYS */;
INSERT INTO `iam_user_profile` VALUES (1,1001,'平台管理�?,'平台管理�?,NULL,NULL,NULL,'zh-CN',NULL,NULL,NULL,'2026-06-18 22:05:06','2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `iam_user_profile` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_user_security_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `mfa_enabled` tinyint NOT NULL DEFAULT '0',
  `password_login_enabled` tinyint NOT NULL DEFAULT '1',
  `sms_login_enabled` tinyint NOT NULL DEFAULT '1',
  `email_login_enabled` tinyint NOT NULL DEFAULT '1',
  `passkey_enabled` tinyint NOT NULL DEFAULT '0',
  `login_notify_enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_security_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `iam_user_security_setting` DISABLE KEYS */;
INSERT INTO `iam_user_security_setting` VALUES (1,1001,0,1,0,0,1,1,'2026-06-18 22:05:06','2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `iam_user_security_setting` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `msg_delivery_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_id` bigint DEFAULT NULL,
  `channel` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_user_name` varchar(64) DEFAULT NULL,
  `target_email` varchar(128) DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `send_status` varchar(32) NOT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_delivery_log_tenant_channel_created` (`tenant_id`,`channel`,`created_at`),
  KEY `idx_msg_delivery_log_tenant_status_created` (`tenant_id`,`send_status`,`created_at`),
  KEY `idx_msg_delivery_log_notice` (`tenant_id`,`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `msg_delivery_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `msg_delivery_log` ENABLE KEYS */;
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
  KEY `idx_msg_notice_tenant_target_role_created` (`tenant_id`,`target_role_id`,`created_at`),
  KEY `idx_msg_notice_visible_recent` (`tenant_id`,`publish_status`,`deleted`,`target_scope`,`id`),
  KEY `idx_msg_notice_visible_target_user_recent` (`tenant_id`,`publish_status`,`deleted`,`target_scope`,`target_user_id`,`id`),
  KEY `idx_msg_notice_visible_target_role_recent` (`tenant_id`,`publish_status`,`deleted`,`target_scope`,`target_role_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `msg_notice` DISABLE KEYS */;
/*!40000 ALTER TABLE `msg_notice` ENABLE KEYS */;
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

/*!40000 ALTER TABLE `msg_notice_read` DISABLE KEYS */;
/*!40000 ALTER TABLE `msg_notice_read` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `source_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_outbox_event` (`tenant_id`,`source_type`,`event_type`,`event_key`),
  KEY `idx_payment_outbox_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_payment_outbox_created_at` (`tenant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `payment_event_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_event_outbox` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_order_no` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `client_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notify_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_json` longtext COLLATE utf8mb4_unicode_ci,
  `response_json` longtext COLLATE utf8mb4_unicode_ci,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_order_tenant_order_no` (`tenant_id`,`order_no`),
  UNIQUE KEY `uk_payment_order_tenant_idempotency_key` (`tenant_id`,`idempotency_key`),
  KEY `idx_payment_order_tenant_status` (`tenant_id`,`status`),
  KEY `idx_payment_order_provider` (`tenant_id`,`provider_code`,`provider_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `payment_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_order` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `provider_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `environment` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_config_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `configured` tinyint(1) NOT NULL DEFAULT '0',
  `last_tested_at` datetime DEFAULT NULL,
  `last_test_success` tinyint(1) DEFAULT NULL,
  `last_test_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_provider_config_tenant_provider` (`tenant_id`,`provider_code`),
  KEY `idx_payment_provider_config_tenant_deleted` (`tenant_id`,`deleted`),
  KEY `idx_payment_provider_config_provider` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `payment_provider_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_provider_config` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `refund_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_refund_no` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_json` longtext COLLATE utf8mb4_unicode_ci,
  `response_json` longtext COLLATE utf8mb4_unicode_ci,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_refund_tenant_refund_no` (`tenant_id`,`refund_no`),
  UNIQUE KEY `uk_payment_refund_tenant_idempotency_key` (`tenant_id`,`idempotency_key`),
  KEY `idx_payment_refund_tenant_status` (`tenant_id`,`status`),
  KEY `idx_payment_refund_order_no` (`tenant_id`,`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `payment_refund` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_refund` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_webhook_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `provider_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_timestamp` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `signature` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `signature_valid` tinyint(1) NOT NULL DEFAULT '0',
  `processed` tinyint(1) NOT NULL DEFAULT '0',
  `process_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `received_at` datetime NOT NULL,
  `processed_at` datetime DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_webhook_event_tenant_provider_event` (`tenant_id`,`provider_code`,`event_id`),
  KEY `idx_payment_webhook_event_nonce` (`tenant_id`,`provider_code`,`nonce`),
  KEY `idx_payment_webhook_event_status` (`tenant_id`,`processed`,`retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `payment_webhook_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_webhook_event` ENABLE KEYS */;
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
  KEY `idx_platform_event_outbox_event_key` (`event_key`),
  KEY `idx_platform_event_outbox_owner_queue` (`source_type`,`created_at`,`id`,`dispatch_status`,`next_retry_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `platform_event_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `platform_event_outbox` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_update_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `target_version` varchar(64) DEFAULT NULL,
  `target_commit` varchar(64) DEFAULT NULL,
  `server_image` varchar(255) DEFAULT NULL,
  `frontend_image` varchar(255) DEFAULT NULL,
  `updater_task_id` varchar(64) DEFAULT NULL,
  `backup_path` varchar(512) DEFAULT NULL,
  `log_summary` text,
  `error_message` text,
  `created_by` bigint DEFAULT NULL,
  `created_by_name` varchar(128) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_platform_update_task_created_at` (`created_at`),
  KEY `idx_platform_update_task_status` (`status`),
  KEY `idx_platform_update_task_updater_task_id` (`updater_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `platform_update_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `platform_update_task` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plugin_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `event_type` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_key` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_event_outbox_event` (`tenant_id`,`event_type`,`event_key`),
  KEY `idx_plugin_event_outbox_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_plugin_event_outbox_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `plugin_event_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `plugin_event_outbox` ENABLE KEYS */;
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
  UNIQUE KEY `uk_sys_config_key` (`tenant_id`,`config_key`),
  KEY `idx_sys_config_scope_key_tenant_deleted` (`config_scope`,`config_key`,`tenant_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=7410 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (7001,1001,'platform.name','平台名称','SaaS Foundation','PLATFORM',1,'平台展示名称',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(7003,1001,'security.idle-timeout-seconds','空闲超时时间','1800','PLATFORM',1,'会话在无操作状态下允许保持的秒�?,0,'2026-03-30 17:42:45',0,'2026-04-15 01:20:52',0),(7004,1001,'security.access-token-expire-seconds','Access Token 过期时间','1800','PLATFORM',1,'Access Token 的有效秒�?,0,'2026-03-30 17:42:45',0,'2026-03-30 17:42:45',0),(7005,1001,'security.refresh-token-expire-seconds','Refresh Token 刷新时限','604800','PLATFORM',1,'Refresh Token 的有效秒�?,0,'2026-03-30 17:42:45',0,'2026-03-30 17:42:45',0),(7006,1001,'branding.website-name','站点名称','宏翔商道','PLATFORM',0,'控制台顶部与浏览器标题展示名�?,0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7007,1001,'branding.website-favicon-url','站点图标地址','','PLATFORM',0,'浏览器标签页 icon 地址',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7008,1001,'branding.website-logo-url','站点 Logo 地址','','PLATFORM',0,'控制台左上角品牌 Logo 地址',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7009,1001,'branding.footer-icp','页脚 ICP 备案','','PLATFORM',0,'页脚备案信息',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7010,1001,'branding.footer-copyright','页脚版权声明','','PLATFORM',0,'页脚版权声明',0,'2026-04-03 16:25:38',1001,'2026-04-06 04:47:43',0),(7011,1001,'branding.footer-police-beian','页脚公安备案','','PLATFORM',0,'页脚公安备案信息',0,'2026-06-12 00:00:00',1001,'2026-06-12 00:00:00',0),(7013,1001,'agreement.user-agreement-markdown','用户协议','欢迎使用宏翔商道后台管理系统。\n\n在使用本系统前，请仔细阅读并理解以下内容：\n\n1. 您在登录、访问和使用本系统相关功能时，应遵守国家法律法规以及平台规则。\n2. 您应妥善保管账号、密码及相关身份信息，不得将账号转借、共享或提供给无关第三方。\n3. 平台可能会在提供服务所必需的范围内处理您的账号、日志与业务数据。\n4. 如您不同意本协议内容，请停止使用本系统。\n\n本协议自发布或更新之日起生效�?,'PLATFORM',0,'用户协议 Markdown',0,'2026-04-07 04:32:25',1001,'2026-05-04 13:44:05',0),(7014,1001,'agreement.privacy-agreement-markdown','隐私协议','我们重视并保护您的个人信息。\n\n在提供服务所必需的范围内，我们可能会收集、使用、存储和传输您的账号信息、操作日志和业务数据。\n\n我们不会在未经授权的情况下向无关第三方披露您的个人信息，除非法律法规或监管要求另有规定。\n\n如您对隐私保护有任何疑问，请联系系统管理员�?,'PLATFORM',0,'隐私协议 Markdown',0,'2026-04-07 04:32:25',1001,'2026-05-04 13:44:05',0),(7111,1001,'watermark.enabled','水印开�?,'false','PLATFORM',0,'全局水印开�?,1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7112,1001,'watermark.mode','水印模式','TEXT','PLATFORM',0,'TEXT/IMAGE',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7113,1001,'watermark.text-lines','水印文本','宏翔商道\n后台管理系统','PLATFORM',0,'多行文本水印',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7114,1001,'watermark.image-url','水印图片','','PLATFORM',0,'图片水印 URL',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7115,1001,'watermark.font-color','字体颜色','rgba(0,0,0,0.15)','PLATFORM',0,'字体颜色',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7116,1001,'watermark.font-size','字体大小','14','PLATFORM',0,'字体大小',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7117,1001,'watermark.font-weight','字体粗细','normal','PLATFORM',0,'字体粗细',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7118,1001,'watermark.rotate','旋转角度','-22','PLATFORM',0,'旋转角度',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7119,1001,'watermark.gap-x','横向间距','300','PLATFORM',0,'横向间距',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7120,1001,'watermark.gap-y','纵向间距','200','PLATFORM',0,'纵向间距',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7121,1001,'watermark.offset-x','横向偏移','0','PLATFORM',0,'横向偏移',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7122,1001,'watermark.offset-y','纵向偏移','0','PLATFORM',0,'纵向偏移',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7123,1001,'watermark.z-index','层级','9','PLATFORM',0,'z-index',1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7124,1001,'watermark.opacity','透明�?,'0.15','PLATFORM',0,'透明�?,1001,'2026-04-05 17:14:57',1001,'2026-05-04 01:26:17',0),(7137,1001,'security.allow-multi-device-login','多设备登�?,'0','PLATFORM',1,'是否允许同一账号在多个设备同时在�?,0,'2026-04-06 23:53:20',0,'2026-04-06 23:53:20',0),(7201,1001,'branding.company-name','公司名称','宏翔商道','PLATFORM',0,'页脚版权主体名称',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7202,1001,'branding.copyright-start-year','版权起始年份','2025','PLATFORM',0,'页脚版权起始年份',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7205,1001,'branding.github-link-url','GitHub 链接','https://github.com/Elexvx/lumira','PLATFORM',0,'顶部 GitHub 图标跳转地址',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7206,1001,'branding.help-link-url','帮助链接','https://github.com/Elexvx/lumira/blob/main/README.md','PLATFORM',0,'顶部帮助图标跳转地址',0,'2026-04-07 04:26:31',0,'2026-04-07 04:26:31',0),(7213,1001,'security.captcha-enabled','验证码开�?,'0','PLATFORM',1,'是否开启登录时的人机验证码',0,'2026-04-07 05:42:47',0,'2026-05-04 01:48:47',0),(7214,1001,'security.captcha-type','验证码类�?,'IMAGE','PLATFORM',1,'验证码类型：IMAGE=图片验证�?,0,'2026-04-07 05:42:47',0,'2026-04-13 17:18:29',0),(7215,1001,'security.login-defense-window-minutes','登录防御统计窗口','5','PLATFORM',1,'统计登录尝试与错误次数的时间窗口（分钟）',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7216,1001,'security.login-max-validation-attempts','最大验证次�?,'100','PLATFORM',1,'统计窗口内允许的最大验证码/登录验证尝试次数',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7217,1001,'security.login-max-failure-count','最大错误次�?,'10','PLATFORM',1,'统计窗口内允许的最大登录失败次�?,0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7218,1001,'security.password-min-length','密码最短长�?,'6','PLATFORM',1,'用户密码允许的最少字符数',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7219,1001,'security.password-require-uppercase','密码必须包含大写字母','0','PLATFORM',1,'强制密码包含 A-Z',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7220,1001,'security.password-require-lowercase','密码必须包含小写字母','0','PLATFORM',1,'强制密码包含 a-z',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7221,1001,'security.password-require-special-character','密码必须包含特殊字符','0','PLATFORM',1,'强制密码包含特殊字符',0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7222,1001,'security.password-allow-consecutive-characters','允许连续字符','1','PLATFORM',1,'是否允许密码中出现连续字�?,0,'2026-04-07 05:42:47',0,'2026-04-07 05:42:47',0),(7265,1001,'profile.field.avatar.visible','头像展示开�?,'true','PLATFORM',0,'控制个人中心是否展示头像上传与预览区�?,1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7266,1001,'profile.field.real-name.visible','姓名展示开�?,'true','PLATFORM',0,'控制个人中心是否展示姓名字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7267,1001,'profile.field.mobile.visible','手机号展示开�?,'true','PLATFORM',0,'控制个人中心是否展示手机号字�?,1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7268,1001,'profile.field.email.visible','邮箱展示开�?,'true','PLATFORM',0,'控制个人中心是否展示邮箱字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7269,1001,'profile.field.birth-month.visible','出生年月展示开�?,'true','PLATFORM',0,'控制个人中心是否展示出生年月字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7270,1001,'profile.field.gender.visible','性别展示开�?,'true','PLATFORM',0,'控制个人中心是否展示性别字段',1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7271,1001,'profile.field.region.visible','所在地区展示开�?,'true','PLATFORM',0,'控制个人中心是否展示所在地区字�?,1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7272,1001,'profile.field.available-time.visible','可工作时间展示开�?,'true','PLATFORM',0,'控制个人中心是否展示可工作时间字�?,1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7273,1001,'profile.field.id-card-number.visible','身份证号码展示开�?,'true','PLATFORM',0,'控制个人中心是否展示身份证号码字�?,1001,'2026-04-11 15:57:13',1001,'2026-04-11 20:32:06',0),(7372,1001,'verification.totp.enabled','2FA 启用','true','PLATFORM',0,'是否启用 2FA 登录方式',1001,'2026-04-25 09:24:01',1001,'2026-05-04 01:31:01',0),(7373,1001,'verification.email-login.enabled','邮箱验证码登�?,'false','PLATFORM',0,'是否启用邮箱验证码登�?,1001,'2026-04-25 09:24:01',1001,'2026-05-04 01:31:01',0),(7374,1001,'branding.github-link-enabled','GitHub 链接开�?,'true','PLATFORM',0,'是否显示顶部 GitHub 图标',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(7377,1001,'branding.help-link-enabled','帮助链接开�?,'true','PLATFORM',0,'是否显示顶部帮助图标',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(7380,1001,'verification.wechat-login.enabled','微信登录启用','false','PLATFORM',0,'是否启用微信扫码登录',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7381,1001,'verification.wechat-login.app-id','微信 AppID','','PLATFORM',0,'微信开放平台网站应�?AppID',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7382,1001,'verification.wechat-login.app-secret','微信 AppSecret','','PLATFORM',0,'微信开放平台网站应�?AppSecret',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7383,1001,'verification.wechat-login.redirect-uri','微信登录回调地址','','PLATFORM',0,'微信开放平台授权回调地址',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7384,1001,'verification.wechat-login.state-expire-minutes','微信登录状态有效期','10','PLATFORM',0,'微信登录 state 缓存有效期，单位分钟',1,'2026-05-15 00:52:27',1,'2026-05-15 00:52:27',0),(7385,1001,'verification.passkey.enabled','通行密钥启用','false','PLATFORM',0,'是否启用通行密钥登录',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7386,1001,'verification.passkey.passwordless-enabled','通行密钥无账号登�?,'false','PLATFORM',0,'是否允许发现式凭据无账号登录',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7387,1001,'verification.passkey.self-binding-enabled','通行密钥自助绑定','false','PLATFORM',0,'是否允许用户在个人中心自助绑定通行密钥',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7388,1001,'verification.passkey.rp-id','通行密钥 RP ID','','PLATFORM',0,'WebAuthn RP ID',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7389,1001,'verification.passkey.rp-name','通行密钥 RP 名称','','PLATFORM',0,'WebAuthn RP 显示名称',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7390,1001,'verification.passkey.allowed-origins','通行密钥允许 Origin','','PLATFORM',0,'WebAuthn 允许的前�?Origin',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7391,1001,'verification.passkey.challenge-ttl-seconds','通行密钥 Challenge TTL','120','PLATFORM',0,'WebAuthn challenge 有效期秒�?,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7392,1001,'verification.password-login.enabled','密码登录','true','PLATFORM',0,'是否启用账号密码登录',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7393,1001,'verification.login-mode.order','登录方式排序','password','PLATFORM',0,'登录页分段控制器展示顺序',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(7394,1001,'floating-window.api-docs-qr-enabled','接口文档二维码开�?,'true','PLATFORM',0,'是否在全局悬浮窗展示接口文档二维码入口',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7395,1001,'floating-window.api-docs-qr-title','接口文档二维码标�?,'微信扫码联系我们','PLATFORM',0,'接口文档二维码弹层标�?,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7396,1001,'floating-window.api-docs-qr-image-url','接口文档二维码图�?,'','PLATFORM',0,'接口文档悬浮入口展开后展示的二维码图�?,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(7397,1001,'notification.wechat-official.enabled','微信公众号通知启用','false','PLATFORM',0,'是否启用微信公众�?服务号模板消息通知',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(7398,1001,'notification.wechat-official.app-id','微信公众�?AppID','','PLATFORM',0,'微信公众号或服务�?AppID',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(7399,1001,'notification.wechat-official.app-secret','微信公众�?AppSecret','','PLATFORM',0,'微信公众号或服务�?AppSecret',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(7400,1001,'notification.wechat-official.template-id','微信公众号模�?ID','','PLATFORM',0,'用于系统通知的公众号模板消息 ID',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(7401,1001,'notification.wechat-official.detail-url','微信公众号通知详情链接','','PLATFORM',0,'模板消息点击后打开的系统链接，可留�?,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(7402,1001,'security.verification-code-expire-seconds','验证码有效期','300','PLATFORM',1,'短信/邮箱验证码的有效秒数',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(7403,1001,'security.verification-code-cooldown-seconds','验证码发送倒计�?,'60','PLATFORM',1,'同一账号同一验证码渠道再次发送前需要等待的秒数',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0);
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `dept_code` varchar(64) NOT NULL,
  `dept_name` varchar(128) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_department_code` (`tenant_id`,`dept_code`),
  KEY `idx_sys_department_parent` (`tenant_id`,`parent_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_department` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_department` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=7171 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_dict_item` DISABLE KEYS */;
INSERT INTO `sys_dict_item` VALUES (6001,1001,5001,'ENABLED__deleted_6001','启用',1,0,'2026-03-29 20:37:31',0,'2026-06-18 22:05:07',1,'ENABLED',NULL),(6002,1001,5001,'DISABLED__deleted_6002','停用',2,0,'2026-03-29 20:37:31',0,'2026-06-18 22:05:07',1,'ENABLED',NULL),(6003,1001,5002,'SYSTEM__deleted_6003','系统角色',1,0,'2026-03-29 20:37:31',0,'2026-06-18 22:05:07',1,'ENABLED',NULL),(6004,1001,5002,'CUSTOM__deleted_6004','自定义角�?,2,0,'2026-03-29 20:37:31',0,'2026-06-18 22:05:07',1,'ENABLED',NULL),(6005,1001,5003,'MALE','�?,1,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','男�?),(6006,1001,5003,'FEMALE','�?,2,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','女�?),(6007,1001,5003,'OTHER','其他',3,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','其他或不便透露'),(6008,1001,5004,'BEIJING','北京�?,1,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','直辖�?),(6009,1001,5004,'SHANGHAI','上海�?,2,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','直辖�?),(6010,1001,5004,'TIANJIN','天津�?,3,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','直辖�?),(6011,1001,5004,'CHONGQING','重庆�?,4,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','直辖�?),(6012,1001,5004,'HEBEI','河北�?,5,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6013,1001,5004,'SHANXI','山西�?,6,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6014,1001,5004,'LIAONING','辽宁�?,7,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6015,1001,5004,'JILIN','吉林�?,8,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6016,1001,5004,'HEILONGJIANG','黑龙江省',9,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6017,1001,5004,'JIANGSU','江苏�?,10,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6018,1001,5004,'ZHEJIANG','浙江�?,11,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6019,1001,5004,'ANHUI','安徽�?,12,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6020,1001,5004,'FUJIAN','福建�?,13,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6021,1001,5004,'JIANGXI','江西�?,14,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6022,1001,5004,'SHANDONG','山东�?,15,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6023,1001,5004,'HENAN','河南�?,16,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6024,1001,5004,'HUBEI','湖北�?,17,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6025,1001,5004,'HUNAN','湖南�?,18,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6026,1001,5004,'GUANGDONG','广东�?,19,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6027,1001,5004,'HAINAN','海南�?,20,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6028,1001,5004,'SICHUAN','四川�?,21,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6029,1001,5004,'GUIZHOU','贵州�?,22,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6030,1001,5004,'YUNNAN','云南�?,23,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6031,1001,5004,'SHAANXI','陕西�?,24,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6032,1001,5004,'GANSU','甘肃�?,25,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6033,1001,5004,'QINGHAI','青海�?,26,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6034,1001,5004,'TAIWAN','台湾�?,27,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','�?),(6035,1001,5004,'NEIMENGGU','内蒙古自治区',28,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','自治�?),(6036,1001,5004,'GUANGXI','广西壮族自治�?,29,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','自治�?),(6037,1001,5004,'XIZANG','西藏自治�?,30,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','自治�?),(6038,1001,5004,'NINGXIA','宁夏回族自治�?,31,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','自治�?),(6039,1001,5004,'XINJIANG','新疆维吾尔自治区',32,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','自治�?),(6040,1001,5004,'HONGKONG','香港特别行政�?,33,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','特别行政�?),(6041,1001,5004,'MACAO','澳门特别行政�?,34,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','特别行政�?),(6071,1001,5005,'BEIJING','北京�?,1,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','北京'),(6072,1001,5005,'SHANGHAI','上海�?,2,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','上海'),(6073,1001,5005,'GUANGZHOU','广州�?,3,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','广东�?),(6074,1001,5005,'SHENZHEN','深圳�?,4,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','广东�?),(6075,1001,5005,'HANGZHOU','杭州�?,5,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','浙江�?),(6076,1001,5005,'NANJING','南京�?,6,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','江苏�?),(6077,1001,5005,'CHENGDU','成都�?,7,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','四川�?),(6078,1001,5005,'WUHAN','武汉�?,8,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','湖北�?),(6079,1001,5005,'XIAN','西安�?,9,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','陕西�?),(6080,1001,5005,'TIANJIN','天津�?,10,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','天津'),(6081,1001,5005,'CHONGQING','重庆�?,11,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','重庆'),(6086,1001,5006,'CHAOYANG','朝阳�?,1,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','北京�?),(6087,1001,5006,'HAIDIAN','海淀�?,2,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','北京�?),(6088,1001,5006,'PUDONG','浦东新区',3,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','上海�?),(6089,1001,5006,'HUANGPU','黄浦�?,4,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','上海�?),(6090,1001,5006,'TIANHE','天河�?,5,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','广州�?),(6091,1001,5006,'NANSHAN','南山�?,6,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','深圳�?),(6092,1001,5006,'XIHU','西湖�?,7,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','杭州�?),(6093,1001,5006,'WUHOU','武侯�?,8,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','成都�?),(6101,1001,5007,'100000','北京�?100000',1,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','北京市常用邮�?),(6102,1001,5007,'200000','上海�?200000',2,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','上海市常用邮�?),(6103,1001,5007,'510000','广州�?510000',3,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','广州市常用邮�?),(6104,1001,5007,'518000','深圳�?518000',4,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','深圳市常用邮�?),(6105,1001,5007,'310000','杭州�?310000',5,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','杭州市常用邮�?),(6106,1001,5007,'210000','南京�?210000',6,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','南京市常用邮�?),(6107,1001,5007,'610000','成都�?610000',7,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','成都市常用邮�?),(6108,1001,5007,'430000','武汉�?430000',8,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','武汉市常用邮�?),(6109,1001,5007,'710000','西安�?710000',9,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','西安市常用邮�?),(6116,1001,5008,'DashboardOutlined','DashboardOutlined',196,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DashboardOutlined'),(6117,1001,5008,'AppstoreOutlined','AppstoreOutlined',34,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AppstoreOutlined'),(6118,1001,5008,'SettingOutlined','SettingOutlined',656,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SettingOutlined'),(6119,1001,5008,'UserOutlined','UserOutlined',787,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UserOutlined'),(6120,1001,5008,'TeamOutlined','TeamOutlined',739,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TeamOutlined'),(6121,1001,5008,'UserSwitchOutlined','UserSwitchOutlined',788,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UserSwitchOutlined'),(6122,1001,5008,'IdcardOutlined','IdcardOutlined',417,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: IdcardOutlined'),(6123,1001,5008,'ApartmentOutlined','ApartmentOutlined',26,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ApartmentOutlined'),(6124,1001,5008,'SafetyOutlined','SafetyOutlined',640,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SafetyOutlined'),(6125,1001,5008,'MenuOutlined','MenuOutlined',491,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MenuOutlined'),(6126,1001,5008,'DatabaseOutlined','DatabaseOutlined',200,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DatabaseOutlined'),(6127,1001,5008,'FormOutlined','FormOutlined',356,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FormOutlined'),(6128,1001,5008,'SkinOutlined','SkinOutlined',676,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SkinOutlined'),(6129,1001,5008,'NotificationOutlined','NotificationOutlined',522,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: NotificationOutlined'),(6130,1001,5008,'RobotOutlined','RobotOutlined',629,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RobotOutlined'),(6131,1001,5008,'ApiOutlined','ApiOutlined',28,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ApiOutlined'),(6132,1001,5008,'FolderOpenOutlined','FolderOpenOutlined',346,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderOpenOutlined'),(6133,1001,5008,'TranslationOutlined','TranslationOutlined',754,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TranslationOutlined'),(6134,1001,5008,'FundOutlined','FundOutlined',366,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FundOutlined'),(6135,1001,5008,'FileTextOutlined','FileTextOutlined',320,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileTextOutlined'),(6136,1001,5008,'FileSearchOutlined','FileSearchOutlined',317,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileSearchOutlined'),(6137,1001,5008,'FileOutlined','FileOutlined',309,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileOutlined'),(6138,1001,5008,'AuditOutlined','AuditOutlined',46,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AuditOutlined'),(6139,1001,5008,'RadarChartOutlined','RadarChartOutlined',597,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RadarChartOutlined'),(6148,1001,5008,'AccountBookFilled','AccountBookFilled',1,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AccountBookFilled'),(6149,1001,5008,'AccountBookOutlined','AccountBookOutlined',2,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AccountBookOutlined'),(6150,1001,5008,'AccountBookTwoTone','AccountBookTwoTone',3,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AccountBookTwoTone'),(6151,1001,5008,'AimOutlined','AimOutlined',4,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AimOutlined'),(6152,1001,5008,'AlertFilled','AlertFilled',5,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlertFilled'),(6153,1001,5008,'AlertOutlined','AlertOutlined',6,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlertOutlined'),(6154,1001,5008,'AlertTwoTone','AlertTwoTone',7,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlertTwoTone'),(6155,1001,5008,'AlibabaOutlined','AlibabaOutlined',8,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlibabaOutlined'),(6156,1001,5008,'AlignCenterOutlined','AlignCenterOutlined',9,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlignCenterOutlined'),(6157,1001,5008,'AlignLeftOutlined','AlignLeftOutlined',10,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlignLeftOutlined'),(6158,1001,5008,'AlignRightOutlined','AlignRightOutlined',11,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlignRightOutlined'),(6159,1001,5008,'AlipayCircleFilled','AlipayCircleFilled',12,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlipayCircleFilled'),(6160,1001,5008,'AlipayCircleOutlined','AlipayCircleOutlined',13,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlipayCircleOutlined'),(6161,1001,5008,'AlipayOutlined','AlipayOutlined',14,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlipayOutlined'),(6162,1001,5008,'AlipaySquareFilled','AlipaySquareFilled',15,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AlipaySquareFilled'),(6163,1001,5008,'AliwangwangFilled','AliwangwangFilled',16,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AliwangwangFilled'),(6164,1001,5008,'AliwangwangOutlined','AliwangwangOutlined',17,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AliwangwangOutlined'),(6165,1001,5008,'AliyunOutlined','AliyunOutlined',18,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AliyunOutlined'),(6166,1001,5008,'AmazonCircleFilled','AmazonCircleFilled',19,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AmazonCircleFilled'),(6167,1001,5008,'AmazonOutlined','AmazonOutlined',20,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AmazonOutlined'),(6168,1001,5008,'AmazonSquareFilled','AmazonSquareFilled',21,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AmazonSquareFilled'),(6169,1001,5008,'AndroidFilled','AndroidFilled',22,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AndroidFilled'),(6170,1001,5008,'AndroidOutlined','AndroidOutlined',23,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AndroidOutlined'),(6171,1001,5008,'AntCloudOutlined','AntCloudOutlined',24,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AntCloudOutlined'),(6172,1001,5008,'AntDesignOutlined','AntDesignOutlined',25,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AntDesignOutlined'),(6173,1001,5008,'ApiFilled','ApiFilled',27,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ApiFilled'),(6174,1001,5008,'ApiTwoTone','ApiTwoTone',29,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ApiTwoTone'),(6175,1001,5008,'AppleFilled','AppleFilled',30,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AppleFilled'),(6176,1001,5008,'AppleOutlined','AppleOutlined',31,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AppleOutlined'),(6177,1001,5008,'AppstoreAddOutlined','AppstoreAddOutlined',32,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AppstoreAddOutlined'),(6178,1001,5008,'AppstoreFilled','AppstoreFilled',33,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AppstoreFilled'),(6179,1001,5008,'AppstoreTwoTone','AppstoreTwoTone',35,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AppstoreTwoTone'),(6180,1001,5008,'AreaChartOutlined','AreaChartOutlined',36,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AreaChartOutlined'),(6181,1001,5008,'ArrowDownOutlined','ArrowDownOutlined',37,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ArrowDownOutlined'),(6182,1001,5008,'ArrowLeftOutlined','ArrowLeftOutlined',38,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ArrowLeftOutlined'),(6183,1001,5008,'ArrowRightOutlined','ArrowRightOutlined',39,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ArrowRightOutlined'),(6184,1001,5008,'ArrowsAltOutlined','ArrowsAltOutlined',40,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ArrowsAltOutlined'),(6185,1001,5008,'ArrowUpOutlined','ArrowUpOutlined',41,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ArrowUpOutlined'),(6186,1001,5008,'AudioFilled','AudioFilled',42,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AudioFilled'),(6187,1001,5008,'AudioMutedOutlined','AudioMutedOutlined',43,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AudioMutedOutlined'),(6188,1001,5008,'AudioOutlined','AudioOutlined',44,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AudioOutlined'),(6189,1001,5008,'AudioTwoTone','AudioTwoTone',45,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: AudioTwoTone'),(6190,1001,5008,'BackwardFilled','BackwardFilled',47,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BackwardFilled'),(6191,1001,5008,'BackwardOutlined','BackwardOutlined',48,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BackwardOutlined'),(6192,1001,5008,'BaiduOutlined','BaiduOutlined',49,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BaiduOutlined'),(6193,1001,5008,'BankFilled','BankFilled',50,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BankFilled'),(6194,1001,5008,'BankOutlined','BankOutlined',51,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BankOutlined'),(6195,1001,5008,'BankTwoTone','BankTwoTone',52,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BankTwoTone'),(6196,1001,5008,'BarChartOutlined','BarChartOutlined',53,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BarChartOutlined'),(6197,1001,5008,'BarcodeOutlined','BarcodeOutlined',54,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BarcodeOutlined'),(6198,1001,5008,'BarsOutlined','BarsOutlined',55,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BarsOutlined'),(6199,1001,5008,'BehanceCircleFilled','BehanceCircleFilled',56,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BehanceCircleFilled'),(6200,1001,5008,'BehanceOutlined','BehanceOutlined',57,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BehanceOutlined'),(6201,1001,5008,'BehanceSquareFilled','BehanceSquareFilled',58,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BehanceSquareFilled'),(6202,1001,5008,'BehanceSquareOutlined','BehanceSquareOutlined',59,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BehanceSquareOutlined'),(6203,1001,5008,'BellFilled','BellFilled',60,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BellFilled'),(6204,1001,5008,'BellOutlined','BellOutlined',61,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BellOutlined'),(6205,1001,5008,'BellTwoTone','BellTwoTone',62,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BellTwoTone'),(6206,1001,5008,'BgColorsOutlined','BgColorsOutlined',63,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BgColorsOutlined'),(6207,1001,5008,'BilibiliFilled','BilibiliFilled',64,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BilibiliFilled'),(6208,1001,5008,'BilibiliOutlined','BilibiliOutlined',65,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BilibiliOutlined'),(6209,1001,5008,'BlockOutlined','BlockOutlined',66,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BlockOutlined'),(6210,1001,5008,'BoldOutlined','BoldOutlined',67,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BoldOutlined'),(6211,1001,5008,'BookFilled','BookFilled',68,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BookFilled'),(6212,1001,5008,'BookOutlined','BookOutlined',69,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BookOutlined'),(6213,1001,5008,'BookTwoTone','BookTwoTone',70,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BookTwoTone'),(6214,1001,5008,'BorderBottomOutlined','BorderBottomOutlined',71,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderBottomOutlined'),(6215,1001,5008,'BorderHorizontalOutlined','BorderHorizontalOutlined',72,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderHorizontalOutlined'),(6216,1001,5008,'BorderInnerOutlined','BorderInnerOutlined',73,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderInnerOutlined'),(6217,1001,5008,'BorderLeftOutlined','BorderLeftOutlined',74,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderLeftOutlined'),(6218,1001,5008,'BorderlessTableOutlined','BorderlessTableOutlined',75,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderlessTableOutlined'),(6219,1001,5008,'BorderOuterOutlined','BorderOuterOutlined',76,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderOuterOutlined'),(6220,1001,5008,'BorderOutlined','BorderOutlined',77,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderOutlined'),(6221,1001,5008,'BorderRightOutlined','BorderRightOutlined',78,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderRightOutlined'),(6222,1001,5008,'BorderTopOutlined','BorderTopOutlined',79,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderTopOutlined'),(6223,1001,5008,'BorderVerticleOutlined','BorderVerticleOutlined',80,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BorderVerticleOutlined'),(6224,1001,5008,'BoxPlotFilled','BoxPlotFilled',81,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BoxPlotFilled'),(6225,1001,5008,'BoxPlotOutlined','BoxPlotOutlined',82,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BoxPlotOutlined'),(6226,1001,5008,'BoxPlotTwoTone','BoxPlotTwoTone',83,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BoxPlotTwoTone'),(6227,1001,5008,'BranchesOutlined','BranchesOutlined',84,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BranchesOutlined'),(6228,1001,5008,'BugFilled','BugFilled',85,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BugFilled'),(6229,1001,5008,'BugOutlined','BugOutlined',86,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BugOutlined'),(6230,1001,5008,'BugTwoTone','BugTwoTone',87,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BugTwoTone'),(6231,1001,5008,'BuildFilled','BuildFilled',88,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BuildFilled'),(6232,1001,5008,'BuildOutlined','BuildOutlined',89,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BuildOutlined'),(6233,1001,5008,'BuildTwoTone','BuildTwoTone',90,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BuildTwoTone'),(6234,1001,5008,'BulbFilled','BulbFilled',91,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BulbFilled'),(6235,1001,5008,'BulbOutlined','BulbOutlined',92,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BulbOutlined'),(6236,1001,5008,'BulbTwoTone','BulbTwoTone',93,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: BulbTwoTone'),(6237,1001,5008,'CalculatorFilled','CalculatorFilled',94,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CalculatorFilled'),(6238,1001,5008,'CalculatorOutlined','CalculatorOutlined',95,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CalculatorOutlined'),(6239,1001,5008,'CalculatorTwoTone','CalculatorTwoTone',96,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CalculatorTwoTone'),(6240,1001,5008,'CalendarFilled','CalendarFilled',97,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CalendarFilled'),(6241,1001,5008,'CalendarOutlined','CalendarOutlined',98,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CalendarOutlined'),(6242,1001,5008,'CalendarTwoTone','CalendarTwoTone',99,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CalendarTwoTone'),(6243,1001,5008,'CameraFilled','CameraFilled',100,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CameraFilled'),(6244,1001,5008,'CameraOutlined','CameraOutlined',101,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CameraOutlined'),(6245,1001,5008,'CameraTwoTone','CameraTwoTone',102,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CameraTwoTone'),(6246,1001,5008,'CaretDownFilled','CaretDownFilled',103,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretDownFilled'),(6247,1001,5008,'CaretDownOutlined','CaretDownOutlined',104,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretDownOutlined'),(6248,1001,5008,'CaretLeftFilled','CaretLeftFilled',105,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretLeftFilled'),(6249,1001,5008,'CaretLeftOutlined','CaretLeftOutlined',106,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretLeftOutlined'),(6250,1001,5008,'CaretRightFilled','CaretRightFilled',107,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretRightFilled'),(6251,1001,5008,'CaretRightOutlined','CaretRightOutlined',108,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretRightOutlined'),(6252,1001,5008,'CaretUpFilled','CaretUpFilled',109,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretUpFilled'),(6253,1001,5008,'CaretUpOutlined','CaretUpOutlined',110,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CaretUpOutlined'),(6254,1001,5008,'CarFilled','CarFilled',111,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CarFilled'),(6255,1001,5008,'CarOutlined','CarOutlined',112,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CarOutlined'),(6256,1001,5008,'CarryOutFilled','CarryOutFilled',113,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CarryOutFilled'),(6257,1001,5008,'CarryOutOutlined','CarryOutOutlined',114,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CarryOutOutlined'),(6258,1001,5008,'CarryOutTwoTone','CarryOutTwoTone',115,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CarryOutTwoTone'),(6259,1001,5008,'CarTwoTone','CarTwoTone',116,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CarTwoTone'),(6260,1001,5008,'CheckCircleFilled','CheckCircleFilled',117,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CheckCircleFilled'),(6261,1001,5008,'CheckCircleOutlined','CheckCircleOutlined',118,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CheckCircleOutlined'),(6262,1001,5008,'CheckCircleTwoTone','CheckCircleTwoTone',119,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CheckCircleTwoTone'),(6263,1001,5008,'CheckOutlined','CheckOutlined',120,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CheckOutlined'),(6264,1001,5008,'CheckSquareFilled','CheckSquareFilled',121,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CheckSquareFilled'),(6265,1001,5008,'CheckSquareOutlined','CheckSquareOutlined',122,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CheckSquareOutlined'),(6266,1001,5008,'CheckSquareTwoTone','CheckSquareTwoTone',123,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CheckSquareTwoTone'),(6267,1001,5008,'ChromeFilled','ChromeFilled',124,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ChromeFilled'),(6268,1001,5008,'ChromeOutlined','ChromeOutlined',125,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ChromeOutlined'),(6269,1001,5008,'CiCircleFilled','CiCircleFilled',126,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CiCircleFilled'),(6270,1001,5008,'CiCircleOutlined','CiCircleOutlined',127,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CiCircleOutlined'),(6271,1001,5008,'CiCircleTwoTone','CiCircleTwoTone',128,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CiCircleTwoTone'),(6272,1001,5008,'CiOutlined','CiOutlined',129,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CiOutlined'),(6273,1001,5008,'CiTwoTone','CiTwoTone',130,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CiTwoTone'),(6274,1001,5008,'ClearOutlined','ClearOutlined',131,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ClearOutlined'),(6275,1001,5008,'ClockCircleFilled','ClockCircleFilled',132,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ClockCircleFilled'),(6276,1001,5008,'ClockCircleOutlined','ClockCircleOutlined',133,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ClockCircleOutlined'),(6277,1001,5008,'ClockCircleTwoTone','ClockCircleTwoTone',134,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ClockCircleTwoTone'),(6278,1001,5008,'CloseCircleFilled','CloseCircleFilled',135,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloseCircleFilled'),(6279,1001,5008,'CloseCircleOutlined','CloseCircleOutlined',136,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloseCircleOutlined'),(6280,1001,5008,'CloseCircleTwoTone','CloseCircleTwoTone',137,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloseCircleTwoTone'),(6281,1001,5008,'CloseOutlined','CloseOutlined',138,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloseOutlined'),(6282,1001,5008,'CloseSquareFilled','CloseSquareFilled',139,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloseSquareFilled'),(6283,1001,5008,'CloseSquareOutlined','CloseSquareOutlined',140,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloseSquareOutlined'),(6284,1001,5008,'CloseSquareTwoTone','CloseSquareTwoTone',141,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloseSquareTwoTone'),(6285,1001,5008,'CloudDownloadOutlined','CloudDownloadOutlined',142,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloudDownloadOutlined'),(6286,1001,5008,'CloudFilled','CloudFilled',143,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloudFilled'),(6287,1001,5008,'CloudOutlined','CloudOutlined',144,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloudOutlined'),(6288,1001,5008,'CloudServerOutlined','CloudServerOutlined',145,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloudServerOutlined'),(6289,1001,5008,'CloudSyncOutlined','CloudSyncOutlined',146,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloudSyncOutlined'),(6290,1001,5008,'CloudTwoTone','CloudTwoTone',147,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloudTwoTone'),(6291,1001,5008,'CloudUploadOutlined','CloudUploadOutlined',148,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CloudUploadOutlined'),(6292,1001,5008,'ClusterOutlined','ClusterOutlined',149,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ClusterOutlined'),(6293,1001,5008,'CodeFilled','CodeFilled',150,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodeFilled'),(6294,1001,5008,'CodeOutlined','CodeOutlined',151,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodeOutlined'),(6295,1001,5008,'CodepenCircleFilled','CodepenCircleFilled',152,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodepenCircleFilled'),(6296,1001,5008,'CodepenCircleOutlined','CodepenCircleOutlined',153,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodepenCircleOutlined'),(6297,1001,5008,'CodepenOutlined','CodepenOutlined',154,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodepenOutlined'),(6298,1001,5008,'CodepenSquareFilled','CodepenSquareFilled',155,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodepenSquareFilled'),(6299,1001,5008,'CodeSandboxCircleFilled','CodeSandboxCircleFilled',156,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodeSandboxCircleFilled'),(6300,1001,5008,'CodeSandboxOutlined','CodeSandboxOutlined',157,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodeSandboxOutlined'),(6301,1001,5008,'CodeSandboxSquareFilled','CodeSandboxSquareFilled',158,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodeSandboxSquareFilled'),(6302,1001,5008,'CodeTwoTone','CodeTwoTone',159,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CodeTwoTone'),(6303,1001,5008,'CoffeeOutlined','CoffeeOutlined',160,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CoffeeOutlined'),(6304,1001,5008,'ColumnHeightOutlined','ColumnHeightOutlined',161,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ColumnHeightOutlined'),(6305,1001,5008,'ColumnWidthOutlined','ColumnWidthOutlined',162,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ColumnWidthOutlined'),(6306,1001,5008,'CommentOutlined','CommentOutlined',163,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CommentOutlined'),(6307,1001,5008,'CompassFilled','CompassFilled',164,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CompassFilled'),(6308,1001,5008,'CompassOutlined','CompassOutlined',165,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CompassOutlined'),(6309,1001,5008,'CompassTwoTone','CompassTwoTone',166,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CompassTwoTone'),(6310,1001,5008,'CompressOutlined','CompressOutlined',167,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CompressOutlined'),(6311,1001,5008,'ConsoleSqlOutlined','ConsoleSqlOutlined',168,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ConsoleSqlOutlined'),(6312,1001,5008,'ContactsFilled','ContactsFilled',169,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ContactsFilled'),(6313,1001,5008,'ContactsOutlined','ContactsOutlined',170,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ContactsOutlined'),(6314,1001,5008,'ContactsTwoTone','ContactsTwoTone',171,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ContactsTwoTone'),(6315,1001,5008,'ContainerFilled','ContainerFilled',172,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ContainerFilled'),(6316,1001,5008,'ContainerOutlined','ContainerOutlined',173,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ContainerOutlined'),(6317,1001,5008,'ContainerTwoTone','ContainerTwoTone',174,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ContainerTwoTone'),(6318,1001,5008,'ControlFilled','ControlFilled',175,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ControlFilled'),(6319,1001,5008,'ControlOutlined','ControlOutlined',176,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ControlOutlined'),(6320,1001,5008,'ControlTwoTone','ControlTwoTone',177,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ControlTwoTone'),(6321,1001,5008,'CopyFilled','CopyFilled',178,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyFilled'),(6322,1001,5008,'CopyOutlined','CopyOutlined',179,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyOutlined'),(6323,1001,5008,'CopyrightCircleFilled','CopyrightCircleFilled',180,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyrightCircleFilled'),(6324,1001,5008,'CopyrightCircleOutlined','CopyrightCircleOutlined',181,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyrightCircleOutlined'),(6325,1001,5008,'CopyrightCircleTwoTone','CopyrightCircleTwoTone',182,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyrightCircleTwoTone'),(6326,1001,5008,'CopyrightOutlined','CopyrightOutlined',183,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyrightOutlined'),(6327,1001,5008,'CopyrightTwoTone','CopyrightTwoTone',184,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyrightTwoTone'),(6328,1001,5008,'CopyTwoTone','CopyTwoTone',185,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CopyTwoTone'),(6329,1001,5008,'CreditCardFilled','CreditCardFilled',186,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CreditCardFilled'),(6330,1001,5008,'CreditCardOutlined','CreditCardOutlined',187,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CreditCardOutlined'),(6331,1001,5008,'CreditCardTwoTone','CreditCardTwoTone',188,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CreditCardTwoTone'),(6332,1001,5008,'CrownFilled','CrownFilled',189,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CrownFilled'),(6333,1001,5008,'CrownOutlined','CrownOutlined',190,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CrownOutlined'),(6334,1001,5008,'CrownTwoTone','CrownTwoTone',191,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CrownTwoTone'),(6335,1001,5008,'CustomerServiceFilled','CustomerServiceFilled',192,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CustomerServiceFilled'),(6336,1001,5008,'CustomerServiceOutlined','CustomerServiceOutlined',193,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CustomerServiceOutlined'),(6337,1001,5008,'CustomerServiceTwoTone','CustomerServiceTwoTone',194,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: CustomerServiceTwoTone'),(6338,1001,5008,'DashboardFilled','DashboardFilled',195,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DashboardFilled'),(6339,1001,5008,'DashboardTwoTone','DashboardTwoTone',197,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DashboardTwoTone'),(6340,1001,5008,'DashOutlined','DashOutlined',198,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DashOutlined'),(6341,1001,5008,'DatabaseFilled','DatabaseFilled',199,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DatabaseFilled'),(6342,1001,5008,'DatabaseTwoTone','DatabaseTwoTone',201,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DatabaseTwoTone'),(6343,1001,5008,'DeleteColumnOutlined','DeleteColumnOutlined',202,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DeleteColumnOutlined'),(6344,1001,5008,'DeleteFilled','DeleteFilled',203,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DeleteFilled'),(6345,1001,5008,'DeleteOutlined','DeleteOutlined',204,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DeleteOutlined'),(6346,1001,5008,'DeleteRowOutlined','DeleteRowOutlined',205,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DeleteRowOutlined'),(6347,1001,5008,'DeleteTwoTone','DeleteTwoTone',206,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DeleteTwoTone'),(6348,1001,5008,'DeliveredProcedureOutlined','DeliveredProcedureOutlined',207,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DeliveredProcedureOutlined'),(6349,1001,5008,'DeploymentUnitOutlined','DeploymentUnitOutlined',208,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DeploymentUnitOutlined'),(6350,1001,5008,'DesktopOutlined','DesktopOutlined',209,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DesktopOutlined'),(6351,1001,5008,'DiffFilled','DiffFilled',210,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DiffFilled'),(6352,1001,5008,'DiffOutlined','DiffOutlined',211,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DiffOutlined'),(6353,1001,5008,'DiffTwoTone','DiffTwoTone',212,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DiffTwoTone'),(6354,1001,5008,'DingdingOutlined','DingdingOutlined',213,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DingdingOutlined'),(6355,1001,5008,'DingtalkCircleFilled','DingtalkCircleFilled',214,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DingtalkCircleFilled'),(6356,1001,5008,'DingtalkOutlined','DingtalkOutlined',215,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DingtalkOutlined'),(6357,1001,5008,'DingtalkSquareFilled','DingtalkSquareFilled',216,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DingtalkSquareFilled'),(6358,1001,5008,'DisconnectOutlined','DisconnectOutlined',217,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DisconnectOutlined'),(6359,1001,5008,'DiscordFilled','DiscordFilled',218,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DiscordFilled'),(6360,1001,5008,'DiscordOutlined','DiscordOutlined',219,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DiscordOutlined'),(6361,1001,5008,'DislikeFilled','DislikeFilled',220,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DislikeFilled'),(6362,1001,5008,'DislikeOutlined','DislikeOutlined',221,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DislikeOutlined'),(6363,1001,5008,'DislikeTwoTone','DislikeTwoTone',222,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DislikeTwoTone'),(6364,1001,5008,'DockerOutlined','DockerOutlined',223,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DockerOutlined'),(6365,1001,5008,'DollarCircleFilled','DollarCircleFilled',224,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DollarCircleFilled'),(6366,1001,5008,'DollarCircleOutlined','DollarCircleOutlined',225,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DollarCircleOutlined'),(6367,1001,5008,'DollarCircleTwoTone','DollarCircleTwoTone',226,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DollarCircleTwoTone'),(6368,1001,5008,'DollarOutlined','DollarOutlined',227,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DollarOutlined'),(6369,1001,5008,'DollarTwoTone','DollarTwoTone',228,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DollarTwoTone'),(6370,1001,5008,'DotChartOutlined','DotChartOutlined',229,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DotChartOutlined'),(6371,1001,5008,'DotNetOutlined','DotNetOutlined',230,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DotNetOutlined'),(6372,1001,5008,'DoubleLeftOutlined','DoubleLeftOutlined',231,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DoubleLeftOutlined'),(6373,1001,5008,'DoubleRightOutlined','DoubleRightOutlined',232,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DoubleRightOutlined'),(6374,1001,5008,'DownCircleFilled','DownCircleFilled',233,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownCircleFilled'),(6375,1001,5008,'DownCircleOutlined','DownCircleOutlined',234,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownCircleOutlined'),(6376,1001,5008,'DownCircleTwoTone','DownCircleTwoTone',235,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownCircleTwoTone'),(6377,1001,5008,'DownloadOutlined','DownloadOutlined',236,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownloadOutlined'),(6378,1001,5008,'DownOutlined','DownOutlined',237,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownOutlined'),(6379,1001,5008,'DownSquareFilled','DownSquareFilled',238,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownSquareFilled'),(6380,1001,5008,'DownSquareOutlined','DownSquareOutlined',239,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownSquareOutlined'),(6381,1001,5008,'DownSquareTwoTone','DownSquareTwoTone',240,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DownSquareTwoTone'),(6382,1001,5008,'DragOutlined','DragOutlined',241,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DragOutlined'),(6383,1001,5008,'DribbbleCircleFilled','DribbbleCircleFilled',242,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DribbbleCircleFilled'),(6384,1001,5008,'DribbbleOutlined','DribbbleOutlined',243,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DribbbleOutlined'),(6385,1001,5008,'DribbbleSquareFilled','DribbbleSquareFilled',244,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DribbbleSquareFilled'),(6386,1001,5008,'DribbbleSquareOutlined','DribbbleSquareOutlined',245,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DribbbleSquareOutlined'),(6387,1001,5008,'DropboxCircleFilled','DropboxCircleFilled',246,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DropboxCircleFilled'),(6388,1001,5008,'DropboxOutlined','DropboxOutlined',247,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DropboxOutlined'),(6389,1001,5008,'DropboxSquareFilled','DropboxSquareFilled',248,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: DropboxSquareFilled'),(6390,1001,5008,'EditFilled','EditFilled',249,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EditFilled'),(6391,1001,5008,'EditOutlined','EditOutlined',250,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EditOutlined'),(6392,1001,5008,'EditTwoTone','EditTwoTone',251,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EditTwoTone'),(6393,1001,5008,'EllipsisOutlined','EllipsisOutlined',252,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EllipsisOutlined'),(6394,1001,5008,'EnterOutlined','EnterOutlined',253,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EnterOutlined'),(6395,1001,5008,'EnvironmentFilled','EnvironmentFilled',254,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EnvironmentFilled'),(6396,1001,5008,'EnvironmentOutlined','EnvironmentOutlined',255,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EnvironmentOutlined'),(6397,1001,5008,'EnvironmentTwoTone','EnvironmentTwoTone',256,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EnvironmentTwoTone'),(6398,1001,5008,'EuroCircleFilled','EuroCircleFilled',257,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EuroCircleFilled'),(6399,1001,5008,'EuroCircleOutlined','EuroCircleOutlined',258,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EuroCircleOutlined'),(6400,1001,5008,'EuroCircleTwoTone','EuroCircleTwoTone',259,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EuroCircleTwoTone'),(6401,1001,5008,'EuroOutlined','EuroOutlined',260,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EuroOutlined'),(6402,1001,5008,'EuroTwoTone','EuroTwoTone',261,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EuroTwoTone'),(6403,1001,5008,'ExceptionOutlined','ExceptionOutlined',262,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExceptionOutlined'),(6404,1001,5008,'ExclamationCircleFilled','ExclamationCircleFilled',263,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExclamationCircleFilled'),(6405,1001,5008,'ExclamationCircleOutlined','ExclamationCircleOutlined',264,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExclamationCircleOutlined'),(6406,1001,5008,'ExclamationCircleTwoTone','ExclamationCircleTwoTone',265,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExclamationCircleTwoTone'),(6407,1001,5008,'ExclamationOutlined','ExclamationOutlined',266,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExclamationOutlined'),(6408,1001,5008,'ExpandAltOutlined','ExpandAltOutlined',267,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExpandAltOutlined'),(6409,1001,5008,'ExpandOutlined','ExpandOutlined',268,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExpandOutlined'),(6410,1001,5008,'ExperimentFilled','ExperimentFilled',269,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExperimentFilled'),(6411,1001,5008,'ExperimentOutlined','ExperimentOutlined',270,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExperimentOutlined'),(6412,1001,5008,'ExperimentTwoTone','ExperimentTwoTone',271,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExperimentTwoTone'),(6413,1001,5008,'ExportOutlined','ExportOutlined',272,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ExportOutlined'),(6414,1001,5008,'EyeFilled','EyeFilled',273,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EyeFilled'),(6415,1001,5008,'EyeInvisibleFilled','EyeInvisibleFilled',274,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EyeInvisibleFilled'),(6416,1001,5008,'EyeInvisibleOutlined','EyeInvisibleOutlined',275,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EyeInvisibleOutlined'),(6417,1001,5008,'EyeInvisibleTwoTone','EyeInvisibleTwoTone',276,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EyeInvisibleTwoTone'),(6418,1001,5008,'EyeOutlined','EyeOutlined',277,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EyeOutlined'),(6419,1001,5008,'EyeTwoTone','EyeTwoTone',278,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: EyeTwoTone'),(6420,1001,5008,'FacebookFilled','FacebookFilled',279,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FacebookFilled'),(6421,1001,5008,'FacebookOutlined','FacebookOutlined',280,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FacebookOutlined'),(6422,1001,5008,'FallOutlined','FallOutlined',281,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FallOutlined'),(6423,1001,5008,'FastBackwardFilled','FastBackwardFilled',282,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FastBackwardFilled'),(6424,1001,5008,'FastBackwardOutlined','FastBackwardOutlined',283,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FastBackwardOutlined'),(6425,1001,5008,'FastForwardFilled','FastForwardFilled',284,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FastForwardFilled'),(6426,1001,5008,'FastForwardOutlined','FastForwardOutlined',285,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FastForwardOutlined'),(6427,1001,5008,'FieldBinaryOutlined','FieldBinaryOutlined',286,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FieldBinaryOutlined'),(6428,1001,5008,'FieldNumberOutlined','FieldNumberOutlined',287,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FieldNumberOutlined'),(6429,1001,5008,'FieldStringOutlined','FieldStringOutlined',288,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FieldStringOutlined'),(6430,1001,5008,'FieldTimeOutlined','FieldTimeOutlined',289,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FieldTimeOutlined'),(6431,1001,5008,'FileAddFilled','FileAddFilled',290,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileAddFilled'),(6432,1001,5008,'FileAddOutlined','FileAddOutlined',291,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileAddOutlined'),(6433,1001,5008,'FileAddTwoTone','FileAddTwoTone',292,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileAddTwoTone'),(6434,1001,5008,'FileDoneOutlined','FileDoneOutlined',293,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileDoneOutlined'),(6435,1001,5008,'FileExcelFilled','FileExcelFilled',294,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileExcelFilled'),(6436,1001,5008,'FileExcelOutlined','FileExcelOutlined',295,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileExcelOutlined'),(6437,1001,5008,'FileExcelTwoTone','FileExcelTwoTone',296,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileExcelTwoTone'),(6438,1001,5008,'FileExclamationFilled','FileExclamationFilled',297,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileExclamationFilled'),(6439,1001,5008,'FileExclamationOutlined','FileExclamationOutlined',298,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileExclamationOutlined'),(6440,1001,5008,'FileExclamationTwoTone','FileExclamationTwoTone',299,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileExclamationTwoTone'),(6441,1001,5008,'FileFilled','FileFilled',300,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileFilled'),(6442,1001,5008,'FileGifOutlined','FileGifOutlined',301,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileGifOutlined'),(6443,1001,5008,'FileImageFilled','FileImageFilled',302,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileImageFilled'),(6444,1001,5008,'FileImageOutlined','FileImageOutlined',303,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileImageOutlined'),(6445,1001,5008,'FileImageTwoTone','FileImageTwoTone',304,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileImageTwoTone'),(6446,1001,5008,'FileJpgOutlined','FileJpgOutlined',305,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileJpgOutlined'),(6447,1001,5008,'FileMarkdownFilled','FileMarkdownFilled',306,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileMarkdownFilled'),(6448,1001,5008,'FileMarkdownOutlined','FileMarkdownOutlined',307,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileMarkdownOutlined'),(6449,1001,5008,'FileMarkdownTwoTone','FileMarkdownTwoTone',308,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileMarkdownTwoTone'),(6450,1001,5008,'FilePdfFilled','FilePdfFilled',310,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilePdfFilled'),(6451,1001,5008,'FilePdfOutlined','FilePdfOutlined',311,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilePdfOutlined'),(6452,1001,5008,'FilePdfTwoTone','FilePdfTwoTone',312,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilePdfTwoTone'),(6453,1001,5008,'FilePptFilled','FilePptFilled',313,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilePptFilled'),(6454,1001,5008,'FilePptOutlined','FilePptOutlined',314,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilePptOutlined'),(6455,1001,5008,'FilePptTwoTone','FilePptTwoTone',315,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilePptTwoTone'),(6456,1001,5008,'FileProtectOutlined','FileProtectOutlined',316,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileProtectOutlined'),(6457,1001,5008,'FileSyncOutlined','FileSyncOutlined',318,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileSyncOutlined'),(6458,1001,5008,'FileTextFilled','FileTextFilled',319,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileTextFilled'),(6459,1001,5008,'FileTextTwoTone','FileTextTwoTone',321,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileTextTwoTone'),(6460,1001,5008,'FileTwoTone','FileTwoTone',322,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileTwoTone'),(6461,1001,5008,'FileUnknownFilled','FileUnknownFilled',323,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileUnknownFilled'),(6462,1001,5008,'FileUnknownOutlined','FileUnknownOutlined',324,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileUnknownOutlined'),(6463,1001,5008,'FileUnknownTwoTone','FileUnknownTwoTone',325,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileUnknownTwoTone'),(6464,1001,5008,'FileWordFilled','FileWordFilled',326,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileWordFilled'),(6465,1001,5008,'FileWordOutlined','FileWordOutlined',327,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileWordOutlined'),(6466,1001,5008,'FileWordTwoTone','FileWordTwoTone',328,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileWordTwoTone'),(6467,1001,5008,'FileZipFilled','FileZipFilled',329,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileZipFilled'),(6468,1001,5008,'FileZipOutlined','FileZipOutlined',330,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileZipOutlined'),(6469,1001,5008,'FileZipTwoTone','FileZipTwoTone',331,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FileZipTwoTone'),(6470,1001,5008,'FilterFilled','FilterFilled',332,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilterFilled'),(6471,1001,5008,'FilterOutlined','FilterOutlined',333,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilterOutlined'),(6472,1001,5008,'FilterTwoTone','FilterTwoTone',334,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FilterTwoTone'),(6473,1001,5008,'FireFilled','FireFilled',335,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FireFilled'),(6474,1001,5008,'FireOutlined','FireOutlined',336,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FireOutlined'),(6475,1001,5008,'FireTwoTone','FireTwoTone',337,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FireTwoTone'),(6476,1001,5008,'FlagFilled','FlagFilled',338,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FlagFilled'),(6477,1001,5008,'FlagOutlined','FlagOutlined',339,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FlagOutlined'),(6478,1001,5008,'FlagTwoTone','FlagTwoTone',340,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FlagTwoTone'),(6479,1001,5008,'FolderAddFilled','FolderAddFilled',341,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderAddFilled'),(6480,1001,5008,'FolderAddOutlined','FolderAddOutlined',342,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderAddOutlined'),(6481,1001,5008,'FolderAddTwoTone','FolderAddTwoTone',343,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderAddTwoTone'),(6482,1001,5008,'FolderFilled','FolderFilled',344,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderFilled'),(6483,1001,5008,'FolderOpenFilled','FolderOpenFilled',345,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderOpenFilled'),(6484,1001,5008,'FolderOpenTwoTone','FolderOpenTwoTone',347,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderOpenTwoTone'),(6485,1001,5008,'FolderOutlined','FolderOutlined',348,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderOutlined'),(6486,1001,5008,'FolderTwoTone','FolderTwoTone',349,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderTwoTone'),(6487,1001,5008,'FolderViewOutlined','FolderViewOutlined',350,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FolderViewOutlined'),(6488,1001,5008,'FontColorsOutlined','FontColorsOutlined',351,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FontColorsOutlined'),(6489,1001,5008,'FontSizeOutlined','FontSizeOutlined',352,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FontSizeOutlined'),(6490,1001,5008,'ForkOutlined','ForkOutlined',353,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ForkOutlined'),(6491,1001,5008,'FormatPainterFilled','FormatPainterFilled',354,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FormatPainterFilled'),(6492,1001,5008,'FormatPainterOutlined','FormatPainterOutlined',355,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FormatPainterOutlined'),(6493,1001,5008,'ForwardFilled','ForwardFilled',357,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ForwardFilled'),(6494,1001,5008,'ForwardOutlined','ForwardOutlined',358,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ForwardOutlined'),(6495,1001,5008,'FrownFilled','FrownFilled',359,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FrownFilled'),(6496,1001,5008,'FrownOutlined','FrownOutlined',360,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FrownOutlined'),(6497,1001,5008,'FrownTwoTone','FrownTwoTone',361,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FrownTwoTone'),(6498,1001,5008,'FullscreenExitOutlined','FullscreenExitOutlined',362,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FullscreenExitOutlined'),(6499,1001,5008,'FullscreenOutlined','FullscreenOutlined',363,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FullscreenOutlined'),(6500,1001,5008,'FunctionOutlined','FunctionOutlined',364,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FunctionOutlined'),(6501,1001,5008,'FundFilled','FundFilled',365,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FundFilled'),(6502,1001,5008,'FundProjectionScreenOutlined','FundProjectionScreenOutlined',367,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FundProjectionScreenOutlined'),(6503,1001,5008,'FundTwoTone','FundTwoTone',368,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FundTwoTone'),(6504,1001,5008,'FundViewOutlined','FundViewOutlined',369,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FundViewOutlined'),(6505,1001,5008,'FunnelPlotFilled','FunnelPlotFilled',370,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FunnelPlotFilled'),(6506,1001,5008,'FunnelPlotOutlined','FunnelPlotOutlined',371,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FunnelPlotOutlined'),(6507,1001,5008,'FunnelPlotTwoTone','FunnelPlotTwoTone',372,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: FunnelPlotTwoTone'),(6508,1001,5008,'GatewayOutlined','GatewayOutlined',373,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GatewayOutlined'),(6509,1001,5008,'GifOutlined','GifOutlined',374,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GifOutlined'),(6510,1001,5008,'GiftFilled','GiftFilled',375,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GiftFilled'),(6511,1001,5008,'GiftOutlined','GiftOutlined',376,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GiftOutlined'),(6512,1001,5008,'GiftTwoTone','GiftTwoTone',377,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GiftTwoTone'),(6513,1001,5008,'GithubFilled','GithubFilled',378,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GithubFilled'),(6514,1001,5008,'GithubOutlined','GithubOutlined',379,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GithubOutlined'),(6515,1001,5008,'GitlabFilled','GitlabFilled',380,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GitlabFilled'),(6516,1001,5008,'GitlabOutlined','GitlabOutlined',381,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GitlabOutlined'),(6517,1001,5008,'GlobalOutlined','GlobalOutlined',382,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GlobalOutlined'),(6518,1001,5008,'GoldenFilled','GoldenFilled',383,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GoldenFilled'),(6519,1001,5008,'GoldFilled','GoldFilled',384,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GoldFilled'),(6520,1001,5008,'GoldOutlined','GoldOutlined',385,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GoldOutlined'),(6521,1001,5008,'GoldTwoTone','GoldTwoTone',386,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GoldTwoTone'),(6522,1001,5008,'GoogleCircleFilled','GoogleCircleFilled',387,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GoogleCircleFilled'),(6523,1001,5008,'GoogleOutlined','GoogleOutlined',388,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GoogleOutlined'),(6524,1001,5008,'GooglePlusCircleFilled','GooglePlusCircleFilled',389,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GooglePlusCircleFilled'),(6525,1001,5008,'GooglePlusOutlined','GooglePlusOutlined',390,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GooglePlusOutlined'),(6526,1001,5008,'GooglePlusSquareFilled','GooglePlusSquareFilled',391,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GooglePlusSquareFilled'),(6527,1001,5008,'GoogleSquareFilled','GoogleSquareFilled',392,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GoogleSquareFilled'),(6528,1001,5008,'GroupOutlined','GroupOutlined',393,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: GroupOutlined'),(6529,1001,5008,'HarmonyOSOutlined','HarmonyOSOutlined',394,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HarmonyOSOutlined'),(6530,1001,5008,'HddFilled','HddFilled',395,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HddFilled'),(6531,1001,5008,'HddOutlined','HddOutlined',396,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HddOutlined'),(6532,1001,5008,'HddTwoTone','HddTwoTone',397,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HddTwoTone'),(6533,1001,5008,'HeartFilled','HeartFilled',398,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HeartFilled'),(6534,1001,5008,'HeartOutlined','HeartOutlined',399,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HeartOutlined'),(6535,1001,5008,'HeartTwoTone','HeartTwoTone',400,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HeartTwoTone'),(6536,1001,5008,'HeatMapOutlined','HeatMapOutlined',401,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HeatMapOutlined'),(6537,1001,5008,'HighlightFilled','HighlightFilled',402,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HighlightFilled'),(6538,1001,5008,'HighlightOutlined','HighlightOutlined',403,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HighlightOutlined'),(6539,1001,5008,'HighlightTwoTone','HighlightTwoTone',404,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HighlightTwoTone'),(6540,1001,5008,'HistoryOutlined','HistoryOutlined',405,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HistoryOutlined'),(6541,1001,5008,'HolderOutlined','HolderOutlined',406,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HolderOutlined'),(6542,1001,5008,'HomeFilled','HomeFilled',407,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HomeFilled'),(6543,1001,5008,'HomeOutlined','HomeOutlined',408,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HomeOutlined'),(6544,1001,5008,'HomeTwoTone','HomeTwoTone',409,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HomeTwoTone'),(6545,1001,5008,'HourglassFilled','HourglassFilled',410,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HourglassFilled'),(6546,1001,5008,'HourglassOutlined','HourglassOutlined',411,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HourglassOutlined'),(6547,1001,5008,'HourglassTwoTone','HourglassTwoTone',412,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: HourglassTwoTone'),(6548,1001,5008,'Html5Filled','Html5Filled',413,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: Html5Filled'),(6549,1001,5008,'Html5Outlined','Html5Outlined',414,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: Html5Outlined'),(6550,1001,5008,'Html5TwoTone','Html5TwoTone',415,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: Html5TwoTone'),(6551,1001,5008,'IdcardFilled','IdcardFilled',416,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: IdcardFilled'),(6552,1001,5008,'IdcardTwoTone','IdcardTwoTone',418,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: IdcardTwoTone'),(6553,1001,5008,'IeCircleFilled','IeCircleFilled',419,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: IeCircleFilled'),(6554,1001,5008,'IeOutlined','IeOutlined',420,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: IeOutlined'),(6555,1001,5008,'IeSquareFilled','IeSquareFilled',421,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: IeSquareFilled'),(6556,1001,5008,'ImportOutlined','ImportOutlined',422,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ImportOutlined'),(6557,1001,5008,'InboxOutlined','InboxOutlined',423,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InboxOutlined'),(6558,1001,5008,'InfoCircleFilled','InfoCircleFilled',424,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InfoCircleFilled'),(6559,1001,5008,'InfoCircleOutlined','InfoCircleOutlined',425,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InfoCircleOutlined'),(6560,1001,5008,'InfoCircleTwoTone','InfoCircleTwoTone',426,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InfoCircleTwoTone'),(6561,1001,5008,'InfoOutlined','InfoOutlined',427,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InfoOutlined'),(6562,1001,5008,'InsertRowAboveOutlined','InsertRowAboveOutlined',428,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InsertRowAboveOutlined'),(6563,1001,5008,'InsertRowBelowOutlined','InsertRowBelowOutlined',429,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InsertRowBelowOutlined'),(6564,1001,5008,'InsertRowLeftOutlined','InsertRowLeftOutlined',430,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InsertRowLeftOutlined'),(6565,1001,5008,'InsertRowRightOutlined','InsertRowRightOutlined',431,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InsertRowRightOutlined'),(6566,1001,5008,'InstagramFilled','InstagramFilled',432,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InstagramFilled'),(6567,1001,5008,'InstagramOutlined','InstagramOutlined',433,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InstagramOutlined'),(6568,1001,5008,'InsuranceFilled','InsuranceFilled',434,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InsuranceFilled'),(6569,1001,5008,'InsuranceOutlined','InsuranceOutlined',435,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InsuranceOutlined'),(6570,1001,5008,'InsuranceTwoTone','InsuranceTwoTone',436,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InsuranceTwoTone'),(6571,1001,5008,'InteractionFilled','InteractionFilled',437,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InteractionFilled'),(6572,1001,5008,'InteractionOutlined','InteractionOutlined',438,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InteractionOutlined'),(6573,1001,5008,'InteractionTwoTone','InteractionTwoTone',439,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: InteractionTwoTone'),(6574,1001,5008,'IssuesCloseOutlined','IssuesCloseOutlined',440,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: IssuesCloseOutlined'),(6575,1001,5008,'ItalicOutlined','ItalicOutlined',441,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ItalicOutlined'),(6576,1001,5008,'JavaOutlined','JavaOutlined',442,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: JavaOutlined'),(6577,1001,5008,'JavaScriptOutlined','JavaScriptOutlined',443,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: JavaScriptOutlined'),(6578,1001,5008,'KeyOutlined','KeyOutlined',444,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: KeyOutlined'),(6579,1001,5008,'KubernetesOutlined','KubernetesOutlined',445,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: KubernetesOutlined'),(6580,1001,5008,'LaptopOutlined','LaptopOutlined',446,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LaptopOutlined'),(6581,1001,5008,'LayoutFilled','LayoutFilled',447,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LayoutFilled'),(6582,1001,5008,'LayoutOutlined','LayoutOutlined',448,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LayoutOutlined'),(6583,1001,5008,'LayoutTwoTone','LayoutTwoTone',449,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LayoutTwoTone'),(6584,1001,5008,'LeftCircleFilled','LeftCircleFilled',450,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LeftCircleFilled'),(6585,1001,5008,'LeftCircleOutlined','LeftCircleOutlined',451,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LeftCircleOutlined'),(6586,1001,5008,'LeftCircleTwoTone','LeftCircleTwoTone',452,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LeftCircleTwoTone'),(6587,1001,5008,'LeftOutlined','LeftOutlined',453,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LeftOutlined'),(6588,1001,5008,'LeftSquareFilled','LeftSquareFilled',454,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LeftSquareFilled'),(6589,1001,5008,'LeftSquareOutlined','LeftSquareOutlined',455,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LeftSquareOutlined'),(6590,1001,5008,'LeftSquareTwoTone','LeftSquareTwoTone',456,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LeftSquareTwoTone'),(6591,1001,5008,'LikeFilled','LikeFilled',457,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LikeFilled'),(6592,1001,5008,'LikeOutlined','LikeOutlined',458,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LikeOutlined'),(6593,1001,5008,'LikeTwoTone','LikeTwoTone',459,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LikeTwoTone'),(6594,1001,5008,'LineChartOutlined','LineChartOutlined',460,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LineChartOutlined'),(6595,1001,5008,'LineHeightOutlined','LineHeightOutlined',461,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LineHeightOutlined'),(6596,1001,5008,'LineOutlined','LineOutlined',462,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LineOutlined'),(6597,1001,5008,'LinkedinFilled','LinkedinFilled',463,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LinkedinFilled'),(6598,1001,5008,'LinkedinOutlined','LinkedinOutlined',464,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LinkedinOutlined'),(6599,1001,5008,'LinkOutlined','LinkOutlined',465,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LinkOutlined'),(6600,1001,5008,'LinuxOutlined','LinuxOutlined',466,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LinuxOutlined'),(6601,1001,5008,'Loading3QuartersOutlined','Loading3QuartersOutlined',467,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: Loading3QuartersOutlined'),(6602,1001,5008,'LoadingOutlined','LoadingOutlined',468,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LoadingOutlined'),(6603,1001,5008,'LockFilled','LockFilled',469,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LockFilled'),(6604,1001,5008,'LockOutlined','LockOutlined',470,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LockOutlined'),(6605,1001,5008,'LockTwoTone','LockTwoTone',471,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LockTwoTone'),(6606,1001,5008,'LoginOutlined','LoginOutlined',472,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LoginOutlined'),(6607,1001,5008,'LogoutOutlined','LogoutOutlined',473,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: LogoutOutlined'),(6608,1001,5008,'MacCommandFilled','MacCommandFilled',474,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MacCommandFilled'),(6609,1001,5008,'MacCommandOutlined','MacCommandOutlined',475,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MacCommandOutlined'),(6610,1001,5008,'MailFilled','MailFilled',476,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MailFilled'),(6611,1001,5008,'MailOutlined','MailOutlined',477,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MailOutlined'),(6612,1001,5008,'MailTwoTone','MailTwoTone',478,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MailTwoTone'),(6613,1001,5008,'ManOutlined','ManOutlined',479,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ManOutlined'),(6614,1001,5008,'MedicineBoxFilled','MedicineBoxFilled',480,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MedicineBoxFilled'),(6615,1001,5008,'MedicineBoxOutlined','MedicineBoxOutlined',481,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MedicineBoxOutlined'),(6616,1001,5008,'MedicineBoxTwoTone','MedicineBoxTwoTone',482,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MedicineBoxTwoTone'),(6617,1001,5008,'MediumCircleFilled','MediumCircleFilled',483,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MediumCircleFilled'),(6618,1001,5008,'MediumOutlined','MediumOutlined',484,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MediumOutlined'),(6619,1001,5008,'MediumSquareFilled','MediumSquareFilled',485,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MediumSquareFilled'),(6620,1001,5008,'MediumWorkmarkOutlined','MediumWorkmarkOutlined',486,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MediumWorkmarkOutlined'),(6621,1001,5008,'MehFilled','MehFilled',487,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MehFilled'),(6622,1001,5008,'MehOutlined','MehOutlined',488,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MehOutlined'),(6623,1001,5008,'MehTwoTone','MehTwoTone',489,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MehTwoTone'),(6624,1001,5008,'MenuFoldOutlined','MenuFoldOutlined',490,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MenuFoldOutlined'),(6625,1001,5008,'MenuUnfoldOutlined','MenuUnfoldOutlined',492,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MenuUnfoldOutlined'),(6626,1001,5008,'MergeCellsOutlined','MergeCellsOutlined',493,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MergeCellsOutlined'),(6627,1001,5008,'MergeFilled','MergeFilled',494,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MergeFilled'),(6628,1001,5008,'MergeOutlined','MergeOutlined',495,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MergeOutlined'),(6629,1001,5008,'MessageFilled','MessageFilled',496,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MessageFilled'),(6630,1001,5008,'MessageOutlined','MessageOutlined',497,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MessageOutlined'),(6631,1001,5008,'MessageTwoTone','MessageTwoTone',498,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MessageTwoTone'),(6632,1001,5008,'MinusCircleFilled','MinusCircleFilled',499,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MinusCircleFilled'),(6633,1001,5008,'MinusCircleOutlined','MinusCircleOutlined',500,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MinusCircleOutlined'),(6634,1001,5008,'MinusCircleTwoTone','MinusCircleTwoTone',501,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MinusCircleTwoTone'),(6635,1001,5008,'MinusOutlined','MinusOutlined',502,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MinusOutlined'),(6636,1001,5008,'MinusSquareFilled','MinusSquareFilled',503,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MinusSquareFilled'),(6637,1001,5008,'MinusSquareOutlined','MinusSquareOutlined',504,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MinusSquareOutlined'),(6638,1001,5008,'MinusSquareTwoTone','MinusSquareTwoTone',505,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MinusSquareTwoTone'),(6639,1001,5008,'MobileFilled','MobileFilled',506,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MobileFilled'),(6640,1001,5008,'MobileOutlined','MobileOutlined',507,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MobileOutlined'),(6641,1001,5008,'MobileTwoTone','MobileTwoTone',508,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MobileTwoTone'),(6642,1001,5008,'MoneyCollectFilled','MoneyCollectFilled',509,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MoneyCollectFilled'),(6643,1001,5008,'MoneyCollectOutlined','MoneyCollectOutlined',510,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MoneyCollectOutlined'),(6644,1001,5008,'MoneyCollectTwoTone','MoneyCollectTwoTone',511,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MoneyCollectTwoTone'),(6645,1001,5008,'MonitorOutlined','MonitorOutlined',512,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MonitorOutlined'),(6646,1001,5008,'MoonFilled','MoonFilled',513,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MoonFilled'),(6647,1001,5008,'MoonOutlined','MoonOutlined',514,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MoonOutlined'),(6648,1001,5008,'MoreOutlined','MoreOutlined',515,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MoreOutlined'),(6649,1001,5008,'MutedFilled','MutedFilled',516,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MutedFilled'),(6650,1001,5008,'MutedOutlined','MutedOutlined',517,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: MutedOutlined'),(6651,1001,5008,'NodeCollapseOutlined','NodeCollapseOutlined',518,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: NodeCollapseOutlined'),(6652,1001,5008,'NodeExpandOutlined','NodeExpandOutlined',519,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: NodeExpandOutlined'),(6653,1001,5008,'NodeIndexOutlined','NodeIndexOutlined',520,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: NodeIndexOutlined'),(6654,1001,5008,'NotificationFilled','NotificationFilled',521,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: NotificationFilled'),(6655,1001,5008,'NotificationTwoTone','NotificationTwoTone',523,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: NotificationTwoTone'),(6656,1001,5008,'NumberOutlined','NumberOutlined',524,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: NumberOutlined'),(6657,1001,5008,'OneToOneOutlined','OneToOneOutlined',525,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: OneToOneOutlined'),(6658,1001,5008,'OpenAIFilled','OpenAIFilled',526,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: OpenAIFilled'),(6659,1001,5008,'OpenAIOutlined','OpenAIOutlined',527,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: OpenAIOutlined'),(6660,1001,5008,'OrderedListOutlined','OrderedListOutlined',528,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: OrderedListOutlined'),(6661,1001,5008,'PaperClipOutlined','PaperClipOutlined',529,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PaperClipOutlined'),(6662,1001,5008,'PartitionOutlined','PartitionOutlined',530,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PartitionOutlined'),(6663,1001,5008,'PauseCircleFilled','PauseCircleFilled',531,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PauseCircleFilled'),(6664,1001,5008,'PauseCircleOutlined','PauseCircleOutlined',532,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PauseCircleOutlined'),(6665,1001,5008,'PauseCircleTwoTone','PauseCircleTwoTone',533,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PauseCircleTwoTone'),(6666,1001,5008,'PauseOutlined','PauseOutlined',534,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PauseOutlined'),(6667,1001,5008,'PayCircleFilled','PayCircleFilled',535,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PayCircleFilled'),(6668,1001,5008,'PayCircleOutlined','PayCircleOutlined',536,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PayCircleOutlined'),(6669,1001,5008,'PercentageOutlined','PercentageOutlined',537,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PercentageOutlined'),(6670,1001,5008,'PhoneFilled','PhoneFilled',538,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PhoneFilled'),(6671,1001,5008,'PhoneOutlined','PhoneOutlined',539,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PhoneOutlined'),(6672,1001,5008,'PhoneTwoTone','PhoneTwoTone',540,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PhoneTwoTone'),(6673,1001,5008,'PicCenterOutlined','PicCenterOutlined',541,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PicCenterOutlined'),(6674,1001,5008,'PicLeftOutlined','PicLeftOutlined',542,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PicLeftOutlined'),(6675,1001,5008,'PicRightOutlined','PicRightOutlined',543,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PicRightOutlined'),(6676,1001,5008,'PictureFilled','PictureFilled',544,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PictureFilled'),(6677,1001,5008,'PictureOutlined','PictureOutlined',545,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PictureOutlined'),(6678,1001,5008,'PictureTwoTone','PictureTwoTone',546,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PictureTwoTone'),(6679,1001,5008,'PieChartFilled','PieChartFilled',547,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PieChartFilled'),(6680,1001,5008,'PieChartOutlined','PieChartOutlined',548,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PieChartOutlined'),(6681,1001,5008,'PieChartTwoTone','PieChartTwoTone',549,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PieChartTwoTone'),(6682,1001,5008,'PinterestFilled','PinterestFilled',550,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PinterestFilled'),(6683,1001,5008,'PinterestOutlined','PinterestOutlined',551,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PinterestOutlined'),(6684,1001,5008,'PlayCircleFilled','PlayCircleFilled',552,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlayCircleFilled'),(6685,1001,5008,'PlayCircleOutlined','PlayCircleOutlined',553,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlayCircleOutlined'),(6686,1001,5008,'PlayCircleTwoTone','PlayCircleTwoTone',554,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlayCircleTwoTone'),(6687,1001,5008,'PlaySquareFilled','PlaySquareFilled',555,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlaySquareFilled'),(6688,1001,5008,'PlaySquareOutlined','PlaySquareOutlined',556,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlaySquareOutlined'),(6689,1001,5008,'PlaySquareTwoTone','PlaySquareTwoTone',557,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlaySquareTwoTone'),(6690,1001,5008,'PlusCircleFilled','PlusCircleFilled',558,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlusCircleFilled'),(6691,1001,5008,'PlusCircleOutlined','PlusCircleOutlined',559,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlusCircleOutlined'),(6692,1001,5008,'PlusCircleTwoTone','PlusCircleTwoTone',560,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlusCircleTwoTone'),(6693,1001,5008,'PlusOutlined','PlusOutlined',561,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlusOutlined'),(6694,1001,5008,'PlusSquareFilled','PlusSquareFilled',562,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlusSquareFilled'),(6695,1001,5008,'PlusSquareOutlined','PlusSquareOutlined',563,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlusSquareOutlined'),(6696,1001,5008,'PlusSquareTwoTone','PlusSquareTwoTone',564,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PlusSquareTwoTone'),(6697,1001,5008,'PoundCircleFilled','PoundCircleFilled',565,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PoundCircleFilled'),(6698,1001,5008,'PoundCircleOutlined','PoundCircleOutlined',566,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PoundCircleOutlined'),(6699,1001,5008,'PoundCircleTwoTone','PoundCircleTwoTone',567,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PoundCircleTwoTone'),(6700,1001,5008,'PoundOutlined','PoundOutlined',568,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PoundOutlined'),(6701,1001,5008,'PoweroffOutlined','PoweroffOutlined',569,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PoweroffOutlined'),(6702,1001,5008,'PrinterFilled','PrinterFilled',570,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PrinterFilled'),(6703,1001,5008,'PrinterOutlined','PrinterOutlined',571,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PrinterOutlined'),(6704,1001,5008,'PrinterTwoTone','PrinterTwoTone',572,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PrinterTwoTone'),(6705,1001,5008,'ProductFilled','ProductFilled',573,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProductFilled'),(6706,1001,5008,'ProductOutlined','ProductOutlined',574,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProductOutlined'),(6707,1001,5008,'ProfileFilled','ProfileFilled',575,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProfileFilled'),(6708,1001,5008,'ProfileOutlined','ProfileOutlined',576,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProfileOutlined'),(6709,1001,5008,'ProfileTwoTone','ProfileTwoTone',577,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProfileTwoTone'),(6710,1001,5008,'ProjectFilled','ProjectFilled',578,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProjectFilled'),(6711,1001,5008,'ProjectOutlined','ProjectOutlined',579,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProjectOutlined'),(6712,1001,5008,'ProjectTwoTone','ProjectTwoTone',580,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ProjectTwoTone'),(6713,1001,5008,'PropertySafetyFilled','PropertySafetyFilled',581,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PropertySafetyFilled'),(6714,1001,5008,'PropertySafetyOutlined','PropertySafetyOutlined',582,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PropertySafetyOutlined'),(6715,1001,5008,'PropertySafetyTwoTone','PropertySafetyTwoTone',583,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PropertySafetyTwoTone'),(6716,1001,5008,'PullRequestOutlined','PullRequestOutlined',584,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PullRequestOutlined'),(6717,1001,5008,'PushpinFilled','PushpinFilled',585,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PushpinFilled'),(6718,1001,5008,'PushpinOutlined','PushpinOutlined',586,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PushpinOutlined'),(6719,1001,5008,'PushpinTwoTone','PushpinTwoTone',587,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PushpinTwoTone'),(6720,1001,5008,'PythonOutlined','PythonOutlined',588,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: PythonOutlined'),(6721,1001,5008,'QqCircleFilled','QqCircleFilled',589,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QqCircleFilled'),(6722,1001,5008,'QqOutlined','QqOutlined',590,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QqOutlined'),(6723,1001,5008,'QqSquareFilled','QqSquareFilled',591,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QqSquareFilled'),(6724,1001,5008,'QrcodeOutlined','QrcodeOutlined',592,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QrcodeOutlined'),(6725,1001,5008,'QuestionCircleFilled','QuestionCircleFilled',593,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QuestionCircleFilled'),(6726,1001,5008,'QuestionCircleOutlined','QuestionCircleOutlined',594,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QuestionCircleOutlined'),(6727,1001,5008,'QuestionCircleTwoTone','QuestionCircleTwoTone',595,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QuestionCircleTwoTone'),(6728,1001,5008,'QuestionOutlined','QuestionOutlined',596,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: QuestionOutlined'),(6729,1001,5008,'RadiusBottomleftOutlined','RadiusBottomleftOutlined',598,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RadiusBottomleftOutlined'),(6730,1001,5008,'RadiusBottomrightOutlined','RadiusBottomrightOutlined',599,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RadiusBottomrightOutlined'),(6731,1001,5008,'RadiusSettingOutlined','RadiusSettingOutlined',600,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RadiusSettingOutlined'),(6732,1001,5008,'RadiusUpleftOutlined','RadiusUpleftOutlined',601,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RadiusUpleftOutlined'),(6733,1001,5008,'RadiusUprightOutlined','RadiusUprightOutlined',602,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RadiusUprightOutlined'),(6734,1001,5008,'ReadFilled','ReadFilled',603,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ReadFilled'),(6735,1001,5008,'ReadOutlined','ReadOutlined',604,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ReadOutlined'),(6736,1001,5008,'ReconciliationFilled','ReconciliationFilled',605,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ReconciliationFilled'),(6737,1001,5008,'ReconciliationOutlined','ReconciliationOutlined',606,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ReconciliationOutlined'),(6738,1001,5008,'ReconciliationTwoTone','ReconciliationTwoTone',607,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ReconciliationTwoTone'),(6739,1001,5008,'RedditCircleFilled','RedditCircleFilled',608,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RedditCircleFilled'),(6740,1001,5008,'RedditOutlined','RedditOutlined',609,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RedditOutlined'),(6741,1001,5008,'RedditSquareFilled','RedditSquareFilled',610,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RedditSquareFilled'),(6742,1001,5008,'RedEnvelopeFilled','RedEnvelopeFilled',611,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RedEnvelopeFilled'),(6743,1001,5008,'RedEnvelopeOutlined','RedEnvelopeOutlined',612,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RedEnvelopeOutlined'),(6744,1001,5008,'RedEnvelopeTwoTone','RedEnvelopeTwoTone',613,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RedEnvelopeTwoTone'),(6745,1001,5008,'RedoOutlined','RedoOutlined',614,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RedoOutlined'),(6746,1001,5008,'ReloadOutlined','ReloadOutlined',615,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ReloadOutlined'),(6747,1001,5008,'RestFilled','RestFilled',616,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RestFilled'),(6748,1001,5008,'RestOutlined','RestOutlined',617,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RestOutlined'),(6749,1001,5008,'RestTwoTone','RestTwoTone',618,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RestTwoTone'),(6750,1001,5008,'RetweetOutlined','RetweetOutlined',619,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RetweetOutlined'),(6751,1001,5008,'RightCircleFilled','RightCircleFilled',620,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RightCircleFilled'),(6752,1001,5008,'RightCircleOutlined','RightCircleOutlined',621,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RightCircleOutlined'),(6753,1001,5008,'RightCircleTwoTone','RightCircleTwoTone',622,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RightCircleTwoTone'),(6754,1001,5008,'RightOutlined','RightOutlined',623,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RightOutlined'),(6755,1001,5008,'RightSquareFilled','RightSquareFilled',624,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RightSquareFilled'),(6756,1001,5008,'RightSquareOutlined','RightSquareOutlined',625,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RightSquareOutlined'),(6757,1001,5008,'RightSquareTwoTone','RightSquareTwoTone',626,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RightSquareTwoTone'),(6758,1001,5008,'RiseOutlined','RiseOutlined',627,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RiseOutlined'),(6759,1001,5008,'RobotFilled','RobotFilled',628,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RobotFilled'),(6760,1001,5008,'RocketFilled','RocketFilled',630,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RocketFilled'),(6761,1001,5008,'RocketOutlined','RocketOutlined',631,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RocketOutlined'),(6762,1001,5008,'RocketTwoTone','RocketTwoTone',632,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RocketTwoTone'),(6763,1001,5008,'RollbackOutlined','RollbackOutlined',633,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RollbackOutlined'),(6764,1001,5008,'RotateLeftOutlined','RotateLeftOutlined',634,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RotateLeftOutlined'),(6765,1001,5008,'RotateRightOutlined','RotateRightOutlined',635,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RotateRightOutlined'),(6766,1001,5008,'RubyOutlined','RubyOutlined',636,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: RubyOutlined'),(6767,1001,5008,'SafetyCertificateFilled','SafetyCertificateFilled',637,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SafetyCertificateFilled'),(6768,1001,5008,'SafetyCertificateOutlined','SafetyCertificateOutlined',638,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SafetyCertificateOutlined'),(6769,1001,5008,'SafetyCertificateTwoTone','SafetyCertificateTwoTone',639,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SafetyCertificateTwoTone'),(6770,1001,5008,'SaveFilled','SaveFilled',641,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SaveFilled'),(6771,1001,5008,'SaveOutlined','SaveOutlined',642,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SaveOutlined'),(6772,1001,5008,'SaveTwoTone','SaveTwoTone',643,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SaveTwoTone'),(6773,1001,5008,'ScanOutlined','ScanOutlined',644,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ScanOutlined'),(6774,1001,5008,'ScheduleFilled','ScheduleFilled',645,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ScheduleFilled'),(6775,1001,5008,'ScheduleOutlined','ScheduleOutlined',646,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ScheduleOutlined'),(6776,1001,5008,'ScheduleTwoTone','ScheduleTwoTone',647,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ScheduleTwoTone'),(6777,1001,5008,'ScissorOutlined','ScissorOutlined',648,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ScissorOutlined'),(6778,1001,5008,'SearchOutlined','SearchOutlined',649,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SearchOutlined'),(6779,1001,5008,'SecurityScanFilled','SecurityScanFilled',650,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SecurityScanFilled'),(6780,1001,5008,'SecurityScanOutlined','SecurityScanOutlined',651,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SecurityScanOutlined'),(6781,1001,5008,'SecurityScanTwoTone','SecurityScanTwoTone',652,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SecurityScanTwoTone'),(6782,1001,5008,'SelectOutlined','SelectOutlined',653,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SelectOutlined'),(6783,1001,5008,'SendOutlined','SendOutlined',654,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SendOutlined'),(6784,1001,5008,'SettingFilled','SettingFilled',655,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SettingFilled'),(6785,1001,5008,'SettingTwoTone','SettingTwoTone',657,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SettingTwoTone'),(6786,1001,5008,'ShakeOutlined','ShakeOutlined',658,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShakeOutlined'),(6787,1001,5008,'ShareAltOutlined','ShareAltOutlined',659,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShareAltOutlined'),(6788,1001,5008,'ShopFilled','ShopFilled',660,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShopFilled'),(6789,1001,5008,'ShopOutlined','ShopOutlined',661,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShopOutlined'),(6790,1001,5008,'ShoppingCartOutlined','ShoppingCartOutlined',662,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShoppingCartOutlined'),(6791,1001,5008,'ShoppingFilled','ShoppingFilled',663,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShoppingFilled'),(6792,1001,5008,'ShoppingOutlined','ShoppingOutlined',664,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShoppingOutlined'),(6793,1001,5008,'ShoppingTwoTone','ShoppingTwoTone',665,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShoppingTwoTone'),(6794,1001,5008,'ShopTwoTone','ShopTwoTone',666,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShopTwoTone'),(6795,1001,5008,'ShrinkOutlined','ShrinkOutlined',667,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ShrinkOutlined'),(6796,1001,5008,'SignalFilled','SignalFilled',668,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SignalFilled'),(6797,1001,5008,'SignatureFilled','SignatureFilled',669,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SignatureFilled'),(6798,1001,5008,'SignatureOutlined','SignatureOutlined',670,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SignatureOutlined'),(6799,1001,5008,'SisternodeOutlined','SisternodeOutlined',671,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SisternodeOutlined'),(6800,1001,5008,'SketchCircleFilled','SketchCircleFilled',672,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SketchCircleFilled'),(6801,1001,5008,'SketchOutlined','SketchOutlined',673,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SketchOutlined'),(6802,1001,5008,'SketchSquareFilled','SketchSquareFilled',674,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SketchSquareFilled'),(6803,1001,5008,'SkinFilled','SkinFilled',675,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SkinFilled'),(6804,1001,5008,'SkinTwoTone','SkinTwoTone',677,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SkinTwoTone'),(6805,1001,5008,'SkypeFilled','SkypeFilled',678,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SkypeFilled'),(6806,1001,5008,'SkypeOutlined','SkypeOutlined',679,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SkypeOutlined'),(6807,1001,5008,'SlackCircleFilled','SlackCircleFilled',680,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SlackCircleFilled'),(6808,1001,5008,'SlackOutlined','SlackOutlined',681,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SlackOutlined'),(6809,1001,5008,'SlackSquareFilled','SlackSquareFilled',682,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SlackSquareFilled'),(6810,1001,5008,'SlackSquareOutlined','SlackSquareOutlined',683,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SlackSquareOutlined'),(6811,1001,5008,'SlidersFilled','SlidersFilled',684,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SlidersFilled'),(6812,1001,5008,'SlidersOutlined','SlidersOutlined',685,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SlidersOutlined'),(6813,1001,5008,'SlidersTwoTone','SlidersTwoTone',686,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SlidersTwoTone'),(6814,1001,5008,'SmallDashOutlined','SmallDashOutlined',687,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SmallDashOutlined'),(6815,1001,5008,'SmileFilled','SmileFilled',688,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SmileFilled'),(6816,1001,5008,'SmileOutlined','SmileOutlined',689,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SmileOutlined'),(6817,1001,5008,'SmileTwoTone','SmileTwoTone',690,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SmileTwoTone'),(6818,1001,5008,'SnippetsFilled','SnippetsFilled',691,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SnippetsFilled'),(6819,1001,5008,'SnippetsOutlined','SnippetsOutlined',692,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SnippetsOutlined'),(6820,1001,5008,'SnippetsTwoTone','SnippetsTwoTone',693,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SnippetsTwoTone'),(6821,1001,5008,'SolutionOutlined','SolutionOutlined',694,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SolutionOutlined'),(6822,1001,5008,'SortAscendingOutlined','SortAscendingOutlined',695,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SortAscendingOutlined'),(6823,1001,5008,'SortDescendingOutlined','SortDescendingOutlined',696,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SortDescendingOutlined'),(6824,1001,5008,'SoundFilled','SoundFilled',697,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SoundFilled'),(6825,1001,5008,'SoundOutlined','SoundOutlined',698,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SoundOutlined'),(6826,1001,5008,'SoundTwoTone','SoundTwoTone',699,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SoundTwoTone'),(6827,1001,5008,'SplitCellsOutlined','SplitCellsOutlined',700,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SplitCellsOutlined'),(6828,1001,5008,'SpotifyFilled','SpotifyFilled',701,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SpotifyFilled'),(6829,1001,5008,'SpotifyOutlined','SpotifyOutlined',702,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SpotifyOutlined'),(6830,1001,5008,'StarFilled','StarFilled',703,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StarFilled'),(6831,1001,5008,'StarOutlined','StarOutlined',704,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StarOutlined'),(6832,1001,5008,'StarTwoTone','StarTwoTone',705,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StarTwoTone'),(6833,1001,5008,'StepBackwardFilled','StepBackwardFilled',706,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StepBackwardFilled'),(6834,1001,5008,'StepBackwardOutlined','StepBackwardOutlined',707,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StepBackwardOutlined'),(6835,1001,5008,'StepForwardFilled','StepForwardFilled',708,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StepForwardFilled'),(6836,1001,5008,'StepForwardOutlined','StepForwardOutlined',709,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StepForwardOutlined'),(6837,1001,5008,'StockOutlined','StockOutlined',710,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StockOutlined'),(6838,1001,5008,'StopFilled','StopFilled',711,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StopFilled'),(6839,1001,5008,'StopOutlined','StopOutlined',712,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StopOutlined'),(6840,1001,5008,'StopTwoTone','StopTwoTone',713,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StopTwoTone'),(6841,1001,5008,'StrikethroughOutlined','StrikethroughOutlined',714,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: StrikethroughOutlined'),(6842,1001,5008,'SubnodeOutlined','SubnodeOutlined',715,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SubnodeOutlined'),(6843,1001,5008,'SunFilled','SunFilled',716,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SunFilled'),(6844,1001,5008,'SunOutlined','SunOutlined',717,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SunOutlined'),(6845,1001,5008,'SwapLeftOutlined','SwapLeftOutlined',718,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SwapLeftOutlined'),(6846,1001,5008,'SwapOutlined','SwapOutlined',719,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SwapOutlined'),(6847,1001,5008,'SwapRightOutlined','SwapRightOutlined',720,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SwapRightOutlined'),(6848,1001,5008,'SwitcherFilled','SwitcherFilled',721,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SwitcherFilled'),(6849,1001,5008,'SwitcherOutlined','SwitcherOutlined',722,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SwitcherOutlined'),(6850,1001,5008,'SwitcherTwoTone','SwitcherTwoTone',723,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SwitcherTwoTone'),(6851,1001,5008,'SyncOutlined','SyncOutlined',724,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: SyncOutlined'),(6852,1001,5008,'TableOutlined','TableOutlined',725,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TableOutlined'),(6853,1001,5008,'TabletFilled','TabletFilled',726,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TabletFilled'),(6854,1001,5008,'TabletOutlined','TabletOutlined',727,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TabletOutlined'),(6855,1001,5008,'TabletTwoTone','TabletTwoTone',728,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TabletTwoTone'),(6856,1001,5008,'TagFilled','TagFilled',729,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TagFilled'),(6857,1001,5008,'TagOutlined','TagOutlined',730,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TagOutlined'),(6858,1001,5008,'TagsFilled','TagsFilled',731,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TagsFilled'),(6859,1001,5008,'TagsOutlined','TagsOutlined',732,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TagsOutlined'),(6860,1001,5008,'TagsTwoTone','TagsTwoTone',733,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TagsTwoTone'),(6861,1001,5008,'TagTwoTone','TagTwoTone',734,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TagTwoTone'),(6862,1001,5008,'TaobaoCircleFilled','TaobaoCircleFilled',735,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TaobaoCircleFilled'),(6863,1001,5008,'TaobaoCircleOutlined','TaobaoCircleOutlined',736,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TaobaoCircleOutlined'),(6864,1001,5008,'TaobaoOutlined','TaobaoOutlined',737,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TaobaoOutlined'),(6865,1001,5008,'TaobaoSquareFilled','TaobaoSquareFilled',738,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TaobaoSquareFilled'),(6866,1001,5008,'ThunderboltFilled','ThunderboltFilled',740,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ThunderboltFilled'),(6867,1001,5008,'ThunderboltOutlined','ThunderboltOutlined',741,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ThunderboltOutlined'),(6868,1001,5008,'ThunderboltTwoTone','ThunderboltTwoTone',742,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ThunderboltTwoTone'),(6869,1001,5008,'TikTokFilled','TikTokFilled',743,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TikTokFilled'),(6870,1001,5008,'TikTokOutlined','TikTokOutlined',744,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TikTokOutlined'),(6871,1001,5008,'ToolFilled','ToolFilled',745,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ToolFilled'),(6872,1001,5008,'ToolOutlined','ToolOutlined',746,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ToolOutlined'),(6873,1001,5008,'ToolTwoTone','ToolTwoTone',747,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ToolTwoTone'),(6874,1001,5008,'ToTopOutlined','ToTopOutlined',748,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ToTopOutlined'),(6875,1001,5008,'TrademarkCircleFilled','TrademarkCircleFilled',749,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TrademarkCircleFilled'),(6876,1001,5008,'TrademarkCircleOutlined','TrademarkCircleOutlined',750,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TrademarkCircleOutlined'),(6877,1001,5008,'TrademarkCircleTwoTone','TrademarkCircleTwoTone',751,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TrademarkCircleTwoTone'),(6878,1001,5008,'TrademarkOutlined','TrademarkOutlined',752,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TrademarkOutlined'),(6879,1001,5008,'TransactionOutlined','TransactionOutlined',753,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TransactionOutlined'),(6880,1001,5008,'TrophyFilled','TrophyFilled',755,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TrophyFilled'),(6881,1001,5008,'TrophyOutlined','TrophyOutlined',756,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TrophyOutlined'),(6882,1001,5008,'TrophyTwoTone','TrophyTwoTone',757,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TrophyTwoTone'),(6883,1001,5008,'TruckFilled','TruckFilled',758,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TruckFilled'),(6884,1001,5008,'TruckOutlined','TruckOutlined',759,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TruckOutlined'),(6885,1001,5008,'TwitchFilled','TwitchFilled',760,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TwitchFilled'),(6886,1001,5008,'TwitchOutlined','TwitchOutlined',761,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TwitchOutlined'),(6887,1001,5008,'TwitterCircleFilled','TwitterCircleFilled',762,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TwitterCircleFilled'),(6888,1001,5008,'TwitterOutlined','TwitterOutlined',763,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TwitterOutlined'),(6889,1001,5008,'TwitterSquareFilled','TwitterSquareFilled',764,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: TwitterSquareFilled'),(6890,1001,5008,'UnderlineOutlined','UnderlineOutlined',765,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UnderlineOutlined'),(6891,1001,5008,'UndoOutlined','UndoOutlined',766,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UndoOutlined'),(6892,1001,5008,'UngroupOutlined','UngroupOutlined',767,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UngroupOutlined'),(6893,1001,5008,'UnlockFilled','UnlockFilled',768,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UnlockFilled'),(6894,1001,5008,'UnlockOutlined','UnlockOutlined',769,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UnlockOutlined'),(6895,1001,5008,'UnlockTwoTone','UnlockTwoTone',770,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UnlockTwoTone'),(6896,1001,5008,'UnorderedListOutlined','UnorderedListOutlined',771,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UnorderedListOutlined'),(6897,1001,5008,'UpCircleFilled','UpCircleFilled',772,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UpCircleFilled'),(6898,1001,5008,'UpCircleOutlined','UpCircleOutlined',773,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UpCircleOutlined'),(6899,1001,5008,'UpCircleTwoTone','UpCircleTwoTone',774,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UpCircleTwoTone'),(6900,1001,5008,'UploadOutlined','UploadOutlined',775,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UploadOutlined'),(6901,1001,5008,'UpOutlined','UpOutlined',776,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UpOutlined'),(6902,1001,5008,'UpSquareFilled','UpSquareFilled',777,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UpSquareFilled'),(6903,1001,5008,'UpSquareOutlined','UpSquareOutlined',778,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UpSquareOutlined'),(6904,1001,5008,'UpSquareTwoTone','UpSquareTwoTone',779,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UpSquareTwoTone'),(6905,1001,5008,'UsbFilled','UsbFilled',780,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UsbFilled'),(6906,1001,5008,'UsbOutlined','UsbOutlined',781,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UsbOutlined'),(6907,1001,5008,'UsbTwoTone','UsbTwoTone',782,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UsbTwoTone'),(6908,1001,5008,'UserAddOutlined','UserAddOutlined',783,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UserAddOutlined'),(6909,1001,5008,'UserDeleteOutlined','UserDeleteOutlined',784,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UserDeleteOutlined'),(6910,1001,5008,'UsergroupAddOutlined','UsergroupAddOutlined',785,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UsergroupAddOutlined'),(6911,1001,5008,'UsergroupDeleteOutlined','UsergroupDeleteOutlined',786,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: UsergroupDeleteOutlined'),(6912,1001,5008,'VerifiedOutlined','VerifiedOutlined',789,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VerifiedOutlined'),(6913,1001,5008,'VerticalAlignBottomOutlined','VerticalAlignBottomOutlined',790,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VerticalAlignBottomOutlined'),(6914,1001,5008,'VerticalAlignMiddleOutlined','VerticalAlignMiddleOutlined',791,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VerticalAlignMiddleOutlined'),(6915,1001,5008,'VerticalAlignTopOutlined','VerticalAlignTopOutlined',792,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VerticalAlignTopOutlined'),(6916,1001,5008,'VerticalLeftOutlined','VerticalLeftOutlined',793,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VerticalLeftOutlined'),(6917,1001,5008,'VerticalRightOutlined','VerticalRightOutlined',794,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VerticalRightOutlined'),(6918,1001,5008,'VideoCameraAddOutlined','VideoCameraAddOutlined',795,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VideoCameraAddOutlined'),(6919,1001,5008,'VideoCameraFilled','VideoCameraFilled',796,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VideoCameraFilled'),(6920,1001,5008,'VideoCameraOutlined','VideoCameraOutlined',797,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VideoCameraOutlined'),(6921,1001,5008,'VideoCameraTwoTone','VideoCameraTwoTone',798,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: VideoCameraTwoTone'),(6922,1001,5008,'WalletFilled','WalletFilled',799,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WalletFilled'),(6923,1001,5008,'WalletOutlined','WalletOutlined',800,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WalletOutlined'),(6924,1001,5008,'WalletTwoTone','WalletTwoTone',801,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WalletTwoTone'),(6925,1001,5008,'WarningFilled','WarningFilled',802,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WarningFilled'),(6926,1001,5008,'WarningOutlined','WarningOutlined',803,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WarningOutlined'),(6927,1001,5008,'WarningTwoTone','WarningTwoTone',804,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WarningTwoTone'),(6928,1001,5008,'WechatFilled','WechatFilled',805,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WechatFilled'),(6929,1001,5008,'WechatOutlined','WechatOutlined',806,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WechatOutlined'),(6930,1001,5008,'WechatWorkFilled','WechatWorkFilled',807,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WechatWorkFilled'),(6931,1001,5008,'WechatWorkOutlined','WechatWorkOutlined',808,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WechatWorkOutlined'),(6932,1001,5008,'WeiboCircleFilled','WeiboCircleFilled',809,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WeiboCircleFilled'),(6933,1001,5008,'WeiboCircleOutlined','WeiboCircleOutlined',810,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WeiboCircleOutlined'),(6934,1001,5008,'WeiboOutlined','WeiboOutlined',811,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WeiboOutlined'),(6935,1001,5008,'WeiboSquareFilled','WeiboSquareFilled',812,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WeiboSquareFilled'),(6936,1001,5008,'WeiboSquareOutlined','WeiboSquareOutlined',813,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WeiboSquareOutlined'),(6937,1001,5008,'WhatsAppOutlined','WhatsAppOutlined',814,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WhatsAppOutlined'),(6938,1001,5008,'WifiOutlined','WifiOutlined',815,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WifiOutlined'),(6939,1001,5008,'WindowsFilled','WindowsFilled',816,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WindowsFilled'),(6940,1001,5008,'WindowsOutlined','WindowsOutlined',817,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WindowsOutlined'),(6941,1001,5008,'WomanOutlined','WomanOutlined',818,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: WomanOutlined'),(6942,1001,5008,'XFilled','XFilled',819,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: XFilled'),(6943,1001,5008,'XOutlined','XOutlined',820,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: XOutlined'),(6944,1001,5008,'YahooFilled','YahooFilled',821,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: YahooFilled'),(6945,1001,5008,'YahooOutlined','YahooOutlined',822,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: YahooOutlined'),(6946,1001,5008,'YoutubeFilled','YoutubeFilled',823,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: YoutubeFilled'),(6947,1001,5008,'YoutubeOutlined','YoutubeOutlined',824,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: YoutubeOutlined'),(6948,1001,5008,'YuqueFilled','YuqueFilled',825,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: YuqueFilled'),(6949,1001,5008,'YuqueOutlined','YuqueOutlined',826,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: YuqueOutlined'),(6950,1001,5008,'ZhihuCircleFilled','ZhihuCircleFilled',827,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ZhihuCircleFilled'),(6951,1001,5008,'ZhihuOutlined','ZhihuOutlined',828,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ZhihuOutlined'),(6952,1001,5008,'ZhihuSquareFilled','ZhihuSquareFilled',829,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ZhihuSquareFilled'),(6953,1001,5008,'ZoomInOutlined','ZoomInOutlined',830,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ZoomInOutlined'),(6954,1001,5008,'ZoomOutOutlined','ZoomOutOutlined',831,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED','Ant Design icon: ZoomOutOutlined');
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=5011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (5001,1001,'user_status__deleted_5001','用户状�?,0,'2026-03-29 20:37:31',0,'2026-06-18 22:05:07',1,'ENABLED',1,'系统用户状态字�?),(5002,1001,'role_type__deleted_5002','角色类型',0,'2026-03-29 20:37:31',0,'2026-06-18 22:05:07',1,'ENABLED',1,'系统角色类型字典'),(5003,1001,'gender','性别',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED',1,'用户基础资料性别选项'),(5004,1001,'region_province','省份',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED',1,'所在地区省份选项'),(5005,1001,'region_city','城市',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED',1,'所在地区城市选项'),(5006,1001,'region_district','区县',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED',1,'所在地区区县选项'),(5007,1001,'postal_code','邮政编码',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED',1,'常用地区邮政编码选项'),(5008,1001,'menu_icon','菜单图标',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'ENABLED',1,'Ant Design 菜单图标原始名称');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `module_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_payload` json DEFAULT NULL,
  `selected_fields` json DEFAULT NULL,
  `total_count` bigint DEFAULT '0',
  `file_id` bigint DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_export_task_tenant_creator` (`tenant_id`,`created_by`,`created_at`),
  KEY `idx_sys_export_task_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_export_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_export_task` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL,
  `message_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_locale` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_entry_namespace_key` (`namespace_id`,`message_key`),
  KEY `idx_sys_localization_entry_status` (`status`,`updated_at`),
  KEY `idx_sys_localization_entry_source` (`source_type`,`source_ref`),
  KEY `idx_sys_localization_entry_namespace_deleted_status` (`namespace_id`,`deleted`,`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_localization_entry` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_localization_entry` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_language` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `language_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `native_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fallback_locale` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
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

/*!40000 ALTER TABLE `sys_localization_language` DISABLE KEYS */;
INSERT INTO `sys_localization_language` VALUES (1,'zh-CN','Chinese','简体中�?,NULL,1,1,'ENABLED',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(2,'en-US','English','English','zh-CN',2,0,'ENABLED',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0);
/*!40000 ALTER TABLE `sys_localization_language` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_namespace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `namespace_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_namespace_code` (`namespace_code`),
  KEY `idx_sys_localization_namespace_status` (`status`,`sort_no`),
  KEY `idx_sys_localization_namespace_deleted_sort` (`deleted`,`sort_no`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_localization_namespace` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_localization_namespace` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_release` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_version` bigint NOT NULL,
  `fallback_locale` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bundle_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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

/*!40000 ALTER TABLE `sys_localization_release` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_localization_release` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_translation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `locale_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `translated_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `translation_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TRANSLATED',
  `machine_generated` tinyint(1) NOT NULL DEFAULT '0',
  `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `translated_by` bigint DEFAULT NULL,
  `translated_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_translation_entry_locale` (`entry_id`,`locale_code`),
  KEY `idx_sys_localization_translation_locale_status` (`locale_code`,`translation_status`),
  KEY `idx_sys_localization_translation_locale_deleted_entry` (`locale_code`,`deleted`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_localization_translation` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_localization_translation` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_localization_usage_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_line` int DEFAULT NULL,
  `source_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
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

/*!40000 ALTER TABLE `sys_localization_usage_ref` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_localization_usage_ref` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=4035 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (3001,1001,0,'dashboard.home','首页','MENU','/dashboard/home','@/pages/dashboard/Home',0,'2026-03-29 17:20:41',1001,'2026-04-07 04:07:16',0,'DashboardOutlined',0,'dashboard:view','ENABLED'),(3002,1001,0,'settings.root','系统设置','CATALOG','/settings',NULL,0,'2026-03-29 17:20:41',0,'2026-05-15 00:52:27',0,'SettingOutlined',20,'system:view','ENABLED'),(3003,1001,3002,'settings.plugins','插件管理中心','MENU','/settings/plugins','@/pages/settings/plugins',0,'2026-03-29 17:20:41',1001,'2026-05-15 00:52:27',0,'ApiOutlined',8,'plugin:management:view','ENABLED'),(3004,1001,4032,'profile.center','个人资料','MENU','/user-center/personal-center/profile','@/pages/profile/Center',0,'2026-03-29 17:20:41',0,'2026-05-15 00:52:27',0,'UserOutlined',1,'profile:view','ENABLED'),(3007,1001,3002,'settings.monitoring.audit','审计中心','MENU','/settings/audit','@/pages/settings/monitoring/Audit',0,'2026-03-29 20:37:31',0,'2026-05-15 00:52:27',0,'AuditOutlined',12,'audit:view','ENABLED'),(3008,1001,3020,'system.users','用户管理','MENU','/user-center/users','@/pages/system/users',0,'2026-03-29 20:37:31',0,'2026-04-14 02:28:36',0,'UserOutlined',21,'system:user:view','ENABLED'),(3009,1001,3020,'system.roles','角色管理','MENU','/user-center/roles','@/pages/system/roles',0,'2026-03-29 20:37:31',0,'2026-04-14 02:28:36',0,'SafetyOutlined',23,'system:role:view','ENABLED'),(3010,1001,3002,'settings.menus','菜单管理','MENU','/settings/menus','@/pages/settings/menus',0,'2026-03-29 20:37:31',1001,'2026-05-15 00:52:27',0,'MenuOutlined',1,'system:menu:view','ENABLED'),(3011,1001,3002,'settings.dicts','字典管理','MENU','/settings/dicts','@/pages/settings/dicts',0,'2026-03-29 20:37:31',1001,'2026-05-15 00:52:27',0,'DatabaseOutlined',2,'system:dict:view','ENABLED'),(3013,1001,3002,'settings.security','安全设置','MENU','/settings/security','@/pages/settings/security',0,'2026-03-30 17:42:45',1001,'2026-05-15 00:52:27',0,'SafetyOutlined',5,'system:config:view','ENABLED'),(3014,1001,3002,'settings.personalization','个性化设置','MENU','/settings/personalization','@/pages/settings/personalization',0,'2026-04-03 16:25:38',1001,'2026-05-15 00:52:27',0,'SkinOutlined',4,'system:config:view','ENABLED'),(3015,1001,3020,'system.online-users','在线用户','MENU','/user-center/online-users','@/pages/system/online-users',0,'2026-04-05 22:53:05',0,'2026-04-14 02:28:36',0,'UserSwitchOutlined',22,'system:online-user:view','ENABLED'),(3016,1001,3002,'settings.monitoring.root','系统监控','CATALOG','/settings/monitoring','@/pages/settings/monitoring/index',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'FundOutlined',10,'system:monitor:view','ENABLED'),(3017,1001,3016,'settings.monitoring.service','服务监控','MENU','/settings/monitoring/service','redirect:/settings/monitoring?tab=service',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'RadarChartOutlined',22,'system:monitor:service:view','ENABLED'),(3018,1001,3016,'settings.monitoring.redis','Redis监控','MENU','/settings/monitoring/redis','redirect:/settings/monitoring?tab=redis',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'DatabaseOutlined',23,'system:monitor:redis:view','ENABLED'),(3019,1001,3002,'settings.monitoring.api-docs','接口文档','MENU','/settings/api-docs','@/pages/settings/monitoring/ApiDocs',0,'2026-04-06 11:55:39',0,'2026-05-15 00:52:27',0,'FileTextOutlined',11,'system:monitor:docs:view','ENABLED'),(3020,1001,0,'user.center.root','用户中心','CATALOG','/user-center','@/layouts/SettingsLayout',0,'2026-04-07 04:02:31',0,'2026-04-14 02:31:58',0,'TeamOutlined',18,'user:center:view','ENABLED'),(3024,1001,3002,'settings.ai-employees','数字员工','MENU','/settings/ai-employees','@/pages/settings/ai-employees',0,'2026-05-15 00:52:27',0,'2026-06-18 22:05:07',0,'RobotOutlined',24,'ai:view','ENABLED'),(3025,1001,3002,'settings.profile-fields','字段管理','MENU','/settings/profile-fields','@/pages/settings/profile-fields',0,'2026-04-11 15:36:20',0,'2026-05-15 00:52:27',0,'FormOutlined',3,'system:config:view','ENABLED'),(3026,1001,3002,'settings.notifications','通知中心','MENU','/settings/notifications','@/pages/settings/notifications/index',0,'2026-04-14 01:30:39',0,'2026-05-15 00:52:27',0,'NotificationOutlined',7,'system:notification:view','ENABLED'),(3027,1001,3002,'settings.verification','验证管理','MENU','/settings/verification','@/pages/settings/verification',0,'2026-04-22 21:55:16',0,'2026-05-15 00:52:27',0,'SafetyOutlined',6,'system:verification:view','ENABLED'),(3029,1001,4032,'files.my','我的文件','MENU','/user-center/files','@/pages/files/Center',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:27',0,'FileOutlined',2,'system:file:view','ENABLED'),(3030,1001,3002,'settings.files','文件管理�?,'MENU','/settings/files/all','@/pages/settings/files/Center',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:27',0,'FolderOpenOutlined',9,'system:file:manage','ENABLED'),(3031,1001,3002,'localization.root','本地化中�?,'MENU','/settings/localization','@/pages/settings/localization',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'TranslationOutlined',29,'localization:view','ENABLED'),(3032,1001,0,'files.download-center','下载中心','MENU','/download-center','@/pages/files/DownloadCenter',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0,'DownloadOutlined',1,'download:center:view','ENABLED'),(3033,1001,3020,'system.departments','组织部门','MENU','/user-center/departments','@/pages/system/departments',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0,'ApartmentOutlined',22,'system:department:view','ENABLED'),(3034,1001,0,'ai.root','AI','CATALOG','/ai','redirect:/ai/assistant',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0,'RobotOutlined',2,NULL,'ENABLED'),(3035,1001,3034,'ai.assistant','AI 助手','MENU','/ai/assistant','@/pages/ai/Assistant',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0,'RobotOutlined',1,'ai:chat:send','ENABLED'),(3036,1001,3034,'ai.knowledge','知识�?,'MENU','/ai/knowledge','@/pages/ai/knowledge',0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0,'FileSearchOutlined',2,'ai:knowledge:view','ENABLED'),(3048,1001,3002,'settings.monitoring.update','平台更新','MENU','/settings/monitoring/update','redirect:/settings/monitoring?tab=update',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',1,'CloudSyncOutlined',10,'system:update:view','DISABLED'),(4032,1001,0,'user.center.personal','个人中心','CATALOG','/user-center/personal-center','@/layouts/SettingsLayout',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0,'IdcardOutlined',19,'profile:view','ENABLED');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `module_code` varchar(64) NOT NULL DEFAULT 'system',
  `permission_type` varchar(32) NOT NULL DEFAULT 'CORE',
  `remark` varchar(512) DEFAULT NULL,
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
) ENGINE=InnoDB AUTO_INCREMENT=3134 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` VALUES (1,1001,'dashboard:view','查看首页','system','CORE',NULL,'dashboard','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(2,1001,'system:view','查看系统管理','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(3,1001,'profile:view','查看个人中心','system','CORE',NULL,'profile','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(4,1001,'plugin:management:view','查看插件管理','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(5,1001,'plugin:management:upload','上传插件','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(6,1001,'plugin:management:install','安装插件','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(7,1001,'plugin:management:upgrade','升级插件','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(8,1001,'plugin:management:rollback','回滚插件','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(9,1001,'plugin:management:enable','启用插件','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(10,1001,'plugin:management:disable','停用插件','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(11,1001,'plugin:management:logs','查看插件日志','system','CORE',NULL,'plugin','CORE',NULL,0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(28,1001,'audit:view','查看审计中心','system','CORE',NULL,'audit','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(29,1001,'audit:login:view','查看登录日志','system','CORE',NULL,'audit','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(30,1001,'audit:operation:view','查看操作日志','system','CORE',NULL,'audit','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(31,1001,'iam:view','查看权限中心','system','CORE',NULL,'iam','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(32,1001,'system:user:view','查看用户管理','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(33,1001,'system:user:create','创建用户','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(34,1001,'system:user:update','编辑用户','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(35,1001,'system:user:status','启停用户','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(36,1001,'system:role:view','查看角色管理','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(37,1001,'system:role:create','创建角色','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(38,1001,'system:role:update','编辑角色','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(39,1001,'system:role:permissions','分配角色权限','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(40,1001,'system:menu:view','查看菜单管理','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(41,1001,'system:menu:create','创建菜单','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(42,1001,'system:menu:update','编辑菜单','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(43,1001,'system:menu:status','启停菜单','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(44,1001,'system:dict:view','查看字典管理','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(45,1001,'system:dict:create','创建字典','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(46,1001,'system:dict:update','编辑字典','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(47,1001,'system:config:view','查看参数配置','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(48,1001,'system:config:update','编辑参数配置','system','CORE',NULL,'system','CORE',NULL,0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(80,1001,'system:online-user:view','查看在线用户','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(81,1001,'system:online-user:kick','踢出在线会话','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(82,1001,'system:online-user:ban','封禁在线用户','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(86,1001,'system:monitor:view','查看系统监控','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(87,1001,'system:monitor:service:view','查看服务监控','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(88,1001,'system:monitor:redis:view','查看Redis监控','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(89,1001,'system:monitor:docs:view','查看接口文档','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(90,1001,'system:update:view','查看平台更新','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(91,1001,'system:update:check','检查平台更�?,'system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(97,1001,'plugin:2fa:view','查看 2FA 验证','system','CORE',NULL,'2fa','PLUGIN','2fa',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(98,1001,'plugin:2fa:manage','管理 2FA 验证','system','CORE',NULL,'2fa','PLUGIN','2fa',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(101,1001,'plugin:sms:view','查看短信验证','system','CORE',NULL,'sms','PLUGIN','sms',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(102,1001,'plugin:sms:manage','管理短信验证','system','CORE',NULL,'sms','PLUGIN','sms',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(109,1001,'user:center:view','查看用户中心','system','CORE',NULL,'user-center','CORE',NULL,0,'2026-04-11 12:00:32',0,'2026-04-11 12:00:32',0),(113,1001,'plugin:announcement:view','查看公告','system','CORE',NULL,'announcement','PLUGIN','announcement',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(114,1001,'plugin:announcement:write','维护公告','system','CORE',NULL,'announcement','PLUGIN','announcement',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(115,1001,'system:notification:view','查看通知中心','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-14 01:30:39',0,'2026-04-23 01:00:47',0),(116,1001,'system:notification:write','手动发布通知','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-14 01:30:39',0,'2026-04-22 23:54:15',0),(123,1001,'message:message:view','查看站内�?,'system','CORE',NULL,'message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(124,1001,'message:message:write','发送站内信','system','CORE',NULL,'message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(125,1001,'message:message:read','标记站内信已�?,'system','CORE',NULL,'message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(126,1001,'message:message:retract','撤回站内�?,'system','CORE',NULL,'message','CORE',NULL,0,'2026-04-19 13:46:52',0,'2026-04-23 01:00:47',0),(142,1001,'system:verification:view','查看验证管理','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(143,1001,'system:verification:manage','管理验证方式','system','CORE',NULL,'system','CORE',NULL,0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(145,1001,'download:center:view','查看下载中心','system','CORE',NULL,'download','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(146,1001,'system:file:view','查看文件中心','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(147,1001,'system:file:upload','上传文档','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(148,1001,'system:file:delete','删除文档','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(149,1001,'download:center:create','上传下载中心文件','system','CORE',NULL,'download','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(150,1001,'download:center:update','编辑下载中心文件','system','CORE',NULL,'download','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(151,1001,'download:center:delete','删除下载中心文件','system','CORE',NULL,'download','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:07',0),(152,1001,'system:file:manage','查看全站文件管理','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(153,1001,'system:file:manage:delete','删除全站文件','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(156,1001,'localization:view','查看本地化中�?,'system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(157,1001,'localization:create','新增翻译词条','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(158,1001,'localization:update','编辑翻译词条','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(159,1001,'localization:delete','删除翻译词条','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(160,1001,'localization:sync','同步翻译词条','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(161,1001,'localization:publish','发布翻译版本','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(162,1001,'localization:rollback','回滚翻译版本','system','CORE',NULL,'system','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(163,1001,'payment:view','支付设置查看','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(164,1001,'payment:config:view','支付配置查看','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(165,1001,'payment:config:update','支付配置修改','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(166,1001,'payment:config:test','支付配置测试','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(167,1001,'payment:order:view','支付订单查看','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(169,1001,'payment:order:create','支付订单创建','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(170,1001,'payment:refund:view','退款单查看','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(171,1001,'payment:refund:create','退款单创建','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(172,1001,'payment:webhook:view','Webhook 查看','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(173,1001,'payment:webhook:retry','Webhook 重试','system','CORE',NULL,'payment','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(180,1001,'ai:view','查看数字员工','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-06-18 22:05:07',0),(181,1001,'ai:employee:create','创建数字员工','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(182,1001,'ai:employee:update','编辑数字员工','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(183,1001,'ai:employee:delete','删除数字员工','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(184,1001,'ai:employee:status','启停数字员工','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(185,1001,'ai:employee:skills','配置数字员工技�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(186,1001,'ai:llm:create','创建 LLM 服务','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(187,1001,'ai:llm:update','编辑 LLM 服务','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(188,1001,'ai:llm:delete','删除 LLM 服务','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(189,1001,'ai:llm:status','启停 LLM 服务','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(190,1001,'ai:skill:view','查看技能列�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(191,1001,'ai:chat:send','发�?AI 对话','system','CORE',NULL,'ai','CORE',NULL,0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(192,1001,'system:user:delete','删除用户','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(193,1001,'system:user:sensitive:view','查看用户敏感信息','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(194,1001,'system:department:view','查看组织部门','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(195,1001,'system:department:create','创建组织部门','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(196,1001,'system:department:update','编辑组织部门','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(197,1001,'system:department:delete','删除组织部门','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(198,1001,'system:role:delete','删除角色','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(203,1001,'system:menu:delete','删除菜单','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(204,1001,'system:dict:delete','删除字典','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(205,1001,'ai:knowledge:view','查看知识�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(206,1001,'ai:knowledge:create','创建知识�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(207,1001,'ai:knowledge:update','编辑知识�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(208,1001,'ai:knowledge:delete','删除知识�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(209,1001,'ai:knowledge:document:upload','上传知识库文�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(210,1001,'ai:knowledge:document:index','重建知识库索�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(211,1001,'ai:knowledge:document:delete','删除知识库文�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(212,1001,'ai:knowledge:bind','绑定数字员工知识�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(213,1001,'ai:knowledge:query','检索知识库','system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(214,1001,'ai:knowledge:share','共享知识�?,'system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(215,1001,'ai:tool:view','查看 AI 工具','system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(216,1001,'ai:tool:execute','执行 AI 工具','system','CORE',NULL,'ai','CORE',NULL,0,'2026-06-18 22:05:04',0,'2026-06-18 22:05:04',0),(217,1001,'system:user:export','导出用户','system','CORE',NULL,'system','CORE',NULL,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3118,1001,'plugin:sensitive-words:view','查看敏感词拦�?,'system','CORE',NULL,'sensitive-words','PLUGIN','sensitive-words',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3119,1001,'plugin:sensitive-words:manage','管理敏感词拦�?,'system','CORE',NULL,'sensitive-words','PLUGIN','sensitive-words',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3120,1001,'plugin:sensitive-words:import','导入敏感�?,'system','CORE',NULL,'sensitive-words','PLUGIN','sensitive-words',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3125,1001,'system:tenant:create','创建租户','system','CORE',NULL,'tenant','CORE',NULL,0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3126,1001,'system:tenant:update','更新租户','system','CORE',NULL,'tenant','CORE',NULL,0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3127,1001,'system:tenant:delete','归档租户','system','CORE',NULL,'tenant','CORE',NULL,0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3128,1001,'system:tenant:member','管理租户成员','system','CORE',NULL,'tenant','CORE',NULL,0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3132,1001,'system:update:install','安装平台更新','system','CORE',NULL,NULL,'CORE',NULL,0,'2026-06-18 22:08:49',0,'2026-06-18 22:08:49',0),(3133,1001,'system:update:rollback','回滚平台更新','system','CORE',NULL,NULL,'CORE',NULL,0,'2026-06-18 22:08:49',0,'2026-06-18 22:08:49',0);
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
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
  `schema_mode` varchar(32) NOT NULL DEFAULT 'ISOLATED',
  `supports_hot_disable` tinyint NOT NULL DEFAULT '1',
  `supports_data_purge` tinyint NOT NULL DEFAULT '0',
  `runtime_contributions_json` json DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_definition_code` (`plugin_code`)
) ENGINE=InnoDB AUTO_INCREMENT=10002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_plugin_definition` DISABLE KEYS */;
INSERT INTO `sys_plugin_definition` VALUES (10001,'sensitive-words','敏感词拦�?,'BUSINESS','全局检测后台输入中的敏感词并阻止提�?,'lumira','1.0.0',1,'ENABLED',30,'ISOLATED',1,1,'[\"routes\", \"menus\", \"permissions\", \"importers\", \"interceptors\"]',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0);
/*!40000 ALTER TABLE `sys_plugin_definition` ENABLE KEYS */;
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

/*!40000 ALTER TABLE `sys_plugin_dependency` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_plugin_dependency` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=10002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_plugin_menu_rel` DISABLE KEYS */;
INSERT INTO `sys_plugin_menu_rel` VALUES (10001,'sensitive-words','1.0.0','plugin.sensitive-words','敏感词拦�?,'/plugins/sensitive-words','StopOutlined','plugin:sensitive-words:view','settings.root',30,0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0);
/*!40000 ALTER TABLE `sys_plugin_menu_rel` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=10004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_plugin_permission_rel` DISABLE KEYS */;
INSERT INTO `sys_plugin_permission_rel` VALUES (10001,'sensitive-words','1.0.0','plugin:sensitive-words:view','查看敏感词拦�?,'sensitive-words',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(10002,'sensitive-words','1.0.0','plugin:sensitive-words:manage','管理敏感词拦�?,'sensitive-words',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(10003,'sensitive-words','1.0.0','plugin:sensitive-words:import','导入敏感�?,'sensitive-words',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0);
/*!40000 ALTER TABLE `sys_plugin_permission_rel` ENABLE KEYS */;
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

/*!40000 ALTER TABLE `sys_plugin_runtime_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_plugin_runtime_log` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_plugin_schema_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `step_name` varchar(128) NOT NULL,
  `direction` varchar(16) NOT NULL,
  `script_path` varchar(512) DEFAULT NULL,
  `execution_status` varchar(32) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_plugin_schema_history_plugin_created` (`plugin_code`,`plugin_version`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_plugin_schema_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_plugin_schema_history` ENABLE KEYS */;
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
  UNIQUE KEY `uk_sys_plugin_tenant_rel` (`tenant_id`,`plugin_code`),
  KEY `idx_sys_plugin_tenant_current` (`tenant_id`,`enabled`,`deleted`,`plugin_code`,`plugin_version`)
) ENGINE=InnoDB AUTO_INCREMENT=10002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_plugin_tenant` DISABLE KEYS */;
INSERT INTO `sys_plugin_tenant` VALUES (10001,1001,'sensitive-words','1.0.0',1,'{\"builtin\": true}',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0);
/*!40000 ALTER TABLE `sys_plugin_tenant` ENABLE KEYS */;
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
  `lifecycle_status` varchar(32) NOT NULL DEFAULT 'INSTALLED',
  `schema_status` varchar(32) NOT NULL DEFAULT 'PENDING',
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
) ENGINE=InnoDB AUTO_INCREMENT=10002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_plugin_version` DISABLE KEYS */;
INSERT INTO `sys_plugin_version` VALUES (10001,'sensitive-words','1.0.0',NULL,NULL,NULL,NULL,NULL,NULL,'0.1.0','LOADED','LOADED','HEALTHY','ENABLED','READY',1,0,'{\"kind\": \"BUSINESS\", \"version\": \"1.0.0\", \"pluginCode\": \"sensitive-words\", \"pluginName\": \"敏感词拦截\", \"schemaMode\": \"ISOLATED\", \"pluginApiVersion\": \"1.0.0\", \"checksumAlgorithm\": \"SHA-256\", \"supportsDataPurge\": true, \"minPlatformVersion\": \"0.1.0\", \"supportsHotDisable\": true, \"runtimeContributions\": [\"routes\", \"menus\", \"permissions\", \"importers\", \"interceptors\"]}','{\"builtin\": true}',NULL,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0);
/*!40000 ALTER TABLE `sys_plugin_version` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_code` varchar(64) NOT NULL,
  `role_name` varchar(128) NOT NULL,
  `role_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `default_home_path` varchar(255) NOT NULL DEFAULT '/dashboard/home',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`,`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=2004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (2001,1001,'ADMIN','平台管理�?,'BUILTIN','/dashboard/home',0,'2026-03-30 14:28:54',0,'2026-03-30 14:28:54',0),(2003,1001,'commonuser','普通用�?,'CUSTOM','/dashboard/home',1001,'2026-04-23 02:09:41',1001,'2026-04-25 09:04:23',0);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `resource_code` varchar(128) NOT NULL DEFAULT '*',
  `scope_type` varchar(32) NOT NULL DEFAULT 'SELF',
  `custom_dept_ids` varchar(1024) DEFAULT NULL,
  `custom_user_ids` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_data_scope_resource` (`tenant_id`,`role_id`,`resource_code`),
  KEY `idx_sys_role_data_scope_role` (`tenant_id`,`role_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_role_data_scope` DISABLE KEYS */;
INSERT INTO `sys_role_data_scope` VALUES (1,1001,2001,'*','ALL',NULL,NULL,0,'2026-06-18 22:05:06',0,'2026-06-18 22:05:06',0),(2,1001,2003,'*','SELF',NULL,NULL,0,'2026-06-18 22:05:06',0,'2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `sys_role_data_scope` ENABLE KEYS */;
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
  UNIQUE KEY `uk_sys_role_permission_rel` (`tenant_id`,`role_id`,`permission_key`),
  KEY `idx_sys_role_permission_tenant_role_deleted_perm` (`tenant_id`,`role_id`,`deleted`,`permission_key`)
) ENGINE=InnoDB AUTO_INCREMENT=3059 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` VALUES (1,1001,2001,'dashboard:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(2,1001,2001,'plugin:management:disable',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(3,1001,2001,'plugin:management:enable',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(4,1001,2001,'plugin:management:install',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(5,1001,2001,'plugin:management:logs',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(6,1001,2001,'plugin:management:rollback',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(7,1001,2001,'plugin:management:upgrade',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(8,1001,2001,'plugin:management:upload',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(9,1001,2001,'plugin:management:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(10,1001,2001,'profile:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(11,1001,2001,'system:view',0,'2026-03-29 17:10:10',0,'2026-03-29 17:10:10',0),(31,1001,2001,'audit:login:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(32,1001,2001,'audit:operation:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(33,1001,2001,'audit:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(34,1001,2001,'iam:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(35,1001,2001,'system:config:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(36,1001,2001,'system:config:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(37,1001,2001,'system:dict:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(38,1001,2001,'system:dict:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(39,1001,2001,'system:dict:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(40,1001,2001,'system:menu:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(41,1001,2001,'system:menu:status',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(42,1001,2001,'system:menu:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(43,1001,2001,'system:menu:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(44,1001,2001,'system:role:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(45,1001,2001,'system:role:permissions',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(46,1001,2001,'system:role:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(47,1001,2001,'system:role:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(48,1001,2001,'system:user:create',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(49,1001,2001,'system:user:status',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(50,1001,2001,'system:user:update',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(51,1001,2001,'system:user:view',0,'2026-03-29 20:37:31',0,'2026-03-29 20:37:31',0),(93,1001,2001,'system:online-user:ban',0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(94,1001,2001,'system:online-user:kick',0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(95,1001,2001,'system:online-user:view',0,'2026-04-05 22:53:05',0,'2026-04-05 22:53:05',0),(99,1001,2001,'system:monitor:docs:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(100,1001,2001,'system:monitor:redis:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(101,1001,2001,'system:monitor:service:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(102,1001,2001,'system:monitor:view',0,'2026-04-06 11:55:39',0,'2026-04-06 11:55:39',0),(103,1001,2001,'system:update:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:07',0),(104,1001,2001,'system:update:check',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:07',0),(113,1001,2001,'plugin:2fa:view',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(114,1001,2001,'plugin:2fa:manage',0,'2026-04-10 05:45:16',0,'2026-04-12 14:08:14',0),(117,1001,2001,'plugin:sms:view',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(118,1001,2001,'plugin:sms:manage',0,'2026-04-10 23:01:15',0,'2026-04-10 23:01:15',0),(125,1001,2001,'user:center:view',0,'2026-04-11 12:00:32',0,'2026-04-11 12:00:32',0),(129,1001,2001,'plugin:announcement:view',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(130,1001,2001,'plugin:announcement:write',0,'2026-04-13 18:46:57',0,'2026-04-13 18:46:57',0),(131,1001,2001,'system:notification:view',0,'2026-04-14 01:30:39',0,'2026-04-14 01:30:39',0),(132,1001,2001,'system:notification:write',0,'2026-04-14 01:30:39',0,'2026-04-14 01:30:39',0),(140,1001,2001,'message:message:read',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(141,1001,2001,'message:message:retract',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(142,1001,2001,'message:message:view',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(143,1001,2001,'message:message:write',0,'2026-04-19 13:46:52',0,'2026-04-19 13:46:52',0),(166,1001,2001,'system:verification:manage',0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(167,1001,2001,'system:verification:view',0,'2026-04-22 21:55:16',0,'2026-04-22 21:55:16',0),(168,1001,2001,'payment:view',0,'2026-06-08 13:33:36',0,'2026-06-08 13:33:36',0),(176,1001,2003,'download:center:view',1001,'2026-06-18 22:05:05',1001,'2026-06-18 22:05:05',0),(177,1001,2003,'dashboard:view',1001,'2026-04-25 09:04:22',1001,'2026-04-25 09:04:22',0),(178,1001,2003,'profile:view',1001,'2026-04-25 09:04:22',1001,'2026-04-25 09:04:22',0),(179,1001,2001,'system:file:delete',0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(180,1001,2001,'system:file:upload',0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(181,1001,2001,'system:file:view',0,'2026-05-04 14:23:01',0,'2026-05-04 14:23:01',0),(182,1001,2001,'download:center:create',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(183,1001,2001,'download:center:update',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(184,1001,2001,'download:center:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(185,1001,2001,'system:file:manage',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(186,1001,2001,'system:file:manage:delete',0,'2026-05-15 00:52:26',0,'2026-05-15 00:52:26',0),(187,1001,2001,'download:center:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(189,1001,2001,'localization:view',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(190,1001,2001,'localization:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(191,1001,2001,'localization:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(192,1001,2001,'localization:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(193,1001,2001,'localization:sync',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(194,1001,2001,'localization:publish',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(195,1001,2001,'localization:rollback',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(196,1001,2001,'payment:config:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(197,1001,2001,'payment:config:update',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(198,1001,2001,'payment:config:test',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(199,1001,2001,'payment:order:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(200,1001,2001,'payment:order:create',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(201,1001,2001,'payment:refund:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(202,1001,2001,'payment:refund:create',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(203,1001,2001,'payment:webhook:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(204,1001,2001,'payment:webhook:retry',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(213,1001,2001,'ai:view',0,'2026-05-15 00:52:27',0,'2026-06-18 22:05:07',0),(214,1001,2001,'ai:employee:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(215,1001,2001,'ai:employee:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(216,1001,2001,'ai:employee:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(217,1001,2001,'ai:employee:status',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(218,1001,2001,'ai:employee:skills',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(219,1001,2001,'ai:llm:create',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(220,1001,2001,'ai:llm:update',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(221,1001,2001,'ai:llm:delete',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(222,1001,2001,'ai:llm:status',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(223,1001,2001,'ai:skill:view',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(224,1001,2001,'ai:chat:send',0,'2026-05-15 00:52:27',0,'2026-05-15 00:52:27',0),(225,1001,2001,'system:user:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(226,1001,2001,'system:user:sensitive:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(227,1001,2001,'system:department:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(228,1001,2001,'system:department:create',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(229,1001,2001,'system:department:update',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(230,1001,2001,'system:department:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(231,1001,2001,'system:role:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(236,1001,2001,'system:menu:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(237,1001,2001,'system:dict:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(238,1001,2001,'ai:knowledge:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(239,1001,2001,'ai:knowledge:create',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(240,1001,2001,'ai:knowledge:update',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(241,1001,2001,'ai:knowledge:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(242,1001,2001,'ai:knowledge:document:upload',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(243,1001,2001,'ai:knowledge:document:index',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(244,1001,2001,'ai:knowledge:document:delete',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(245,1001,2001,'ai:knowledge:bind',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(246,1001,2001,'ai:knowledge:query',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(247,1001,2001,'ai:knowledge:share',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(248,1001,2001,'ai:tool:view',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(249,1001,2001,'ai:tool:execute',0,'2026-06-18 22:05:05',0,'2026-06-18 22:05:05',0),(250,1001,2001,'system:user:export',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3042,1001,2001,'plugin:sensitive-words:view',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3043,1001,2001,'plugin:sensitive-words:manage',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3044,1001,2001,'plugin:sensitive-words:import',0,'2026-06-18 22:05:07',0,'2026-06-18 22:05:07',0),(3050,1001,2001,'system:tenant:create',0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3051,1001,2001,'system:tenant:update',0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3052,1001,2001,'system:tenant:delete',0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3053,1001,2001,'system:tenant:member',0,'2026-06-18 22:05:08',0,'2026-06-18 22:05:08',0),(3057,1001,2001,'system:update:install',0,'2026-06-18 22:08:49',0,'2026-06-18 22:08:49',0),(3058,1001,2001,'system:update:rollback',0,'2026-06-18 22:08:49',0,'2026-06-18 22:08:49',0);
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `word` varchar(128) NOT NULL,
  `normalized_word` varchar(128) NOT NULL,
  `category` varchar(64) DEFAULT NULL,
  `severity` varchar(32) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sensitive_word_tenant_normalized` (`tenant_id`,`normalized_word`,`deleted`),
  KEY `idx_sys_sensitive_word_tenant_enabled` (`tenant_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_sensitive_word` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_sensitive_word` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_tenant` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) NOT NULL,
  `tenant_name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(512) DEFAULT NULL,
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint unsigned DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_code` (`tenant_code`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_tenant` DISABLE KEYS */;
INSERT INTO `sys_tenant` VALUES (1001,'platform','平台默认租户','ENABLED','系统默认租户，用于兼容既有平台数据�?,0,'2026-06-18 22:05:06',0,'2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `sys_tenant` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1001,'admin','平台管理�?,'平台管理�?,NULL,NULL,NULL,NULL,NULL,NULL,'$2a$10$OoeukQEfBNqpig.E0ZnA.e3wWxfEYg.WdWXPN5in.AfiH3BQTzHDu',NULL,'admin@example.com','ENABLED',0,'2026-06-18 22:05:06',0,'2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `primary_flag` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_department_rel` (`tenant_id`,`user_id`,`dept_id`),
  KEY `idx_sys_user_department_dept` (`tenant_id`,`dept_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_user_department` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_department` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_passkey_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_handle` varchar(128) NOT NULL,
  `credential_id` varchar(512) NOT NULL,
  `public_key_cose` text NOT NULL,
  `sign_count` bigint NOT NULL DEFAULT '0',
  `transports` varchar(255) DEFAULT NULL,
  `backup_eligible` tinyint NOT NULL DEFAULT '0',
  `backup_state` tinyint NOT NULL DEFAULT '0',
  `label` varchar(128) NOT NULL DEFAULT '通行密钥',
  `last_used_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_passkey_credential_id` (`credential_id`),
  KEY `idx_passkey_user` (`tenant_id`,`user_id`,`deleted`),
  KEY `idx_passkey_user_handle` (`user_handle`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_user_passkey_credential` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_passkey_credential` ENABLE KEYS */;
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
  UNIQUE KEY `uk_sys_user_role_rel` (`tenant_id`,`user_id`,`role_id`),
  KEY `idx_sys_user_role_tenant_user_deleted` (`tenant_id`,`user_id`,`deleted`,`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1001,1001,2001,0,'2026-06-18 22:05:06',0,'2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_rel` (`tenant_id`,`user_id`),
  KEY `idx_sys_user_tenant_user_status` (`user_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_user_tenant` DISABLE KEYS */;
INSERT INTO `sys_user_tenant` VALUES (1,1001,1001,1,'ENABLED',0,'2026-06-18 22:05:06',0,'2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `sys_user_tenant` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40000 ALTER TABLE `sys_user_tenant_profile` DISABLE KEYS */;
INSERT INTO `sys_user_tenant_profile` VALUES (1,1001,1001,'平台管理�?,NULL,'zh-CN',0,'2026-06-18 22:05:06',0,'2026-06-18 22:05:06',0);
/*!40000 ALTER TABLE `sys_user_tenant_profile` ENABLE KEYS */;
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

/*!40000 ALTER TABLE `sys_user_wechat_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_wechat_binding` ENABLE KEYS */;
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
  `secret_key` varchar(512) DEFAULT NULL,
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

/*!40000 ALTER TABLE `sys_verification_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_verification_binding` ENABLE KEYS */;
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
  `setup_secret` varchar(512) DEFAULT NULL,
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

/*!40000 ALTER TABLE `sys_verification_challenge` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_verification_challenge` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
