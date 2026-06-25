-- MySQL dump 10.13  Distrib 8.4.9, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: lumira
-- ------------------------------------------------------
-- Server version	8.4.9

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

--
-- Current Database: `lumira`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `lumira` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `lumira`;

--
-- Table structure for table `ai_conversation`
--

DROP TABLE IF EXISTS `ai_conversation`;
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

--
-- Dumping data for table `ai_conversation`
--

LOCK TABLES `ai_conversation` WRITE;
/*!40000 ALTER TABLE `ai_conversation` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_conversation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_employee`
--

DROP TABLE IF EXISTS `ai_employee`;
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

--
-- Dumping data for table `ai_employee`
--

LOCK TABLES `ai_employee` WRITE;
/*!40000 ALTER TABLE `ai_employee` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_employee_knowledge_base`
--

DROP TABLE IF EXISTS `ai_employee_knowledge_base`;
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

--
-- Dumping data for table `ai_employee_knowledge_base`
--

LOCK TABLES `ai_employee_knowledge_base` WRITE;
/*!40000 ALTER TABLE `ai_employee_knowledge_base` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee_knowledge_base` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_employee_skill`
--

DROP TABLE IF EXISTS `ai_employee_skill`;
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

--
-- Dumping data for table `ai_employee_skill`
--

LOCK TABLES `ai_employee_skill` WRITE;
/*!40000 ALTER TABLE `ai_employee_skill` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee_skill` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_employee_tool_grant`
--

DROP TABLE IF EXISTS `ai_employee_tool_grant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee_tool_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `tool_code` varchar(128) NOT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `resource_code` varchar(128) DEFAULT NULL,
  `action_code` varchar(64) DEFAULT NULL,
  `permission_mode` varchar(32) NOT NULL DEFAULT 'DENY',
  `max_risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `require_confirm` tinyint NOT NULL DEFAULT '1',
  `require_approval` tinyint NOT NULL DEFAULT '0',
  `data_scope_type` varchar(32) NOT NULL DEFAULT 'SELF',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_tool_grant` (`tenant_id`,`employee_id`,`tool_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_employee_tool_grant`
--

LOCK TABLES `ai_employee_tool_grant` WRITE;
/*!40000 ALTER TABLE `ai_employee_tool_grant` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee_tool_grant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_employee_tool_grant_dept`
--

DROP TABLE IF EXISTS `ai_employee_tool_grant_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee_tool_grant_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `grant_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_employee_tool_grant_dept` (`tenant_id`,`grant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_employee_tool_grant_dept`
--

LOCK TABLES `ai_employee_tool_grant_dept` WRITE;
/*!40000 ALTER TABLE `ai_employee_tool_grant_dept` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee_tool_grant_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_employee_tool_grant_user`
--

DROP TABLE IF EXISTS `ai_employee_tool_grant_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_employee_tool_grant_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `grant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_ai_employee_tool_grant_user` (`tenant_id`,`grant_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_employee_tool_grant_user`
--

LOCK TABLES `ai_employee_tool_grant_user` WRITE;
/*!40000 ALTER TABLE `ai_employee_tool_grant_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_employee_tool_grant_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge_base`
--

DROP TABLE IF EXISTS `ai_knowledge_base`;
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
  `document_count` bigint NOT NULL DEFAULT '0',
  `chunk_count` bigint NOT NULL DEFAULT '0',
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_base_code` (`tenant_id`,`kb_code`),
  UNIQUE KEY `uk_ai_knowledge_base_owner_name` (`tenant_id`,`owner_user_id`,`name`,`is_deleted`),
  KEY `idx_ai_knowledge_base_tenant_status` (`tenant_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_owner` (`tenant_id`,`owner_user_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_access` (`tenant_id`,`owner_user_id`,`visibility_scope`,`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_base`
--

LOCK TABLES `ai_knowledge_base` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_base` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_base` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge_base_acl`
--

DROP TABLE IF EXISTS `ai_knowledge_base_acl`;
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
  KEY `idx_ai_knowledge_acl_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_base_acl_subject` (`tenant_id`,`knowledge_base_id`,`subject_type`,`subject_id`,`permission`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_base_acl`
--

LOCK TABLES `ai_knowledge_base_acl` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_base_acl` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_base_acl` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge_base_stats`
--

DROP TABLE IF EXISTS `ai_knowledge_base_stats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_base_stats` (
  `tenant_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `document_count` bigint unsigned NOT NULL DEFAULT '0',
  `chunk_count` bigint unsigned NOT NULL DEFAULT '0',
  `vector_indexed_chunk_count` bigint unsigned NOT NULL DEFAULT '0',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_base_stats`
--

LOCK TABLES `ai_knowledge_base_stats` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_base_stats` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_base_stats` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge_chunk`
--

DROP TABLE IF EXISTS `ai_knowledge_chunk`;
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
  `embedding_vector_blob` mediumblob,
  `embedding_norm` double DEFAULT NULL,
  `vector_indexed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_chunk_index` (`tenant_id`,`document_id`,`chunk_index`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_document` (`tenant_id`,`document_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_vector` (`tenant_id`,`knowledge_base_id`,`is_deleted`,`embedding_model`,`update_time`),
  KEY `idx_ai_knowledge_chunk_acl` (`tenant_id`,`knowledge_base_id`,`document_id`,`is_deleted`,`update_time`,`id`),
  FULLTEXT KEY `ft_ai_knowledge_chunk_search_text` (`search_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_chunk`
--

LOCK TABLES `ai_knowledge_chunk` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_chunk` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_chunk` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge_document`
--

DROP TABLE IF EXISTS `ai_knowledge_document`;
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
  KEY `idx_ai_knowledge_document_index_retry` (`status`,`is_deleted`,`index_next_retry_at`,`update_time`,`id`),
  KEY `idx_ai_knowledge_document_status` (`tenant_id`,`knowledge_base_id`,`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_document`
--

LOCK TABLES `ai_knowledge_document` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_document` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_document` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_llm_model`
--

DROP TABLE IF EXISTS `ai_llm_model`;
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

--
-- Dumping data for table `ai_llm_model`
--

LOCK TABLES `ai_llm_model` WRITE;
/*!40000 ALTER TABLE `ai_llm_model` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_llm_model` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_llm_service`
--

DROP TABLE IF EXISTS `ai_llm_service`;
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

--
-- Dumping data for table `ai_llm_service`
--

LOCK TABLES `ai_llm_service` WRITE;
/*!40000 ALTER TABLE `ai_llm_service` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_llm_service` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_message`
--

DROP TABLE IF EXISTS `ai_message`;
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

--
-- Dumping data for table `ai_message`
--

LOCK TABLES `ai_message` WRITE;
/*!40000 ALTER TABLE `ai_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_message_attachment`
--

DROP TABLE IF EXISTS `ai_message_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message_attachment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `message_id` bigint unsigned NOT NULL,
  `file_id` bigint unsigned DEFAULT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `file_size_bytes` bigint unsigned DEFAULT NULL,
  `public_url` varchar(512) DEFAULT NULL,
  `preview_url` varchar(512) DEFAULT NULL,
  `download_url` varchar(512) DEFAULT NULL,
  `preview_mode` varchar(32) DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_attachment_message` (`tenant_id`,`message_id`,`is_deleted`),
  KEY `idx_ai_message_attachment_conversation` (`tenant_id`,`conversation_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_message_attachment`
--

LOCK TABLES `ai_message_attachment` WRITE;
/*!40000 ALTER TABLE `ai_message_attachment` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_message_attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_skill`
--

DROP TABLE IF EXISTS `ai_skill`;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_skill`
--

LOCK TABLES `ai_skill` WRITE;
/*!40000 ALTER TABLE `ai_skill` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_skill` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_tool_audit_log`
--

DROP TABLE IF EXISTS `ai_tool_audit_log`;
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

--
-- Dumping data for table `ai_tool_audit_log`
--

LOCK TABLES `ai_tool_audit_log` WRITE;
/*!40000 ALTER TABLE `ai_tool_audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_tool_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_tool_call_plan`
--

DROP TABLE IF EXISTS `ai_tool_call_plan`;
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
  `arguments_hash` varchar(128) DEFAULT NULL,
  `authorization_snapshot_json` longtext,
  `approval_required` tinyint NOT NULL DEFAULT '0',
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
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

--
-- Dumping data for table `ai_tool_call_plan`
--

LOCK TABLES `ai_tool_call_plan` WRITE;
/*!40000 ALTER TABLE `ai_tool_call_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_tool_call_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_tool_execution_audit`
--

DROP TABLE IF EXISTS `ai_tool_execution_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_tool_execution_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `employee_id` bigint DEFAULT NULL,
  `conversation_id` bigint DEFAULT NULL,
  `pending_tool_call_id` bigint DEFAULT NULL,
  `tool_code` varchar(128) NOT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `resource_code` varchar(128) DEFAULT NULL,
  `action_code` varchar(64) DEFAULT NULL,
  `risk_level` varchar(32) NOT NULL,
  `execution_status` varchar(32) NOT NULL,
  `arguments_hash` varchar(128) DEFAULT NULL,
  `result_summary` varchar(1000) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_execution_audit_tenant_created` (`tenant_id`,`created_at`),
  KEY `idx_ai_tool_execution_audit_employee` (`tenant_id`,`employee_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_tool_execution_audit`
--

LOCK TABLES `ai_tool_execution_audit` WRITE;
/*!40000 ALTER TABLE `ai_tool_execution_audit` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_tool_execution_audit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_tool_policy`
--

DROP TABLE IF EXISTS `ai_tool_policy`;
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
  KEY `idx_ai_tool_policy_tool` (`tenant_id`,`tool_code`,`enabled`,`is_deleted`),
  KEY `idx_ai_tool_policy_runtime` (`tenant_id`,`enabled`,`is_deleted`,`tool_code`,`action_type`,`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_tool_policy`
--

LOCK TABLES `ai_tool_policy` WRITE;
/*!40000 ALTER TABLE `ai_tool_policy` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_tool_policy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiadc_activity`
--

DROP TABLE IF EXISTS `aiadc_activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `aiadc_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `locale` varchar(16) NOT NULL DEFAULT 'zh',
  `title` varchar(128) NOT NULL,
  `subtitle` varchar(64) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `icon_key` varchar(64) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '100',
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `tags` varchar(1000) DEFAULT NULL,
  `cta_label` varchar(64) DEFAULT NULL,
  `cta_href` varchar(512) DEFAULT NULL,
  `badge_text` varchar(64) DEFAULT NULL,
  `badge_tone` varchar(32) DEFAULT NULL,
  `activity_date` varchar(64) NOT NULL,
  `activity_time` varchar(64) NOT NULL,
  `location` varchar(255) NOT NULL,
  `featured` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_activity_code` (`tenant_id`,`code`,`locale`,`deleted`),
  KEY `idx_aiadc_activity_status` (`tenant_id`,`status`,`deleted`,`sort`),
  KEY `idx_aiadc_activity_featured` (`tenant_id`,`featured`,`deleted`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiadc_activity`
--

LOCK TABLES `aiadc_activity` WRITE;
/*!40000 ALTER TABLE `aiadc_activity` DISABLE KEYS */;
INSERT INTO `aiadc_activity` VALUES (1,1001,'act-20260624144802703-memk','zh','哈哈','路演活动','阿斯顿法国红酒看来','/api/uploads/2026/06/24/7-c9dd_ad5a51be.jpg',NULL,100,'published',NULL,'查看详情','/login',NULL,NULL,'2026.06.01','00:00-00:00','赛事',0,1001,'2026-06-24 14:48:02',1001,'2026-06-24 16:35:20',0);
/*!40000 ALTER TABLE `aiadc_activity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiadc_competition`
--

DROP TABLE IF EXISTS `aiadc_competition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `aiadc_competition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `locale` varchar(16) NOT NULL DEFAULT 'zh',
  `title` varchar(128) NOT NULL,
  `short_name` varchar(128) DEFAULT NULL,
  `category` varchar(64) NOT NULL,
  `level` varchar(64) DEFAULT NULL,
  `competition_level` varchar(64) DEFAULT NULL,
  `organizer` varchar(128) DEFAULT NULL,
  `organizers_json` text,
  `registration_start` varchar(64) DEFAULT NULL,
  `registration_end` varchar(64) DEFAULT NULL,
  `competition_start` varchar(64) NOT NULL,
  `competition_end` varchar(64) DEFAULT NULL,
  `location` varchar(255) NOT NULL,
  `participation_scope` varchar(255) DEFAULT NULL,
  `participation_requirement` text,
  `schedule_json` text,
  `description` varchar(1000) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `contact_name` varchar(128) DEFAULT NULL,
  `contact_qr_code_url` varchar(512) DEFAULT NULL,
  `homepage_content` mediumtext,
  `tags` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `fee_mode` varchar(16) NOT NULL DEFAULT 'TEAM',
  `entry_fee_minor` bigint NOT NULL DEFAULT '0',
  `currency` varchar(16) NOT NULL DEFAULT 'CNY',
  `featured` tinyint NOT NULL DEFAULT '0',
  `sort` int NOT NULL DEFAULT '100',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_competition_code` (`tenant_id`,`code`,`locale`,`deleted`),
  KEY `idx_aiadc_competition_category` (`tenant_id`,`category`,`deleted`,`sort`),
  KEY `idx_aiadc_competition_status` (`tenant_id`,`status`,`deleted`,`sort`),
  KEY `idx_aiadc_competition_featured` (`tenant_id`,`featured`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiadc_competition`
--

LOCK TABLES `aiadc_competition` WRITE;
/*!40000 ALTER TABLE `aiadc_competition` DISABLE KEYS */;
/*!40000 ALTER TABLE `aiadc_competition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiadc_expert`
--

DROP TABLE IF EXISTS `aiadc_expert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `aiadc_expert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `name` varchar(64) NOT NULL,
  `title` varchar(128) DEFAULT NULL,
  `organization` varchar(128) DEFAULT NULL,
  `position` varchar(128) DEFAULT NULL,
  `expertise` varchar(255) NOT NULL,
  `phone` varchar(64) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL,
  `avatar_url` varchar(512) DEFAULT NULL,
  `bio` varchar(1000) DEFAULT NULL,
  `tags` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'active',
  `sort` int NOT NULL DEFAULT '100',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_expert_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_aiadc_expert_status` (`tenant_id`,`status`,`deleted`,`sort`),
  KEY `idx_aiadc_expert_name` (`tenant_id`,`name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiadc_expert`
--

LOCK TABLES `aiadc_expert` WRITE;
/*!40000 ALTER TABLE `aiadc_expert` DISABLE KEYS */;
/*!40000 ALTER TABLE `aiadc_expert` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `aiadc_project`
--

DROP TABLE IF EXISTS `aiadc_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `aiadc_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(64) NOT NULL,
  `locale` varchar(16) NOT NULL DEFAULT 'zh',
  `title` varchar(128) NOT NULL,
  `category` varchar(64) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `owner_name` varchar(128) DEFAULT NULL,
  `rating` varchar(32) NOT NULL DEFAULT 'popular',
  `sort` int NOT NULL DEFAULT '100',
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `tags` varchar(1000) DEFAULT NULL,
  `cta_label` varchar(64) DEFAULT NULL,
  `cta_href` varchar(512) DEFAULT NULL,
  `featured` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_project_code` (`tenant_id`,`code`,`locale`,`deleted`),
  KEY `idx_aiadc_project_category` (`tenant_id`,`category`,`deleted`,`sort`),
  KEY `idx_aiadc_project_status` (`tenant_id`,`status`,`deleted`,`sort`),
  KEY `idx_aiadc_project_featured` (`tenant_id`,`featured`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `aiadc_project`
--

LOCK TABLES `aiadc_project` WRITE;
/*!40000 ALTER TABLE `aiadc_project` DISABLE KEYS */;
/*!40000 ALTER TABLE `aiadc_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_login_log`
--

DROP TABLE IF EXISTS `audit_login_log`;
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
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_login_log`
--

LOCK TABLES `audit_login_log` WRITE;
/*!40000 ALTER TABLE `audit_login_log` DISABLE KEYS */;
INSERT INTO `audit_login_log` VALUES (1,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','15dcc80d-03a5-42ba-bcbd-2928915d01e8','d550cac9-9652-4391-93d7-40440a84c54a','2026-06-24 00:41:32'),(2,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','2fef92a8-6038-46b7-bd62-6a5bf0fbc05e','52e14629-f51f-4ae6-a934-528238d792ad','2026-06-24 00:41:36'),(3,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','fdd2bc10-49cd-46c1-a994-c81d712a8f42','236e4bfc-2372-4ea2-87a5-1fdf89b4010a','2026-06-24 00:44:46'),(4,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','546db02f-9a7a-48e5-96cd-f0464d78820e','96076444-2fad-4dec-bbd5-1537d0020976','2026-06-24 00:44:46'),(5,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1',NULL,'b1eadb4a-d6b3-45d2-9cd1-bb8aeb836232','bc6f4c76-664c-497e-b178-44f1c99187f4','2026-06-24 00:53:01'),(6,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','127.0.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','fd780417-5e41-4614-8761-24fc7cdf2028','999939e9-7710-4b4b-a145-8ccece85a2d2','2026-06-24 14:16:45'),(7,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','127.0.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','ba3b182b-78cf-49c7-ba69-c0833757abd0','2c153f01-b06f-4377-b4f4-323f19997ef8','2026-06-24 14:16:45'),(8,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'127.0.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','24bff8b1-df9a-472f-9cef-53a051d7db38','2b8c597e-ede3-4fd9-b093-326c36af0c86','2026-06-24 14:16:53'),(9,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'127.0.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','18f23737-3ae6-47e6-8d20-a3267df48335','c62f9747-cae4-41b7-b4ed-ea1f16162ea5','2026-06-24 14:46:05'),(10,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','127.0.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','9942b1ab-4967-453c-bc4b-c630912c7b8d','60dc5235-4d79-4831-9836-ddc22750db53','2026-06-24 15:16:04'),(11,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','127.0.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','2ab75036-2bb9-4df7-9930-b4211755be79','eafdfe12-e0fb-4973-bd98-0c681d985e0a','2026-06-24 15:16:04'),(12,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'127.0.0.1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','d3cd7710-33e1-4f04-9340-dd8c75ff536d','531b70ec-3d79-460d-9f0f-5d248713e20a','2026-06-24 15:16:07'),(13,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','6ab2c8e2-bb23-4872-99f1-83005643abe1','26813c46-8aeb-474a-984e-5af0c5730979','2026-06-24 15:51:28'),(14,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','20b206e4-1848-4c92-bbe8-de114b4fc46c','46595f3a-09db-4b09-8bd3-3bebc196cfb5','2026-06-24 15:51:28'),(15,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','27d5a01c-d51c-4f66-a00c-4a1e181a12a5','664caed4-2bc6-4491-95db-b1da76a8a10f','2026-06-24 15:54:31'),(16,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','dab2f43c-cf24-4fe9-a324-09eec0a03a3f','4d22ca3e-8ade-4bfa-94e7-f4f264097c6a','2026-06-24 15:54:31'),(17,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','3515153f-04be-4c7c-9fa7-8262ddb60e4a','183d70c4-1d75-4f21-bbd5-56e9ee4684bd','2026-06-24 15:54:41'),(18,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','a23e292c-8a46-4a4e-81c1-56835f651910','54b746e6-d685-4141-9b68-39c0cab44c28','2026-06-24 15:54:41'),(19,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','742943fc-b0c6-47d4-bfb0-c460d416eaec','fa143d94-a0fa-4227-a3ed-ba66aebb644f','2026-06-24 15:55:06'),(20,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','0bfcfeb0-3a9b-4a10-a87d-be0e1bfd4c85','7ab53b21-f3a0-4f17-860f-01cb78906456','2026-06-24 15:55:06'),(21,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','34c4ce32-0a98-4d06-9b1a-4fd50b0f519b','880d6c71-66a4-4848-99ef-828a5d320ba3','2026-06-24 15:55:46'),(22,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','3f1b6349-0eb2-4763-b4f1-a007cc2286f8','506200ca-0ef0-44e2-b12a-b4c5d8b88874','2026-06-24 15:55:46'),(23,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','cc4ad827-3ea5-4f98-8ae2-3891ad3c77a2','5a83b124-22a6-4f30-99ae-f44a9eb415f2','2026-06-24 15:58:10'),(24,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','549f9dad-43d9-49cc-8bdd-1912b7c9ee5b','6e57dbb0-a6b8-489f-a54a-651598728ef3','2026-06-24 15:58:16'),(25,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','2ce9b4f3-914d-4e19-8f17-8e97eef1bc25','e516026e-0c37-4b95-9775-6fead6cf1352','2026-06-24 16:07:46'),(26,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','47399ef2-9e95-4cce-acdf-d1a90b43ccaf','197f3906-74fd-4625-b195-d73e57fbb362','2026-06-24 16:08:33'),(27,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','28b1c703-8335-4fda-a0ed-dece658e2d1b','46ac94c2-b9c6-480c-aa05-4848c55b28d5','2026-06-24 16:09:08'),(28,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','73ce6cb5-f49c-4e49-8fe6-5fe50ff8a450','aebab647-6d8f-4b41-a5d9-5704f8cb21db','2026-06-24 16:09:58'),(29,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','f9b66960-5c76-497a-959c-beb32748aa55','2a336c24-11f9-42b9-a6b7-cb1724834201','2026-06-24 16:10:17'),(30,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','757a8ffe-94c0-4c85-86ea-72eca3e19c52','e3537ed9-bcd5-4bde-b0f5-c49536322119','2026-06-24 16:10:54'),(31,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/149.0.0.0 Safari/537.36','1c4374b8-e07c-48a4-ba43-7a352bb303da','53f376f4-c8ea-4733-8980-e8722aec2bcf','2026-06-24 16:11:12'),(32,1001,1001,'admin','PASSWORD','SUCCESS',NULL,'0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','f17a02ee-968f-4b13-b8a3-25b73d7c1b0d','25aed34b-9f68-40df-b3bb-0a37dc65f3ef','2026-06-24 16:15:29'),(33,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','127.0.0.1','node','e99de18e-3d44-4b60-9979-4bc8235c38e0','93ff4a4c-aedf-4635-9729-9cd147e168de','2026-06-24 16:43:20'),(34,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','76e27f8c-5f9e-4cf8-8c20-e0c13df4abc5','4f7da9f5-2c03-460d-872b-956f1200d8a8','2026-06-24 16:49:07'),(35,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','04473916-7e8f-4089-b066-e2caf8e6b858','aee7e8f4-ebce-44b0-99d5-0470527aaadb','2026-06-24 16:49:07'),(36,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','d0a5d34c-071d-465a-9859-d633b7bdd8e0','053274a8-85c3-4f41-a3d8-b88142f16036','2026-06-24 16:49:11'),(37,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','c961707f-d8a2-4a10-862b-6ca3252944ac','e4a99373-13e8-49ce-8712-e4c91cb1d3a1','2026-06-24 16:49:11'),(38,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','15c1e90f-b405-4066-9bf1-0ea74c1d894e','cce1547c-65cd-421e-b91f-b196be698a4b','2026-06-24 16:49:15'),(39,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','c7ff1b13-a0aa-4bca-8cc5-6c34c9e632cc','dd7c85a0-5929-4094-b751-c1c516fd4ec9','2026-06-24 16:49:15'),(40,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','5eb829ca-bc3c-4d06-9aa9-b9bb72b196d6','9cbb7181-1d1f-4649-b595-585ed54f85f6','2026-06-24 16:49:24'),(41,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','fa4e58af-09d8-4182-9500-99962a06d581','a028798a-3fc2-4196-b537-f12b38db9c16','2026-06-24 16:49:24'),(42,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','9e5566ee-0d0d-4390-9cb6-6714490a0ae1','268069cf-9774-494a-abf1-3f4ae123e003','2026-06-24 16:51:32'),(43,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','0d3e7e97-a9f1-4bdd-8de8-1912bfea25cf','913e1231-49db-4c21-bc74-829e4c6dc00e','2026-06-24 16:51:32'),(44,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','181248b2-32e1-49b7-b1db-75f2166fa545','192ca2da-d6ae-491c-95cb-21a4ad27948f','2026-06-24 16:55:46'),(45,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','e10194c0-3c7c-451c-8b5c-969fdd6a9d77','2efa8a7d-a616-4646-9121-f390439bf011','2026-06-24 16:55:46'),(46,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','b3e54698-8ee6-462b-8f22-96ab9049bc9e','f954842f-79b8-4d12-95c3-7127f4d1189c','2026-06-24 16:55:57'),(47,1001,1001,'admin','PASSWORD','FAIL','PASSWORD_MISMATCH','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36','c375601c-d78c-4713-adf2-ee3681e01f89','2f5c95d8-173f-4d4d-8d7c-a9d4f4d315f5','2026-06-24 16:55:57');
/*!40000 ALTER TABLE `audit_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_operation_log`
--

DROP TABLE IF EXISTS `audit_operation_log`;
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_operation_log`
--

LOCK TABLES `audit_operation_log` WRITE;
/*!40000 ALTER TABLE `audit_operation_log` DISABLE KEYS */;
INSERT INTO `audit_operation_log` VALUES (1,1001,1001,'admin','profile','password','UPDATE','SUCCESS','修改登录密码','962c3ee1-d3cc-4993-90cf-281858a944eb','d0a22082-4b26-4ada-9ef5-48c16ee5e2c8',1001,'2026-06-24 00:41:35',0),(2,1001,1001,'admin','profile','password','UPDATE','SUCCESS','修改登录密码','77085b02-8072-4ea7-bb38-8fb9c98a7249','5ddce905-2d7e-4fa1-bc37-ec998dff280f',1001,'2026-06-24 15:58:16',0);
/*!40000 ALTER TABLE `audit_operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `certificate_batch`
--

DROP TABLE IF EXISTS `certificate_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `certificate_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `batch_no` varchar(64) NOT NULL,
  `batch_name` varchar(128) DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `template_version_id` bigint NOT NULL,
  `competition_id` bigint DEFAULT NULL,
  `stage_id` bigint DEFAULT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `source_ref_id` bigint DEFAULT NULL,
  `total_count` int NOT NULL DEFAULT '0',
  `success_count` int NOT NULL DEFAULT '0',
  `failed_count` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `error_message` varchar(1000) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_batch_no` (`tenant_id`,`batch_no`,`deleted`),
  KEY `idx_certificate_batch_template` (`tenant_id`,`template_id`,`template_version_id`,`deleted`),
  KEY `idx_certificate_batch_status` (`tenant_id`,`status`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `certificate_batch`
--

LOCK TABLES `certificate_batch` WRITE;
/*!40000 ALTER TABLE `certificate_batch` DISABLE KEYS */;
/*!40000 ALTER TABLE `certificate_batch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `certificate_record`
--

DROP TABLE IF EXISTS `certificate_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `certificate_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `certificate_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) NOT NULL,
  `public_token` varchar(64) NOT NULL,
  `batch_id` bigint DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `template_version_id` bigint NOT NULL,
  `competition_id` bigint DEFAULT NULL,
  `stage_id` bigint DEFAULT NULL,
  `registration_id` bigint DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `recipient_name` varchar(128) NOT NULL,
  `recipient_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `competition_title` varchar(128) DEFAULT NULL,
  `project_name` varchar(128) DEFAULT NULL,
  `team_name` varchar(128) DEFAULT NULL,
  `award_name` varchar(128) DEFAULT NULL,
  `issue_date` date NOT NULL,
  `expire_date` date DEFAULT NULL,
  `data_json` longtext NOT NULL,
  `certificate_file_id` bigint DEFAULT NULL,
  `certificate_file_url` varchar(512) DEFAULT NULL,
  `preview_image_file_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'GENERATED',
  `revoked_reason` varchar(500) DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_record_no` (`tenant_id`,`certificate_no`,`deleted`),
  UNIQUE KEY `uk_certificate_record_token` (`public_token`,`deleted`),
  KEY `idx_certificate_record_batch` (`tenant_id`,`batch_id`,`deleted`),
  KEY `idx_certificate_record_template` (`tenant_id`,`template_id`,`template_version_id`,`deleted`),
  KEY `idx_certificate_record_status` (`tenant_id`,`status`,`deleted`,`created_at`),
  KEY `idx_certificate_record_recipient` (`tenant_id`,`recipient_name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `certificate_record`
--

LOCK TABLES `certificate_record` WRITE;
/*!40000 ALTER TABLE `certificate_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `certificate_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `certificate_template`
--

DROP TABLE IF EXISTS `certificate_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `certificate_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `template_code` varchar(64) NOT NULL,
  `template_name` varchar(128) NOT NULL,
  `template_type` varchar(32) NOT NULL DEFAULT 'CERTIFICATE',
  `scene_type` varchar(32) NOT NULL DEFAULT 'COMPETITION_AWARD',
  `description` varchar(1000) DEFAULT NULL,
  `latest_version` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_template_code` (`tenant_id`,`template_code`,`deleted`),
  KEY `idx_certificate_template_status` (`tenant_id`,`status`,`deleted`,`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `certificate_template`
--

LOCK TABLES `certificate_template` WRITE;
/*!40000 ALTER TABLE `certificate_template` DISABLE KEYS */;
INSERT INTO `certificate_template` VALUES (1,1001,'CTPL-20260624143119010','金娟不过','CERTIFICATE','COMPETITION_AWARD',NULL,1,'DRAFT',1001,'2026-06-24 14:31:19',1001,'2026-06-24 14:31:19',0);
/*!40000 ALTER TABLE `certificate_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `certificate_template_version`
--

DROP TABLE IF EXISTS `certificate_template_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `certificate_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `version` int NOT NULL,
  `background_file_id` bigint DEFAULT NULL,
  `background_url` varchar(512) DEFAULT NULL,
  `page_width` int NOT NULL DEFAULT '3508',
  `page_height` int NOT NULL DEFAULT '2480',
  `orientation` varchar(16) NOT NULL DEFAULT 'LANDSCAPE',
  `unit` varchar(16) NOT NULL DEFAULT 'PX',
  `dpi` int NOT NULL DEFAULT '300',
  `canvas_json` longtext NOT NULL,
  `variable_schema_json` longtext,
  `preview_file_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_template_version` (`tenant_id`,`template_id`,`version`,`deleted`),
  KEY `idx_certificate_template_version_status` (`tenant_id`,`template_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `certificate_template_version`
--

LOCK TABLES `certificate_template_version` WRITE;
/*!40000 ALTER TABLE `certificate_template_version` DISABLE KEYS */;
INSERT INTO `certificate_template_version` VALUES (1,1001,1,1,2,'/api/uploads/2026/06/24/ChatGPT_Image_2026年6月21日_01_07_31_f361f82a.png',3508,2480,'LANDSCAPE','PX',300,'{\"page\":{\"width\":3508,\"height\":2480,\"dpi\":300,\"orientation\":\"LANDSCAPE\"},\"elements\":[\n  {\"id\":\"el_name\",\"type\":\"text\",\"fieldKey\":\"recipientName\",\"x\":1200,\"y\":920,\"width\":1100,\"height\":120,\"fontFamily\":\"Microsoft YaHei\",\"fontSize\":72,\"fontWeight\":\"bold\",\"color\":\"#222222\",\"textAlign\":\"center\",\"placeholder\":\"${recipientName}\"},\n  {\"id\":\"el_award\",\"type\":\"text\",\"fieldKey\":\"awardName\",\"x\":1200,\"y\":1200,\"width\":1100,\"height\":100,\"fontFamily\":\"Microsoft YaHei\",\"fontSize\":56,\"fontWeight\":\"normal\",\"color\":\"#222222\",\"textAlign\":\"center\",\"placeholder\":\"${awardName}\"},\n  {\"id\":\"el_qr\",\"type\":\"qrcode\",\"fieldKey\":\"verificationUrl\",\"x\":2920,\"y\":1900,\"width\":220,\"height\":220}\n]}\n','{\"variables\":[\n  {\"key\":\"recipientName\",\"label\":\"Recipient\",\"type\":\"text\",\"required\":true},\n  {\"key\":\"competitionTitle\",\"label\":\"Competition\",\"type\":\"text\",\"required\":true},\n  {\"key\":\"projectName\",\"label\":\"Project\",\"type\":\"text\",\"required\":false},\n  {\"key\":\"teamName\",\"label\":\"Team\",\"type\":\"text\",\"required\":false},\n  {\"key\":\"awardName\",\"label\":\"Award\",\"type\":\"text\",\"required\":true},\n  {\"key\":\"certificateNo\",\"label\":\"Certificate No\",\"type\":\"text\",\"required\":true},\n  {\"key\":\"issueDate\",\"label\":\"Issue Date\",\"type\":\"date\",\"required\":true},\n  {\"key\":\"verificationUrl\",\"label\":\"Verification URL\",\"type\":\"qrcode\",\"required\":true}\n]}\n',NULL,'DRAFT',1001,'2026-06-24 14:31:19',1001,'2026-06-24 14:31:50',0);
/*!40000 ALTER TABLE `certificate_template_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `certificate_verify_log`
--

DROP TABLE IF EXISTS `certificate_verify_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `certificate_verify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `certificate_id` bigint DEFAULT NULL,
  `certificate_no` varchar(64) DEFAULT NULL,
  `query_type` varchar(32) NOT NULL,
  `query_result` varchar(32) NOT NULL,
  `client_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_certificate_verify_log_certificate` (`certificate_id`,`created_at`),
  KEY `idx_certificate_verify_log_no` (`certificate_no`,`created_at`),
  KEY `idx_certificate_verify_log_result` (`query_result`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `certificate_verify_log`
--

LOCK TABLES `certificate_verify_log` WRITE;
/*!40000 ALTER TABLE `certificate_verify_log` DISABLE KEYS */;
INSERT INTO `certificate_verify_log` VALUES (1,NULL,NULL,'NOT-FOUND','CERT_NO','NOT_FOUND','127.0.0.1','Mozilla/5.0 (Windows NT; Windows NT 10.0; zh-CN) WindowsPowerShell/5.1.19041.7417','2026-06-24 13:57:38'),(2,NULL,NULL,'NOT-FOUND','CERT_NO','NOT_FOUND','127.0.0.1','Mozilla/5.0 (Windows NT; Windows NT 10.0; zh-CN) WindowsPowerShell/5.1.19041.7417','2026-06-24 14:00:52');
/*!40000 ALTER TABLE `certificate_verify_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `competition_registration`
--

DROP TABLE IF EXISTS `competition_registration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `competition_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `registration_no` varchar(64) NOT NULL,
  `competition_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
  `fee_mode` varchar(16) NOT NULL DEFAULT 'TEAM',
  `entry_fee_minor` bigint NOT NULL DEFAULT '0',
  `member_count` int NOT NULL DEFAULT '0',
  `payable_amount_minor` bigint NOT NULL DEFAULT '0',
  `currency` varchar(16) NOT NULL DEFAULT 'CNY',
  `payment_order_no` varchar(64) DEFAULT NULL,
  `participant_no` varchar(64) DEFAULT NULL,
  `team_snapshot_json` longtext,
  `project_snapshot_json` longtext,
  `member_snapshot_json` longtext,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_registration_no` (`tenant_id`,`registration_no`,`deleted`),
  UNIQUE KEY `uk_competition_registration_participant` (`tenant_id`,`participant_no`,`deleted`),
  KEY `idx_competition_registration_owner` (`tenant_id`,`owner_user_id`,`deleted`,`created_at`),
  KEY `idx_competition_registration_competition` (`tenant_id`,`competition_id`,`status`,`deleted`),
  KEY `idx_competition_registration_payment` (`tenant_id`,`payment_order_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `competition_registration`
--

LOCK TABLES `competition_registration` WRITE;
/*!40000 ALTER TABLE `competition_registration` DISABLE KEYS */;
/*!40000 ALTER TABLE `competition_registration` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `competition_stage`
--

DROP TABLE IF EXISTS `competition_stage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `competition_stage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `competition_id` bigint NOT NULL,
  `stage_code` varchar(32) NOT NULL,
  `stage_name` varchar(128) NOT NULL,
  `material_submit_start` datetime DEFAULT NULL,
  `material_submit_end` datetime DEFAULT NULL,
  `review_start` datetime DEFAULT NULL,
  `review_end` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `sort` int NOT NULL DEFAULT '100',
  `promotion_rule_type` varchar(16) DEFAULT NULL,
  `promotion_rule_value` decimal(10,2) DEFAULT NULL,
  `promotion_tie_policy` varchar(32) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_code` (`tenant_id`,`competition_id`,`stage_code`,`deleted`),
  KEY `idx_competition_stage_competition` (`tenant_id`,`competition_id`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `competition_stage`
--

LOCK TABLES `competition_stage` WRITE;
/*!40000 ALTER TABLE `competition_stage` DISABLE KEYS */;
/*!40000 ALTER TABLE `competition_stage` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `competition_stage_form`
--

DROP TABLE IF EXISTS `competition_stage_form`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `competition_stage_form` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `form_name` varchar(128) NOT NULL,
  `form_schema_json` longtext NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_form` (`tenant_id`,`stage_id`,`version`,`deleted`),
  KEY `idx_competition_stage_form_competition` (`tenant_id`,`competition_id`,`stage_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `competition_stage_form`
--

LOCK TABLES `competition_stage_form` WRITE;
/*!40000 ALTER TABLE `competition_stage_form` DISABLE KEYS */;
/*!40000 ALTER TABLE `competition_stage_form` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ddd_read_model_version`
--

DROP TABLE IF EXISTS `ddd_read_model_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ddd_read_model_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `context_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  `last_event_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rebuilt_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ddd_read_model_version_scope` (`tenant_id`,`context_name`,`scope`),
  KEY `idx_ddd_read_model_version_context` (`context_name`,`updated_at`),
  KEY `idx_ddd_read_model_version_event_key` (`last_event_key`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ddd_read_model_version`
--

LOCK TABLES `ddd_read_model_version` WRITE;
/*!40000 ALTER TABLE `ddd_read_model_version` DISABLE KEYS */;
INSERT INTO `ddd_read_model_version` VALUES (1,1001,'platform','runtime-appearance',3,'manual-branding-update','2026-06-24 00:40:14','2026-06-24 00:38:34','2026-06-24 00:40:14'),(4,1001,'IAM','permission-snapshot',2,'iam.permission.invalidate:1001','2026-06-24 00:51:44','2026-06-24 00:41:31','2026-06-24 00:51:43'),(5,1001,'plugin','bootstrap',2,'plugin.enabled','2026-06-24 00:51:44','2026-06-24 00:41:35','2026-06-24 00:51:43'),(6,1001,'message','unread',1,'initialize','2026-06-24 00:41:36','2026-06-24 00:41:36','2026-06-24 00:41:36');
/*!40000 ALTER TABLE `ddd_read_model_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_object`
--

DROP TABLE IF EXISTS `file_object`;
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_object`
--

LOCK TABLES `file_object` WRITE;
/*!40000 ALTER TABLE `file_object` DISABLE KEYS */;
INSERT INTO `file_object` VALUES (1,1001,'LOCAL','local','2026/06/24/ChatGPT_Image_2026年6月17日_19_03_25_ba9981cf.png',1001,'admin',NULL,'PUBLIC','ChatGPT_Image_2026年6月17日_19_03_25.png','png','image/png',1170154,NULL,'/api/uploads/2026/06/24/ChatGPT_Image_2026年6月17日_19_03_25_ba9981cf.png','IMAGE',1,'系统图片',NULL,'系统配置图片上传','ENABLED',1001,'2026-06-24 00:44:20',1001,'2026-06-24 00:44:20',0),(2,1001,'LOCAL','local','2026/06/24/ChatGPT_Image_2026年6月21日_01_07_31_f361f82a.png',1001,'admin',NULL,'PUBLIC','ChatGPT_Image_2026年6月21日_01_07_31.png','png','image/png',1843703,NULL,'/api/uploads/2026/06/24/ChatGPT_Image_2026年6月21日_01_07_31_f361f82a.png','IMAGE',1,'certificate-template',NULL,'certificate background','ENABLED',1001,'2026-06-24 14:31:50',1001,'2026-06-24 14:31:50',0),(3,1001,'LOCAL','local','2026/06/24/7-c9dd_208e1f03.jpg',1001,'admin',NULL,'PUBLIC','7-c9dd.jpg','jpg','image/jpeg',3250417,NULL,'/api/uploads/2026/06/24/7-c9dd_208e1f03.jpg','IMAGE',1,'系统图片',NULL,'系统配置图片上传','ENABLED',1001,'2026-06-24 14:47:16',1001,'2026-06-24 14:47:16',0),(4,1001,'LOCAL','local','2026/06/24/7-c9dd_ad5a51be.jpg',1001,'admin',NULL,'PUBLIC','7-c9dd.jpg','jpg','image/jpeg',3250417,NULL,'/api/uploads/2026/06/24/7-c9dd_ad5a51be.jpg','IMAGE',1,'系统图片',NULL,'系统配置图片上传','ENABLED',1001,'2026-06-24 14:48:00',1001,'2026-06-24 14:48:00',0),(5,1001,'LOCAL','local','2026/06/24/qrcode_for_gh_043dc9f020c6_1280_8576145c.jpg',1001,'admin',NULL,'PUBLIC','qrcode_for_gh_043dc9f020c6_1280.jpg','jpg','image/jpeg',148391,NULL,'/api/uploads/2026/06/24/qrcode_for_gh_043dc9f020c6_1280_8576145c.jpg','IMAGE',1,'系统图片',NULL,'系统配置图片上传','ENABLED',1001,'2026-06-24 14:49:46',1001,'2026-06-24 14:49:46',0),(6,1001,'LOCAL','local','2026/06/24/ChatGPT_Image_2026年6月17日_01_58_14_b74bc217.png',1001,'admin',NULL,'PUBLIC','ChatGPT_Image_2026年6月17日_01_58_14.png','png','image/png',1130127,NULL,'/api/uploads/2026/06/24/ChatGPT_Image_2026年6月17日_01_58_14_b74bc217.png','IMAGE',1,'系统图片',NULL,'系统配置图片上传','ENABLED',1001,'2026-06-24 14:52:04',1001,'2026-06-24 14:52:04',0);
/*!40000 ALTER TABLE `file_object` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_processing_artifact`
--

DROP TABLE IF EXISTS `file_processing_artifact`;
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

--
-- Dumping data for table `file_processing_artifact`
--

LOCK TABLES `file_processing_artifact` WRITE;
/*!40000 ALTER TABLE `file_processing_artifact` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_processing_artifact` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_processing_task`
--

DROP TABLE IF EXISTS `file_processing_task`;
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
  `claimed_by` varchar(128) DEFAULT NULL,
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
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
  KEY `idx_file_processing_task_tenant_created` (`tenant_id`,`deleted`,`status`,`created_at`,`id`),
  KEY `idx_file_processing_batch_claim` (`deleted`,`status`,`next_retry_at`,`priority`,`created_at`,`id`),
  KEY `idx_file_processing_claim_token` (`claim_token`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_processing_task`
--

LOCK TABLES `file_processing_task` WRITE;
/*!40000 ALTER TABLE `file_processing_task` DISABLE KEYS */;
INSERT INTO `file_processing_task` VALUES (1,1001,1,'SECURITY_SCAN','PENDING',100,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 00:44:19',1001,'2026-06-24 00:44:19',0),(2,1001,1,'THUMBNAIL','PENDING',80,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 00:44:19',1001,'2026-06-24 00:44:19',0),(3,1001,1,'OCR','PENDING',60,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 00:44:19',1001,'2026-06-24 00:44:19',0),(4,1001,2,'SECURITY_SCAN','PENDING',100,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:31:50',1001,'2026-06-24 14:31:50',0),(5,1001,2,'THUMBNAIL','PENDING',80,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:31:50',1001,'2026-06-24 14:31:50',0),(6,1001,2,'OCR','PENDING',60,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:31:50',1001,'2026-06-24 14:31:50',0),(7,1001,3,'SECURITY_SCAN','PENDING',100,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:47:16',1001,'2026-06-24 14:47:16',0),(8,1001,3,'THUMBNAIL','PENDING',80,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:47:16',1001,'2026-06-24 14:47:16',0),(9,1001,3,'OCR','PENDING',60,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:47:16',1001,'2026-06-24 14:47:16',0),(10,1001,4,'SECURITY_SCAN','PENDING',100,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:47:59',1001,'2026-06-24 14:47:59',0),(11,1001,4,'THUMBNAIL','PENDING',80,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:47:59',1001,'2026-06-24 14:47:59',0),(12,1001,4,'OCR','PENDING',60,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:47:59',1001,'2026-06-24 14:47:59',0),(13,1001,5,'SECURITY_SCAN','PENDING',100,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:49:45',1001,'2026-06-24 14:49:45',0),(14,1001,5,'THUMBNAIL','PENDING',80,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:49:45',1001,'2026-06-24 14:49:45',0),(15,1001,5,'OCR','PENDING',60,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:49:45',1001,'2026-06-24 14:49:45',0),(16,1001,6,'SECURITY_SCAN','PENDING',100,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:52:04',1001,'2026-06-24 14:52:04',0),(17,1001,6,'THUMBNAIL','PENDING',80,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:52:04',1001,'2026-06-24 14:52:04',0),(18,1001,6,'OCR','PENDING',60,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1001,'2026-06-24 14:52:04',1001,'2026-06-24 14:52:04',0);
/*!40000 ALTER TABLE `file_processing_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_storage_space`
--

DROP TABLE IF EXISTS `file_storage_space`;
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_storage_space`
--

LOCK TABLES `file_storage_space` WRITE;
/*!40000 ALTER TABLE `file_storage_space` DISABLE KEYS */;
INSERT INTO `file_storage_space` VALUES (1,1001,'用户上传文件','local','LOCAL','storage/uploads/','','','','',NULL,'APPEND_RANDOM_ID',20,'*',1,0,1,'ENABLED',1,'2026-06-24 00:42:07',1,'2026-06-24 00:42:07',0),(2,1001,'下载中心','download_center','LOCAL','storage/uploads/download_center/','','','','',NULL,'APPEND_RANDOM_ID',100,'*',0,0,1,'ENABLED',1,'2026-06-24 00:42:07',1,'2026-06-24 00:42:07',0),(3,1001,'AI 聊天附件','ai_chat','LOCAL','storage/uploads/ai_chat/','','','','',NULL,'APPEND_RANDOM_ID',20,'*',0,0,0,'ENABLED',1,'2026-06-24 00:42:07',1,'2026-06-24 00:42:07',0),(4,1001,'头像文件','avatar','LOCAL','storage/uploads/avatar/','','','','',NULL,'APPEND_RANDOM_ID',10,'*',0,0,1,'ENABLED',1,'2026-06-24 00:42:07',1,'2026-06-24 00:42:07',0),(5,1001,'Support feedback images','support_feedback','LOCAL','storage/uploads/support_feedback/','','','','',NULL,'APPEND_RANDOM_ID',20,'*',0,0,1,'ENABLED',1,'2026-06-24 14:31:50',1,'2026-06-24 14:31:50',0);
/*!40000 ALTER TABLE `file_storage_space` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_delegation_grant`
--

DROP TABLE IF EXISTS `iam_delegation_grant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_delegation_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `delegator_subject_id` bigint NOT NULL,
  `delegate_subject_id` bigint NOT NULL,
  `resource_code` varchar(128) DEFAULT NULL,
  `action_code` varchar(64) DEFAULT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `tool_code` varchar(128) DEFAULT NULL,
  `scope_type` varchar(32) NOT NULL DEFAULT 'SELF',
  `max_risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `require_confirm` tinyint NOT NULL DEFAULT '1',
  `require_approval` tinyint NOT NULL DEFAULT '0',
  `valid_from` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_delegation_delegate` (`tenant_id`,`delegate_subject_id`,`deleted`),
  KEY `idx_delegation_delegator` (`tenant_id`,`delegator_subject_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_delegation_grant`
--

LOCK TABLES `iam_delegation_grant` WRITE;
/*!40000 ALTER TABLE `iam_delegation_grant` DISABLE KEYS */;
/*!40000 ALTER TABLE `iam_delegation_grant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_permission`
--

DROP TABLE IF EXISTS `iam_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `resource_code` varchar(128) NOT NULL,
  `action_code` varchar(64) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_group` varchar(128) DEFAULT NULL,
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `require_confirm` tinyint NOT NULL DEFAULT '0',
  `require_approval` tinyint NOT NULL DEFAULT '0',
  `data_scope_required` tinyint NOT NULL DEFAULT '0',
  `source_type` varchar(32) NOT NULL DEFAULT 'SYSTEM',
  `plugin_code` varchar(128) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_permission_key` (`tenant_id`,`permission_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_permission`
--

LOCK TABLES `iam_permission` WRITE;
/*!40000 ALTER TABLE `iam_permission` DISABLE KEYS */;
/*!40000 ALTER TABLE `iam_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_subject`
--

DROP TABLE IF EXISTS `iam_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `subject_type` varchar(32) NOT NULL,
  `ref_id` bigint NOT NULL,
  `subject_code` varchar(128) DEFAULT NULL,
  `display_name` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_subject_tenant_type_ref` (`tenant_id`,`subject_type`,`ref_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_subject`
--

LOCK TABLES `iam_subject` WRITE;
/*!40000 ALTER TABLE `iam_subject` DISABLE KEYS */;
INSERT INTO `iam_subject` VALUES (1,1001,'USER',1001,'admin','Administrator','ENABLED',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(2,1001,'USER',1002,'user','Common User','ENABLED',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `iam_subject` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_subject_role`
--

DROP TABLE IF EXISTS `iam_subject_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `iam_subject_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_subject_role` (`tenant_id`,`subject_id`,`role_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_subject_role`
--

LOCK TABLES `iam_subject_role` WRITE;
/*!40000 ALTER TABLE `iam_subject_role` DISABLE KEYS */;
INSERT INTO `iam_subject_role` VALUES (1,1001,1,1001,0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(2,1001,2,1002,0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `iam_subject_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_user`
--

DROP TABLE IF EXISTS `iam_user`;
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

--
-- Dumping data for table `iam_user`
--

LOCK TABLES `iam_user` WRITE;
/*!40000 ALTER TABLE `iam_user` DISABLE KEYS */;
INSERT INTO `iam_user` VALUES (1001,'admin','Administrator',NULL,'ENABLED','SYSTEM','BOOTSTRAP_SQL','2026-06-24 00:26:01',NULL,'2026-06-24 00:26:01','2026-06-24 00:26:01',0),(1002,'user','Common User',NULL,'ENABLED','REGISTERED','BOOTSTRAP_SQL','2026-06-24 00:26:02',NULL,'2026-06-24 00:26:02','2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `iam_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_user_credential`
--

DROP TABLE IF EXISTS `iam_user_credential`;
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_user_credential`
--

LOCK TABLES `iam_user_credential` WRITE;
/*!40000 ALTER TABLE `iam_user_credential` DISABLE KEYS */;
INSERT INTO `iam_user_credential` VALUES (1,1001,'PASSWORD','$2a$10$Bd/k4przuW.qo/4X.WpeIemuQETrDb5J9gq/ymN9Mqew8tNubC1nu','BCRYPT',1,NULL,'2026-06-24 15:58:15','ENABLED','2026-06-24 00:26:02','2026-06-24 15:58:15',0),(2,1002,'PASSWORD','$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te','BCRYPT',1,NULL,'2026-06-24 00:26:02','ENABLED','2026-06-24 00:26:02','2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `iam_user_credential` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_user_device`
--

DROP TABLE IF EXISTS `iam_user_device`;
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

--
-- Dumping data for table `iam_user_device`
--

LOCK TABLES `iam_user_device` WRITE;
/*!40000 ALTER TABLE `iam_user_device` DISABLE KEYS */;
/*!40000 ALTER TABLE `iam_user_device` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_user_event`
--

DROP TABLE IF EXISTS `iam_user_event`;
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

--
-- Dumping data for table `iam_user_event`
--

LOCK TABLES `iam_user_event` WRITE;
/*!40000 ALTER TABLE `iam_user_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `iam_user_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_user_identity`
--

DROP TABLE IF EXISTS `iam_user_identity`;
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_user_identity`
--

LOCK TABLES `iam_user_identity` WRITE;
/*!40000 ALTER TABLE `iam_user_identity` DISABLE KEYS */;
INSERT INTO `iam_user_identity` VALUES (1,1001,'USERNAME','admin','admin',1,1,'2026-06-24 00:26:02',NULL,'ENABLED','2026-06-24 00:26:02','2026-06-24 00:26:02',0),(2,1002,'USERNAME','user','user',1,1,'2026-06-24 00:26:02',NULL,'ENABLED','2026-06-24 00:26:02','2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `iam_user_identity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_user_profile`
--

DROP TABLE IF EXISTS `iam_user_profile`;
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_user_profile`
--

LOCK TABLES `iam_user_profile` WRITE;
/*!40000 ALTER TABLE `iam_user_profile` DISABLE KEYS */;
INSERT INTO `iam_user_profile` VALUES (1,1001,'Administrator','Administrator',NULL,NULL,NULL,'zh-CN',NULL,NULL,NULL,'2026-06-24 00:26:02','2026-06-24 00:26:02',0),(2,1002,'Common User','Common User',NULL,NULL,NULL,'zh-CN',NULL,NULL,NULL,'2026-06-24 00:26:02','2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `iam_user_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `iam_user_security_setting`
--

DROP TABLE IF EXISTS `iam_user_security_setting`;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `iam_user_security_setting`
--

LOCK TABLES `iam_user_security_setting` WRITE;
/*!40000 ALTER TABLE `iam_user_security_setting` DISABLE KEYS */;
/*!40000 ALTER TABLE `iam_user_security_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `msg_delivery_log`
--

DROP TABLE IF EXISTS `msg_delivery_log`;
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

--
-- Dumping data for table `msg_delivery_log`
--

LOCK TABLES `msg_delivery_log` WRITE;
/*!40000 ALTER TABLE `msg_delivery_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `msg_delivery_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `msg_notice`
--

DROP TABLE IF EXISTS `msg_notice`;
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
  KEY `idx_msg_notice_visible_recent` (`tenant_id`,`publish_status`,`deleted`,`id`),
  KEY `idx_msg_notice_visible_target_user_recent` (`tenant_id`,`publish_status`,`deleted`,`target_user_id`,`id`),
  KEY `idx_msg_notice_visible_target_role_recent` (`tenant_id`,`publish_status`,`deleted`,`target_role_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `msg_notice`
--

LOCK TABLES `msg_notice` WRITE;
/*!40000 ALTER TABLE `msg_notice` DISABLE KEYS */;
/*!40000 ALTER TABLE `msg_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `msg_notice_read`
--

DROP TABLE IF EXISTS `msg_notice_read`;
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
  KEY `idx_msg_notice_read_user_created` (`tenant_id`,`user_id`,`read_at`),
  KEY `idx_msg_notice_read_tenant_notice_user_deleted` (`tenant_id`,`notice_id`,`user_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `msg_notice_read`
--

LOCK TABLES `msg_notice_read` WRITE;
/*!40000 ALTER TABLE `msg_notice_read` DISABLE KEYS */;
/*!40000 ALTER TABLE `msg_notice_read` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_event_outbox`
--

DROP TABLE IF EXISTS `payment_event_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `source_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_outbox_event` (`tenant_id`,`source_type`,`event_type`,`event_key`),
  KEY `idx_payment_outbox_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_payment_outbox_created_at` (`tenant_id`,`created_at`),
  KEY `idx_payment_outbox_deleted_status_retry_created` (`deleted`,`status`,`next_retry_at`,`created_at`,`id`),
  KEY `idx_payment_outbox_deleted_status` (`deleted`,`status`),
  KEY `idx_payment_outbox_owner_queue` (`deleted`,`source_type`,`status`,`next_retry_at`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_event_outbox`
--

LOCK TABLES `payment_event_outbox` WRITE;
/*!40000 ALTER TABLE `payment_event_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_event_outbox` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_order`
--

DROP TABLE IF EXISTS `payment_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_order_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `client_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notify_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `response_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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

--
-- Dumping data for table `payment_order`
--

LOCK TABLES `payment_order` WRITE;
/*!40000 ALTER TABLE `payment_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_provider_config`
--

DROP TABLE IF EXISTS `payment_provider_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `provider_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `environment` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `configured` tinyint(1) NOT NULL DEFAULT '0',
  `last_tested_at` datetime DEFAULT NULL,
  `last_test_success` tinyint(1) DEFAULT NULL,
  `last_test_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_provider_config_tenant_provider` (`tenant_id`,`provider_code`),
  KEY `idx_payment_provider_config_tenant_deleted` (`tenant_id`,`deleted`),
  KEY `idx_payment_provider_config_provider` (`provider_code`),
  KEY `idx_payment_provider_config_tenant_provider_deleted_id` (`tenant_id`,`provider_code`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_provider_config`
--

LOCK TABLES `payment_provider_config` WRITE;
/*!40000 ALTER TABLE `payment_provider_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_provider_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_refund`
--

DROP TABLE IF EXISTS `payment_refund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `refund_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_refund_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `response_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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

--
-- Dumping data for table `payment_refund`
--

LOCK TABLES `payment_refund` WRITE;
/*!40000 ALTER TABLE `payment_refund` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_refund` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_webhook_event`
--

DROP TABLE IF EXISTS `payment_webhook_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_webhook_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `provider_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_timestamp` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `signature` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `signature_valid` tinyint(1) NOT NULL DEFAULT '0',
  `processed` tinyint(1) NOT NULL DEFAULT '0',
  `process_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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
  KEY `idx_payment_webhook_event_status` (`tenant_id`,`processed`,`retry_count`),
  KEY `idx_payment_webhook_event_tenant_provider_nonce_deleted_received` (`tenant_id`,`provider_code`,`nonce`,`deleted`,`received_at`),
  KEY `idx_payment_webhook_event_tenant_provider_event_deleted_id` (`tenant_id`,`provider_code`,`event_id`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_webhook_event`
--

LOCK TABLES `payment_webhook_event` WRITE;
/*!40000 ALTER TABLE `payment_webhook_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_webhook_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `platform_event_outbox`
--

DROP TABLE IF EXISTS `platform_event_outbox`;
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
  `claimed_by` varchar(128) DEFAULT NULL,
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
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
  KEY `idx_platform_event_outbox_owner_queue` (`source_type`,`created_at`,`id`,`dispatch_status`,`next_retry_at`,`deleted`),
  KEY `idx_platform_event_outbox_batch_claim` (`source_type`,`deleted`,`dispatch_status`,`next_retry_at`,`created_at`,`id`),
  KEY `idx_platform_event_outbox_claim_token` (`claim_token`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `platform_event_outbox`
--

LOCK TABLES `platform_event_outbox` WRITE;
/*!40000 ALTER TABLE `platform_event_outbox` DISABLE KEYS */;
INSERT INTO `platform_event_outbox` VALUES (1,1001,NULL,'FILE','FILE_OBJECT_UPLOADED','FILE_OBJECT_UPLOADED:1001:file.object:1','{\"schemaVersion\":1,\"occurredAt\":1782233059.939606200,\"tenantId\":1001,\"userId\":null,\"aggregateType\":\"file.object\",\"aggregateId\":\"1\",\"eventId\":\"7e86516e-18a3-431c-91bb-e6443dfd4e37\",\"eventKey\":\"FILE_OBJECT_UPLOADED:1001:file.object:1\",\"attributes\":{\"contentType\":\"image/png\",\"sizeBytes\":1170154}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'b7a22739-7e31-41c9-a845-31d42acaad4a','a7905c08-4fb6-401c-8dbe-891a54a49e8c',0,'2026-06-24 00:44:20',0,'2026-06-24 00:44:20',0),(2,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:1:SECURITY_SCAN','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,0,44,19,945445700],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"1:SECURITY_SCAN\",\"attributes\":{\"fileExtension\":\"png\",\"taskType\":\"SECURITY_SCAN\",\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月17日_19_03_25_ba9981cf.png\",\"mimeType\":\"image/png\",\"fileId\":1}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'b7a22739-7e31-41c9-a845-31d42acaad4a','a7905c08-4fb6-401c-8dbe-891a54a49e8c',1001,'2026-06-24 00:44:20',1001,'2026-06-24 00:44:20',0),(3,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:1:THUMBNAIL','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,0,44,19,946541600],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"1:THUMBNAIL\",\"attributes\":{\"fileExtension\":\"png\",\"taskType\":\"THUMBNAIL\",\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月17日_19_03_25_ba9981cf.png\",\"mimeType\":\"image/png\",\"fileId\":1}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'b7a22739-7e31-41c9-a845-31d42acaad4a','a7905c08-4fb6-401c-8dbe-891a54a49e8c',1001,'2026-06-24 00:44:20',1001,'2026-06-24 00:44:20',0),(4,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:1:OCR','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,0,44,19,947612500],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"1:OCR\",\"attributes\":{\"fileExtension\":\"png\",\"taskType\":\"OCR\",\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月17日_19_03_25_ba9981cf.png\",\"mimeType\":\"image/png\",\"fileId\":1}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'b7a22739-7e31-41c9-a845-31d42acaad4a','a7905c08-4fb6-401c-8dbe-891a54a49e8c',1001,'2026-06-24 00:44:20',1001,'2026-06-24 00:44:20',0),(5,1001,NULL,'FILE','FILE_OBJECT_UPLOADED','FILE_OBJECT_UPLOADED:1001:file.object:2','{\"schemaVersion\":1,\"occurredAt\":1782282710.260272000,\"tenantId\":1001,\"userId\":null,\"aggregateType\":\"file.object\",\"aggregateId\":\"2\",\"eventId\":\"5f68a5f5-2c49-412c-8f86-9eb5f48cc81c\",\"eventKey\":\"FILE_OBJECT_UPLOADED:1001:file.object:2\",\"attributes\":{\"contentType\":\"image/png\",\"sizeBytes\":1843703}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9e61d359-b265-41d9-8222-fdbc1f3a7c26','46bc9208-d19b-4068-a17c-3deb4edbeaae',0,'2026-06-24 14:31:50',0,'2026-06-24 14:31:50',0),(6,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:2:SECURITY_SCAN','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,31,50,265271100],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"2:SECURITY_SCAN\",\"attributes\":{\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月21日_01_07_31_f361f82a.png\",\"taskType\":\"SECURITY_SCAN\",\"fileExtension\":\"png\",\"fileId\":2,\"mimeType\":\"image/png\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9e61d359-b265-41d9-8222-fdbc1f3a7c26','46bc9208-d19b-4068-a17c-3deb4edbeaae',1001,'2026-06-24 14:31:50',1001,'2026-06-24 14:31:50',0),(7,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:2:THUMBNAIL','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,31,50,265271100],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"2:THUMBNAIL\",\"attributes\":{\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月21日_01_07_31_f361f82a.png\",\"taskType\":\"THUMBNAIL\",\"fileExtension\":\"png\",\"fileId\":2,\"mimeType\":\"image/png\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9e61d359-b265-41d9-8222-fdbc1f3a7c26','46bc9208-d19b-4068-a17c-3deb4edbeaae',1001,'2026-06-24 14:31:50',1001,'2026-06-24 14:31:50',0),(8,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:2:OCR','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,31,50,266270100],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"2:OCR\",\"attributes\":{\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月21日_01_07_31_f361f82a.png\",\"taskType\":\"OCR\",\"fileExtension\":\"png\",\"fileId\":2,\"mimeType\":\"image/png\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9e61d359-b265-41d9-8222-fdbc1f3a7c26','46bc9208-d19b-4068-a17c-3deb4edbeaae',1001,'2026-06-24 14:31:50',1001,'2026-06-24 14:31:50',0),(9,1001,NULL,'FILE','FILE_OBJECT_UPLOADED','FILE_OBJECT_UPLOADED:1001:file.object:3','{\"schemaVersion\":1,\"occurredAt\":1782283636.491312000,\"tenantId\":1001,\"userId\":null,\"aggregateType\":\"file.object\",\"aggregateId\":\"3\",\"eventId\":\"d5d4834f-b219-4448-b97a-d8b80b9572c0\",\"eventKey\":\"FILE_OBJECT_UPLOADED:1001:file.object:3\",\"attributes\":{\"contentType\":\"image/jpeg\",\"sizeBytes\":3250417}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'20676be1-8d7c-4956-9e8b-335532f14eb2','563c909e-cecc-4511-a38e-41bba6855517',0,'2026-06-24 14:47:17',0,'2026-06-24 14:47:17',0),(10,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:3:SECURITY_SCAN','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,47,16,492838100],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"3:SECURITY_SCAN\",\"attributes\":{\"storagePath\":\"2026/06/24/7-c9dd_208e1f03.jpg\",\"taskType\":\"SECURITY_SCAN\",\"fileExtension\":\"jpg\",\"fileId\":3,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'20676be1-8d7c-4956-9e8b-335532f14eb2','563c909e-cecc-4511-a38e-41bba6855517',1001,'2026-06-24 14:47:17',1001,'2026-06-24 14:47:17',0),(11,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:3:THUMBNAIL','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,47,16,493838000],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"3:THUMBNAIL\",\"attributes\":{\"storagePath\":\"2026/06/24/7-c9dd_208e1f03.jpg\",\"taskType\":\"THUMBNAIL\",\"fileExtension\":\"jpg\",\"fileId\":3,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'20676be1-8d7c-4956-9e8b-335532f14eb2','563c909e-cecc-4511-a38e-41bba6855517',1001,'2026-06-24 14:47:17',1001,'2026-06-24 14:47:17',0),(12,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:3:OCR','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,47,16,494836200],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"3:OCR\",\"attributes\":{\"storagePath\":\"2026/06/24/7-c9dd_208e1f03.jpg\",\"taskType\":\"OCR\",\"fileExtension\":\"jpg\",\"fileId\":3,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'20676be1-8d7c-4956-9e8b-335532f14eb2','563c909e-cecc-4511-a38e-41bba6855517',1001,'2026-06-24 14:47:17',1001,'2026-06-24 14:47:17',0),(13,1001,NULL,'FILE','FILE_OBJECT_UPLOADED','FILE_OBJECT_UPLOADED:1001:file.object:4','{\"schemaVersion\":1,\"occurredAt\":1782283679.873711900,\"tenantId\":1001,\"userId\":null,\"aggregateType\":\"file.object\",\"aggregateId\":\"4\",\"eventId\":\"4c959bca-e3e9-4137-87f8-413bd6c73ba2\",\"eventKey\":\"FILE_OBJECT_UPLOADED:1001:file.object:4\",\"attributes\":{\"contentType\":\"image/jpeg\",\"sizeBytes\":3250417}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'c783e01e-3a20-42dc-86d2-c0f06395d2bb','9e1eb428-0edd-4cdf-a865-32165e338226',0,'2026-06-24 14:48:00',0,'2026-06-24 14:48:00',0),(14,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:4:SECURITY_SCAN','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,47,59,874725800],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"4:SECURITY_SCAN\",\"attributes\":{\"storagePath\":\"2026/06/24/7-c9dd_ad5a51be.jpg\",\"taskType\":\"SECURITY_SCAN\",\"fileExtension\":\"jpg\",\"fileId\":4,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'c783e01e-3a20-42dc-86d2-c0f06395d2bb','9e1eb428-0edd-4cdf-a865-32165e338226',1001,'2026-06-24 14:48:00',1001,'2026-06-24 14:48:00',0),(15,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:4:THUMBNAIL','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,47,59,874725800],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"4:THUMBNAIL\",\"attributes\":{\"storagePath\":\"2026/06/24/7-c9dd_ad5a51be.jpg\",\"taskType\":\"THUMBNAIL\",\"fileExtension\":\"jpg\",\"fileId\":4,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'c783e01e-3a20-42dc-86d2-c0f06395d2bb','9e1eb428-0edd-4cdf-a865-32165e338226',1001,'2026-06-24 14:48:00',1001,'2026-06-24 14:48:00',0),(16,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:4:OCR','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,47,59,875727200],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"4:OCR\",\"attributes\":{\"storagePath\":\"2026/06/24/7-c9dd_ad5a51be.jpg\",\"taskType\":\"OCR\",\"fileExtension\":\"jpg\",\"fileId\":4,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'c783e01e-3a20-42dc-86d2-c0f06395d2bb','9e1eb428-0edd-4cdf-a865-32165e338226',1001,'2026-06-24 14:48:00',1001,'2026-06-24 14:48:00',0),(17,1001,NULL,'FILE','FILE_OBJECT_UPLOADED','FILE_OBJECT_UPLOADED:1001:file.object:5','{\"schemaVersion\":1,\"occurredAt\":1782283785.696602400,\"tenantId\":1001,\"userId\":null,\"aggregateType\":\"file.object\",\"aggregateId\":\"5\",\"eventId\":\"ba341aa1-c1fe-41c5-a929-21b2d375d534\",\"eventKey\":\"FILE_OBJECT_UPLOADED:1001:file.object:5\",\"attributes\":{\"contentType\":\"image/jpeg\",\"sizeBytes\":148391}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9bc001fd-9470-44dc-a5d7-02c67411f712','d5e42f05-e740-41f1-84a5-c4e941173f29',0,'2026-06-24 14:49:46',0,'2026-06-24 14:49:46',0),(18,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:5:SECURITY_SCAN','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,49,45,697741100],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"5:SECURITY_SCAN\",\"attributes\":{\"storagePath\":\"2026/06/24/qrcode_for_gh_043dc9f020c6_1280_8576145c.jpg\",\"taskType\":\"SECURITY_SCAN\",\"fileExtension\":\"jpg\",\"fileId\":5,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9bc001fd-9470-44dc-a5d7-02c67411f712','d5e42f05-e740-41f1-84a5-c4e941173f29',1001,'2026-06-24 14:49:46',1001,'2026-06-24 14:49:46',0),(19,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:5:THUMBNAIL','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,49,45,697777300],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"5:THUMBNAIL\",\"attributes\":{\"storagePath\":\"2026/06/24/qrcode_for_gh_043dc9f020c6_1280_8576145c.jpg\",\"taskType\":\"THUMBNAIL\",\"fileExtension\":\"jpg\",\"fileId\":5,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9bc001fd-9470-44dc-a5d7-02c67411f712','d5e42f05-e740-41f1-84a5-c4e941173f29',1001,'2026-06-24 14:49:46',1001,'2026-06-24 14:49:46',0),(20,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:5:OCR','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,49,45,698446900],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"5:OCR\",\"attributes\":{\"storagePath\":\"2026/06/24/qrcode_for_gh_043dc9f020c6_1280_8576145c.jpg\",\"taskType\":\"OCR\",\"fileExtension\":\"jpg\",\"fileId\":5,\"mimeType\":\"image/jpeg\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'9bc001fd-9470-44dc-a5d7-02c67411f712','d5e42f05-e740-41f1-84a5-c4e941173f29',1001,'2026-06-24 14:49:46',1001,'2026-06-24 14:49:46',0),(21,1001,NULL,'FILE','FILE_OBJECT_UPLOADED','FILE_OBJECT_UPLOADED:1001:file.object:6','{\"schemaVersion\":1,\"occurredAt\":1782283924.109113000,\"tenantId\":1001,\"userId\":null,\"aggregateType\":\"file.object\",\"aggregateId\":\"6\",\"eventId\":\"5653eca7-3602-466f-b45c-9d0550767b00\",\"eventKey\":\"FILE_OBJECT_UPLOADED:1001:file.object:6\",\"attributes\":{\"contentType\":\"image/png\",\"sizeBytes\":1130127}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'43beeee7-450f-43b1-9a3a-d61dea962078','ead2a0e9-a715-4bb4-8a6c-5b917adfe56d',0,'2026-06-24 14:52:04',0,'2026-06-24 14:52:04',0),(22,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:6:SECURITY_SCAN','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,52,4,109637300],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"6:SECURITY_SCAN\",\"attributes\":{\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月17日_01_58_14_b74bc217.png\",\"taskType\":\"SECURITY_SCAN\",\"fileExtension\":\"png\",\"fileId\":6,\"mimeType\":\"image/png\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'43beeee7-450f-43b1-9a3a-d61dea962078','ead2a0e9-a715-4bb4-8a6c-5b917adfe56d',1001,'2026-06-24 14:52:04',1001,'2026-06-24 14:52:04',0),(23,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:6:THUMBNAIL','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,52,4,110633700],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"6:THUMBNAIL\",\"attributes\":{\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月17日_01_58_14_b74bc217.png\",\"taskType\":\"THUMBNAIL\",\"fileExtension\":\"png\",\"fileId\":6,\"mimeType\":\"image/png\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'43beeee7-450f-43b1-9a3a-d61dea962078','ead2a0e9-a715-4bb4-8a6c-5b917adfe56d',1001,'2026-06-24 14:52:04',1001,'2026-06-24 14:52:04',0),(24,1001,1001,'FILE','FileProcessingTaskRequested','FileProcessingTaskRequested:1001:file.processing_task:6:OCR','{\"schemaVersion\":1,\"occurredAt\":[2026,6,24,14,52,4,110633700],\"tenantId\":1001,\"userId\":1001,\"aggregateType\":\"file.processing_task\",\"aggregateId\":\"6:OCR\",\"attributes\":{\"storagePath\":\"2026/06/24/ChatGPT_Image_2026年6月17日_01_58_14_b74bc217.png\",\"taskType\":\"OCR\",\"fileExtension\":\"png\",\"fileId\":6,\"mimeType\":\"image/png\"}}','RECORDED',0,NULL,NULL,NULL,NULL,NULL,NULL,'43beeee7-450f-43b1-9a3a-d61dea962078','ead2a0e9-a715-4bb4-8a6c-5b917adfe56d',1001,'2026-06-24 14:52:04',1001,'2026-06-24 14:52:04',0);
/*!40000 ALTER TABLE `platform_event_outbox` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `platform_update_task`
--

DROP TABLE IF EXISTS `platform_update_task`;
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

--
-- Dumping data for table `platform_update_task`
--

LOCK TABLES `platform_update_task` WRITE;
/*!40000 ALTER TABLE `platform_update_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `platform_update_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plugin_event_outbox`
--

DROP TABLE IF EXISTS `plugin_event_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plugin_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `event_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_key` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_event_outbox_event` (`tenant_id`,`event_type`,`event_key`),
  KEY `idx_plugin_event_outbox_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_plugin_event_outbox_created_at` (`created_at`),
  KEY `idx_plugin_event_outbox_deleted_status_retry_created` (`deleted`,`status`,`next_retry_at`,`created_at`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plugin_event_outbox`
--

LOCK TABLES `plugin_event_outbox` WRITE;
/*!40000 ALTER TABLE `plugin_event_outbox` DISABLE KEYS */;
INSERT INTO `plugin_event_outbox` VALUES (1,1001,0,'PLUGIN_TENANT_ENABLED','PLUGIN_TENANT_ENABLED:1001:plugin.tenant-plugin:sensitive-words','{\"eventId\": \"f3f139a0-07d7-476c-bb2b-97f3abc9a3c9\", \"eventKey\": \"PLUGIN_TENANT_ENABLED:1001:plugin.tenant-plugin:sensitive-words\", \"tenantId\": 1001, \"eventType\": \"PLUGIN_TENANT_ENABLED\", \"attributes\": {\"version\": \"1.0.0\"}, \"occurredAt\": \"2026-06-23T16:51:43.740437200Z\", \"aggregateId\": \"sensitive-words\", \"aggregateType\": \"plugin.tenant-plugin\", \"schemaVersion\": 1}','PENDING',0,'2026-06-24 00:51:44',NULL,0,'2026-06-24 00:51:43',0,'2026-06-24 00:51:43',0);
/*!40000 ALTER TABLE `plugin_event_outbox` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `registration_material_submission`
--

DROP TABLE IF EXISTS `registration_material_submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `registration_material_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `form_version` int NOT NULL DEFAULT '1',
  `submitter_user_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `submitted_at` datetime DEFAULT NULL,
  `locked_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_material_submission` (`tenant_id`,`registration_id`,`stage_id`,`deleted`),
  KEY `idx_registration_material_submission_competition` (`tenant_id`,`competition_id`,`stage_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `registration_material_submission`
--

LOCK TABLES `registration_material_submission` WRITE;
/*!40000 ALTER TABLE `registration_material_submission` DISABLE KEYS */;
/*!40000 ALTER TABLE `registration_material_submission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `registration_material_value`
--

DROP TABLE IF EXISTS `registration_material_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `registration_material_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `submission_id` bigint NOT NULL,
  `field_key` varchar(128) NOT NULL,
  `field_type` varchar(32) NOT NULL,
  `text_value` longtext,
  `file_id` bigint DEFAULT NULL,
  `json_value` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_registration_material_value_submission` (`tenant_id`,`submission_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `registration_material_value`
--

LOCK TABLES `registration_material_value` WRITE;
/*!40000 ALTER TABLE `registration_material_value` DISABLE KEYS */;
/*!40000 ALTER TABLE `registration_material_value` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `security_audit_event`
--

DROP TABLE IF EXISTS `security_audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `security_audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `event_type` varchar(128) NOT NULL,
  `severity` varchar(32) NOT NULL,
  `source_ip` varchar(128) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `resource_code` varchar(128) DEFAULT NULL,
  `action_code` varchar(64) DEFAULT NULL,
  `target_id` varchar(128) DEFAULT NULL,
  `result` varchar(32) NOT NULL,
  `reason_code` varchar(128) DEFAULT NULL,
  `message` varchar(1000) DEFAULT NULL,
  `metadata_json` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_security_audit_created_at` (`created_at`),
  KEY `idx_security_audit_event_type_created_at` (`event_type`,`created_at`),
  KEY `idx_security_audit_tenant_created_at` (`tenant_id`,`created_at`),
  KEY `idx_security_audit_request_id` (`request_id`),
  KEY `idx_security_audit_source_ip_created_at` (`source_ip`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `security_audit_event`
--

LOCK TABLES `security_audit_event` WRITE;
/*!40000 ALTER TABLE `security_audit_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `security_audit_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
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
) ENGINE=InnoDB AUTO_INCREMENT=122 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,1001,'auth.default-registration-role-code','Default registration role','commonuser','PLATFORM',1,'Default role code assigned to newly registered users',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(2,1001,'branding.website-name','站点名称','Lumira','PLATFORM',0,'控制台顶部与浏览器标题展示名称',0,'2026-06-24 00:26:01',0,'2026-06-24 00:37:38',0),(3,1001,'branding.website-favicon-url','站点图标地址','','PLATFORM',0,'浏览器标签页 icon 地址',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(4,1001,'branding.website-logo-url','站点 Logo 地址','','PLATFORM',0,'控制台左上角品牌 Logo 地址',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(5,1001,'branding.login-background-url','登录页背景图地址','','PLATFORM',0,'登录页背景图地址',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(6,1001,'branding.github-link-enabled','GitHub 链接开关','true','PLATFORM',0,'是否显示顶部 GitHub 图标',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(7,1001,'branding.github-link-url','GitHub 链接','','PLATFORM',0,'顶部 GitHub 图标跳转地址',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(8,1001,'branding.help-link-enabled','帮助链接开关','true','PLATFORM',0,'是否显示顶部帮助图标',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(9,1001,'branding.help-link-url','帮助链接','','PLATFORM',0,'顶部帮助图标跳转地址',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(10,1001,'branding.company-name','公司名称','Lumira','PLATFORM',0,'页脚版权主体名称',0,'2026-06-24 00:26:01',0,'2026-06-24 00:40:14',0),(11,1001,'branding.copyright-start-year','版权起始年份','2026','PLATFORM',0,'页脚版权起始年份',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(12,1001,'branding.footer-icp','页脚备案','','PLATFORM',0,'页脚备案信息',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(13,1001,'branding.footer-police-beian','页脚公安备案','','PLATFORM',0,'页脚公安备案信息',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(14,1001,'branding.footer-copyright','页脚版权声明','Copyright 2026 Lumira All Rights Reserved','PLATFORM',0,'页脚版权声明',0,'2026-06-24 00:26:01',0,'2026-06-24 00:40:14',0),(15,1001,'agreement.user-agreement-markdown','用户协议','','PLATFORM',0,'用户协议 Markdown',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(16,1001,'agreement.privacy-agreement-markdown','隐私协议','','PLATFORM',0,'隐私协议 Markdown',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(17,1001,'watermark.enabled','水印开关','false','PLATFORM',0,'全局水印开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(18,1001,'watermark.mode','水印模式','TEXT','PLATFORM',0,'TEXT/IMAGE',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(19,1001,'watermark.text-lines','水印文本','','PLATFORM',0,'多行文本水印',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(20,1001,'watermark.image-url','水印图片','','PLATFORM',0,'图片水印 URL',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(21,1001,'watermark.font-color','字体颜色','rgba(0,0,0,0.15)','PLATFORM',0,'字体颜色',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(22,1001,'watermark.font-size','字体大小','14','PLATFORM',0,'字体大小',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(23,1001,'watermark.font-weight','字体粗细','normal','PLATFORM',0,'字体粗细',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(24,1001,'watermark.rotate','旋转角度','-22','PLATFORM',0,'旋转角度',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(25,1001,'watermark.gap-x','横向间距','100','PLATFORM',0,'横向间距',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(26,1001,'watermark.gap-y','纵向间距','100','PLATFORM',0,'纵向间距',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(27,1001,'watermark.offset-x','横向偏移','0','PLATFORM',0,'横向偏移',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(28,1001,'watermark.offset-y','纵向偏移','0','PLATFORM',0,'纵向偏移',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(29,1001,'watermark.z-index','层级','9','PLATFORM',0,'z-index',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(30,1001,'watermark.opacity','透明度','0.15','PLATFORM',0,'透明度',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(31,1001,'floating-window.api-docs-qr-enabled','接口文档二维码开关','false','PLATFORM',0,'是否在全局悬浮窗展示接口文档二维码入口',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(32,1001,'floating-window.api-docs-qr-title','接口文档二维码标题','','PLATFORM',0,'接口文档二维码弹层标题',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(33,1001,'floating-window.api-docs-qr-image-url','接口文档二维码图片','','PLATFORM',0,'接口文档悬浮入口展开后展示的二维码图片',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(34,1001,'smtp.enabled','SMTP 邮箱通知启用','false','PLATFORM',0,'是否启用邮箱通知渠道',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(35,1001,'smtp.host','SMTP 主机','','PLATFORM',0,'邮件服务器地址',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(36,1001,'smtp.port','SMTP 端口','25','PLATFORM',0,'邮件服务器端口',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(37,1001,'smtp.username','SMTP 用户名','','PLATFORM',0,'SMTP 登录用户名',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(38,1001,'smtp.password','SMTP 密码','','PLATFORM',0,'SMTP 登录密码',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(39,1001,'smtp.from','发件人地址','','PLATFORM',0,'SMTP 默认发件人',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(40,1001,'smtp.auth-enabled','SMTP 认证','true','PLATFORM',0,'是否启用 SMTP AUTH',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(41,1001,'smtp.starttls-enabled','SMTP STARTTLS','true','PLATFORM',0,'是否启用 STARTTLS',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(42,1001,'smtp.ssl-enabled','SMTP SSL','false','PLATFORM',0,'是否启用 SSL',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(43,1001,'notification.wechat-official.enabled','微信公众号通知启用','false','PLATFORM',0,'是否启用微信公众号/服务号模板消息通知',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(44,1001,'notification.wechat-official.app-id','微信公众号 AppID','','PLATFORM',0,'微信公众号或服务号 AppID',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(45,1001,'notification.wechat-official.app-secret','微信公众号 AppSecret','','PLATFORM',0,'微信公众号或服务号 AppSecret',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(46,1001,'notification.wechat-official.template-id','微信公众号模板 ID','','PLATFORM',0,'用于系统通知的公众号模板消息 ID',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(47,1001,'notification.wechat-official.detail-url','微信公众号通知详情链接','','PLATFORM',0,'模板消息点击后打开的系统链接，可留空',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(48,1001,'verification.totp.enabled','2FA 启用','true','PLATFORM',0,'是否启用 2FA 登录方式',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(49,1001,'verification.password-login.enabled','密码登录','true','PLATFORM',0,'是否启用账号密码登录',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(50,1001,'verification.email-login.enabled','邮箱验证码登录','false','PLATFORM',0,'是否启用邮箱验证码登录',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(51,1001,'verification.login-mode.order','登录方式排序','password,sms,email,wechat,passkey','PLATFORM',0,'登录页分段控制器展示顺序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(52,1001,'verification.sms.enabled','短信验证码启用','false','PLATFORM',0,'是否启用短信验证码服务',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(53,1001,'verification.sms.provider','短信验证码服务商','aliyun','PLATFORM',0,'短信验证码服务提供方',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(54,1001,'verification.sms.sign-name','短信签名','','PLATFORM',0,'短信验证码签名',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(55,1001,'verification.sms.template-code','短信模板编码','','PLATFORM',0,'短信验证码模板编码',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(56,1001,'verification.sms.access-key-id','短信 Access Key ID','','PLATFORM',0,'短信验证码访问密钥 ID',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(57,1001,'verification.sms.access-key-secret','短信 Access Key Secret','','PLATFORM',0,'短信验证码访问密钥 Secret',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(58,1001,'verification.sms.endpoint','短信服务地址','','PLATFORM',0,'短信验证码服务端点',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(59,1001,'verification.sms.region','短信服务地域','','PLATFORM',0,'短信验证码服务地域',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(60,1001,'verification.wechat-login.enabled','微信登录启用','false','PLATFORM',0,'是否启用微信扫码登录',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(61,1001,'verification.wechat-login.app-id','微信 AppID','','PLATFORM',0,'微信开放平台网站应用 AppID',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(62,1001,'verification.wechat-login.app-secret','微信 AppSecret','','PLATFORM',0,'微信开放平台网站应用 AppSecret',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(63,1001,'verification.wechat-login.redirect-uri','微信登录回调地址','','PLATFORM',0,'微信开放平台授权回调地址',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(64,1001,'verification.wechat-login.state-expire-minutes','微信登录状态有效期','10','PLATFORM',0,'微信登录 state 缓存有效期，单位分钟',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(65,1001,'verification.passkey.enabled','通行密钥启用','false','PLATFORM',0,'是否启用通行密钥登录',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(66,1001,'verification.passkey.passwordless-enabled','通行密钥无账号登录','false','PLATFORM',0,'是否允许发现式凭据无账号登录',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(67,1001,'verification.passkey.self-binding-enabled','通行密钥自助绑定','false','PLATFORM',0,'是否允许用户在个人中心自助绑定通行密钥',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(68,1001,'verification.passkey.rp-id','通行密钥 RP ID','','PLATFORM',0,'WebAuthn RP ID',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(69,1001,'verification.passkey.rp-name','通行密钥 RP 名称','','PLATFORM',0,'WebAuthn RP 显示名称',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(70,1001,'verification.passkey.allowed-origins','通行密钥允许 Origin','','PLATFORM',0,'WebAuthn 允许的前端 Origin',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(71,1001,'verification.passkey.challenge-ttl-seconds','通行密钥 Challenge TTL','120','PLATFORM',0,'WebAuthn challenge 有效期秒数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(72,1001,'security.idle-timeout-seconds','空闲超时时间','1800','PLATFORM',1,'会话在无操作状态下允许保持的秒数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(73,1001,'security.access-token-expire-seconds','Access Token 过期时间','1800','PLATFORM',1,'Access Token 的有效秒数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(74,1001,'security.refresh-token-expire-seconds','Refresh Token 刷新时限','604800','PLATFORM',1,'Refresh Token 的有效秒数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(75,1001,'security.allow-multi-device-login','多设备登录','1','PLATFORM',1,'是否允许同一账号在多个设备同时在线',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(76,1001,'security.captcha-enabled','验证码开关','0','PLATFORM',1,'是否开启登录时的人机验证码',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(77,1001,'security.captcha-type','验证码类型','IMAGE','PLATFORM',1,'验证码类型：IMAGE/SLIDER',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(78,1001,'security.login-defense-window-minutes','登录防御统计窗口','5','PLATFORM',1,'统计登录尝试与错误次数的时间窗口，单位分钟',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(79,1001,'security.login-max-validation-attempts','最大验证次数','100','PLATFORM',1,'统计窗口内允许的最大验证码/登录验证尝试次数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(80,1001,'security.login-max-failure-count','最大错误次数','10','PLATFORM',1,'统计窗口内允许的最大登录失败次数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(81,1001,'security.verification-code-expire-seconds','验证码有效期','300','PLATFORM',1,'短信/邮箱验证码的有效秒数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(82,1001,'security.verification-code-cooldown-seconds','验证码重发冷却','60','PLATFORM',1,'同一账号同一验证码渠道再次发送前需要等待的秒数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(83,1001,'security.password-min-length','密码最短长度','6','PLATFORM',1,'用户密码允许的最少字符数',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(84,1001,'security.password-require-uppercase','密码必须包含大写字母','0','PLATFORM',1,'强制密码包含 A-Z',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(85,1001,'security.password-require-lowercase','密码必须包含小写字母','0','PLATFORM',1,'强制密码包含 a-z',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(86,1001,'security.password-require-special-character','密码必须包含特殊字符','0','PLATFORM',1,'强制密码包含特殊字符',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(87,1001,'security.password-allow-consecutive-characters','允许连续字符','1','PLATFORM',1,'是否允许密码中出现连续字符',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(88,1001,'profile.field.system.overrides','System profile field metadata overrides','[]','PLATFORM',0,'Stores editable labels, descriptions, placeholders, and groups for built-in profile fields',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(89,1001,'profile.field.custom.definitions','自定义资料字段定义','[]','PLATFORM',0,'保存个人中心可扩展的自定义资料字段定义',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(90,1001,'profile.field.avatar.visible','头像显示开关','true','PLATFORM',0,'个人中心头像字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(91,1001,'profile.field.avatar.weight','头像评分权重','10','PLATFORM',0,'个人中心头像字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(92,1001,'profile.field.avatar.required','头像 required','false','PLATFORM',0,'个人中心头像字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(93,1001,'profile.field.avatar.sort','头像 sort','10','PLATFORM',0,'个人中心头像字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(94,1001,'profile.field.real-name.visible','姓名显示开关','true','PLATFORM',0,'个人中心姓名字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(95,1001,'profile.field.real-name.weight','姓名评分权重','15','PLATFORM',0,'个人中心姓名字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(96,1001,'profile.field.real-name.required','姓名 required','false','PLATFORM',0,'个人中心姓名字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(97,1001,'profile.field.real-name.sort','姓名 sort','20','PLATFORM',0,'个人中心姓名字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(98,1001,'profile.field.mobile.visible','手机号显示开关','true','PLATFORM',0,'个人中心手机号字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(99,1001,'profile.field.mobile.weight','手机号评分权重','15','PLATFORM',0,'个人中心手机号字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(100,1001,'profile.field.mobile.required','手机号 required','false','PLATFORM',0,'个人中心手机号字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(101,1001,'profile.field.mobile.sort','手机号 sort','30','PLATFORM',0,'个人中心手机号字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(102,1001,'profile.field.email.visible','邮箱显示开关','true','PLATFORM',0,'个人中心邮箱字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(103,1001,'profile.field.email.weight','邮箱评分权重','15','PLATFORM',0,'个人中心邮箱字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(104,1001,'profile.field.email.required','邮箱 required','false','PLATFORM',0,'个人中心邮箱字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(105,1001,'profile.field.email.sort','邮箱 sort','40','PLATFORM',0,'个人中心邮箱字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(106,1001,'profile.field.birth-month.visible','出生年月显示开关','true','PLATFORM',0,'个人中心出生年月字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(107,1001,'profile.field.birth-month.weight','出生年月评分权重','10','PLATFORM',0,'个人中心出生年月字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(108,1001,'profile.field.birth-month.required','出生年月 required','false','PLATFORM',0,'个人中心出生年月字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(109,1001,'profile.field.birth-month.sort','出生年月 sort','50','PLATFORM',0,'个人中心出生年月字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(110,1001,'profile.field.gender.visible','性别显示开关','true','PLATFORM',0,'个人中心性别字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(111,1001,'profile.field.gender.weight','性别评分权重','10','PLATFORM',0,'个人中心性别字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(112,1001,'profile.field.gender.required','性别 required','false','PLATFORM',0,'个人中心性别字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(113,1001,'profile.field.gender.sort','性别 sort','60','PLATFORM',0,'个人中心性别字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(114,1001,'profile.field.region.visible','所在地区显示开关','true','PLATFORM',0,'个人中心所在地区字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(115,1001,'profile.field.region.weight','所在地区评分权重','10','PLATFORM',0,'个人中心所在地区字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(116,1001,'profile.field.region.required','所在地区 required','false','PLATFORM',0,'个人中心所在地区字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(117,1001,'profile.field.region.sort','所在地区 sort','70','PLATFORM',0,'个人中心所在地区字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(118,1001,'profile.field.id-card-number.visible','身份证号码显示开关','true','PLATFORM',0,'个人中心身份证号码字段显示开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(119,1001,'profile.field.id-card-number.weight','身份证号码评分权重','5','PLATFORM',0,'个人中心身份证号码字段评分权重',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(120,1001,'profile.field.id-card-number.required','身份证号码 required','false','PLATFORM',0,'个人中心身份证号码字段必填开关',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(121,1001,'profile.field.id-card-number.sort','身份证号码 sort','80','PLATFORM',0,'个人中心身份证号码字段排序',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0);
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_department`
--

DROP TABLE IF EXISTS `sys_department`;
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

--
-- Dumping data for table `sys_department`
--

LOCK TABLES `sys_department` WRITE;
/*!40000 ALTER TABLE `sys_department` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_department_closure`
--

DROP TABLE IF EXISTS `sys_department_closure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_department_closure` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `ancestor_id` bigint NOT NULL,
  `descendant_id` bigint NOT NULL,
  `depth` int NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_closure` (`tenant_id`,`ancestor_id`,`descendant_id`),
  KEY `idx_dept_closure_descendant` (`tenant_id`,`descendant_id`),
  KEY `idx_dept_closure_ancestor_depth` (`tenant_id`,`ancestor_id`,`depth`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_department_closure`
--

LOCK TABLES `sys_department_closure` WRITE;
/*!40000 ALTER TABLE `sys_department_closure` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_department_closure` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_item`
--

DROP TABLE IF EXISTS `sys_dict_item`;
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
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_item`
--

LOCK TABLES `sys_dict_item` WRITE;
/*!40000 ALTER TABLE `sys_dict_item` DISABLE KEYS */;
INSERT INTO `sys_dict_item` VALUES (1,1001,9,'GENERAL','通用团队',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队类型'),(2,1001,9,'DEV','开发团队',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队类型'),(3,1001,9,'COMPETITION','竞赛团队',30,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队类型'),(4,1001,9,'CLUB','社团组织',40,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队类型'),(5,1001,9,'OTHER','其他',50,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队类型'),(6,1001,10,'PRIVATE','私有',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队可见性'),(7,1001,10,'PUBLIC','公开',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队可见性'),(8,1001,11,'INVITE_ONLY','仅邀请',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队加入方式'),(9,1001,11,'APPLY','申请加入',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队加入方式'),(10,1001,11,'OPEN','开放加入',30,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队加入方式'),(16,1001,1,'MALE','男',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','用户性别'),(17,1001,1,'FEMALE','女',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','用户性别'),(18,1001,1,'OTHER','其他',30,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','用户性别'),(19,1001,1,'UNKNOWN','未知',40,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','用户性别'),(20,1001,2,'ENABLED','启用',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','用户状态'),(21,1001,2,'DISABLED','禁用',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','用户状态'),(22,1001,2,'LOCKED','锁定',30,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','用户状态'),(23,1001,3,'ENABLED','启用',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','通用状态'),(24,1001,3,'DISABLED','停用',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','通用状态'),(25,1001,4,'YES','是',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','是否'),(26,1001,4,'NO','否',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','是否'),(27,1001,5,'SYSTEM','系统角色',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','角色类型'),(28,1001,5,'CUSTOM','自定义角色',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','角色类型'),(29,1001,6,'CATALOG','目录',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','菜单类型'),(30,1001,6,'MENU','菜单',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','菜单类型'),(31,1001,6,'BUTTON','按钮',30,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','菜单类型'),(32,1001,6,'LINK','外链',40,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','菜单类型'),(33,1001,7,'ALL','全部数据',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','数据范围类型'),(34,1001,7,'DEPT_AND_CHILD','本部门及下级',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','数据范围类型'),(35,1001,7,'DEPT','本部门',30,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','数据范围类型'),(36,1001,7,'SELF','仅本人',40,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','数据范围类型'),(37,1001,7,'CUSTOM','自定义',50,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','数据范围类型'),(38,1001,8,'OWNER','所有者',10,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队成员角色'),(39,1001,8,'ADMIN','管理员',20,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队成员角色'),(40,1001,8,'MANAGER','协作者',30,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队成员角色'),(41,1001,8,'MEMBER','成员',40,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED','团队成员角色');
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
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
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,1001,'sys_user_gender','用户性别',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'系统字典：用户性别'),(2,1001,'sys_user_status','用户状态',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'系统字典：用户状态'),(3,1001,'sys_common_status','通用状态',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'系统字典：通用状态'),(4,1001,'sys_yes_no','是否',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'系统字典：是否'),(5,1001,'sys_role_type','角色类型',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'系统字典：角色类型'),(6,1001,'sys_menu_type','菜单类型',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'系统字典：菜单类型'),(7,1001,'sys_data_scope_type','数据范围类型',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'系统字典：数据范围类型'),(8,1001,'team_member_role','团队成员角色',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'团队模块字典'),(9,1001,'team_type','团队类型',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'团队模块字典'),(10,1001,'team_visibility','团队可见性',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'团队模块字典'),(11,1001,'team_join_mode','团队加入方式',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0,'ENABLED',1,'团队模块字典');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_export_task`
--

DROP TABLE IF EXISTS `sys_export_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `module_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_payload` json DEFAULT NULL,
  `selected_fields` json DEFAULT NULL,
  `total_count` bigint DEFAULT '0',
  `file_id` bigint DEFAULT NULL,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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

--
-- Dumping data for table `sys_export_task`
--

LOCK TABLES `sys_export_task` WRITE;
/*!40000 ALTER TABLE `sys_export_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_export_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_localization_entry`
--

DROP TABLE IF EXISTS `sys_localization_entry`;
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
) ENGINE=InnoDB AUTO_INCREMENT=606 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_localization_entry`
--

LOCK TABLES `sys_localization_entry` WRITE;
/*!40000 ALTER TABLE `sys_localization_entry` DISABLE KEYS */;
INSERT INTO `sys_localization_entry` VALUES (1,1,'app.bootstrap.backendNotReady','后端暂未准备好','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(2,1,'app.bootstrap.backendRetryInSeconds','后端暂未启动，{seconds} 秒后自动重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(3,1,'app.bootstrap.backendRetrying','后端暂未就绪，正在进行第 {attempt} 次重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(4,1,'app.bootstrap.backendStarting','后端启动中','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(5,1,'app.bootstrap.checkingBackend','正在检查后端服务','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(6,1,'app.bootstrap.checkingServiceReady','正在检查服务是否可用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(7,1,'app.bootstrap.connectingBackend','正在连接后端','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(8,1,'app.bootstrap.enterWorkbench','正在进入工作台','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(9,1,'app.bootstrap.healthHtmlResponse','后端健康检查返回了前端页面，请检查 API 代理配置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(10,1,'app.bootstrap.healthHttpFailed','后端健康检查失败：HTTP {status}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(11,1,'app.bootstrap.healthJsonResponse','后端健康检查未返回 JSON，请检查 API 代理配置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(12,1,'app.bootstrap.healthStatusAbnormal','后端健康状态异常：{status}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(13,1,'app.bootstrap.loadBranding','加载品牌信息','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(14,1,'app.bootstrap.loadSecuritySettings','加载安全配置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(15,1,'app.bootstrap.preparePostLoginResources','正在准备登录后的菜单、插件和外观设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(16,1,'app.bootstrap.ready','系统已就绪','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(17,1,'app.bootstrap.showLoginPage','正在展示登录页','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(18,1,'app.bootstrap.starting','正在启动系统','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(19,1,'app.bootstrap.syncLoginPageBranding','正在同步登录页品牌与外观设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(20,1,'app.bootstrap.syncLoginPolicy','正在同步登录策略','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(21,1,'app.bootstrap.syncPostLoginBranding','正在同步登录后可见的品牌与外观设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(22,1,'app.bootstrap.syncResources','同步系统资源','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(23,1,'app.layout.backToMainRoute','返回主路由','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(24,1,'app.layout.uploadQrHint','请在个性化设置上传二维码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(25,1,'app.locale.current','当前语言：{locale}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(26,1,'app.locale.en-US','English','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(27,1,'app.locale.saved','语言偏好已保存','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(28,1,'app.locale.switch','语言切换','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(29,1,'app.locale.zh-CN','中文','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(30,2,'auth.logout','退出登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(31,3,'common.actions','操作','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(32,3,'common.back','返回','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(33,3,'common.badRequest','请求内容有误，请检查后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(34,3,'common.bizError','当前操作无法完成，请检查业务状态','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(35,3,'common.cancel','取消','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(36,3,'common.captchaDefault','向右拖动滑块完成验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(37,3,'common.captchaError','验证失败，请重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(38,3,'common.captchaExpired','拖动验证码已失效','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(39,3,'common.captchaLoadFailed','加载失败，点击重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(40,3,'common.captchaLoading','验证码加载中...','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(41,3,'common.captchaResourceIncomplete','拖动验证码资源不完整','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(42,3,'common.captchaSuccess','验证通过','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(43,3,'common.captchaVerifying','正在校验...','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(44,3,'common.collapse','收起','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(45,3,'common.confirm','确认','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(46,3,'common.copyLink','复制链接','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(47,3,'common.delete','删除','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(48,3,'common.deleted','已删除','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(49,3,'common.download','下载','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(50,3,'common.edit','编辑','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(51,3,'common.expand','展开','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(52,3,'common.failure','操作失败，请稍后重试','zh-CN','UI','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(53,3,'common.invalidIdCard','请输入有效身份证号码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(54,3,'common.invalidMobile','请输入有效手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(55,3,'common.loading','处理中...','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(56,3,'common.mustBeGreaterThanZero','必须大于 0','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(57,3,'common.networkError','网络异常，请检查连接后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(58,3,'common.noPermission','当前账号没有访问权限','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(59,3,'common.pleaseInput','请输入','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(60,3,'common.pleaseLogin','请先登录后再继续操作','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(61,3,'common.pleaseSelect','请选择','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(62,3,'common.pluginEntryNotRegistered','插件入口未成功注册','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(63,3,'common.pluginLoadFailed','插件加载失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(64,3,'common.pluginManifestEntryMissing','插件 manifest 的 entry 必须包含在 assets 中','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(65,3,'common.pluginManifestMissingFields','插件 manifest 缺少必要字段','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(66,3,'common.pluginManifestReactDepRequired','插件 manifest 必须声明 react 共享依赖','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(67,3,'common.pluginNotEnabled','当前未启用该插件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(68,3,'common.pluginRenderFailed','插件渲染失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(69,3,'common.pluginResourceLoadFailed','加载插件资源失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(70,3,'common.pluginScriptExecutionFailed','插件脚本执行失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(71,3,'common.pluginScriptLoadFailed','插件脚本加载失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(72,3,'common.pluginStyleLoadFailed','插件样式加载失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(73,3,'common.pluginUnavailable','插件不可用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(74,3,'common.query','查询','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(75,3,'common.refresh','刷新','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(76,3,'common.requestTimeout','请求超时，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(77,3,'common.reset','重置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(78,3,'common.resourceNotFound','请求的资源不存在','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(79,3,'common.retry','重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(80,3,'common.save','保存','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(81,3,'common.serviceUnavailable','服务暂时不可用，请稍后再试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(82,3,'common.sessionExpired','登录状态已失效，请重新登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(83,3,'common.success','操作成功','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(84,3,'common.systemError','系统异常，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(85,3,'common.tooManyRequests','操作过于频繁，请稍后再试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(86,3,'common.upload','上传','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(87,3,'common.uploadDocument','上传文档','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(88,4,'github.link','GitHub 链接','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(89,4,'github.link.unconfigured','GitHub 链接（未配置）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(90,5,'global.float.backTop','回到顶部','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(91,5,'global.float.qrCode','二维码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(92,5,'global.float.refresh','刷新页面','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(93,6,'help.center','帮助中心','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(94,6,'help.center.unconfigured','帮助中心（未配置链接）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(95,7,'message.center.all','全部','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(96,7,'message.center.ariaLabel','消息中心，当前有 {count} 条未读消息','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(97,7,'message.center.connected','消息通道已连接','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(98,7,'message.center.count','共 {total} 条消息 · {unread} 条未读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(99,7,'message.center.detail','详情','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(100,7,'message.center.loadError','消息加载失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(101,7,'message.center.loadPartialError','部分消息加载失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(102,7,'message.center.loading','加载消息中','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(103,7,'message.center.markAllRead','全部标为已读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(104,7,'message.center.markRead','标为已读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(105,7,'message.center.messageType','消息类型','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(106,7,'message.center.newNotificationDescription','你有一条新的站内信，请前往消息中心查看。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(107,7,'message.center.newNotificationTitle','收到新消息','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(108,7,'message.center.none','暂无消息','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(109,7,'message.center.noneFiltered','暂无符合条件的消息','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(110,7,'message.center.preview','预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(111,7,'message.center.publish','发布','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(112,7,'message.center.published','已发布','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(113,7,'message.center.read','已读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(114,7,'message.center.readFlag.false','未读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(115,7,'message.center.readFlag.true','已读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(116,7,'message.center.refresh','刷新','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(117,7,'message.center.retract','撤回','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(118,7,'message.center.retracted','已撤回','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(119,7,'message.center.site','站内信','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(120,7,'message.center.statusRead','已读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(121,7,'message.center.statusUnread','未读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(122,7,'message.center.time','时间：{time}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(123,7,'message.center.time.now','刚刚','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(124,7,'message.center.time.soon','即将','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(125,7,'message.center.title','消息中心','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(126,7,'message.center.type.message','站内信','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(127,7,'message.center.unread','未读','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(128,8,'nav.activities.activities','活动管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(129,8,'nav.activities.activityGroup','活动','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(130,8,'nav.activities.activitySearch','活动查询','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(131,8,'nav.activities.root','活动','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(132,8,'nav.ai.assistant','AI 助手','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(133,8,'nav.ai.knowledge','知识库','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(134,8,'nav.ai.root','AI','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(135,8,'nav.dashboard.home','工作台','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(136,8,'nav.dashboard.root','工作台','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(137,8,'nav.experts.management','专家管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(138,8,'nav.experts.root','专家库','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(139,8,'nav.files.all','文件管理器','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(140,8,'nav.files.downloadCenter','下载中心','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(141,8,'nav.files.my','我的文件','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(142,8,'nav.files.root','文件中心','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(143,8,'nav.localization.root','本地化中心','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(144,8,'nav.settings.root','系统设置','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(145,8,'nav.system.aiEmployees','数字员工','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(146,8,'nav.system.dicts','字典管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(147,8,'nav.system.menus','菜单管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(148,8,'nav.system.monitoring.apiDocs','接口文档','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(149,8,'nav.system.monitoring.audit','审计中心','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(150,8,'nav.system.monitoring.redis','Redis监控','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(151,8,'nav.system.monitoring.root','系统监控','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(152,8,'nav.system.monitoring.service','服务监控','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(153,8,'nav.system.notifications','通知中心','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(154,8,'nav.system.payment','支付设置','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(155,8,'nav.system.personalization','个性化设置','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(156,8,'nav.system.plugins','插件管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(157,8,'nav.system.profileFields','字段管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(158,8,'nav.system.root','系统总览','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(159,8,'nav.system.security','安全设置','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(160,8,'nav.system.verification','验证管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(161,8,'nav.team.create','创建团队','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(162,8,'nav.team.detail','团队详情','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(163,8,'nav.team.invites','团队邀请','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(164,8,'nav.team.join','加入团队','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(165,8,'nav.team.management','团队管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(166,8,'nav.team.members','团队成员','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(167,8,'nav.team.root','团队','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(168,8,'nav.team.search','团队','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(169,8,'nav.user.center','用户中心','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(170,8,'nav.user.changePassword','修改密码','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(171,8,'nav.user.departments','组织部门','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(172,8,'nav.user.menu','用户菜单','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(173,8,'nav.user.onlineUsers','在线用户','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(174,8,'nav.user.password.confirm','确认新密码','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(175,8,'nav.user.password.confirmMismatch','两次输入的新密码不一致','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(176,8,'nav.user.password.current','当前密码','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(177,8,'nav.user.password.enterConfirm','请再次输入新密码','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(178,8,'nav.user.password.enterCurrent','请输入当前密码','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(179,8,'nav.user.password.enterNew','请输入新密码','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(180,8,'nav.user.password.minLength','密码长度至少为 {length} 位','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(181,8,'nav.user.password.new','新密码','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(182,8,'nav.user.password.policy.lowercase','需包含小写字母','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(183,8,'nav.user.password.policy.minLength','至少 {length} 位','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(184,8,'nav.user.password.policy.special','需包含特殊字符','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(185,8,'nav.user.password.policy.title','密码规则：','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(186,8,'nav.user.password.policy.uppercase','需包含大写字母','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(187,8,'nav.user.password.requireLowercase','需包含小写字母','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(188,8,'nav.user.password.requireSpecial','需包含特殊字符','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(189,8,'nav.user.password.requireUppercase','需包含大写字母','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(190,8,'nav.user.password.updateSuccess','密码已修改','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(191,8,'nav.user.personalCenter','个人中心','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(192,8,'nav.user.profile','个人资料','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(193,8,'nav.user.role.current','当前账号权限','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(194,8,'nav.user.role.currentMeta','基于当前账号的默认权限视图','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(195,8,'nav.user.role.currentTag','当前','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(196,8,'nav.user.role.meta','编码：{roleCode} · {permissionCount} 个权限','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(197,8,'nav.user.role.restoreSuccess','已恢复默认权限','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(198,8,'nav.user.role.simulation','角色模拟','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(199,8,'nav.user.role.simulationHint','当前正在模拟 {roleName}','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(200,8,'nav.user.role.switchSuccess','角色已切换','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(201,8,'nav.user.role.switchSuccessWithName','已切换至 {roleName}','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(202,8,'nav.user.roles','角色管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(203,8,'nav.user.switchRole','切换角色','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(204,8,'nav.user.users','用户管理','zh-CN','ROUTE','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(205,9,'page.exception.noPermission.back','返回上一页','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(206,9,'page.exception.noPermission.home','回到首页','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(207,9,'page.exception.noPermission.subtitle','当前账号没有访问该页面的权限','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(208,9,'page.exception.notFound.backHome','返回首页','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(209,9,'page.exception.notFound.subtitle','页面不存在，请返回首页继续操作。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(210,9,'page.exception.serverError.backHome','返回首页','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(211,9,'page.exception.serverError.subtitle','服务器发生异常，请稍后再试。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(212,9,'page.localization.active','当前生效','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(213,9,'page.localization.addEntry','新增词条','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(214,9,'page.localization.addLanguage','新增语言','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(215,9,'page.localization.allNamespaces','全部命名空间','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(216,9,'page.localization.copyKey','复制键名','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(217,9,'page.localization.coverage','覆盖率：{rate}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(218,9,'page.localization.currentLocale','当前语言：{locale}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(219,9,'page.localization.currentNamespace','当前模块：{namespace}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(220,9,'page.localization.currentStatus','当前状态','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(221,9,'page.localization.default','默认','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(222,9,'page.localization.defaultLanguage','默认语言','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(223,9,'page.localization.defaultMessage','原文','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(224,9,'page.localization.editEntry','编辑词条','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(225,9,'page.localization.editLanguage','编辑语言','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(226,9,'page.localization.entrySaved','词条已保存','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(227,9,'page.localization.fallbackLocale','回退语言','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(228,9,'page.localization.history','版本历史','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(229,9,'page.localization.key','键名','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(230,9,'page.localization.languageName','语言名称','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(231,9,'page.localization.languageNamePlaceholder','例如：简体中文 / English','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(232,9,'page.localization.languageSaved','语言已保存','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(233,9,'page.localization.languageSwitch','语言切换','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(234,9,'page.localization.languages','语言','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(235,9,'page.localization.localeCode','语言代码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(236,9,'page.localization.namespace','命名空间','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(237,9,'page.localization.namespaceCode','命名空间','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(238,9,'page.localization.nativeName','本地名称','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(239,9,'page.localization.noLanguages','暂无语言','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(240,9,'page.localization.noReleases','暂无发布记录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(241,9,'page.localization.publish','发布','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(242,9,'page.localization.publishNote','本地化中心发布','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(243,9,'page.localization.publishSuccess','翻译版本已发布','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(244,9,'page.localization.rollback','回滚','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(245,9,'page.localization.rollbackConfirm','确认回滚该版本吗？','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(246,9,'page.localization.rollbackSuccess','翻译版本已回滚','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(247,9,'page.localization.searchFilters','搜索筛选','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(248,9,'page.localization.searchPlaceholder','搜索键名、原文或来源','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(249,9,'page.localization.sortNo','排序','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(250,9,'page.localization.sourceLocale','源语言','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(251,9,'page.localization.sourceRef','来源','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(252,9,'page.localization.sourceType','来源类型','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(253,9,'page.localization.status','状态','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(254,9,'page.localization.sync','同步源码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(255,9,'page.localization.syncSuccess','已同步源码词条','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(256,9,'page.localization.title','本地化中心','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(257,9,'page.localization.translation','译文','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(258,9,'page.localization.untranslated','待翻译','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(259,9,'page.localization.usageCount','引用数','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(260,9,'page.login.agreement.accept','我已阅读并同意','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(261,9,'page.login.agreement.and','和','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(262,9,'page.login.agreement.empty','后台暂未配置该条款内容。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(263,9,'page.login.agreement.preview','协议预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(264,9,'page.login.agreement.privacy','《隐私协议》','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(265,9,'page.login.agreement.required','请先同意条款后再登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(266,9,'page.login.agreement.user','《用户协议》','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(267,9,'page.login.captcha.alt','验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(268,9,'page.login.captcha.refresh','点击刷新验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(269,9,'page.login.captcha.refreshText','点击刷新','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(270,9,'page.login.captcha.retry','点击重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(271,9,'page.login.captcha.sliderTitle','拖动验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(272,9,'page.login.captcha.sliderVerified','已验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(273,9,'page.login.captcha.startSlider','验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(274,9,'page.login.captchaExpired','验证码已过期，请刷新后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(275,9,'page.login.code.cooldown','请等待 {seconds}s 后再发送','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(276,9,'page.login.code.countdown','{seconds}s 后重发','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(277,9,'page.login.code.debug','调试验证码：{code}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(278,9,'page.login.code.refresh','重新发送','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(279,9,'page.login.code.secondFactor','请输入收到的验证码完成二次验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(280,9,'page.login.code.send','发送验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(281,9,'page.login.code.to','验证码将发送到 {contact}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(282,9,'page.login.codeSendFailed','验证码发送失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(283,9,'page.login.emailAccount','邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(284,9,'page.login.emailCode','邮箱验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(285,9,'page.login.emailSubtitle','邮箱验证码登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(286,9,'page.login.error.accountLength','账号长度不能超过 128 个字符','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(287,9,'page.login.error.captchaExpired','验证码已过期，请刷新后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(288,9,'page.login.error.codeSendFailed','验证码发送失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(289,9,'page.login.error.emailDisabled','当前未启用邮箱验证码登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(290,9,'page.login.error.invalidAccountCharacters','账号包含不允许的字符','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(291,9,'page.login.error.invalidCodeCharacters','验证码只能包含字母和数字','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(292,9,'page.login.error.invalidEmail','请输入有效邮箱地址','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(293,9,'page.login.error.invalidMobile','请输入有效手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(294,9,'page.login.error.loginEncryption','登录加密信息加载失败，请刷新后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(295,9,'page.login.error.loginFailed','登录失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(296,9,'page.login.error.loginModeUnavailable','当前登录方式不可用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(297,9,'page.login.error.passwordLength','密码长度不能少于 6 位','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(298,9,'page.login.error.pleaseCompleteSliderCaptcha','请先完成拖动验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(299,9,'page.login.error.pleaseEnterAccount','请输入账号、手机号或邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(300,9,'page.login.error.pleaseEnterCaptcha','请输入验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(301,9,'page.login.error.pleaseEnterEmail','请输入邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(302,9,'page.login.error.pleaseEnterMobile','请输入手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(303,9,'page.login.error.pleaseEnterPassword','请输入密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(304,9,'page.login.error.pleaseSendCode','请先发送验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(305,9,'page.login.error.refreshCaptcha','验证码刷新失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(306,9,'page.login.error.smsDisabled','当前未启用短信验证码登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(307,9,'page.login.initialPasswordChange.confirmPassword','确认新密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(308,9,'page.login.initialPasswordChange.confirmPasswordRequired','请再次输入新密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(309,9,'page.login.initialPasswordChange.failed','密码修改失败，请检查后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(310,9,'page.login.initialPasswordChange.newPassword','新密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(311,9,'page.login.initialPasswordChange.newPasswordRequired','请输入新密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(312,9,'page.login.initialPasswordChange.notInitial','新密码不能继续使用初始密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(313,9,'page.login.initialPasswordChange.notice','当前账号仍在使用初始密码，必须修改后才能进入系统。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(314,9,'page.login.initialPasswordChange.passwordMismatch','两次输入的新密码不一致','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(315,9,'page.login.initialPasswordChange.required','当前账号仍在使用初始密码，请先修改密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(316,9,'page.login.initialPasswordChange.submit','确认修改','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(317,9,'page.login.initialPasswordChange.success','密码已修改，请使用新密码登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(318,9,'page.login.initialPasswordChange.title','修改初始密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(319,9,'page.login.loadingEncryption','正在加载登录加密信息...','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(320,9,'page.login.loginModeUnavailable','当前登录方式不可用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(321,9,'page.login.passkey','使用通行密钥登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(322,9,'page.login.passkey.cancelled','已取消通行密钥验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(323,9,'page.login.passkey.unsupported','当前浏览器不支持通行密钥','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(324,9,'page.login.passkeyShort','通行密钥','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(325,9,'page.login.passwordAccount','密码登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(326,9,'page.login.passwordPassword','密码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(327,9,'page.login.passwordSubtitle','密码登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(328,9,'page.login.remember','记住我','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(329,9,'page.login.secondFactor.prompt','{name} 需要完成二次验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(330,9,'page.login.smsAccount','手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(331,9,'page.login.smsCode','短信验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(332,9,'page.login.smsSubtitle','短信验证码登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(333,9,'page.login.submit.login','登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(334,9,'page.login.submit.verify','验证并登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(335,9,'page.login.success.codeSent','验证码已发送','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(336,9,'page.login.success.loggedIn','登录成功，正在进入系统','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(337,9,'page.login.success.secondFactor','请输入验证码完成二次验证','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(338,9,'page.login.title','登录','zh-CN','UI','lumira-ui/src/routes/meta.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(339,9,'page.login.wechat','微信登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(340,9,'page.login.wechatStarting','正在跳转到微信登录...','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(341,9,'page.plugins.activateVersion','激活插件版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(342,9,'page.plugins.apiVersion','API 版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(343,9,'page.plugins.author','作者','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(344,9,'page.plugins.cancel','取消','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(345,9,'page.plugins.cancelUpload','取消','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(346,9,'page.plugins.chooseZip','选择 zip 插件包','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(347,9,'page.plugins.chooseZip.desc','选择 zip 插件包','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(348,9,'page.plugins.confirm','确认','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(349,9,'page.plugins.confirmActivate','激活插件版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(350,9,'page.plugins.confirmDisable','停用插件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(351,9,'page.plugins.confirmEnable','启用插件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(352,9,'page.plugins.confirmInstall','安装插件版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(353,9,'page.plugins.confirmRollback','回滚插件版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(354,9,'page.plugins.confirmUninstall','确认后将卸载插件 {name}。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(355,9,'page.plugins.currentEnabledVersion','当前启用版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(356,9,'page.plugins.currentVersion','当前版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(357,9,'page.plugins.description','描述','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(358,9,'page.plugins.detail','插件详情','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(359,9,'page.plugins.disable','停用插件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(360,9,'page.plugins.enable','启用插件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(361,9,'page.plugins.enabled','是否启用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(362,9,'page.plugins.enabled.false','未启用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(363,9,'page.plugins.enabled.true','已启用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(364,9,'page.plugins.error.activate','激活插件失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(365,9,'page.plugins.error.disable','停用插件失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(366,9,'page.plugins.error.enable','启用插件失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(367,9,'page.plugins.error.install','安装插件失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(368,9,'page.plugins.error.installableVersion','请先安装可用版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(369,9,'page.plugins.error.listRefresh','插件已更新，但列表刷新失败，请手动刷新页面','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(370,9,'page.plugins.error.load','加载插件信息失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(371,9,'page.plugins.error.logs','加载插件日志失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(372,9,'page.plugins.error.max50mb','插件包不能超过 50MB','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(373,9,'page.plugins.error.menuRefresh','插件已更新，但菜单刷新失败，请手动刷新页面','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(374,9,'page.plugins.error.operation','操作失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(375,9,'page.plugins.error.rollback','回滚插件失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(376,9,'page.plugins.error.selectPackage','请先选择插件包','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(377,9,'page.plugins.error.uninstall','卸载插件失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(378,9,'page.plugins.error.upload','上传插件失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(379,9,'page.plugins.error.zipOnly','仅支持 zip 插件包','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(380,9,'page.plugins.installVersion','安装插件版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(381,9,'page.plugins.log','插件日志','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(382,9,'page.plugins.menuCount','菜单数','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(383,9,'page.plugins.name','插件名称','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(384,9,'page.plugins.onlyUninstall','仅卸载插件，不删除数据库数据','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(385,9,'page.plugins.pluginCode','插件编码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(386,9,'page.plugins.refresh','刷新','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(387,9,'page.plugins.rollbackVersion','回滚插件版本','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(388,9,'page.plugins.routeCount','路由数','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(389,9,'page.plugins.searchPlaceholder','输入插件编码或名称','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(390,9,'page.plugins.status','状态','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(391,9,'page.plugins.success.activated','插件激活版本已切换','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(392,9,'page.plugins.success.disabled','插件已停用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(393,9,'page.plugins.success.enabled','插件已启用','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(394,9,'page.plugins.success.installed','插件安装完成','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(395,9,'page.plugins.success.rollback','插件已回滚','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(396,9,'page.plugins.success.uninstalled','插件已卸载','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(397,9,'page.plugins.success.uninstalledAndDeleted','插件已卸载，并已删除数据库数据','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(398,9,'page.plugins.success.uploaded','插件上传并完成校验','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(399,9,'page.plugins.title','插件管理','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(400,9,'page.plugins.uninstall','卸载插件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(401,9,'page.plugins.uninstallAndDeleteData','卸载并删除数据库数据','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(402,9,'page.plugins.uninstallDesc','你可以选择是否同时删除插件相关数据库数据。选择删除后，会清理插件运行日志、启用关联、版本记录和插件定义等数据。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(403,9,'page.plugins.uninstallWithName','卸载 {name}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(404,9,'page.plugins.upload','上传插件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(405,9,'page.plugins.uploadConfirm','上传','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(406,9,'page.plugins.uploadPackage','上传插件包','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(407,9,'page.plugins.versionCount','版本数量','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(408,9,'page.plugins.versionManagement','版本管理','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(409,9,'page.profile.avatar.selectImage','请选择图片文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(410,9,'page.profile.avatar.uploadFailed','头像上传失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(411,9,'page.profile.avatar.uploadSuccess','头像已上传，请点击保存资料','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(412,9,'page.profile.bind.codeFailed','验证码校验失败，请重试。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(413,9,'page.profile.bind.codeSent','验证码已发送，请输入验证码后继续','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(414,9,'page.profile.bind.completed','绑定已完成','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(415,9,'page.profile.bind.emailBound','邮箱已绑定','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(416,9,'page.profile.bind.emailDisabled','当前未启用邮箱验证码，暂不允许绑定邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(417,9,'page.profile.bind.enterCode','请输入验证码。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(418,9,'page.profile.bind.expired','绑定信息已失效，请重新发起绑定。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(419,9,'page.profile.bind.failed','绑定失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(420,9,'page.profile.bind.fetchFailed','获取绑定信息失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(421,9,'page.profile.bind.mobileBound','手机号已绑定','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(422,9,'page.profile.bind.mobileDisabled','当前未启用短信验证码，暂不允许绑定手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(423,9,'page.profile.bind.sendFailed','验证码发送失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(424,9,'page.profile.bind.unbindFailed','解绑失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(425,9,'page.profile.bind.unbound','已解绑','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(426,9,'page.profile.bind.updateSuccess','个人资料已更新','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(427,9,'page.profile.contact.bindEmail','绑定邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(428,9,'page.profile.contact.bindMobile','绑定手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(429,9,'page.profile.contact.bound','已绑定','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(430,9,'page.profile.contact.confirm','确认绑定','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(431,9,'page.profile.contact.description.email.disabled','当前未启用邮箱验证码，暂不允许绑定邮箱。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(432,9,'page.profile.contact.description.email.required','当前已开启邮箱验证码验证，绑定邮箱时需要先获取并输入验证码。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(433,9,'page.profile.contact.description.mobile.disabled','当前未启用短信验证码，暂不允许绑定手机号。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(434,9,'page.profile.contact.description.mobile.required','当前已开启短信验证码验证，绑定手机号时需要先获取并输入验证码。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(435,9,'page.profile.contact.editEmail','修改邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(436,9,'page.profile.contact.editMobile','修改手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(437,9,'page.profile.contact.email','邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(438,9,'page.profile.contact.label.email','邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(439,9,'page.profile.contact.label.mobile','手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(440,9,'page.profile.contact.mobile','手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(441,9,'page.profile.contact.notSetEmail','未设置邮箱','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(442,9,'page.profile.contact.notSetMobile','未设置手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(443,9,'page.profile.contact.placeholder.email','请输入邮箱地址','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(444,9,'page.profile.contact.placeholder.mobile','请输入手机号','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(445,9,'page.profile.contact.save','保存','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(446,9,'page.profile.contact.sendCode','发送验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(447,9,'page.profile.contact.unbound','未绑定','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(448,9,'page.profile.contact.verificationRequired','需验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(449,9,'page.profile.loading','加载中','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(450,9,'page.profile.loginMethod.email','邮箱登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(451,9,'page.profile.loginMethod.emailCode','邮箱登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(452,9,'page.profile.loginMethod.mobile','手机号登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(453,9,'page.profile.loginMethod.smsCode','短信登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(454,9,'page.profile.loginRecord','登录记录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(455,9,'page.profile.passkey.bound','通行密钥已绑定','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(456,9,'page.profile.passkey.cancelled','已取消通行密钥绑定','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(457,9,'page.profile.passkey.failed','通行密钥绑定失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(458,9,'page.profile.passkey.renamePrompt','请输入通行密钥名称','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(459,9,'page.profile.passkey.renamed','通行密钥已重命名','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(460,9,'page.profile.passkey.timeout','通行密钥绑定超时，请重新尝试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(461,9,'page.profile.passkey.unsupported','当前浏览器不支持通行密钥','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(462,9,'page.profile.recentLogins','最近登录记录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(463,9,'page.profile.recentLogins.none','暂无最近登录记录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(464,9,'page.profile.recentLogins.record','登录记录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(465,9,'page.profile.recentLogins.unknownUser','未知用户','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(466,9,'page.profile.title','个人中心','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(467,9,'page.security.accessTokenExpire','Access Token 过期时间（秒）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(468,9,'page.security.accessTokenExpire.help','Access Token 的有效秒数。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(469,9,'page.security.captcha.image','图片验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(470,9,'page.security.captcha.slider','拖动验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(471,9,'page.security.captchaDescription','开启后，登录页会要求完成人机验证码。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(472,9,'page.security.captchaSettings','验证码设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(473,9,'page.security.captchaType','验证码类型','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(474,9,'page.security.captchaType.help','图片验证码需要输入字符；拖动验证码需要拖动拼图完成校验。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(475,9,'page.security.defenseDescription','用于限制账号与 IP 维度的高频登录尝试，减少爆破和脚本攻击。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(476,9,'page.security.defenseThreshold','防御阈值','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(477,9,'page.security.enableCaptcha','启用人机验证码','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(478,9,'page.security.enableCaptcha.help','关闭后登录页不会展示验证码。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(479,9,'page.security.example10','例如：10','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(480,9,'page.security.example100','例如：100','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(481,9,'page.security.example1800','例如：1800','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(482,9,'page.security.example300','例如：300','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(483,9,'page.security.example5','例如：5','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(484,9,'page.security.example6','例如：6','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(485,9,'page.security.example60','例如：60','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(486,9,'page.security.example604800','例如：604800','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(487,9,'page.security.idleTimeout','空闲超时（秒）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(488,9,'page.security.idleTimeout.help','用户在无操作状态下允许保持登录的秒数。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(489,9,'page.security.maxAttempts','最大验证次数','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(490,9,'page.security.maxAttempts.help','统计窗口内允许的最大登录验证请求次数，超过后将拦截。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(491,9,'page.security.maxFailures','最大错误次数','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(492,9,'page.security.maxFailures.help','统计窗口内允许的最大登录失败次数，超过后将拦截。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(493,9,'page.security.multiDeviceLogin','多设备登录','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(494,9,'page.security.multiDeviceLogin.help','关闭后，同一账号在新的设备登录时，旧设备的会话将自动失效。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(495,9,'page.security.password.consecutive','允许连续字符','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(496,9,'page.security.password.consecutive.help','关闭后，密码中不能包含类似 123 或 abc 的连续字符。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(497,9,'page.security.password.lowercase','必须包含小写字母','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(498,9,'page.security.password.lowercase.help','强制密码中必须包含 a-z。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(499,9,'page.security.password.minLength','最短长度','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(500,9,'page.security.password.minLength.help','用户密码允许的最少字符数。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(501,9,'page.security.password.special','必须包含特殊字符','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(502,9,'page.security.password.special.help','强制密码中必须包含特殊字符，例如 !@#$%^&*。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(503,9,'page.security.password.uppercase','必须包含大写字母','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(504,9,'page.security.password.uppercase.help','强制密码中必须包含 A-Z。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(505,9,'page.security.passwordPolicy','密码规范设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(506,9,'page.security.passwordPolicy.help','这些规则会直接作用于用户新增、重置密码和修改密码的服务端校验。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(507,9,'page.security.refreshTokenExpire','Refresh Token 刷新时限（秒）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(508,9,'page.security.refreshTokenExpire.help','Refresh Token 的有效秒数。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(509,9,'page.security.resetDefault','恢复默认值','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(510,9,'page.security.save','保存设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(511,9,'page.security.saved','安全设置已保存，并已立即生效','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(512,9,'page.security.sliderPreview','拖动验证码预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(513,9,'page.security.sliderPreview.help','拖动验证码预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(514,9,'page.security.sliderPreviewSuccess','拖动验证码预览验证通过','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(515,9,'page.security.title','安全设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(516,9,'page.security.tokenDescription','这部分配置决定登录会话、Access Token 和 Refresh Token 的生命周期。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(517,9,'page.security.tokenStrategy','Token 策略','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(518,9,'page.security.verificationCodeCooldown','发送倒计时（秒）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(519,9,'page.security.verificationCodeCooldown.help','验证码发送后，发送按钮会倒计时，期间不能再次发送。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(520,9,'page.security.verificationCodeCooldown.required','请输入发送倒计时时间','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(521,9,'page.security.verificationCodeExpire','验证码过期时间（秒）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(522,9,'page.security.verificationCodeExpire.help','短信和邮箱验证码在多少秒后过期。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(523,9,'page.security.verificationCodeExpire.required','请输入验证码过期时间','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(524,9,'page.security.window','统计窗口（分钟）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(525,9,'page.security.window.help','用于统计高频访问的时间窗口大小。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(526,10,'role.builtin','内置角色','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(527,10,'role.custom','自定义角色','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(528,10,'role.system','系统角色','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(529,11,'settings.menu','系统设置','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(530,12,'system.files.bucketUploadDisabled','管理端存储空间不支持上传文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(531,12,'system.files.category.business','业务资料','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(532,12,'system.files.category.contract','合同协议','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(533,12,'system.files.category.image','图片素材','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(534,12,'system.files.category.other','其他','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(535,12,'system.files.category.rules','制度文档','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(536,12,'system.files.copyFailed','复制失败，请手动复制链接','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(537,12,'system.files.copySuccess','链接已复制','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(538,12,'system.files.delete.confirmMine','确认删除文件「{name}」吗？删除后文件和记录都会被清理。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(539,12,'system.files.delete.confirmTenant','确认删除文件「{name}」吗？删除后文件和记录都会被清理，可能影响头像、Logo、品牌图等正在引用这个文件的业务展示。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(540,12,'system.files.delete.okText','确认删除','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(541,12,'system.files.delete.title','删除文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(542,12,'system.files.deleteSuccess','文件已删除','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(543,12,'system.files.detailLoadFailed','文件详情加载失败，已使用列表数据展示','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(544,12,'system.files.downloadFailed','下载失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(545,12,'system.files.drawer.cancel','取消','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(546,12,'system.files.drawer.categoryLabel','分类','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(547,12,'system.files.drawer.categoryPlaceholder','如：制度文档、业务资料、合同协议','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(548,12,'system.files.drawer.draggerHint','仅允许 PDF、Word、Excel、PPT，一次最多上传 5 个。','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(549,12,'system.files.drawer.draggerTitle','点击或拖拽文件到这里上传','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(550,12,'system.files.drawer.remarkLabel','备注','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(551,12,'system.files.drawer.remarkPlaceholder','可选，写一些文件说明','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(552,12,'system.files.drawer.selectFiles','选择文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(553,12,'system.files.drawer.startUpload','开始上传','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(554,12,'system.files.drawer.tagsExtra','多个标签请用英文逗号分隔','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(555,12,'system.files.drawer.tagsLabel','标签','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(556,12,'system.files.drawer.tagsPlaceholder','如：运营,合同,归档','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(557,12,'system.files.drawer.uploadTitle','上传文档','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(558,12,'system.files.field.actions','操作','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(559,12,'system.files.field.category','分类','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(560,12,'system.files.field.fileName','文件名','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(561,12,'system.files.field.size','大小','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(562,12,'system.files.field.tags','标签','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(563,12,'system.files.field.type','类型','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(564,12,'system.files.field.uploadTime','上传时间','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(565,12,'system.files.field.uploader','上传人','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(566,12,'system.files.maxUploadCount','一次最多上传 {count} 个文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(567,12,'system.files.onlySupportDocument','仅允许上传 PDF、Word、Excel、PPT 文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(568,12,'system.files.pdfPreviewFailed','PDF 预览加载失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(569,12,'system.files.preview.close','关闭','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(570,12,'system.files.preview.downloadLink','下载链接','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(571,12,'system.files.preview.downloadOnly','仅下载','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(572,12,'system.files.preview.image','图片预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(573,12,'system.files.preview.loadingDetails','正在加载文件详情','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(574,12,'system.files.preview.loadingPdf','正在加载 PDF 预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(575,12,'system.files.preview.loadingText','正在加载文本内容','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(576,12,'system.files.preview.noText','暂无文本内容','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(577,12,'system.files.preview.none','暂无文件详情','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(578,12,'system.files.preview.onlineTitle','在线预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(579,12,'system.files.preview.pdf','PDF 预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(580,12,'system.files.preview.text','文本预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(581,12,'system.files.preview.title','文件预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(582,12,'system.files.preview.unsupportedHint','你可以直接下载文件查看完整内容','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(583,12,'system.files.preview.unsupportedTitle','当前格式暂不支持在线预览','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(584,12,'system.files.search.category','输入分类名称','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(585,12,'system.files.search.categoryLabel','分类','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(586,12,'system.files.search.keyword','文件名、标签、备注','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(587,12,'system.files.search.keywordLabel','关键字','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(588,12,'system.files.selectUploadFile','请先选择要上传的文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(589,12,'system.files.storage.provider.aliyunOss','阿里云 OSS','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(590,12,'system.files.storage.provider.local','本地存储','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(591,12,'system.files.storage.provider.tencentCos','腾讯云 COS','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(592,12,'system.files.storage.rename.appendRandomId','追加随机 ID','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(593,12,'system.files.storage.rename.keepOriginal','保持原名（同名文件将被覆盖）','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(594,12,'system.files.storage.rename.randomString','随机字符串','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(595,12,'system.files.textPreviewFailed','文本预览加载失败','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(596,12,'system.files.title.all','文件管理器','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(597,12,'system.files.title.downloadCenter','下载中心','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(598,12,'system.files.title.my','我的文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(599,12,'system.files.uploadFailed','文件上传失败，请稍后重试','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(600,12,'system.files.uploadSuccess','已上传 {count} 个文件','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(601,13,'theme.compact','紧凑主题','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(602,13,'theme.dark','暗黑主题','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(603,13,'theme.light','浅色主题','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(604,13,'theme.switch','主题切换，当前{theme}','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(605,13,'theme.system','跟随系统','zh-CN','UI','lumira-ui/src/locales/zh-CN.ts','ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0);
/*!40000 ALTER TABLE `sys_localization_entry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_localization_language`
--

DROP TABLE IF EXISTS `sys_localization_language`;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_localization_language`
--

LOCK TABLES `sys_localization_language` WRITE;
/*!40000 ALTER TABLE `sys_localization_language` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_localization_language` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_localization_namespace`
--

DROP TABLE IF EXISTS `sys_localization_namespace`;
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
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_localization_namespace`
--

LOCK TABLES `sys_localization_namespace` WRITE;
/*!40000 ALTER TABLE `sys_localization_namespace` DISABLE KEYS */;
INSERT INTO `sys_localization_namespace` VALUES (1,'app','app · UI','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(2,'auth','认证','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(3,'common','公共','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(4,'github','github · UI','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(5,'global','global · UI','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(6,'help','help · UI','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(7,'message','消息','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(8,'nav','导航','ROUTE','lumira-ui/src/routes/meta.ts',0,'ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(9,'page','页面','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(10,'role','role · UI','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(11,'settings','settings · UI','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(12,'system','系统','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(13,'theme','主题','UI','lumira-ui/src/locales/zh-CN.ts',0,'ENABLED',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0);
/*!40000 ALTER TABLE `sys_localization_namespace` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_localization_release`
--

DROP TABLE IF EXISTS `sys_localization_release`;
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

--
-- Dumping data for table `sys_localization_release`
--

LOCK TABLES `sys_localization_release` WRITE;
/*!40000 ALTER TABLE `sys_localization_release` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_localization_release` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_localization_translation`
--

DROP TABLE IF EXISTS `sys_localization_translation`;
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
) ENGINE=InnoDB AUTO_INCREMENT=1211 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_localization_translation`
--

LOCK TABLES `sys_localization_translation` WRITE;
/*!40000 ALTER TABLE `sys_localization_translation` DISABLE KEYS */;
INSERT INTO `sys_localization_translation` VALUES (1,1,'zh-CN','后端暂未准备好','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(2,1,'en-US','Backend is not ready yet','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(3,2,'zh-CN','后端暂未启动，{seconds} 秒后自动重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(4,2,'en-US','Backend has not started yet. Retrying automatically in {seconds}s','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(5,3,'zh-CN','后端暂未就绪，正在进行第 {attempt} 次重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(6,3,'en-US','Backend is not ready yet. Retrying attempt {attempt}.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(7,4,'zh-CN','后端启动中','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(8,4,'en-US','Backend starting','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(9,5,'zh-CN','正在检查后端服务','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(10,5,'en-US','Checking backend service','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(11,6,'zh-CN','正在检查服务是否可用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(12,6,'en-US','Checking whether the service is available','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(13,7,'zh-CN','正在连接后端','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(14,7,'en-US','Connecting to backend','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(15,8,'zh-CN','正在进入工作台','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(16,8,'en-US','Entering the workbench','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(17,9,'zh-CN','后端健康检查返回了前端页面，请检查 API 代理配置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(18,9,'en-US','The backend health check returned a lumira-ui page. Please check the API proxy configuration.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(19,10,'zh-CN','后端健康检查失败：HTTP {status}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(20,10,'en-US','Backend health check failed: HTTP {status}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(21,11,'zh-CN','后端健康检查未返回 JSON，请检查 API 代理配置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(22,11,'en-US','The backend health check did not return JSON. Please check the API proxy configuration.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(23,12,'zh-CN','后端健康状态异常：{status}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(24,12,'en-US','Backend health status is abnormal: {status}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(25,13,'zh-CN','加载品牌信息','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(26,13,'en-US','Loading brand information','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(27,14,'zh-CN','加载安全配置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(28,14,'en-US','Loading security settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(29,15,'zh-CN','正在准备登录后的菜单、插件和外观设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(30,15,'en-US','Preparing menus, plugins, and appearance settings for the signed-in experience','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(31,16,'zh-CN','系统已就绪','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(32,16,'en-US','System ready','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(33,17,'zh-CN','正在展示登录页','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(34,17,'en-US','Showing login page','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(35,18,'zh-CN','正在启动系统','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(36,18,'en-US','Starting system','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(37,19,'zh-CN','正在同步登录页品牌与外观设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(38,19,'en-US','Syncing brand and appearance settings for the login page','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(39,20,'zh-CN','正在同步登录策略','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(40,20,'en-US','Syncing login policy','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(41,21,'zh-CN','正在同步登录后可见的品牌与外观设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(42,21,'en-US','Syncing brand and appearance settings visible after login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(43,22,'zh-CN','同步系统资源','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(44,22,'en-US','Sync system resources','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(45,23,'zh-CN','返回主路由','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(46,23,'en-US','Back to main route','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(47,24,'zh-CN','请在个性化设置上传二维码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(48,24,'en-US','Please upload a QR code in personalization settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(49,25,'zh-CN','当前语言：{locale}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(50,25,'en-US','Current language: {locale}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(51,26,'zh-CN','English','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(52,26,'en-US','English','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(53,27,'zh-CN','语言偏好已保存','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(54,27,'en-US','Language preference saved','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(55,28,'zh-CN','语言切换','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(56,28,'en-US','Language','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(57,29,'zh-CN','中文','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(58,29,'en-US','Chinese','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(59,30,'zh-CN','退出登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(60,30,'en-US','Log out','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(61,31,'zh-CN','操作','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(62,31,'en-US','Actions','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(63,32,'zh-CN','返回','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(64,32,'en-US','Back','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(65,33,'zh-CN','请求内容有误，请检查后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(66,33,'en-US','The request is invalid. Please check and try again.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(67,34,'zh-CN','当前操作无法完成，请检查业务状态','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(68,34,'en-US','This operation cannot be completed. Please check the business state.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(69,35,'zh-CN','取消','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(70,35,'en-US','Cancel','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(71,36,'zh-CN','向右拖动滑块完成验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(72,36,'en-US','Drag the slider to the right to complete verification','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(73,37,'zh-CN','验证失败，请重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(74,37,'en-US','Verification failed. Please try again.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(75,38,'zh-CN','拖动验证码已失效','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(76,38,'en-US','The slider captcha has expired.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(77,39,'zh-CN','加载失败，点击重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(78,39,'en-US','Failed to load. Click to retry','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(79,40,'zh-CN','验证码加载中...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(80,40,'en-US','Captcha is loading...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(81,41,'zh-CN','拖动验证码资源不完整','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(82,41,'en-US','The slider captcha assets are incomplete.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(83,42,'zh-CN','验证通过','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(84,42,'en-US','Verification passed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(85,43,'zh-CN','正在校验...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(86,43,'en-US','Verifying...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(87,44,'zh-CN','收起','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(88,44,'en-US','Collapse','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(89,45,'zh-CN','确认','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(90,45,'en-US','Confirm','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(91,46,'zh-CN','复制链接','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(92,46,'en-US','Copy link','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(93,47,'zh-CN','删除','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(94,47,'en-US','Delete','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(95,48,'zh-CN','已删除','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(96,48,'en-US','Deleted','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(97,49,'zh-CN','下载','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(98,49,'en-US','Download','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(99,50,'zh-CN','编辑','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(100,50,'en-US','Edit','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(101,51,'zh-CN','展开','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(102,51,'en-US','Expand','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(103,52,'zh-CN','操作失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(104,52,'en-US','Operation failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(105,53,'zh-CN','请输入有效身份证号码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(106,53,'en-US','Please enter a valid ID number.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(107,54,'zh-CN','请输入有效手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(108,54,'en-US','Please enter a valid mobile number.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(109,55,'zh-CN','处理中...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(110,55,'en-US','Loading...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(111,56,'zh-CN','必须大于 0','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(112,56,'en-US','Must be greater than 0','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(113,57,'zh-CN','网络异常，请检查连接后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(114,57,'en-US','A network error occurred. Please check your connection and try again.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(115,58,'zh-CN','当前账号没有访问权限','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(116,58,'en-US','This account does not have access permission.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(117,59,'zh-CN','请输入','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(118,59,'en-US','Please enter','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(119,60,'zh-CN','请先登录后再继续操作','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(120,60,'en-US','Please sign in before continuing.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(121,61,'zh-CN','请选择','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(122,61,'en-US','Please select','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(123,62,'zh-CN','插件入口未成功注册','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(124,62,'en-US','The plugin entry was not registered successfully.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(125,63,'zh-CN','插件加载失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(126,63,'en-US','Failed to load the plugin. Please try again later.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(127,64,'zh-CN','插件 manifest 的 entry 必须包含在 assets 中','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(128,64,'en-US','The manifest entry must be included in assets.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(129,65,'zh-CN','插件 manifest 缺少必要字段','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(130,65,'en-US','The plugin manifest is missing required fields.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(131,66,'zh-CN','插件 manifest 必须声明 react 共享依赖','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(132,66,'en-US','The plugin manifest must declare the react shared dependency.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(133,67,'zh-CN','当前未启用该插件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(134,67,'en-US','This plugin is not enabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(135,68,'zh-CN','插件渲染失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(136,68,'en-US','Plugin rendering failed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(137,69,'zh-CN','加载插件资源失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(138,69,'en-US','Failed to load plugin resources. Please try again later.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(139,70,'zh-CN','插件脚本执行失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(140,70,'en-US','Failed to execute the plugin script','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(141,71,'zh-CN','插件脚本加载失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(142,71,'en-US','Failed to load the plugin script','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(143,72,'zh-CN','插件样式加载失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(144,72,'en-US','Failed to load the plugin style','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(145,73,'zh-CN','插件不可用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(146,73,'en-US','Plugin unavailable','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(147,74,'zh-CN','查询','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(148,74,'en-US','Search','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(149,75,'zh-CN','刷新','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(150,75,'en-US','Refresh','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(151,76,'zh-CN','请求超时，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(152,76,'en-US','The request timed out. Please try again later.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(153,77,'zh-CN','重置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(154,77,'en-US','Reset','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(155,78,'zh-CN','请求的资源不存在','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(156,78,'en-US','The requested resource was not found.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(157,79,'zh-CN','重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(158,79,'en-US','Retry','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(159,80,'zh-CN','保存','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(160,80,'en-US','Save','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(161,81,'zh-CN','服务暂时不可用，请稍后再试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(162,81,'en-US','The service is temporarily unavailable. Please try again later.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(163,82,'zh-CN','登录状态已失效，请重新登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(164,82,'en-US','Your session has expired. Please sign in again.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(165,83,'zh-CN','操作成功','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(166,83,'en-US','Success','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(167,84,'zh-CN','系统异常，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(168,84,'en-US','A system error occurred. Please try again later.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(169,85,'zh-CN','操作过于频繁，请稍后再试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(170,85,'en-US','Too many requests. Please try again later.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(171,86,'zh-CN','上传','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(172,86,'en-US','Upload','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(173,87,'zh-CN','上传文档','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(174,87,'en-US','Upload document','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(175,88,'zh-CN','GitHub 链接','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(176,88,'en-US','GitHub link','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(177,89,'zh-CN','GitHub 链接（未配置）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(178,89,'en-US','GitHub link (not configured)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(179,90,'zh-CN','回到顶部','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(180,90,'en-US','Back to top','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(181,91,'zh-CN','二维码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(182,91,'en-US','QR code','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(183,92,'zh-CN','刷新页面','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(184,92,'en-US','Refresh page','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(185,93,'zh-CN','帮助中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(186,93,'en-US','Help center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(187,94,'zh-CN','帮助中心（未配置链接）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(188,94,'en-US','Help center (not configured)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(189,95,'zh-CN','全部','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(190,95,'en-US','All','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(191,96,'zh-CN','消息中心，当前有 {count} 条未读消息','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(192,96,'en-US','Message center, {count} unread messages','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(193,97,'zh-CN','消息通道已连接','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(194,97,'en-US','Message channel connected','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(195,98,'zh-CN','共 {total} 条消息 · {unread} 条未读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(196,98,'en-US','{total} messages in total · {unread} unread','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(197,99,'zh-CN','详情','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(198,99,'en-US','Details','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(199,100,'zh-CN','消息加载失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(200,100,'en-US','Failed to load messages, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(201,101,'zh-CN','部分消息加载失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(202,101,'en-US','Some messages failed to load, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(203,102,'zh-CN','加载消息中','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(204,102,'en-US','Loading messages','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(205,103,'zh-CN','全部标为已读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(206,103,'en-US','Mark all as read','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(207,104,'zh-CN','标为已读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(208,104,'en-US','Mark as read','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(209,105,'zh-CN','消息类型','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(210,105,'en-US','Message type','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(211,106,'zh-CN','你有一条新的站内信，请前往消息中心查看。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(212,106,'en-US','You have a new inbox message. Open Message Center to view it.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(213,107,'zh-CN','收到新消息','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(214,107,'en-US','New message received','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(215,108,'zh-CN','暂无消息','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(216,108,'en-US','No messages yet','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(217,109,'zh-CN','暂无符合条件的消息','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(218,109,'en-US','No messages match the current filter','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(219,110,'zh-CN','预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(220,110,'en-US','Preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(221,111,'zh-CN','发布','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(222,111,'en-US','Publish','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(223,112,'zh-CN','已发布','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(224,112,'en-US','Published','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(225,113,'zh-CN','已读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(226,113,'en-US','Read','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(227,114,'zh-CN','未读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(228,114,'en-US','Unread','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(229,115,'zh-CN','已读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(230,115,'en-US','Read','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(231,116,'zh-CN','刷新','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(232,116,'en-US','Refresh','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(233,117,'zh-CN','撤回','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(234,117,'en-US','Retract','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(235,118,'zh-CN','已撤回','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(236,118,'en-US','Retracted','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(237,119,'zh-CN','站内信','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(238,119,'en-US','Inbox','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(239,120,'zh-CN','已读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(240,120,'en-US','Read','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(241,121,'zh-CN','未读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(242,121,'en-US','Unread','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(243,122,'zh-CN','时间：{time}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(244,122,'en-US','Time: {time}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(245,123,'zh-CN','刚刚','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(246,123,'en-US','Just now','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(247,124,'zh-CN','即将','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(248,124,'en-US','Soon','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(249,125,'zh-CN','消息中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(250,125,'en-US','Message Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(251,126,'zh-CN','站内信','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(252,126,'en-US','Inbox','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(253,127,'zh-CN','未读','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(254,127,'en-US','Unread','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(255,128,'zh-CN','活动管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(256,128,'en-US','Activity Management','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(257,129,'zh-CN','活动','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(258,129,'en-US','Activities','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(259,130,'zh-CN','活动查询','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(260,130,'en-US','Activity Search','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(261,131,'zh-CN','活动','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(262,131,'en-US','Activities','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(263,132,'zh-CN','AI 助手','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(264,132,'en-US','AI Assistant','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(265,133,'zh-CN','知识库','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(266,133,'en-US','Knowledge Base','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(267,134,'zh-CN','AI','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(268,134,'en-US','AI','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(269,135,'zh-CN','工作台','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(270,135,'en-US','Workbench','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(271,136,'zh-CN','工作台','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(272,136,'en-US','Workbench','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(273,137,'zh-CN','专家管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(274,137,'en-US','Expert Management','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(275,138,'zh-CN','专家库','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(276,138,'en-US','Expert Library','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(277,139,'zh-CN','文件管理器','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(278,139,'en-US','File Manager','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(279,140,'zh-CN','下载中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(280,140,'en-US','Download Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(281,141,'zh-CN','我的文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(282,141,'en-US','My Files','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(283,142,'zh-CN','文件中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(284,142,'en-US','File Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(285,143,'zh-CN','本地化中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(286,143,'en-US','Localization Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(287,144,'zh-CN','系统设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(288,144,'en-US','Settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(289,145,'zh-CN','数字员工','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(290,145,'en-US','AI Employees','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(291,146,'zh-CN','字典管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(292,146,'en-US','Dictionaries','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(293,147,'zh-CN','菜单管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(294,147,'en-US','Menus','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(295,148,'zh-CN','接口文档','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(296,148,'en-US','API Docs','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(297,149,'zh-CN','审计中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(298,149,'en-US','Audit Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(299,150,'zh-CN','Redis监控','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(300,150,'en-US','Redis Monitoring','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(301,151,'zh-CN','系统监控','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(302,151,'en-US','Monitoring','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(303,152,'zh-CN','服务监控','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(304,152,'en-US','Service Monitoring','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(305,153,'zh-CN','通知中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(306,153,'en-US','Notification Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(307,154,'zh-CN','支付设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(308,154,'en-US','Payment Settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(309,155,'zh-CN','个性化设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(310,155,'en-US','Personalization','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(311,156,'zh-CN','插件管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(312,156,'en-US','Plugins','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(313,157,'zh-CN','字段管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(314,157,'en-US','Profile Fields','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(315,158,'zh-CN','系统总览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(316,158,'en-US','System Overview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(317,159,'zh-CN','安全设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(318,159,'en-US','Security','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(319,160,'zh-CN','验证管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(320,160,'en-US','Verification','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(321,161,'zh-CN','创建团队','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(322,161,'en-US','Create Team','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(323,162,'zh-CN','团队详情','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(324,162,'en-US','Team Detail','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(325,163,'zh-CN','团队邀请','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(326,163,'en-US','Team Invites','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(327,164,'zh-CN','加入团队','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(328,164,'en-US','Join Team','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(329,165,'zh-CN','团队管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(330,165,'en-US','Team Management','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(331,166,'zh-CN','团队成员','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(332,166,'en-US','Team Members','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(333,167,'zh-CN','团队','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(334,167,'en-US','Teams','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(335,168,'zh-CN','团队','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(336,168,'en-US','Teams','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(337,169,'zh-CN','用户中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(338,169,'en-US','User Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(339,170,'zh-CN','修改密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(340,170,'en-US','Change password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(341,171,'zh-CN','组织部门','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(342,171,'en-US','Departments','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(343,172,'zh-CN','用户菜单','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(344,172,'en-US','User menu','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(345,173,'zh-CN','在线用户','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(346,173,'en-US','Online Users','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(347,174,'zh-CN','确认新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(348,174,'en-US','Confirm new password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(349,175,'zh-CN','两次输入的新密码不一致','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(350,175,'en-US','The two passwords do not match','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(351,176,'zh-CN','当前密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(352,176,'en-US','Current password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(353,177,'zh-CN','请再次输入新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(354,177,'en-US','Please enter the new password again','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(355,178,'zh-CN','请输入当前密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(356,178,'en-US','Please enter your current password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(357,179,'zh-CN','请输入新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(358,179,'en-US','Please enter a new password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(359,180,'zh-CN','密码长度至少为 {length} 位','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(360,180,'en-US','Password must be at least {length} characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(361,181,'zh-CN','新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(362,181,'en-US','New password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(363,182,'zh-CN','需包含小写字母','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(364,182,'en-US','include lowercase letters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(365,183,'zh-CN','至少 {length} 位','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(366,183,'en-US','at least {length} characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(367,184,'zh-CN','需包含特殊字符','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(368,184,'en-US','include special characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(369,185,'zh-CN','密码规则：','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(370,185,'en-US','Password rules:','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(371,186,'zh-CN','需包含大写字母','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(372,186,'en-US','include uppercase letters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(373,187,'zh-CN','需包含小写字母','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(374,187,'en-US','include lowercase letters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(375,188,'zh-CN','需包含特殊字符','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(376,188,'en-US','include special characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(377,189,'zh-CN','需包含大写字母','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(378,189,'en-US','include uppercase letters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(379,190,'zh-CN','密码已修改','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(380,190,'en-US','Password updated','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(381,191,'zh-CN','个人中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(382,191,'en-US','Personal Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(383,192,'zh-CN','个人资料','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(384,192,'en-US','Profile','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(385,193,'zh-CN','当前账号权限','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(386,193,'en-US','Current account access','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(387,194,'zh-CN','基于当前账号的默认权限视图','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(388,194,'en-US','Default permission view for the current account','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(389,195,'zh-CN','当前','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(390,195,'en-US','Current','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(391,196,'zh-CN','编码：{roleCode} · {permissionCount} 个权限','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(392,196,'en-US','Code: {roleCode} · {permissionCount} permissions','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(393,197,'zh-CN','已恢复默认权限','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(394,197,'en-US','Default access restored','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(395,198,'zh-CN','角色模拟','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(396,198,'en-US','Role simulation','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(397,199,'zh-CN','当前正在模拟 {roleName}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(398,199,'en-US','Currently simulating {roleName}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(399,200,'zh-CN','角色已切换','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(400,200,'en-US','Role switched','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(401,201,'zh-CN','已切换至 {roleName}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(402,201,'en-US','Switched to {roleName}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(403,202,'zh-CN','角色管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(404,202,'en-US','Roles','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(405,203,'zh-CN','切换角色','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(406,203,'en-US','Switch role','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(407,204,'zh-CN','用户管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(408,204,'en-US','Users','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(409,205,'zh-CN','返回上一页','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(410,205,'en-US','Go back','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(411,206,'zh-CN','回到首页','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(412,206,'en-US','Back to home','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(413,207,'zh-CN','当前账号没有访问该页面的权限','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(414,207,'en-US','Your account does not have permission to access this page.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(415,208,'zh-CN','返回首页','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(416,208,'en-US','Back to home','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(417,209,'zh-CN','页面不存在，请返回首页继续操作。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(418,209,'en-US','This page does not exist. Please return to the home page to continue.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(419,210,'zh-CN','返回首页','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(420,210,'en-US','Back to home','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(421,211,'zh-CN','服务器发生异常，请稍后再试。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(422,211,'en-US','A server error occurred. Please try again later.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(423,212,'zh-CN','当前生效','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(424,212,'en-US','Active','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(425,213,'zh-CN','新增词条','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(426,213,'en-US','Add entry','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(427,214,'zh-CN','新增语言','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(428,214,'en-US','Add language','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(429,215,'zh-CN','全部命名空间','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(430,215,'en-US','All namespaces','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(431,216,'zh-CN','复制键名','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(432,216,'en-US','Copy key','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(433,217,'zh-CN','覆盖率：{rate}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(434,217,'en-US','Coverage: {rate}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(435,218,'zh-CN','当前语言：{locale}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(436,218,'en-US','Current locale: {locale}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(437,219,'zh-CN','当前模块：{namespace}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(438,219,'en-US','Current module: {namespace}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(439,220,'zh-CN','当前状态','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(440,220,'en-US','Current status','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(441,221,'zh-CN','默认','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(442,221,'en-US','Default','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(443,222,'zh-CN','默认语言','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(444,222,'en-US','Default language','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(445,223,'zh-CN','原文','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(446,223,'en-US','Source text','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(447,224,'zh-CN','编辑词条','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(448,224,'en-US','Edit entry','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(449,225,'zh-CN','编辑语言','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(450,225,'en-US','Edit language','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(451,226,'zh-CN','词条已保存','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(452,226,'en-US','Entry saved','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(453,227,'zh-CN','回退语言','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(454,227,'en-US','Fallback locale','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(455,228,'zh-CN','版本历史','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(456,228,'en-US','Version history','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(457,229,'zh-CN','键名','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(458,229,'en-US','Key','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(459,230,'zh-CN','语言名称','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(460,230,'en-US','Language name','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(461,231,'zh-CN','例如：简体中文 / English','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(462,231,'en-US','For example: Simplified Chinese / English','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(463,232,'zh-CN','语言已保存','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(464,232,'en-US','Language saved','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(465,233,'zh-CN','语言切换','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(466,233,'en-US','Language switch','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(467,234,'zh-CN','语言','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(468,234,'en-US','Languages','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(469,235,'zh-CN','语言代码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(470,235,'en-US','Locale code','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(471,236,'zh-CN','命名空间','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(472,236,'en-US','Namespace','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(473,237,'zh-CN','命名空间','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(474,237,'en-US','Namespace','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(475,238,'zh-CN','本地名称','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(476,238,'en-US','Native name','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(477,239,'zh-CN','暂无语言','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(478,239,'en-US','No languages yet','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(479,240,'zh-CN','暂无发布记录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(480,240,'en-US','No release history','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(481,241,'zh-CN','发布','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(482,241,'en-US','Publish','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(483,242,'zh-CN','本地化中心发布','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(484,242,'en-US','Localization Center release','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(485,243,'zh-CN','翻译版本已发布','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(486,243,'en-US','Translation release published','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(487,244,'zh-CN','回滚','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(488,244,'en-US','Rollback','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(489,245,'zh-CN','确认回滚该版本吗？','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(490,245,'en-US','Roll back this release?','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(491,246,'zh-CN','翻译版本已回滚','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(492,246,'en-US','Translation release rolled back','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(493,247,'zh-CN','搜索筛选','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(494,247,'en-US','Search filters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(495,248,'zh-CN','搜索键名、原文或来源','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(496,248,'en-US','Search key, source text or ref','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(497,249,'zh-CN','排序','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(498,249,'en-US','Sort','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(499,250,'zh-CN','源语言','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(500,250,'en-US','Source locale','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(501,251,'zh-CN','来源','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(502,251,'en-US','Source ref','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(503,252,'zh-CN','来源类型','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(504,252,'en-US','Source type','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(505,253,'zh-CN','状态','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(506,253,'en-US','Status','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(507,254,'zh-CN','同步源码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(508,254,'en-US','Sync source','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(509,255,'zh-CN','已同步源码词条','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(510,255,'en-US','Source entries synced','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(511,256,'zh-CN','本地化中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(512,256,'en-US','Localization Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(513,257,'zh-CN','译文','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(514,257,'en-US','Translation','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(515,258,'zh-CN','待翻译','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(516,258,'en-US','Untranslated','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(517,259,'zh-CN','引用数','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(518,259,'en-US','Usage','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(519,260,'zh-CN','我已阅读并同意','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(520,260,'en-US','I have read and agree to','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(521,261,'zh-CN','和','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(522,261,'en-US','and','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(523,262,'zh-CN','后台暂未配置该条款内容。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(524,262,'en-US','The backend has not configured this agreement yet.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(525,263,'zh-CN','协议预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(526,263,'en-US','Agreement preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(527,264,'zh-CN','《隐私协议》','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(528,264,'en-US','Privacy Policy','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(529,265,'zh-CN','请先同意条款后再登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(530,265,'en-US','Please agree to the terms before logging in','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(531,266,'zh-CN','《用户协议》','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(532,266,'en-US','User Agreement','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(533,267,'zh-CN','验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(534,267,'en-US','Captcha','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(535,268,'zh-CN','点击刷新验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(536,268,'en-US','Refresh captcha','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(537,269,'zh-CN','点击刷新','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(538,269,'en-US','Click to refresh','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(539,270,'zh-CN','点击重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(540,270,'en-US','Retry captcha','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(541,271,'zh-CN','拖动验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(542,271,'en-US','Slider verification','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(543,272,'zh-CN','已验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(544,272,'en-US','Verified','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(545,273,'zh-CN','验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(546,273,'en-US','Verify','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(547,274,'zh-CN','验证码已过期，请刷新后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(548,274,'en-US','Captcha expired, please refresh and try again','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(549,275,'zh-CN','请等待 {seconds}s 后再发送','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(550,275,'en-US','Please wait {seconds}s before sending again','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(551,276,'zh-CN','{seconds}s 后重发','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(552,276,'en-US','Resend in {seconds}s','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(553,277,'zh-CN','调试验证码：{code}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(554,277,'en-US','Debug code: {code}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(555,278,'zh-CN','重新发送','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(556,278,'en-US','Resend','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(557,279,'zh-CN','请输入收到的验证码完成二次验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(558,279,'en-US','Please enter the code to complete second-factor verification','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(559,280,'zh-CN','发送验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(560,280,'en-US','Send code','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(561,281,'zh-CN','验证码将发送到 {contact}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(562,281,'en-US','Code will be sent to {contact}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(563,282,'zh-CN','验证码发送失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(564,282,'en-US','Failed to send verification code, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(565,283,'zh-CN','邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(566,283,'en-US','Email','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(567,284,'zh-CN','邮箱验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(568,284,'en-US','Email code','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(569,285,'zh-CN','邮箱验证码登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(570,285,'en-US','Email code login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(571,286,'zh-CN','账号长度不能超过 128 个字符','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(572,286,'en-US','Account cannot exceed 128 characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(573,287,'zh-CN','验证码已过期，请刷新后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(574,287,'en-US','Captcha expired, please refresh and try again','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(575,288,'zh-CN','验证码发送失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(576,288,'en-US','Failed to send verification code, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(577,289,'zh-CN','当前未启用邮箱验证码登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(578,289,'en-US','Email login is not enabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(579,290,'zh-CN','账号包含不允许的字符','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(580,290,'en-US','The account contains unsupported characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(581,291,'zh-CN','验证码只能包含字母和数字','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(582,291,'en-US','Verification code can only contain letters and numbers','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(583,292,'zh-CN','请输入有效邮箱地址','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(584,292,'en-US','Please enter a valid email address','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(585,293,'zh-CN','请输入有效手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(586,293,'en-US','Please enter a valid mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(587,294,'zh-CN','登录加密信息加载失败，请刷新后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(588,294,'en-US','Failed to load login encryption info, please refresh and try again','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(589,295,'zh-CN','登录失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(590,295,'en-US','Login failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(591,296,'zh-CN','当前登录方式不可用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(592,296,'en-US','Current login method is unavailable','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(593,297,'zh-CN','密码长度不能少于 6 位','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(594,297,'en-US','Password must be at least 6 characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(595,298,'zh-CN','请先完成拖动验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(596,298,'en-US','Please complete the slider captcha first','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(597,299,'zh-CN','请输入账号、手机号或邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(598,299,'en-US','Please enter your account, mobile number, or email','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(599,300,'zh-CN','请输入验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(600,300,'en-US','Please enter the captcha','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(601,301,'zh-CN','请输入邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(602,301,'en-US','Please enter your email','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(603,302,'zh-CN','请输入手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(604,302,'en-US','Please enter your mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(605,303,'zh-CN','请输入密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(606,303,'en-US','Please enter your password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(607,304,'zh-CN','请先发送验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(608,304,'en-US','Please send the verification code first','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(609,305,'zh-CN','验证码刷新失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(610,305,'en-US','Captcha refresh failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(611,306,'zh-CN','当前未启用短信验证码登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(612,306,'en-US','SMS login is not enabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(613,307,'zh-CN','确认新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(614,307,'en-US','Confirm new password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(615,308,'zh-CN','请再次输入新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(616,308,'en-US','Please enter the new password again','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(617,309,'zh-CN','密码修改失败，请检查后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(618,309,'en-US','Password update failed, please check and try again','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(619,310,'zh-CN','新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(620,310,'en-US','New password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(621,311,'zh-CN','请输入新密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(622,311,'en-US','Please enter a new password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(623,312,'zh-CN','新密码不能继续使用初始密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(624,312,'en-US','The new password cannot be the initial password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(625,313,'zh-CN','当前账号仍在使用初始密码，必须修改后才能进入系统。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(626,313,'en-US','This account is still using the initial password and must be changed before entering the system.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(627,314,'zh-CN','两次输入的新密码不一致','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(628,314,'en-US','The two passwords do not match','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(629,315,'zh-CN','当前账号仍在使用初始密码，请先修改密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(630,315,'en-US','This account is still using the initial password. Please change it first.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(631,316,'zh-CN','确认修改','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(632,316,'en-US','Confirm change','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(633,317,'zh-CN','密码已修改，请使用新密码登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(634,317,'en-US','Password updated, please log in with the new password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(635,318,'zh-CN','修改初始密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(636,318,'en-US','Change initial password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(637,319,'zh-CN','正在加载登录加密信息...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(638,319,'en-US','Loading login encryption info...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(639,320,'zh-CN','当前登录方式不可用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(640,320,'en-US','Current login method is unavailable','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(641,321,'zh-CN','使用通行密钥登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(642,321,'en-US','Use passkey to log in','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(643,322,'zh-CN','已取消通行密钥验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(644,322,'en-US','Passkey verification cancelled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(645,323,'zh-CN','当前浏览器不支持通行密钥','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(646,323,'en-US','Your browser does not support passkeys','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(647,324,'zh-CN','通行密钥','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(648,324,'en-US','Passkey','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(649,325,'zh-CN','密码登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(650,325,'en-US','Password login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(651,326,'zh-CN','密码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(652,326,'en-US','Password','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(653,327,'zh-CN','密码登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(654,327,'en-US','Password login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(655,328,'zh-CN','记住我','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(656,328,'en-US','Remember me','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(657,329,'zh-CN','{name} 需要完成二次验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(658,329,'en-US','{name} needs second-factor verification','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(659,330,'zh-CN','手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(660,330,'en-US','Mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(661,331,'zh-CN','短信验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(662,331,'en-US','SMS code','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(663,332,'zh-CN','短信验证码登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(664,332,'en-US','SMS code login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(665,333,'zh-CN','登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(666,333,'en-US','Login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(667,334,'zh-CN','验证并登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(668,334,'en-US','Verify and log in','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(669,335,'zh-CN','验证码已发送','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(670,335,'en-US','Verification code sent','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(671,336,'zh-CN','登录成功，正在进入系统','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(672,336,'en-US','Login successful, entering the system','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(673,337,'zh-CN','请输入验证码完成二次验证','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(674,337,'en-US','Please enter the verification code to continue','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(675,338,'zh-CN','登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(676,338,'en-US','Login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(677,339,'zh-CN','微信登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(678,339,'en-US','WeChat login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(679,340,'zh-CN','正在跳转到微信登录...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(680,340,'en-US','Redirecting to WeChat login...','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(681,341,'zh-CN','激活插件版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(682,341,'en-US','Activate plugin version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(683,342,'zh-CN','API 版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(684,342,'en-US','API version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(685,343,'zh-CN','作者','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(686,343,'en-US','Author','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(687,344,'zh-CN','取消','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(688,344,'en-US','Cancel','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(689,345,'zh-CN','取消','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(690,345,'en-US','Cancel','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(691,346,'zh-CN','选择 zip 插件包','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(692,346,'en-US','Choose a zip plugin package','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(693,347,'zh-CN','选择 zip 插件包','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(694,347,'en-US','Choose a zip plugin package','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(695,348,'zh-CN','确认','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(696,348,'en-US','Confirm','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(697,349,'zh-CN','激活插件版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(698,349,'en-US','Activate plugin version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(699,350,'zh-CN','停用插件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(700,350,'en-US','Disable plugin','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(701,351,'zh-CN','启用插件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(702,351,'en-US','Enable plugin','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(703,352,'zh-CN','安装插件版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(704,352,'en-US','Install plugin version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(705,353,'zh-CN','回滚插件版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(706,353,'en-US','Rollback plugin version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(707,354,'zh-CN','确认后将卸载插件 {name}。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(708,354,'en-US','This will uninstall plugin {name}.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(709,355,'zh-CN','当前启用版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(710,355,'en-US','Current enabled version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(711,356,'zh-CN','当前版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(712,356,'en-US','Current version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(713,357,'zh-CN','描述','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(714,357,'en-US','Description','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(715,358,'zh-CN','插件详情','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(716,358,'en-US','Plugin details','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(717,359,'zh-CN','停用插件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(718,359,'en-US','Disable plugin','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(719,360,'zh-CN','启用插件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(720,360,'en-US','Enable plugin','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(721,361,'zh-CN','是否启用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(722,361,'en-US','Enabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(723,362,'zh-CN','未启用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(724,362,'en-US','Disabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(725,363,'zh-CN','已启用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(726,363,'en-US','Enabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(727,364,'zh-CN','激活插件失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(728,364,'en-US','Failed to activate plugin, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(729,365,'zh-CN','停用插件失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(730,365,'en-US','Failed to disable plugin, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(731,366,'zh-CN','启用插件失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(732,366,'en-US','Failed to enable plugin, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(733,367,'zh-CN','安装插件失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(734,367,'en-US','Failed to install plugin, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(735,368,'zh-CN','请先安装可用版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(736,368,'en-US','Please install an available version first','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(737,369,'zh-CN','插件已更新，但列表刷新失败，请手动刷新页面','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(738,369,'en-US','The plugin was updated, but list refresh failed. Please refresh the page manually.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(739,370,'zh-CN','加载插件信息失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(740,370,'en-US','Failed to load plugin information, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(741,371,'zh-CN','加载插件日志失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(742,371,'en-US','Failed to load plugin logs, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(743,372,'zh-CN','插件包不能超过 50MB','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(744,372,'en-US','Plugin packages must not exceed 50MB','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(745,373,'zh-CN','插件已更新，但菜单刷新失败，请手动刷新页面','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(746,373,'en-US','The plugin was updated, but menu refresh failed. Please refresh the page manually.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(747,374,'zh-CN','操作失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(748,374,'en-US','Operation failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(749,375,'zh-CN','回滚插件失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(750,375,'en-US','Failed to rollback plugin, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(751,376,'zh-CN','请先选择插件包','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(752,376,'en-US','Please select a plugin package first','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(753,377,'zh-CN','卸载插件失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(754,377,'en-US','Failed to uninstall plugin, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(755,378,'zh-CN','上传插件失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(756,378,'en-US','Failed to upload plugin, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(757,379,'zh-CN','仅支持 zip 插件包','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(758,379,'en-US','Only zip plugin packages are supported','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(759,380,'zh-CN','安装插件版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(760,380,'en-US','Install plugin version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(761,381,'zh-CN','插件日志','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(762,381,'en-US','Plugin logs','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(763,382,'zh-CN','菜单数','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(764,382,'en-US','Menu count','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(765,383,'zh-CN','插件名称','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(766,383,'en-US','Plugin name','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(767,384,'zh-CN','仅卸载插件，不删除数据库数据','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(768,384,'en-US','Only uninstall the plugin, do not delete database data','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(769,385,'zh-CN','插件编码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(770,385,'en-US','Plugin code','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(771,386,'zh-CN','刷新','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(772,386,'en-US','Refresh','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(773,387,'zh-CN','回滚插件版本','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(774,387,'en-US','Rollback plugin version','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(775,388,'zh-CN','路由数','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(776,388,'en-US','Route count','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(777,389,'zh-CN','输入插件编码或名称','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(778,389,'en-US','Enter plugin code or name','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(779,390,'zh-CN','状态','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(780,390,'en-US','Status','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(781,391,'zh-CN','插件激活版本已切换','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(782,391,'en-US','Plugin activation switched','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(783,392,'zh-CN','插件已停用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(784,392,'en-US','Plugin disabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(785,393,'zh-CN','插件已启用','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(786,393,'en-US','Plugin enabled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(787,394,'zh-CN','插件安装完成','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(788,394,'en-US','Plugin installed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(789,395,'zh-CN','插件已回滚','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(790,395,'en-US','Plugin rolled back','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(791,396,'zh-CN','插件已卸载','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(792,396,'en-US','Plugin uninstalled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(793,397,'zh-CN','插件已卸载，并已删除数据库数据','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(794,397,'en-US','Plugin uninstalled and database data deleted','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(795,398,'zh-CN','插件上传并完成校验','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(796,398,'en-US','Plugin uploaded and verified','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(797,399,'zh-CN','插件管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(798,399,'en-US','Plugins','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(799,400,'zh-CN','卸载插件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(800,400,'en-US','Uninstall plugin','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(801,401,'zh-CN','卸载并删除数据库数据','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(802,401,'en-US','Uninstall and delete database data','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(803,402,'zh-CN','你可以选择是否同时删除插件相关数据库数据。选择删除后，会清理插件运行日志、启用关联、版本记录和插件定义等数据。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(804,402,'en-US','You can choose whether to delete the plugin-related database data as well. If deleted, plugin runtime logs, enablement links, version records, and plugin definitions will be removed.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(805,403,'zh-CN','卸载 {name}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(806,403,'en-US','Uninstall {name}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(807,404,'zh-CN','上传插件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(808,404,'en-US','Upload plugin','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(809,405,'zh-CN','上传','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(810,405,'en-US','Upload','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(811,406,'zh-CN','上传插件包','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(812,406,'en-US','Upload plugin package','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(813,407,'zh-CN','版本数量','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(814,407,'en-US','Version count','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(815,408,'zh-CN','版本管理','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(816,408,'en-US','Version management','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(817,409,'zh-CN','请选择图片文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(818,409,'en-US','Please select an image file','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(819,410,'zh-CN','头像上传失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(820,410,'en-US','Avatar upload failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(821,411,'zh-CN','头像已上传，请点击保存资料','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(822,411,'en-US','Avatar uploaded, please click Save profile','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(823,412,'zh-CN','验证码校验失败，请重试。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(824,412,'en-US','Verification code check failed, please try again.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(825,413,'zh-CN','验证码已发送，请输入验证码后继续','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(826,413,'en-US','Verification code sent, please enter it to continue','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(827,414,'zh-CN','绑定已完成','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(828,414,'en-US','Binding completed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(829,415,'zh-CN','邮箱已绑定','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(830,415,'en-US','Email bound','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(831,416,'zh-CN','当前未启用邮箱验证码，暂不允许绑定邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(832,416,'en-US','Email verification is not enabled, email binding is not allowed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(833,417,'zh-CN','请输入验证码。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(834,417,'en-US','Please enter the verification code.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(835,418,'zh-CN','绑定信息已失效，请重新发起绑定。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(836,418,'en-US','Binding information has expired, please start over.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(837,419,'zh-CN','绑定失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(838,419,'en-US','Binding failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(839,420,'zh-CN','获取绑定信息失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(840,420,'en-US','Failed to load binding information, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(841,421,'zh-CN','手机号已绑定','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(842,421,'en-US','Mobile number bound','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(843,422,'zh-CN','当前未启用短信验证码，暂不允许绑定手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(844,422,'en-US','SMS verification is not enabled, mobile binding is not allowed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(845,423,'zh-CN','验证码发送失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(846,423,'en-US','Verification code send failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(847,424,'zh-CN','解绑失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(848,424,'en-US','Unbinding failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(849,425,'zh-CN','已解绑','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(850,425,'en-US','Unbound','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(851,426,'zh-CN','个人资料已更新','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(852,426,'en-US','Profile updated','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(853,427,'zh-CN','绑定邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(854,427,'en-US','Bind email','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(855,428,'zh-CN','绑定手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(856,428,'en-US','Bind mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(857,429,'zh-CN','已绑定','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(858,429,'en-US','Bound','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(859,430,'zh-CN','确认绑定','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(860,430,'en-US','Confirm binding','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(861,431,'zh-CN','当前未启用邮箱验证码，暂不允许绑定邮箱。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(862,431,'en-US','Email verification is not enabled, email binding is not allowed.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(863,432,'zh-CN','当前已开启邮箱验证码验证，绑定邮箱时需要先获取并输入验证码。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(864,432,'en-US','Email verification is enabled, so you need to request and enter a code before binding your email.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(865,433,'zh-CN','当前未启用短信验证码，暂不允许绑定手机号。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(866,433,'en-US','SMS verification is not enabled, mobile binding is not allowed.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(867,434,'zh-CN','当前已开启短信验证码验证，绑定手机号时需要先获取并输入验证码。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(868,434,'en-US','SMS verification is enabled, so you need to request and enter a code before binding your mobile number.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(869,435,'zh-CN','修改邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(870,435,'en-US','Change email','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(871,436,'zh-CN','修改手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(872,436,'en-US','Change mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(873,437,'zh-CN','邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(874,437,'en-US','Email','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(875,438,'zh-CN','邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(876,438,'en-US','Email','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(877,439,'zh-CN','手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(878,439,'en-US','Mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(879,440,'zh-CN','手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(880,440,'en-US','Mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(881,441,'zh-CN','未设置邮箱','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(882,441,'en-US','Email not set','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(883,442,'zh-CN','未设置手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(884,442,'en-US','Mobile number not set','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(885,443,'zh-CN','请输入邮箱地址','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(886,443,'en-US','Please enter your email address','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(887,444,'zh-CN','请输入手机号','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(888,444,'en-US','Please enter your mobile number','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(889,445,'zh-CN','保存','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(890,445,'en-US','Save','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(891,446,'zh-CN','发送验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(892,446,'en-US','Send code','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(893,447,'zh-CN','未绑定','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(894,447,'en-US','Unbound','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(895,448,'zh-CN','需验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(896,448,'en-US','Verification required','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(897,449,'zh-CN','加载中','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(898,449,'en-US','Loading','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(899,450,'zh-CN','邮箱登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(900,450,'en-US','Email login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(901,451,'zh-CN','邮箱登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(902,451,'en-US','Email login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(903,452,'zh-CN','手机号登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(904,452,'en-US','Mobile login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(905,453,'zh-CN','短信登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(906,453,'en-US','SMS login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(907,454,'zh-CN','登录记录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(908,454,'en-US','Login record','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(909,455,'zh-CN','通行密钥已绑定','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(910,455,'en-US','Passkey bound','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(911,456,'zh-CN','已取消通行密钥绑定','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(912,456,'en-US','Passkey binding cancelled','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(913,457,'zh-CN','通行密钥绑定失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(914,457,'en-US','Passkey binding failed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(915,458,'zh-CN','请输入通行密钥名称','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(916,458,'en-US','Enter a passkey name','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(917,459,'zh-CN','通行密钥已重命名','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(918,459,'en-US','Passkey renamed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(919,460,'zh-CN','通行密钥绑定超时，请重新尝试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(920,460,'en-US','Passkey binding timed out. Please try again.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(921,461,'zh-CN','当前浏览器不支持通行密钥','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(922,461,'en-US','Your browser does not support passkeys','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(923,462,'zh-CN','最近登录记录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(924,462,'en-US','Recent login records','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(925,463,'zh-CN','暂无最近登录记录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(926,463,'en-US','No recent login records','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(927,464,'zh-CN','登录记录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(928,464,'en-US','Login record','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(929,465,'zh-CN','未知用户','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(930,465,'en-US','Unknown user','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(931,466,'zh-CN','个人中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(932,466,'en-US','Personal Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(933,467,'zh-CN','Access Token 过期时间（秒）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(934,467,'en-US','Access Token expiry (seconds)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(935,468,'zh-CN','Access Token 的有效秒数。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(936,468,'en-US','The number of seconds an Access Token remains valid.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(937,469,'zh-CN','图片验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(938,469,'en-US','Image captcha','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(939,470,'zh-CN','拖动验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(940,470,'en-US','Slider captcha','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(941,471,'zh-CN','开启后，登录页会要求完成人机验证码。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(942,471,'en-US','When enabled, the login page requires a human verification challenge.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(943,472,'zh-CN','验证码设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(944,472,'en-US','Captcha settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(945,473,'zh-CN','验证码类型','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(946,473,'en-US','Captcha type','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(947,474,'zh-CN','图片验证码需要输入字符；拖动验证码需要拖动拼图完成校验。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(948,474,'en-US','Image captcha requires typing characters; slider captcha requires dragging the puzzle to complete verification.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(949,475,'zh-CN','用于限制账号与 IP 维度的高频登录尝试，减少爆破和脚本攻击。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(950,475,'en-US','Used to limit high-frequency login attempts by account and IP, reducing brute-force and script attacks.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(951,476,'zh-CN','防御阈值','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(952,476,'en-US','Defense threshold','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(953,477,'zh-CN','启用人机验证码','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(954,477,'en-US','Enable captcha','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(955,478,'zh-CN','关闭后登录页不会展示验证码。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(956,478,'en-US','When disabled, the login page will not show captcha.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(957,479,'zh-CN','例如：10','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(958,479,'en-US','For example: 10','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(959,480,'zh-CN','例如：100','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(960,480,'en-US','For example: 100','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(961,481,'zh-CN','例如：1800','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(962,481,'en-US','For example: 1800','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(963,482,'zh-CN','例如：300','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(964,482,'en-US','For example: 300','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(965,483,'zh-CN','例如：5','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(966,483,'en-US','For example: 5','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(967,484,'zh-CN','例如：6','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(968,484,'en-US','For example: 6','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(969,485,'zh-CN','例如：60','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(970,485,'en-US','For example: 60','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(971,486,'zh-CN','例如：604800','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(972,486,'en-US','For example: 604800','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(973,487,'zh-CN','空闲超时（秒）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(974,487,'en-US','Idle timeout (seconds)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(975,488,'zh-CN','用户在无操作状态下允许保持登录的秒数。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(976,488,'en-US','How long a user can stay signed in while inactive.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(977,489,'zh-CN','最大验证次数','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(978,489,'en-US','Maximum verification attempts','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(979,490,'zh-CN','统计窗口内允许的最大登录验证请求次数，超过后将拦截。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(980,490,'en-US','The maximum number of login verification requests allowed within the window before blocking.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(981,491,'zh-CN','最大错误次数','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(982,491,'en-US','Maximum failure count','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(983,492,'zh-CN','统计窗口内允许的最大登录失败次数，超过后将拦截。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(984,492,'en-US','The maximum number of login failures allowed within the window before blocking.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(985,493,'zh-CN','多设备登录','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(986,493,'en-US','Multi-device login','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(987,494,'zh-CN','关闭后，同一账号在新的设备登录时，旧设备的会话将自动失效。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(988,494,'en-US','When disabled, signing in on a new device will invalidate existing sessions on other devices.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(989,495,'zh-CN','允许连续字符','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(990,495,'en-US','Allow consecutive characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(991,496,'zh-CN','关闭后，密码中不能包含类似 123 或 abc 的连续字符。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(992,496,'en-US','When disabled, passwords cannot contain consecutive sequences like 123 or abc.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(993,497,'zh-CN','必须包含小写字母','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(994,497,'en-US','Must include lowercase letters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(995,498,'zh-CN','强制密码中必须包含 a-z。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(996,498,'en-US','Require a-z in the password.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(997,499,'zh-CN','最短长度','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(998,499,'en-US','Minimum length','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(999,500,'zh-CN','用户密码允许的最少字符数。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1000,500,'en-US','The minimum number of characters allowed for a password.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1001,501,'zh-CN','必须包含特殊字符','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1002,501,'en-US','Must include special characters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1003,502,'zh-CN','强制密码中必须包含特殊字符，例如 !@#$%^&*。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1004,502,'en-US','Require special characters such as !@#$%^&*.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1005,503,'zh-CN','必须包含大写字母','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1006,503,'en-US','Must include uppercase letters','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1007,504,'zh-CN','强制密码中必须包含 A-Z。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1008,504,'en-US','Require A-Z in the password.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1009,505,'zh-CN','密码规范设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1010,505,'en-US','Password policy','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1011,506,'zh-CN','这些规则会直接作用于用户新增、重置密码和修改密码的服务端校验。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1012,506,'en-US','These rules are applied directly to server-side validation for user creation, password reset, and password changes.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1013,507,'zh-CN','Refresh Token 刷新时限（秒）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1014,507,'en-US','Refresh Token refresh window (seconds)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1015,508,'zh-CN','Refresh Token 的有效秒数。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1016,508,'en-US','The number of seconds a Refresh Token remains valid.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1017,509,'zh-CN','恢复默认值','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1018,509,'en-US','Restore defaults','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1019,510,'zh-CN','保存设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1020,510,'en-US','Save settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1021,511,'zh-CN','安全设置已保存，并已立即生效','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1022,511,'en-US','Security settings saved and applied immediately','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1023,512,'zh-CN','拖动验证码预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1024,512,'en-US','Slider captcha preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1025,513,'zh-CN','拖动验证码预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1026,513,'en-US','Slider captcha preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1027,514,'zh-CN','拖动验证码预览验证通过','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1028,514,'en-US','Slider captcha preview verified','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1029,515,'zh-CN','安全设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1030,515,'en-US','Security Settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1031,516,'zh-CN','这部分配置决定登录会话、Access Token 和 Refresh Token 的生命周期。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1032,516,'en-US','These settings control the lifecycle of login sessions, Access Tokens, and Refresh Tokens.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1033,517,'zh-CN','Token 策略','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1034,517,'en-US','Token strategy','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1035,518,'zh-CN','发送倒计时（秒）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1036,518,'en-US','Send countdown (seconds)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1037,519,'zh-CN','验证码发送后，发送按钮会倒计时，期间不能再次发送。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1038,519,'en-US','After a verification code is sent, the send button starts a countdown and cannot be sent again during that time.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1039,520,'zh-CN','请输入发送倒计时时间','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1040,520,'en-US','Please enter the send countdown time','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1041,521,'zh-CN','验证码过期时间（秒）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1042,521,'en-US','Verification code expiry (seconds)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1043,522,'zh-CN','短信和邮箱验证码在多少秒后过期。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1044,522,'en-US','SMS and email verification codes expire after this number of seconds.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1045,523,'zh-CN','请输入验证码过期时间','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1046,523,'en-US','Please enter the verification code expiry time','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1047,524,'zh-CN','统计窗口（分钟）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1048,524,'en-US','Window (minutes)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1049,525,'zh-CN','用于统计高频访问的时间窗口大小。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1050,525,'en-US','The time window used to count high-frequency access.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1051,526,'zh-CN','内置角色','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1052,526,'en-US','Built-in role','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1053,527,'zh-CN','自定义角色','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1054,527,'en-US','Custom role','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1055,528,'zh-CN','系统角色','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1056,528,'en-US','System role','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1057,529,'zh-CN','系统设置','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1058,529,'en-US','Settings','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1059,530,'zh-CN','管理端存储空间不支持上传文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1060,530,'en-US','Storage buckets do not support uploads from the admin console','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1061,531,'zh-CN','业务资料','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1062,531,'en-US','Business materials','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1063,532,'zh-CN','合同协议','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1064,532,'en-US','Contracts','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1065,533,'zh-CN','图片素材','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1066,533,'en-US','Images','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1067,534,'zh-CN','其他','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1068,534,'en-US','Other','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1069,535,'zh-CN','制度文档','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1070,535,'en-US','Policies','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1071,536,'zh-CN','复制失败，请手动复制链接','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1072,536,'en-US','Copy failed, please copy the link manually','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1073,537,'zh-CN','链接已复制','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1074,537,'en-US','Link copied','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1075,538,'zh-CN','确认删除文件「{name}」吗？删除后文件和记录都会被清理。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1076,538,'en-US','Delete file \"{name}\"? The file and its records will be removed.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1077,539,'zh-CN','确认删除文件「{name}」吗？删除后文件和记录都会被清理，可能影响头像、Logo、品牌图等正在引用这个文件的业务展示。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1078,539,'en-US','Delete file \"{name}\"? The file and its records will be removed, and any avatar, logo, or brand image currently using it may be affected.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1079,540,'zh-CN','确认删除','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1080,540,'en-US','Confirm delete','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1081,541,'zh-CN','删除文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1082,541,'en-US','Delete file','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1083,542,'zh-CN','文件已删除','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1084,542,'en-US','File deleted','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1085,543,'zh-CN','文件详情加载失败，已使用列表数据展示','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1086,543,'en-US','File details failed to load, using the list data instead','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1087,544,'zh-CN','下载失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1088,544,'en-US','Download failed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1089,545,'zh-CN','取消','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1090,545,'en-US','Cancel','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1091,546,'zh-CN','分类','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1092,546,'en-US','Category','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1093,547,'zh-CN','如：制度文档、业务资料、合同协议','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1094,547,'en-US','For example: policies, business materials, contracts','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1095,548,'zh-CN','仅允许 PDF、Word、Excel、PPT，一次最多上传 5 个。','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1096,548,'en-US','Only PDF, Word, Excel, and PPT files are allowed. At most 5 files can be uploaded at once.','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1097,549,'zh-CN','点击或拖拽文件到这里上传','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1098,549,'en-US','Click or drag files here to upload','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1099,550,'zh-CN','备注','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1100,550,'en-US','Remarks','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1101,551,'zh-CN','可选，写一些文件说明','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1102,551,'en-US','Optional. Add a short description of this file','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1103,552,'zh-CN','选择文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1104,552,'en-US','Select files','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1105,553,'zh-CN','开始上传','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1106,553,'en-US','Start upload','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1107,554,'zh-CN','多个标签请用英文逗号分隔','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1108,554,'en-US','Use English commas to separate multiple tags','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1109,555,'zh-CN','标签','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1110,555,'en-US','Tags','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1111,556,'zh-CN','如：运营,合同,归档','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1112,556,'en-US','For example: operations, contract, archive','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1113,557,'zh-CN','上传文档','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1114,557,'en-US','Upload document','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1115,558,'zh-CN','操作','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1116,558,'en-US','Actions','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1117,559,'zh-CN','分类','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1118,559,'en-US','Category','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1119,560,'zh-CN','文件名','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1120,560,'en-US','File name','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1121,561,'zh-CN','大小','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1122,561,'en-US','Size','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1123,562,'zh-CN','标签','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1124,562,'en-US','Tags','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1125,563,'zh-CN','类型','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1126,563,'en-US','Type','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1127,564,'zh-CN','上传时间','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1128,564,'en-US','Upload time','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1129,565,'zh-CN','上传人','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1130,565,'en-US','Uploader','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1131,566,'zh-CN','一次最多上传 {count} 个文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1132,566,'en-US','At most {count} files can be uploaded at once','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1133,567,'zh-CN','仅允许上传 PDF、Word、Excel、PPT 文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1134,567,'en-US','Only PDF, Word, Excel, and PPT files are allowed','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1135,568,'zh-CN','PDF 预览加载失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1136,568,'en-US','PDF preview failed to load','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1137,569,'zh-CN','关闭','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1138,569,'en-US','Close','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1139,570,'zh-CN','下载链接','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1140,570,'en-US','Download link','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1141,571,'zh-CN','仅下载','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1142,571,'en-US','Download only','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1143,572,'zh-CN','图片预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1144,572,'en-US','Image preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1145,573,'zh-CN','正在加载文件详情','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1146,573,'en-US','Loading file details','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1147,574,'zh-CN','正在加载 PDF 预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1148,574,'en-US','Loading PDF preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1149,575,'zh-CN','正在加载文本内容','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1150,575,'en-US','Loading text content','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1151,576,'zh-CN','暂无文本内容','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1152,576,'en-US','No text content yet','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1153,577,'zh-CN','暂无文件详情','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1154,577,'en-US','No file details yet','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1155,578,'zh-CN','在线预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1156,578,'en-US','Online preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1157,579,'zh-CN','PDF 预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1158,579,'en-US','PDF preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1159,580,'zh-CN','文本预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1160,580,'en-US','Text preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1161,581,'zh-CN','文件预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1162,581,'en-US','File preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1163,582,'zh-CN','你可以直接下载文件查看完整内容','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1164,582,'en-US','You can download the file to view the full content','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1165,583,'zh-CN','当前格式暂不支持在线预览','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1166,583,'en-US','This file type is not supported for online preview','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1167,584,'zh-CN','输入分类名称','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1168,584,'en-US','Enter category name','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1169,585,'zh-CN','分类','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1170,585,'en-US','Category','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1171,586,'zh-CN','文件名、标签、备注','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1172,586,'en-US','File name, tags, remarks','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1173,587,'zh-CN','关键字','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1174,587,'en-US','Keyword','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1175,588,'zh-CN','请先选择要上传的文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1176,588,'en-US','Please select a file first','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1177,589,'zh-CN','阿里云 OSS','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1178,589,'en-US','Alibaba Cloud OSS','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1179,590,'zh-CN','本地存储','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1180,590,'en-US','Local storage','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1181,591,'zh-CN','腾讯云 COS','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1182,591,'en-US','Tencent Cloud COS','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1183,592,'zh-CN','追加随机 ID','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1184,592,'en-US','Append random ID','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1185,593,'zh-CN','保持原名（同名文件将被覆盖）','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1186,593,'en-US','Keep original name (same-name files will be overwritten)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1187,594,'zh-CN','随机字符串','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1188,594,'en-US','Random string','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1189,595,'zh-CN','文本预览加载失败','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1190,595,'en-US','Text preview failed to load','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1191,596,'zh-CN','文件管理器','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1192,596,'en-US','Global File Management','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1193,597,'zh-CN','下载中心','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1194,597,'en-US','Download Center','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1195,598,'zh-CN','我的文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1196,598,'en-US','My Files','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1197,599,'zh-CN','文件上传失败，请稍后重试','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1198,599,'en-US','File upload failed, please try again later','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1199,600,'zh-CN','已上传 {count} 个文件','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1200,600,'en-US','Uploaded {count} file(s)','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1201,601,'zh-CN','紧凑主题','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1202,601,'en-US','Compact theme','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1203,602,'zh-CN','暗黑主题','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1204,602,'en-US','Dark theme','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1205,603,'zh-CN','浅色主题','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1206,603,'en-US','Light theme','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1207,604,'zh-CN','主题切换，当前{theme}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1208,604,'en-US','Theme switch, current {theme}','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1209,605,'zh-CN','跟随系统','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(1210,605,'en-US','Follow system','TRANSLATED',0,'PENDING',NULL,NULL,0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0);
/*!40000 ALTER TABLE `sys_localization_translation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_localization_usage_ref`
--

DROP TABLE IF EXISTS `sys_localization_usage_ref`;
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
) ENGINE=InnoDB AUTO_INCREMENT=606 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_localization_usage_ref`
--

LOCK TABLES `sys_localization_usage_ref` WRITE;
/*!40000 ALTER TABLE `sys_localization_usage_ref` DISABLE KEYS */;
INSERT INTO `sys_localization_usage_ref` VALUES (1,1,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端暂未准备好',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(2,2,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端暂未启动，{seconds} 秒后自动重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(3,3,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端暂未就绪，正在进行第 {attempt} 次重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(4,4,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端启动中',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(5,5,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在检查后端服务',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(6,6,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在检查服务是否可用',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(7,7,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在连接后端',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(8,8,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在进入工作台',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(9,9,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端健康检查返回了前端页面，请检查 API 代理配置',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(10,10,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端健康检查失败：HTTP {status}',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(11,11,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端健康检查未返回 JSON，请检查 API 代理配置',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(12,12,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后端健康状态异常：{status}',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(13,13,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载品牌信息',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(14,14,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载安全配置',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(15,15,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在准备登录后的菜单、插件和外观设置',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(16,16,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'系统已就绪',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(17,17,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在展示登录页',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(18,18,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在启动系统',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(19,19,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在同步登录页品牌与外观设置',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(20,20,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在同步登录策略',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(21,21,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在同步登录后可见的品牌与外观设置',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(22,22,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'同步系统资源',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(23,23,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'返回主路由',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(24,24,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请在个性化设置上传二维码',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(25,25,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前语言：{locale}',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(26,26,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'English',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(27,27,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'语言偏好已保存',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(28,28,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'语言切换',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(29,29,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'中文',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(30,30,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'退出登录',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(31,31,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'操作',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(32,32,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'返回',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(33,33,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请求内容有误，请检查后重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(34,34,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前操作无法完成，请检查业务状态',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(35,35,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'取消',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(36,36,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'向右拖动滑块完成验证',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(37,37,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证失败，请重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(38,38,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'拖动验证码已失效',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(39,39,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载失败，点击重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(40,40,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码加载中...',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(41,41,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'拖动验证码资源不完整',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(42,42,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证通过',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(43,43,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在校验...',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(44,44,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'收起',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(45,45,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(46,46,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'复制链接',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(47,47,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'删除',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(48,48,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已删除',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(49,49,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'下载',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(50,50,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'编辑',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(51,51,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'展开',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(52,52,'UI','lumira-ui/src/routes/meta.ts',NULL,'操作失败，请稍后重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(53,53,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入有效身份证号码',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(54,54,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入有效手机号',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(55,55,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'处理中...',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(56,56,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'必须大于 0',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(57,57,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'网络异常，请检查连接后重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(58,58,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前账号没有访问权限',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(59,59,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(60,60,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请先登录后再继续操作',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(61,61,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请选择',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(62,62,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件入口未成功注册',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(63,63,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件加载失败，请稍后重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(64,64,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件 manifest 的 entry 必须包含在 assets 中',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(65,65,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件 manifest 缺少必要字段',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(66,66,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件 manifest 必须声明 react 共享依赖',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(67,67,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前未启用该插件',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(68,68,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件渲染失败',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(69,69,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载插件资源失败，请稍后重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(70,70,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件脚本执行失败',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(71,71,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件脚本加载失败',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(72,72,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件样式加载失败',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(73,73,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件不可用',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(74,74,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'查询',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(75,75,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'刷新',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(76,76,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请求超时，请稍后重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(77,77,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'重置',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(78,78,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请求的资源不存在',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(79,79,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(80,80,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'保存',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(81,81,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'服务暂时不可用，请稍后再试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(82,82,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'登录状态已失效，请重新登录',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(83,83,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'操作成功',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(84,84,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'系统异常，请稍后重试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(85,85,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'操作过于频繁，请稍后再试',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(86,86,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(87,87,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传文档',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(88,88,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'GitHub 链接',0,'2026-06-24 00:50:34',0,'2026-06-24 00:50:34',0),(89,89,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'GitHub 链接（未配置）',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(90,90,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'回到顶部',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(91,91,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'二维码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(92,92,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'刷新页面',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(93,93,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'帮助中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(94,94,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'帮助中心（未配置链接）',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(95,95,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'全部',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(96,96,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'消息中心，当前有 {count} 条未读消息',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(97,97,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'消息通道已连接',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(98,98,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'共 {total} 条消息 · {unread} 条未读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(99,99,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'详情',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(100,100,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'消息加载失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(101,101,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'部分消息加载失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(102,102,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载消息中',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(103,103,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'全部标为已读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(104,104,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'标为已读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(105,105,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'消息类型',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(106,106,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'你有一条新的站内信，请前往消息中心查看。',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(107,107,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'收到新消息',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(108,108,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暂无消息',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(109,109,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暂无符合条件的消息',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(110,110,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'预览',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(111,111,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'发布',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(112,112,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已发布',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(113,113,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(114,114,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(115,115,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(116,116,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'刷新',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(117,117,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'撤回',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(118,118,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已撤回',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(119,119,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'站内信',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(120,120,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(121,121,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(122,122,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'时间：{time}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(123,123,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'刚刚',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(124,124,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'即将',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(125,125,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'消息中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(126,126,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'站内信',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(127,127,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未读',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(128,128,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'活动管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(129,129,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'活动',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(130,130,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'活动查询',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(131,131,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'活动',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(132,132,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'AI 助手',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(133,133,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'知识库',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(134,134,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'AI',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(135,135,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'工作台',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(136,136,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'工作台',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(137,137,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'专家管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(138,138,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'专家库',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(139,139,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'文件管理器',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(140,140,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'下载中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(141,141,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'我的文件',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(142,142,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'文件中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(143,143,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'本地化中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(144,144,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'系统设置',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(145,145,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'数字员工',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(146,146,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'字典管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(147,147,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'菜单管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(148,148,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'接口文档',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(149,149,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'审计中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(150,150,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'Redis监控',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(151,151,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'系统监控',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(152,152,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'服务监控',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(153,153,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'通知中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(154,154,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'支付设置',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(155,155,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'个性化设置',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(156,156,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'插件管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(157,157,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'字段管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(158,158,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'系统总览',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(159,159,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'安全设置',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(160,160,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'验证管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(161,161,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'创建团队',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(162,162,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'团队详情',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(163,163,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'团队邀请',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(164,164,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'加入团队',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(165,165,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'团队管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(166,166,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'团队成员',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(167,167,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'团队',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(168,168,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'团队',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(169,169,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'用户中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(170,170,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'修改密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(171,171,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'组织部门',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(172,172,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'用户菜单',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(173,173,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'在线用户',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(174,174,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'确认新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(175,175,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'两次输入的新密码不一致',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(176,176,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'当前密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(177,177,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'请再次输入新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(178,178,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'请输入当前密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(179,179,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'请输入新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(180,180,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'密码长度至少为 {length} 位',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(181,181,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(182,182,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'需包含小写字母',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(183,183,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'至少 {length} 位',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(184,184,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'需包含特殊字符',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(185,185,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'密码规则：',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(186,186,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'需包含大写字母',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(187,187,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'需包含小写字母',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(188,188,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'需包含特殊字符',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(189,189,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'需包含大写字母',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(190,190,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'密码已修改',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(191,191,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'个人中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(192,192,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'个人资料',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(193,193,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'当前账号权限',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(194,194,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'基于当前账号的默认权限视图',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(195,195,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'当前',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(196,196,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'编码：{roleCode} · {permissionCount} 个权限',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(197,197,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'已恢复默认权限',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(198,198,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'角色模拟',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(199,199,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'当前正在模拟 {roleName}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(200,200,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'角色已切换',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(201,201,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'已切换至 {roleName}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(202,202,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'角色管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(203,203,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'切换角色',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(204,204,'ROUTE','lumira-ui/src/routes/meta.ts',NULL,'用户管理',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(205,205,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'返回上一页',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(206,206,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'回到首页',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(207,207,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前账号没有访问该页面的权限',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(208,208,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'返回首页',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(209,209,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'页面不存在，请返回首页继续操作。',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(210,210,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'返回首页',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(211,211,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'服务器发生异常，请稍后再试。',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(212,212,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前生效',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(213,213,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'新增词条',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(214,214,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'新增语言',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(215,215,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'全部命名空间',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(216,216,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'复制键名',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(217,217,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'覆盖率：{rate}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(218,218,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前语言：{locale}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(219,219,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前模块：{namespace}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(220,220,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前状态',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(221,221,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'默认',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(222,222,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'默认语言',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(223,223,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'原文',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(224,224,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'编辑词条',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(225,225,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'编辑语言',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(226,226,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'词条已保存',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(227,227,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'回退语言',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(228,228,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'版本历史',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(229,229,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'键名',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(230,230,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'语言名称',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(231,231,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：简体中文 / English',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(232,232,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'语言已保存',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(233,233,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'语言切换',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(234,234,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'语言',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(235,235,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'语言代码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(236,236,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'命名空间',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(237,237,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'命名空间',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(238,238,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'本地名称',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(239,239,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暂无语言',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(240,240,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暂无发布记录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(241,241,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'发布',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(242,242,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'本地化中心发布',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(243,243,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'翻译版本已发布',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(244,244,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'回滚',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(245,245,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认回滚该版本吗？',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(246,246,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'翻译版本已回滚',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(247,247,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'搜索筛选',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(248,248,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'搜索键名、原文或来源',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(249,249,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'排序',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(250,250,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'源语言',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(251,251,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'来源',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(252,252,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'来源类型',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(253,253,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'状态',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(254,254,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'同步源码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(255,255,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已同步源码词条',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(256,256,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'本地化中心',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(257,257,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'译文',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(258,258,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'待翻译',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(259,259,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'引用数',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(260,260,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'我已阅读并同意',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(261,261,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'和',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(262,262,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'后台暂未配置该条款内容。',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(263,263,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'协议预览',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(264,264,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'《隐私协议》',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(265,265,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请先同意条款后再登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(266,266,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'《用户协议》',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(267,267,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(268,268,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'点击刷新验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(269,269,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'点击刷新',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(270,270,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'点击重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(271,271,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'拖动验证',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(272,272,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已验证',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(273,273,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(274,274,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码已过期，请刷新后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(275,275,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请等待 {seconds}s 后再发送',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(276,276,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'{seconds}s 后重发',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(277,277,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'调试验证码：{code}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(278,278,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'重新发送',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(279,279,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入收到的验证码完成二次验证',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(280,280,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'发送验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(281,281,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码将发送到 {contact}',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(282,282,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码发送失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(283,283,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(284,284,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(285,285,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱验证码登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(286,286,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'账号长度不能超过 128 个字符',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(287,287,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码已过期，请刷新后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(288,288,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码发送失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(289,289,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前未启用邮箱验证码登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(290,290,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'账号包含不允许的字符',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(291,291,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码只能包含字母和数字',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(292,292,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入有效邮箱地址',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(293,293,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入有效手机号',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(294,294,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'登录加密信息加载失败，请刷新后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(295,295,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'登录失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(296,296,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前登录方式不可用',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(297,297,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'密码长度不能少于 6 位',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(298,298,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请先完成拖动验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(299,299,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入账号、手机号或邮箱',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(300,300,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(301,301,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入邮箱',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(302,302,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入手机号',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(303,303,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(304,304,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请先发送验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(305,305,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码刷新失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(306,306,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前未启用短信验证码登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(307,307,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(308,308,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请再次输入新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(309,309,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'密码修改失败，请检查后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(310,310,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(311,311,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入新密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(312,312,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'新密码不能继续使用初始密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(313,313,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前账号仍在使用初始密码，必须修改后才能进入系统。',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(314,314,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'两次输入的新密码不一致',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(315,315,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前账号仍在使用初始密码，请先修改密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(316,316,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认修改',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(317,317,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'密码已修改，请使用新密码登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(318,318,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'修改初始密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(319,319,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在加载登录加密信息...',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(320,320,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前登录方式不可用',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(321,321,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'使用通行密钥登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(322,322,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已取消通行密钥验证',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(323,323,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前浏览器不支持通行密钥',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(324,324,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'通行密钥',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(325,325,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'密码登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(326,326,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'密码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(327,327,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'密码登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(328,328,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'记住我',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(329,329,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'{name} 需要完成二次验证',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(330,330,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'手机号',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(331,331,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'短信验证码',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(332,332,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'短信验证码登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(333,333,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(334,334,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证并登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(335,335,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码已发送',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(336,336,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'登录成功，正在进入系统',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(337,337,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入验证码完成二次验证',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(338,338,'UI','lumira-ui/src/routes/meta.ts',NULL,'登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(339,339,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'微信登录',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(340,340,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在跳转到微信登录...',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(341,341,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'激活插件版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(342,342,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'API 版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(343,343,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'作者',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(344,344,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'取消',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(345,345,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'取消',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(346,346,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'选择 zip 插件包',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(347,347,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'选择 zip 插件包',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(348,348,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(349,349,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'激活插件版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(350,350,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'停用插件',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(351,351,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'启用插件',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(352,352,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'安装插件版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(353,353,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'回滚插件版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(354,354,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认后将卸载插件 {name}。',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(355,355,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前启用版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(356,356,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(357,357,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'描述',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(358,358,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件详情',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(359,359,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'停用插件',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(360,360,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'启用插件',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(361,361,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'是否启用',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(362,362,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未启用',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(363,363,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已启用',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(364,364,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'激活插件失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(365,365,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'停用插件失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(366,366,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'启用插件失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(367,367,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'安装插件失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(368,368,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请先安装可用版本',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(369,369,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件已更新，但列表刷新失败，请手动刷新页面',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(370,370,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载插件信息失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(371,371,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载插件日志失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(372,372,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件包不能超过 50MB',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(373,373,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件已更新，但菜单刷新失败，请手动刷新页面',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(374,374,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'操作失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(375,375,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'回滚插件失败，请稍后重试',0,'2026-06-24 00:50:35',0,'2026-06-24 00:50:35',0),(376,376,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请先选择插件包',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(377,377,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'卸载插件失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(378,378,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传插件失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(379,379,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'仅支持 zip 插件包',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(380,380,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'安装插件版本',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(381,381,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件日志',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(382,382,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'菜单数',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(383,383,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件名称',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(384,384,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'仅卸载插件，不删除数据库数据',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(385,385,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件编码',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(386,386,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'刷新',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(387,387,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'回滚插件版本',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(388,388,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'路由数',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(389,389,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'输入插件编码或名称',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(390,390,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'状态',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(391,391,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件激活版本已切换',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(392,392,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件已停用',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(393,393,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件已启用',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(394,394,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件安装完成',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(395,395,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件已回滚',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(396,396,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件已卸载',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(397,397,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件已卸载，并已删除数据库数据',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(398,398,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件上传并完成校验',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(399,399,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'插件管理',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(400,400,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'卸载插件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(401,401,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'卸载并删除数据库数据',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(402,402,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'你可以选择是否同时删除插件相关数据库数据。选择删除后，会清理插件运行日志、启用关联、版本记录和插件定义等数据。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(403,403,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'卸载 {name}',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(404,404,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传插件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(405,405,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(406,406,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传插件包',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(407,407,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'版本数量',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(408,408,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'版本管理',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(409,409,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请选择图片文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(410,410,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'头像上传失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(411,411,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'头像已上传，请点击保存资料',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(412,412,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码校验失败，请重试。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(413,413,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码已发送，请输入验证码后继续',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(414,414,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'绑定已完成',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(415,415,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱已绑定',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(416,416,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前未启用邮箱验证码，暂不允许绑定邮箱',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(417,417,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入验证码。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(418,418,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'绑定信息已失效，请重新发起绑定。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(419,419,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'绑定失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(420,420,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'获取绑定信息失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(421,421,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'手机号已绑定',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(422,422,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前未启用短信验证码，暂不允许绑定手机号',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(423,423,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码发送失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(424,424,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'解绑失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(425,425,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已解绑',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(426,426,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'个人资料已更新',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(427,427,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'绑定邮箱',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(428,428,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'绑定手机号',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(429,429,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已绑定',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(430,430,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认绑定',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(431,431,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前未启用邮箱验证码，暂不允许绑定邮箱。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(432,432,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前已开启邮箱验证码验证，绑定邮箱时需要先获取并输入验证码。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(433,433,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前未启用短信验证码，暂不允许绑定手机号。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(434,434,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前已开启短信验证码验证，绑定手机号时需要先获取并输入验证码。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(435,435,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'修改邮箱',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(436,436,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'修改手机号',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(437,437,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(438,438,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(439,439,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'手机号',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(440,440,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'手机号',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(441,441,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未设置邮箱',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(442,442,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未设置手机号',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(443,443,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入邮箱地址',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(444,444,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入手机号',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(445,445,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'保存',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(446,446,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'发送验证码',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(447,447,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未绑定',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(448,448,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'需验证码',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(449,449,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'加载中',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(450,450,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱登录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(451,451,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'邮箱登录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(452,452,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'手机号登录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(453,453,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'短信登录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(454,454,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'登录记录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(455,455,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'通行密钥已绑定',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(456,456,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已取消通行密钥绑定',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(457,457,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'通行密钥绑定失败',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(458,458,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入通行密钥名称',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(459,459,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'通行密钥已重命名',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(460,460,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'通行密钥绑定超时，请重新尝试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(461,461,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前浏览器不支持通行密钥',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(462,462,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'最近登录记录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(463,463,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暂无最近登录记录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(464,464,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'登录记录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(465,465,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'未知用户',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(466,466,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'个人中心',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(467,467,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'Access Token 过期时间（秒）',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(468,468,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'Access Token 的有效秒数。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(469,469,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'图片验证码',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(470,470,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'拖动验证码',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(471,471,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'开启后，登录页会要求完成人机验证码。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(472,472,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码设置',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(473,473,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码类型',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(474,474,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'图片验证码需要输入字符；拖动验证码需要拖动拼图完成校验。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(475,475,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'用于限制账号与 IP 维度的高频登录尝试，减少爆破和脚本攻击。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(476,476,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'防御阈值',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(477,477,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'启用人机验证码',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(478,478,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'关闭后登录页不会展示验证码。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(479,479,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：10',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(480,480,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：100',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(481,481,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：1800',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(482,482,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：300',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(483,483,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：5',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(484,484,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：6',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(485,485,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：60',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(486,486,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'例如：604800',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(487,487,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'空闲超时（秒）',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(488,488,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'用户在无操作状态下允许保持登录的秒数。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(489,489,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'最大验证次数',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(490,490,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'统计窗口内允许的最大登录验证请求次数，超过后将拦截。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(491,491,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'最大错误次数',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(492,492,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'统计窗口内允许的最大登录失败次数，超过后将拦截。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(493,493,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'多设备登录',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(494,494,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'关闭后，同一账号在新的设备登录时，旧设备的会话将自动失效。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(495,495,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'允许连续字符',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(496,496,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'关闭后，密码中不能包含类似 123 或 abc 的连续字符。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(497,497,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'必须包含小写字母',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(498,498,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'强制密码中必须包含 a-z。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(499,499,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'最短长度',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(500,500,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'用户密码允许的最少字符数。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(501,501,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'必须包含特殊字符',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(502,502,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'强制密码中必须包含特殊字符，例如 !@#$%^&*。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(503,503,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'必须包含大写字母',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(504,504,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'强制密码中必须包含 A-Z。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(505,505,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'密码规范设置',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(506,506,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'这些规则会直接作用于用户新增、重置密码和修改密码的服务端校验。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(507,507,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'Refresh Token 刷新时限（秒）',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(508,508,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'Refresh Token 的有效秒数。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(509,509,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'恢复默认值',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(510,510,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'保存设置',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(511,511,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'安全设置已保存，并已立即生效',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(512,512,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'拖动验证码预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(513,513,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'拖动验证码预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(514,514,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'拖动验证码预览验证通过',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(515,515,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'安全设置',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(516,516,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'这部分配置决定登录会话、Access Token 和 Refresh Token 的生命周期。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(517,517,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'Token 策略',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(518,518,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'发送倒计时（秒）',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(519,519,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码发送后，发送按钮会倒计时，期间不能再次发送。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(520,520,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入发送倒计时时间',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(521,521,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'验证码过期时间（秒）',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(522,522,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'短信和邮箱验证码在多少秒后过期。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(523,523,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请输入验证码过期时间',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(524,524,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'统计窗口（分钟）',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(525,525,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'用于统计高频访问的时间窗口大小。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(526,526,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'内置角色',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(527,527,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'自定义角色',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(528,528,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'系统角色',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(529,529,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'系统设置',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(530,530,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'管理端存储空间不支持上传文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(531,531,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'业务资料',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(532,532,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'合同协议',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(533,533,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'图片素材',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(534,534,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'其他',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(535,535,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'制度文档',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(536,536,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'复制失败，请手动复制链接',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(537,537,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'链接已复制',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(538,538,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认删除文件「{name}」吗？删除后文件和记录都会被清理。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(539,539,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认删除文件「{name}」吗？删除后文件和记录都会被清理，可能影响头像、Logo、品牌图等正在引用这个文件的业务展示。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(540,540,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'确认删除',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(541,541,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'删除文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(542,542,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文件已删除',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(543,543,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文件详情加载失败，已使用列表数据展示',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(544,544,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'下载失败',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(545,545,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'取消',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(546,546,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'分类',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(547,547,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'如：制度文档、业务资料、合同协议',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(548,548,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'仅允许 PDF、Word、Excel、PPT，一次最多上传 5 个。',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(549,549,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'点击或拖拽文件到这里上传',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(550,550,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'备注',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(551,551,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'可选，写一些文件说明',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(552,552,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'选择文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(553,553,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'开始上传',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(554,554,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'多个标签请用英文逗号分隔',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(555,555,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'标签',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(556,556,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'如：运营,合同,归档',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(557,557,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传文档',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(558,558,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'操作',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(559,559,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'分类',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(560,560,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文件名',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(561,561,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'大小',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(562,562,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'标签',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(563,563,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'类型',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(564,564,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传时间',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(565,565,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'上传人',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(566,566,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'一次最多上传 {count} 个文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(567,567,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'仅允许上传 PDF、Word、Excel、PPT 文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(568,568,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'PDF 预览加载失败',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(569,569,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'关闭',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(570,570,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'下载链接',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(571,571,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'仅下载',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(572,572,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'图片预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(573,573,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在加载文件详情',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(574,574,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在加载 PDF 预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(575,575,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'正在加载文本内容',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(576,576,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暂无文本内容',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(577,577,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暂无文件详情',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(578,578,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'在线预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(579,579,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'PDF 预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(580,580,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文本预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(581,581,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文件预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(582,582,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'你可以直接下载文件查看完整内容',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(583,583,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'当前格式暂不支持在线预览',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(584,584,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'输入分类名称',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(585,585,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'分类',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(586,586,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文件名、标签、备注',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(587,587,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'关键字',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(588,588,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'请先选择要上传的文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(589,589,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'阿里云 OSS',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(590,590,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'本地存储',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(591,591,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'腾讯云 COS',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(592,592,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'追加随机 ID',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(593,593,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'保持原名（同名文件将被覆盖）',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(594,594,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'随机字符串',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(595,595,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文本预览加载失败',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(596,596,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文件管理器',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(597,597,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'下载中心',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(598,598,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'我的文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(599,599,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'文件上传失败，请稍后重试',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(600,600,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'已上传 {count} 个文件',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(601,601,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'紧凑主题',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(602,602,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'暗黑主题',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(603,603,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'浅色主题',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(604,604,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'主题切换，当前{theme}',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0),(605,605,'UI','lumira-ui/src/locales/zh-CN.ts',NULL,'跟随系统',0,'2026-06-24 00:50:36',0,'2026-06-24 00:50:36',0);
/*!40000 ALTER TABLE `sys_localization_usage_ref` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (-1090,1001,-1082,'certificate.records.revoke','Revoke certificate','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,3,'aiadc:certificate:revoke','ENABLED'),(-1089,1001,-1082,'certificate.records.regenerate','Regenerate certificate','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,2,'aiadc:certificate:regenerate','ENABLED'),(-1088,1001,-1082,'certificate.records.download','Download certificate','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,1,'aiadc:certificate:download','ENABLED'),(-1087,1001,-1081,'certificate.generate.create','Generate certificates','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,1,'aiadc:certificate-batch:create','ENABLED'),(-1086,1001,-1080,'certificate.templates.delete','Archive certificate template','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,4,'aiadc:certificate-template:delete','ENABLED'),(-1085,1001,-1080,'certificate.templates.publish','Publish certificate template','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,3,'aiadc:certificate-template:publish','ENABLED'),(-1084,1001,-1080,'certificate.templates.update','Update certificate template','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,2,'aiadc:certificate-template:update','ENABLED'),(-1083,1001,-1080,'certificate.templates.create','Create certificate template','BUTTON',NULL,NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0,NULL,1,'aiadc:certificate-template:create','ENABLED'),(-1082,1001,-1079,'certificate.records','????','MENU','/certificates/records','@/pages/certificates/RecordsPage',0,'2026-06-24 13:59:17',0,'2026-06-24 14:09:37',0,'AuditOutlined',3,'aiadc:certificate:view','ENABLED'),(-1081,1001,-1079,'certificate.generate','????','MENU','/certificates/generate','@/pages/certificates/GeneratePage',0,'2026-06-24 13:59:17',0,'2026-06-24 14:09:37',0,'FileDoneOutlined',2,'aiadc:certificate-batch:create','ENABLED'),(-1080,1001,-1079,'certificate.templates','????','MENU','/certificates/templates','@/pages/certificates/TemplatesPage',0,'2026-06-24 13:59:17',0,'2026-06-24 14:09:37',0,'FileProtectOutlined',1,'aiadc:certificate-template:view','ENABLED'),(-1079,1001,0,'certificate.root','????','CATALOG','/certificates','redirect:/certificates/templates',0,'2026-06-24 14:09:37',0,'2026-06-24 14:09:37',0,'FileProtectOutlined',5,NULL,'ENABLED'),(-1074,1001,-1071,'competition.management.delete','删除赛事','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'aiadc:competition:delete','ENABLED'),(-1073,1001,-1071,'competition.management.update','编辑赛事','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'aiadc:competition:update','ENABLED'),(-1072,1001,-1071,'competition.management.create','新增赛事','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'aiadc:competition:create','ENABLED'),(-1071,1001,-1070,'competition.management','赛事管理','MENU','/competitions/management','@/pages/competition',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'TrophyOutlined',1,'aiadc:competition:view','ENABLED'),(-1070,1001,0,'competition.root','赛事','CATALOG','/competitions','redirect:/competitions/management',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'TrophyOutlined',4,NULL,'ENABLED'),(-1064,1001,-1061,'expert.management.delete','Delete Expert','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'expert:delete','ENABLED'),(-1063,1001,-1061,'expert.management.update','Edit Expert','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'expert:update','ENABLED'),(-1062,1001,-1061,'expert.management.create','Create Expert','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'expert:create','ENABLED'),(-1061,1001,-1060,'expert.management','Expert Management','MENU','/experts/management','@/pages/expert',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SolutionOutlined',1,'expert:view','ENABLED'),(-1060,1001,0,'expert.root','Expert Library','CATALOG','/experts','redirect:/experts/management',0,'2026-06-24 00:54:52',0,'2026-06-24 14:09:37',0,'SolutionOutlined',6,NULL,'ENABLED'),(-1053,1001,-1041,'activity.search','活动查询','MENU','/activities/search','@/pages/activity',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SearchOutlined',2,'aiadc:activity:view','ENABLED'),(-1052,1001,-1041,'activity.activities','活动管理','MENU','/activities/management','@/pages/activity',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'CalendarOutlined',1,'aiadc:activity:view','ENABLED'),(-1051,1001,-989,'ai.assistant.send','发送对话','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'ai:chat:send','ENABLED'),(-1050,1001,-957,'team.search','团队','MENU','/team/search','@/pages/team',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SearchOutlined',2,'team:view','ENABLED'),(-1045,1001,-1052,'activity.activities.delete','删除活动','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'aiadc:activity:delete','ENABLED'),(-1044,1001,-1052,'activity.activities.update','编辑活动','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'aiadc:activity:update','ENABLED'),(-1043,1001,-1052,'activity.activities.create','新增活动','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'aiadc:activity:create','ENABLED'),(-1041,1001,0,'activity.root','活动','CATALOG','/activities','redirect:/activities/management',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'CalendarOutlined',3,NULL,'ENABLED'),(-1040,1001,-957,'team.management','团队管理','MENU','/team/management','@/pages/team',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'TeamOutlined',1,'team:view','ENABLED'),(-1025,1001,-1002,'settings.dicts.delete','删除字典','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'system:dict:delete','ENABLED'),(-1024,1001,-1002,'settings.dicts.update','编辑字典','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'system:dict:update','ENABLED'),(-1023,1001,-1002,'settings.dicts.create','创建字典','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'system:dict:create','ENABLED'),(-1022,1001,-1001,'settings.menus.delete','删除菜单','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'system:menu:delete','ENABLED'),(-1021,1001,-1001,'settings.menus.update','编辑菜单','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'system:menu:update','ENABLED'),(-1020,1001,-1001,'settings.menus.create','创建菜单','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'system:menu:create','ENABLED'),(-1015,1001,-1000,'settings.monitoring','系统监控','MENU','/settings/monitoring','@/pages/settings/monitoring/index',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'FundOutlined',10,'system:monitor:view','ENABLED'),(-1014,1001,-1000,'settings.monitoring.audit','审计中心','MENU','/settings/audit','@/pages/settings/monitoring/Audit',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'AuditOutlined',12,'audit:view','ENABLED'),(-1013,1001,-1000,'settings.monitoring.api-docs','接口文档','MENU','/settings/api-docs','@/pages/settings/monitoring/ApiDocs',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'FileTextOutlined',11,'system:monitor:docs:view','ENABLED'),(-1012,1001,-1000,'settings.files','全站文件管理','MENU','/settings/files/all','@/pages/settings/files/Center',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'FolderOpenOutlined',9,'system:file:manage','ENABLED'),(-1011,1001,-1000,'localization.root','本地化中心','MENU','/settings/localization','@/pages/settings/localization',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'TranslationOutlined',29,'localization:view','ENABLED'),(-1010,1001,-1000,'settings.ai-employees','数字员工','MENU','/settings/ai-employees','@/pages/settings/ai-employees',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'RobotOutlined',24,'ai:view','ENABLED'),(-1009,1001,-1000,'settings.plugins','插件管理中心','MENU','/settings/plugins','@/pages/settings/plugins',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'ApiOutlined',10,'plugin:management:view','ENABLED'),(-1008,1001,-1000,'settings.notifications','通知中心','MENU','/settings/notifications','@/pages/settings/notifications/index',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'NotificationOutlined',9,'system:notification:view','ENABLED'),(-1007,1001,-1000,'settings.payment','支付设置','MENU','/settings/payment','@/pages/settings/payment',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'CreditCardOutlined',8,'payment:view','ENABLED'),(-1006,1001,-1000,'settings.verification','验证管理','MENU','/settings/verification','@/pages/settings/verification',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SafetyOutlined',7,'system:verification:view','ENABLED'),(-1005,1001,-1000,'settings.security','安全设置','MENU','/settings/security','@/pages/settings/security',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SafetyOutlined',6,'system:config:view','ENABLED'),(-1004,1001,-1000,'settings.personalization','个性化设置','MENU','/settings/personalization','@/pages/settings/personalization',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SkinOutlined',5,'system:config:view','ENABLED'),(-1003,1001,-1000,'settings.profile-fields','字段管理','MENU','/settings/profile-fields','@/pages/settings/profile-fields',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'FormOutlined',4,'system:config:view','ENABLED'),(-1002,1001,-1000,'settings.dicts','字典管理','MENU','/settings/dicts','@/pages/settings/dicts',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'DatabaseOutlined',3,'system:dict:view','ENABLED'),(-1001,1001,-1000,'settings.menus','菜单管理','MENU','/settings/menus','@/pages/settings/menus',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'AppstoreOutlined',2,'system:menu:view','ENABLED'),(-1000,1001,0,'settings.root','系统设置','CATALOG','/settings','@/layouts/SettingsLayout',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SettingOutlined',20,'system:view','ENABLED'),(-990,1001,0,'ai.root','AI','CATALOG','/ai','redirect:/ai/assistant',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'RobotOutlined',2,NULL,'ENABLED'),(-989,1001,-990,'ai.assistant','AI 助手','MENU','/ai/assistant','@/pages/ai/Assistant',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'RobotOutlined',1,'ai:assistant:view','ENABLED'),(-988,1001,-990,'ai.knowledge','知识库','MENU','/ai/knowledge','@/pages/ai/knowledge/KnowledgePage',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'FileSearchOutlined',2,'ai:knowledge:view','ENABLED'),(-972,1001,-953,'system.roles.grant','授权角色','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,4,'system:role:grant','ENABLED'),(-971,1001,-953,'system.roles.delete','删除角色','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'system:role:delete','ENABLED'),(-970,1001,-953,'system.roles.update','编辑角色','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'system:role:update','ENABLED'),(-969,1001,-953,'system.roles.create','创建角色','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'system:role:create','ENABLED'),(-968,1001,-951,'system.users.export','导出用户','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,4,'system:user:export','ENABLED'),(-967,1001,-951,'system.users.delete','删除用户','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'system:user:delete','ENABLED'),(-966,1001,-951,'system.users.update','编辑用户','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'system:user:update','ENABLED'),(-965,1001,-951,'system.users.create','创建用户','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'system:user:create','ENABLED'),(-964,1001,-1040,'team.member.role-update','更新成员角色','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,7,'team:member:role-update','ENABLED'),(-963,1001,-1040,'team.member.remove','移除成员','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,6,'team:member:remove','ENABLED'),(-962,1001,-1040,'team.member.invite','邀请成员','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,5,'team:member:invite','ENABLED'),(-961,1001,-1040,'team.member.view','查看成员','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,4,'team:member:view','ENABLED'),(-960,1001,-1040,'team.delete','删除团队','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,3,'team:delete','ENABLED'),(-959,1001,-1040,'team.update','编辑团队','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,2,'team:update','ENABLED'),(-958,1001,-1040,'team.create','创建团队','BUTTON',NULL,NULL,0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,NULL,1,'team:create','ENABLED'),(-957,1001,0,'team.root','团队','CATALOG','/team','redirect:/team/management',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'TeamOutlined',6,'team:view','ENABLED'),(-956,1001,0,'files.download-center','下载中心','MENU','/download-center','@/pages/files/DownloadCenter',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'DownloadOutlined',1,'download:center:view','ENABLED'),(-955,1001,0,'dashboard.home','首页','MENU','/dashboard/home','@/pages/dashboard/DashboardHomePage',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'DashboardOutlined',0,'dashboard:view','ENABLED'),(-954,1001,-950,'system.departments','组织部门','MENU','/user-center/departments','@/pages/system/departments',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'ApartmentOutlined',22,'system:department:view','ENABLED'),(-953,1001,-950,'system.roles','角色管理','MENU','/user-center/roles','@/pages/system/roles',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'SafetyOutlined',24,'system:role:view','ENABLED'),(-952,1001,-950,'system.online-users','在线用户','MENU','/user-center/online-users','@/pages/system/online-users',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'UserSwitchOutlined',23,'system:online-user:view','ENABLED'),(-951,1001,-950,'system.users','用户管理','MENU','/user-center/users','@/pages/system/users',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'TeamOutlined',21,'system:user:view','ENABLED'),(-950,1001,0,'user.center.root','用户中心','CATALOG','/user-center','@/layouts/SettingsLayout',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'TeamOutlined',18,'user:center:view','ENABLED'),(-942,1001,-940,'files.my','我的文件','MENU','/user-center/personal-center/files','@/pages/files/Center',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'FileOutlined',2,'system:file:view','ENABLED'),(-941,1001,-940,'profile.center','个人资料','MENU','/user-center/personal-center/profile','@/pages/profile/Center',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'UserOutlined',1,'profile:view','ENABLED'),(-940,1001,0,'user.center.personal','个人中心','CATALOG','/user-center/personal-center','@/layouts/SettingsLayout',0,'2026-06-24 00:54:52',0,'2026-06-24 00:54:52',0,'IdcardOutlined',19,'profile:view','ENABLED');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
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
) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_permission`
--

LOCK TABLES `sys_permission` WRITE;
/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` VALUES (1,1001,'ai:chat:send','ai:chat:send','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(2,1001,'ai:employee:create','ai:employee:create','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(3,1001,'ai:employee:delete','ai:employee:delete','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(4,1001,'ai:employee:skills','ai:employee:skills','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(5,1001,'ai:employee:status','ai:employee:status','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(6,1001,'ai:employee:update','ai:employee:update','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(7,1001,'ai:knowledge:bind','ai:knowledge:bind','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(8,1001,'ai:knowledge:create','ai:knowledge:create','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(9,1001,'ai:knowledge:delete','ai:knowledge:delete','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(10,1001,'ai:knowledge:document:delete','ai:knowledge:document:delete','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(11,1001,'ai:knowledge:document:index','ai:knowledge:document:index','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(12,1001,'ai:knowledge:document:upload','ai:knowledge:document:upload','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(13,1001,'ai:knowledge:query','ai:knowledge:query','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(14,1001,'ai:knowledge:share','ai:knowledge:share','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(15,1001,'ai:knowledge:update','ai:knowledge:update','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(16,1001,'ai:knowledge:view','ai:knowledge:view','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(17,1001,'ai:llm:create','ai:llm:create','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(18,1001,'ai:llm:delete','ai:llm:delete','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(19,1001,'ai:llm:status','ai:llm:status','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(20,1001,'ai:llm:update','ai:llm:update','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(21,1001,'ai:tool:execute','ai:tool:execute','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(22,1001,'ai:tool:invoke','ai:tool:invoke','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(23,1001,'ai:tool:view','ai:tool:view','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(24,1001,'ai:view','ai:view','ai','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(25,1001,'audit:login:view','audit:login:view','audit','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(26,1001,'audit:operation:view','audit:operation:view','audit','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(27,1001,'audit:view','audit:view','audit','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(28,1001,'dashboard:view','dashboard:view','dashboard','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(29,1001,'download:center:view','download:center:view','download','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(30,1001,'localization:view','localization:view','localization','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(31,1001,'payment:config:test','payment:config:test','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(32,1001,'payment:config:update','payment:config:update','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(33,1001,'payment:config:view','payment:config:view','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(34,1001,'payment:order:create','payment:order:create','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(35,1001,'payment:order:view','payment:order:view','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(36,1001,'payment:refund:create','payment:refund:create','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(37,1001,'payment:refund:view','payment:refund:view','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(38,1001,'payment:view','payment:view','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(39,1001,'payment:webhook:retry','payment:webhook:retry','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(40,1001,'payment:webhook:view','payment:webhook:view','payment','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(41,1001,'plugin:management:view','plugin:management:view','plugin','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(42,1001,'plugin:sensitive-words:import','导入敏感词','sensitive-words','PLUGIN','sensitive-words',0,'2026-06-24 00:26:01',0,'2026-06-24 00:51:43',0),(43,1001,'plugin:sensitive-words:manage','管理敏感词插件','sensitive-words','PLUGIN','sensitive-words',0,'2026-06-24 00:26:01',0,'2026-06-24 00:51:43',0),(44,1001,'plugin:sensitive-words:view','查看敏感词插件','sensitive-words','PLUGIN','sensitive-words',0,'2026-06-24 00:26:01',0,'2026-06-24 00:51:43',0),(45,1001,'profile:view','profile:view','profile','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(46,1001,'system:config:update','system:config:update','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(47,1001,'system:config:view','system:config:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(48,1001,'system:department:create','system:department:create','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(49,1001,'system:department:delete','system:department:delete','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(50,1001,'system:department:update','system:department:update','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(51,1001,'system:department:view','system:department:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(52,1001,'system:dict:create','system:dict:create','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(53,1001,'system:dict:delete','system:dict:delete','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(54,1001,'system:dict:update','system:dict:update','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(55,1001,'system:dict:view','system:dict:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(56,1001,'system:file:delete','system:file:delete','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(57,1001,'system:file:manage','system:file:manage','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(58,1001,'system:file:upload','system:file:upload','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(59,1001,'system:file:view','system:file:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(60,1001,'system:menu:create','system:menu:create','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(61,1001,'system:menu:delete','system:menu:delete','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(62,1001,'system:menu:status','system:menu:status','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(63,1001,'system:menu:update','system:menu:update','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(64,1001,'system:menu:view','system:menu:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(65,1001,'system:monitor:docs:view','system:monitor:docs:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(66,1001,'system:monitor:redis:view','system:monitor:redis:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(67,1001,'system:monitor:service:view','system:monitor:service:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(68,1001,'system:monitor:view','system:monitor:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(69,1001,'system:notification:view','system:notification:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(70,1001,'system:online-user:ban','system:online-user:ban','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(71,1001,'system:online-user:kick','system:online-user:kick','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(72,1001,'system:online-user:view','system:online-user:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(73,1001,'system:role:create','system:role:create','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(74,1001,'system:role:delete','system:role:delete','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(75,1001,'system:role:grant','system:role:grant','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(76,1001,'system:role:permissions','system:role:permissions','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(77,1001,'system:role:update','system:role:update','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(78,1001,'system:role:view','system:role:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(79,1001,'system:update:check','system:update:check','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(80,1001,'system:update:install','system:update:install','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(81,1001,'system:update:rollback','system:update:rollback','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(82,1001,'system:update:view','system:update:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(83,1001,'system:user:create','system:user:create','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(84,1001,'system:user:delete','system:user:delete','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(85,1001,'system:user:export','system:user:export','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(86,1001,'system:user:sensitive:view','system:user:sensitive:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(87,1001,'system:user:status','system:user:status','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(88,1001,'system:user:update','system:user:update','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(89,1001,'system:user:view','system:user:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(90,1001,'system:verification:manage','system:verification:manage','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(91,1001,'system:verification:view','system:verification:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(92,1001,'system:view','system:view','system','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(93,1001,'team:view','team:view','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(94,1001,'team:create','team:create','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(95,1001,'team:update','team:update','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(96,1001,'team:delete','team:delete','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(97,1001,'team:member:view','team:member:view','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(98,1001,'team:member:invite','team:member:invite','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(99,1001,'team:member:remove','team:member:remove','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(100,1001,'team:member:role-update','team:member:role-update','team','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(101,1001,'user:center:view','user:center:view','user','CORE',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(102,1001,'plugin:work-order-feedback:create','提交工单反馈','plugin','PLUGIN','work-order-feedback',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(103,1001,'plugin:work-order-feedback:manage','处理工单反馈','plugin','PLUGIN','work-order-feedback',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(104,1001,'plugin:work-order-feedback:view','查看工单反馈','plugin','PLUGIN','work-order-feedback',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(108,1001,'aiadc:certificate-template:view','aiadc:certificate-template:view','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(109,1001,'aiadc:certificate-template:create','aiadc:certificate-template:create','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(110,1001,'aiadc:certificate-template:update','aiadc:certificate-template:update','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(111,1001,'aiadc:certificate-template:publish','aiadc:certificate-template:publish','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(112,1001,'aiadc:certificate-template:delete','aiadc:certificate-template:delete','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(113,1001,'aiadc:certificate-batch:view','aiadc:certificate-batch:view','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(114,1001,'aiadc:certificate-batch:create','aiadc:certificate-batch:create','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(115,1001,'aiadc:certificate-batch:download','aiadc:certificate-batch:download','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(116,1001,'aiadc:certificate:view','aiadc:certificate:view','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(117,1001,'aiadc:certificate:download','aiadc:certificate:download','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(118,1001,'aiadc:certificate:regenerate','aiadc:certificate:regenerate','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0),(119,1001,'aiadc:certificate:revoke','aiadc:certificate:revoke','aiadc','CORE',NULL,0,'2026-06-24 13:59:17',0,'2026-06-24 13:59:17',0);
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_definition`
--

DROP TABLE IF EXISTS `sys_plugin_definition`;
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
  UNIQUE KEY `uk_sys_plugin_definition_code` (`plugin_code`),
  KEY `idx_sys_plugin_definition_deleted_status_sort_code` (`deleted`,`status`,`sort_no`,`plugin_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_plugin_definition`
--

LOCK TABLES `sys_plugin_definition` WRITE;
/*!40000 ALTER TABLE `sys_plugin_definition` DISABLE KEYS */;
INSERT INTO `sys_plugin_definition` VALUES (1,'sensitive-words','敏感词拦截插件','SECURITY','提供敏感词词库维护、请求内容拦截和导入能力。','Lumira','1.0',1,'ENABLED',10,'SHARED',1,0,'[\"routes\", \"menus\", \"permissions\", \"importers\", \"interceptors\"]',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(2,'work-order-feedback','工单反馈','BUSINESS','允许用户提交富文本问题反馈，管理员可跟进处理。','Lumira','1.0',1,'ENABLED',20,'SHARED',1,0,'[\"routes\", \"menus\", \"permissions\", \"rich-text-upload\"]',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_plugin_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_dependency`
--

DROP TABLE IF EXISTS `sys_plugin_dependency`;
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

--
-- Dumping data for table `sys_plugin_dependency`
--

LOCK TABLES `sys_plugin_dependency` WRITE;
/*!40000 ALTER TABLE `sys_plugin_dependency` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_plugin_dependency` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_menu_rel`
--

DROP TABLE IF EXISTS `sys_plugin_menu_rel`;
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
  UNIQUE KEY `uk_sys_plugin_menu_rel` (`plugin_code`,`plugin_version`,`menu_code`),
  KEY `idx_sys_plugin_menu_rel_code_version_deleted_sort` (`plugin_code`,`plugin_version`,`deleted`,`sort_no`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_plugin_menu_rel`
--

LOCK TABLES `sys_plugin_menu_rel` WRITE;
/*!40000 ALTER TABLE `sys_plugin_menu_rel` DISABLE KEYS */;
INSERT INTO `sys_plugin_menu_rel` VALUES (1,'sensitive-words','1.0.0','plugin.sensitive-words','敏感词管理','/plugins/sensitive-words','SafetyOutlined','plugin:sensitive-words:view','settings.plugins',10,0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(2,'work-order-feedback','1.0.0','plugin.work-order-feedback','工单反馈','/plugins/work-order-feedback','CustomerServiceOutlined','plugin:work-order-feedback:view','settings.plugins',20,0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_plugin_menu_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_permission_rel`
--

DROP TABLE IF EXISTS `sys_plugin_permission_rel`;
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
  UNIQUE KEY `uk_sys_plugin_permission_rel` (`plugin_code`,`plugin_version`,`permission_key`),
  KEY `idx_sys_plugin_permission_rel_code_version_deleted` (`plugin_code`,`plugin_version`,`deleted`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_plugin_permission_rel`
--

LOCK TABLES `sys_plugin_permission_rel` WRITE;
/*!40000 ALTER TABLE `sys_plugin_permission_rel` DISABLE KEYS */;
INSERT INTO `sys_plugin_permission_rel` VALUES (1,'sensitive-words','1.0.0','plugin:sensitive-words:view','查看敏感词插件','sensitive-words',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(2,'sensitive-words','1.0.0','plugin:sensitive-words:manage','管理敏感词插件','sensitive-words',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(3,'sensitive-words','1.0.0','plugin:sensitive-words:import','导入敏感词','sensitive-words',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(4,'work-order-feedback','1.0.0','plugin:work-order-feedback:view','查看工单反馈','work-order-feedback',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(5,'work-order-feedback','1.0.0','plugin:work-order-feedback:create','提交工单反馈','work-order-feedback',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(6,'work-order-feedback','1.0.0','plugin:work-order-feedback:manage','处理工单反馈','work-order-feedback',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_plugin_permission_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_runtime_log`
--

DROP TABLE IF EXISTS `sys_plugin_runtime_log`;
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
  KEY `idx_sys_plugin_runtime_log_plugin_created` (`plugin_code`,`created_at`),
  KEY `idx_sys_plugin_runtime_log_code_deleted_id` (`plugin_code`,`deleted`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_plugin_runtime_log`
--

LOCK TABLES `sys_plugin_runtime_log` WRITE;
/*!40000 ALTER TABLE `sys_plugin_runtime_log` DISABLE KEYS */;
INSERT INTO `sys_plugin_runtime_log` VALUES (1,1001,'sensitive-words','1.0.0','PLUGIN_TENANT_ENABLED','DOMAIN_EVENT','SUCCESS','{version=1.0.0}','a0e49968-663e-43e3-b581-be8546f39d34','6d973471-4338-4dc8-a773-f4b3375366cd',NULL,1001,'2026-06-24 00:51:43',0),(2,1001,'sensitive-words','1.0.0','ENABLE','ENABLED','SUCCESS','平台插件已启用','a0e49968-663e-43e3-b581-be8546f39d34','6d973471-4338-4dc8-a773-f4b3375366cd',NULL,1001,'2026-06-24 00:51:43',0);
/*!40000 ALTER TABLE `sys_plugin_runtime_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_schema_history`
--

DROP TABLE IF EXISTS `sys_plugin_schema_history`;
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_plugin_schema_history`
--

LOCK TABLES `sys_plugin_schema_history` WRITE;
/*!40000 ALTER TABLE `sys_plugin_schema_history` DISABLE KEYS */;
INSERT INTO `sys_plugin_schema_history` VALUES (1,'sensitive-words','1.0.0','V1__sys_sensitive_word.sql','up','classpath:builtin-plugins/sensitive-words/migrations/up/V1__sys_sensitive_word.sql','SUCCESS',NULL,1001,'2026-06-24 00:51:43');
/*!40000 ALTER TABLE `sys_plugin_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_tenant`
--

DROP TABLE IF EXISTS `sys_plugin_tenant`;
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
  KEY `idx_sys_plugin_tenant_current` (`tenant_id`,`enabled`,`deleted`,`plugin_code`,`plugin_version`),
  KEY `idx_sys_plugin_tenant_tenant_deleted_enabled_code` (`tenant_id`,`deleted`,`enabled`,`plugin_code`),
  KEY `idx_sys_plugin_tenant_code_deleted_enabled` (`plugin_code`,`deleted`,`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_plugin_tenant`
--

LOCK TABLES `sys_plugin_tenant` WRITE;
/*!40000 ALTER TABLE `sys_plugin_tenant` DISABLE KEYS */;
INSERT INTO `sys_plugin_tenant` VALUES (1,1001,'work-order-feedback','1.0.0',1,'{\"storageScope\": \"support_feedback\"}',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0),(2,1001,'sensitive-words','1.0.0',1,NULL,1001,'2026-06-24 00:51:43',1001,'2026-06-24 00:51:43',0);
/*!40000 ALTER TABLE `sys_plugin_tenant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_plugin_version`
--

DROP TABLE IF EXISTS `sys_plugin_version`;
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
  UNIQUE KEY `uk_sys_plugin_version_code_version` (`plugin_code`,`version`),
  KEY `idx_sys_plugin_version_plugin_deleted_status_created` (`plugin_code`,`deleted`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_plugin_version`
--

LOCK TABLES `sys_plugin_version` WRITE;
/*!40000 ALTER TABLE `sys_plugin_version` DISABLE KEYS */;
INSERT INTO `sys_plugin_version` VALUES (1,'sensitive-words','1.0.0',NULL,NULL,NULL,NULL,NULL,NULL,'1.0.0','LOADED','LOADED','HEALTHY','ENABLED','READY',1,0,'{\"kind\": \"SECURITY\", \"builtin\": true, \"version\": \"1.0.0\", \"pluginCode\": \"sensitive-words\", \"pluginName\": \"敏感词拦截插件\"}','{\"status\": \"VERIFIED\", \"builtin\": true}',NULL,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0,'2026-06-24 00:51:43',0),(2,'work-order-feedback','1.0.0',NULL,NULL,NULL,NULL,NULL,NULL,'1.0.0','INSTALLED','LOADED','HEALTHY','INSTALLED','READY',1,0,'{\"kind\": \"BUSINESS\", \"builtin\": true, \"version\": \"1.0.0\", \"pluginCode\": \"work-order-feedback\", \"pluginName\": \"工单反馈\"}','{\"status\": \"VERIFIED\", \"builtin\": true}',NULL,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_plugin_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
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
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1001,1001,'ADMIN','Administrator','SYSTEM','/dashboard/home',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(1002,1001,'commonuser','Common User','BUSINESS','/dashboard/home',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_data_scope`
--

DROP TABLE IF EXISTS `sys_role_data_scope`;
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

--
-- Dumping data for table `sys_role_data_scope`
--

LOCK TABLES `sys_role_data_scope` WRITE;
/*!40000 ALTER TABLE `sys_role_data_scope` DISABLE KEYS */;
INSERT INTO `sys_role_data_scope` VALUES (1,1001,1001,'*','ALL',NULL,NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(2,1001,1002,'*','SELF',NULL,NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0);
/*!40000 ALTER TABLE `sys_role_data_scope` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
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
) ENGINE=InnoDB AUTO_INCREMENT=146 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_permission`
--

LOCK TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` VALUES (1,1001,1001,'ai:chat:send',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(2,1001,1001,'ai:employee:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(3,1001,1001,'ai:employee:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(4,1001,1001,'ai:employee:skills',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(5,1001,1001,'ai:employee:status',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(6,1001,1001,'ai:employee:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(7,1001,1001,'ai:knowledge:bind',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(8,1001,1001,'ai:knowledge:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(9,1001,1001,'ai:knowledge:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(10,1001,1001,'ai:knowledge:document:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(11,1001,1001,'ai:knowledge:document:index',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(12,1001,1001,'ai:knowledge:document:upload',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(13,1001,1001,'ai:knowledge:query',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(14,1001,1001,'ai:knowledge:share',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(15,1001,1001,'ai:knowledge:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(16,1001,1001,'ai:knowledge:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(17,1001,1001,'ai:llm:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(18,1001,1001,'ai:llm:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(19,1001,1001,'ai:llm:status',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(20,1001,1001,'ai:llm:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(21,1001,1001,'ai:tool:execute',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(22,1001,1001,'ai:tool:invoke',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(23,1001,1001,'ai:tool:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(24,1001,1001,'ai:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(25,1001,1001,'audit:login:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(26,1001,1001,'audit:operation:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(27,1001,1001,'audit:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(28,1001,1001,'dashboard:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(29,1001,1001,'download:center:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(30,1001,1001,'localization:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(31,1001,1001,'payment:config:test',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(32,1001,1001,'payment:config:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(33,1001,1001,'payment:config:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(34,1001,1001,'payment:order:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(35,1001,1001,'payment:order:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(36,1001,1001,'payment:refund:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(37,1001,1001,'payment:refund:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(38,1001,1001,'payment:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(39,1001,1001,'payment:webhook:retry',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(40,1001,1001,'payment:webhook:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(41,1001,1001,'plugin:management:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(42,1001,1001,'plugin:sensitive-words:import',0,'2026-06-24 00:26:01',0,'2026-06-24 00:51:43',0),(43,1001,1001,'plugin:sensitive-words:manage',0,'2026-06-24 00:26:01',0,'2026-06-24 00:51:43',0),(44,1001,1001,'plugin:sensitive-words:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:51:43',0),(45,1001,1001,'profile:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(46,1001,1001,'system:config:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(47,1001,1001,'system:config:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(48,1001,1001,'system:department:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(49,1001,1001,'system:department:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(50,1001,1001,'system:department:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(51,1001,1001,'system:department:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(52,1001,1001,'system:dict:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(53,1001,1001,'system:dict:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(54,1001,1001,'system:dict:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(55,1001,1001,'system:dict:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(56,1001,1001,'system:file:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(57,1001,1001,'system:file:manage',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(58,1001,1001,'system:file:upload',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(59,1001,1001,'system:file:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(60,1001,1001,'system:menu:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(61,1001,1001,'system:menu:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(62,1001,1001,'system:menu:status',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(63,1001,1001,'system:menu:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(64,1001,1001,'system:menu:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(65,1001,1001,'system:monitor:docs:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(66,1001,1001,'system:monitor:redis:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(67,1001,1001,'system:monitor:service:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(68,1001,1001,'system:monitor:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(69,1001,1001,'system:notification:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(70,1001,1001,'system:online-user:ban',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(71,1001,1001,'system:online-user:kick',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(72,1001,1001,'system:online-user:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(73,1001,1001,'system:role:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(74,1001,1001,'system:role:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(75,1001,1001,'system:role:grant',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(76,1001,1001,'system:role:permissions',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(77,1001,1001,'system:role:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(78,1001,1001,'system:role:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(79,1001,1001,'system:update:check',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(80,1001,1001,'system:update:install',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(81,1001,1001,'system:update:rollback',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(82,1001,1001,'system:update:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(83,1001,1001,'system:user:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(84,1001,1001,'system:user:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(85,1001,1001,'system:user:export',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(86,1001,1001,'system:user:sensitive:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(87,1001,1001,'system:user:status',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(88,1001,1001,'system:user:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(89,1001,1001,'system:user:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(90,1001,1001,'system:verification:manage',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(91,1001,1001,'system:verification:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(92,1001,1001,'system:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(93,1001,1001,'team:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(94,1001,1001,'team:create',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(95,1001,1001,'team:update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(96,1001,1001,'team:delete',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(97,1001,1001,'team:member:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(98,1001,1001,'team:member:invite',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(99,1001,1001,'team:member:remove',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(100,1001,1001,'team:member:role-update',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(101,1001,1001,'user:center:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(128,1001,1002,'ai:chat:send',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(129,1001,1002,'ai:knowledge:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(130,1001,1002,'ai:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(131,1001,1002,'dashboard:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(132,1001,1002,'download:center:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(133,1001,1002,'profile:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(134,1001,1002,'system:file:upload',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(135,1001,1002,'system:file:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(136,1001,1002,'team:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(137,1001,1002,'user:center:view',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0);
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_sensitive_word`
--

DROP TABLE IF EXISTS `sys_sensitive_word`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `word` varchar(128) NOT NULL,
  `normalized_word` varchar(128) NOT NULL,
  `category` varchar(64) DEFAULT NULL,
  `severity` varchar(32) DEFAULT NULL,
  `action` varchar(32) NOT NULL DEFAULT 'BLOCK',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sensitive_word_tenant_normalized` (`tenant_id`,`normalized_word`,`deleted`),
  KEY `idx_sys_sensitive_word_tenant_enabled` (`tenant_id`,`enabled`,`deleted`),
  KEY `idx_sensitive_word_tenant_enabled` (`tenant_id`,`enabled`,`deleted`,`normalized_word`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_sensitive_word`
--

LOCK TABLES `sys_sensitive_word` WRITE;
/*!40000 ALTER TABLE `sys_sensitive_word` DISABLE KEYS */;
INSERT INTO `sys_sensitive_word` VALUES (1,1001,'啊啊啊','啊啊啊','CUSTOM','MEDIUM','BLOCK',1,1001,'2026-06-24 00:52:23',1001,'2026-06-24 00:52:23',0);
/*!40000 ALTER TABLE `sys_sensitive_word` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_tenant`
--

DROP TABLE IF EXISTS `sys_tenant`;
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

--
-- Dumping data for table `sys_tenant`
--

LOCK TABLES `sys_tenant` WRITE;
/*!40000 ALTER TABLE `sys_tenant` DISABLE KEYS */;
INSERT INTO `sys_tenant` VALUES (1001,'platform','Lumira Platform','ENABLED',NULL,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0);
/*!40000 ALTER TABLE `sys_tenant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
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

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1001,'admin','Administrator','Administrator',NULL,NULL,NULL,NULL,NULL,NULL,'$2a$10$Bd/k4przuW.qo/4X.WpeIemuQETrDb5J9gq/ymN9Mqew8tNubC1nu',NULL,NULL,'ENABLED',0,'2026-06-24 00:26:01',1001,'2026-06-24 15:58:16',0),(1002,'user','Common User','Common User',NULL,NULL,NULL,NULL,NULL,NULL,'$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te',NULL,NULL,'ENABLED',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_department`
--

DROP TABLE IF EXISTS `sys_user_department`;
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

--
-- Dumping data for table `sys_user_department`
--

LOCK TABLES `sys_user_department` WRITE;
/*!40000 ALTER TABLE `sys_user_department` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_department` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_passkey_credential`
--

DROP TABLE IF EXISTS `sys_user_passkey_credential`;
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

--
-- Dumping data for table `sys_user_passkey_credential`
--

LOCK TABLES `sys_user_passkey_credential` WRITE;
/*!40000 ALTER TABLE `sys_user_passkey_credential` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_passkey_credential` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1001,1001,1001,0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(2,1001,1002,1002,0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_tenant`
--

DROP TABLE IF EXISTS `sys_user_tenant`;
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_tenant`
--

LOCK TABLES `sys_user_tenant` WRITE;
/*!40000 ALTER TABLE `sys_user_tenant` DISABLE KEYS */;
INSERT INTO `sys_user_tenant` VALUES (1,1001,1001,1,'ENABLED',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(2,1001,1002,1,'ENABLED',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_user_tenant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_tenant_profile`
--

DROP TABLE IF EXISTS `sys_user_tenant_profile`;
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_tenant_profile`
--

LOCK TABLES `sys_user_tenant_profile` WRITE;
/*!40000 ALTER TABLE `sys_user_tenant_profile` DISABLE KEYS */;
INSERT INTO `sys_user_tenant_profile` VALUES (1,1001,1001,'Administrator',NULL,'zh-CN',0,'2026-06-24 00:26:01',0,'2026-06-24 00:26:01',0),(2,1001,1002,'Common User',NULL,'zh-CN',0,'2026-06-24 00:26:02',0,'2026-06-24 00:26:02',0);
/*!40000 ALTER TABLE `sys_user_tenant_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_wechat_binding`
--

DROP TABLE IF EXISTS `sys_user_wechat_binding`;
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

--
-- Dumping data for table `sys_user_wechat_binding`
--

LOCK TABLES `sys_user_wechat_binding` WRITE;
/*!40000 ALTER TABLE `sys_user_wechat_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_wechat_binding` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_verification_binding`
--

DROP TABLE IF EXISTS `sys_verification_binding`;
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

--
-- Dumping data for table `sys_verification_binding`
--

LOCK TABLES `sys_verification_binding` WRITE;
/*!40000 ALTER TABLE `sys_verification_binding` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_verification_binding` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_verification_challenge`
--

DROP TABLE IF EXISTS `sys_verification_challenge`;
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

--
-- Dumping data for table `sys_verification_challenge`
--

LOCK TABLES `sys_verification_challenge` WRITE;
/*!40000 ALTER TABLE `sys_verification_challenge` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_verification_challenge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_work_order_feedback`
--

DROP TABLE IF EXISTS `sys_work_order_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_work_order_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `ticket_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail_html` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `submitter_id` bigint NOT NULL,
  `submitter_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL,
  `handler_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `admin_reply` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_by` bigint NOT NULL DEFAULT '0',
  `updated_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_work_order_ticket_no` (`tenant_id`,`ticket_no`),
  KEY `idx_sys_work_order_tenant_status_updated` (`tenant_id`,`status`,`deleted`,`updated_at`),
  KEY `idx_sys_work_order_submitter_updated` (`tenant_id`,`submitter_id`,`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_work_order_feedback`
--

LOCK TABLES `sys_work_order_feedback` WRITE;
/*!40000 ALTER TABLE `sys_work_order_feedback` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_work_order_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team`
--

DROP TABLE IF EXISTS `team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `team_code` varchar(64) NOT NULL,
  `team_name` varchar(128) NOT NULL,
  `team_type` varchar(32) NOT NULL DEFAULT 'GENERAL',
  `avatar_url` varchar(512) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `visibility` varchar(32) NOT NULL DEFAULT 'PRIVATE',
  `join_mode` varchar(32) NOT NULL DEFAULT 'INVITE_ONLY',
  `owner_user_id` bigint NOT NULL,
  `member_count` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_code` (`tenant_id`,`team_code`,`deleted`),
  KEY `idx_team_owner` (`tenant_id`,`owner_user_id`,`deleted`),
  KEY `idx_team_status` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team`
--

LOCK TABLES `team` WRITE;
/*!40000 ALTER TABLE `team` DISABLE KEYS */;
/*!40000 ALTER TABLE `team` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_invite`
--

DROP TABLE IF EXISTS `team_invite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_invite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `invite_code` varchar(64) DEFAULT NULL,
  `invite_token_hash` varchar(128) NOT NULL,
  `invite_type` varchar(32) NOT NULL DEFAULT 'LINK',
  `role_on_join` varchar(32) NOT NULL DEFAULT 'MEMBER',
  `expires_at` datetime DEFAULT NULL,
  `max_uses` int DEFAULT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  `need_approval` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_invite_token` (`invite_token_hash`,`deleted`),
  UNIQUE KEY `uk_team_invite_code` (`tenant_id`,`invite_code`,`deleted`),
  KEY `idx_team_invite_team` (`tenant_id`,`team_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_invite`
--

LOCK TABLES `team_invite` WRITE;
/*!40000 ALTER TABLE `team_invite` DISABLE KEYS */;
/*!40000 ALTER TABLE `team_invite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_join_request`
--

DROP TABLE IF EXISTS `team_join_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_join_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `invite_id` bigint DEFAULT NULL,
  `apply_message` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_join_pending` (`team_id`,`user_id`,`status`,`deleted`),
  KEY `idx_team_join_team` (`tenant_id`,`team_id`,`status`,`deleted`),
  KEY `idx_team_join_user` (`tenant_id`,`user_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_join_request`
--

LOCK TABLES `team_join_request` WRITE;
/*!40000 ALTER TABLE `team_join_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `team_join_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_member`
--

DROP TABLE IF EXISTS `team_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(32) NOT NULL DEFAULT 'MEMBER',
  `member_alias` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `invited_by` bigint DEFAULT NULL,
  `joined_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_member` (`team_id`,`user_id`,`deleted`),
  KEY `idx_team_member_user` (`tenant_id`,`user_id`,`status`,`deleted`),
  KEY `idx_team_member_team` (`tenant_id`,`team_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_member`
--

LOCK TABLES `team_member` WRITE;
/*!40000 ALTER TABLE `team_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `team_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_group`
--

DROP TABLE IF EXISTS `xxl_job_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_group` (
  `id` int NOT NULL AUTO_INCREMENT,
  `app_name` varchar(64) NOT NULL COMMENT '执行器AppName',
  `title` varchar(64) NOT NULL COMMENT '执行器名称',
  `address_type` tinyint NOT NULL DEFAULT '0' COMMENT '执行器地址类型：0=自动注册、1=手动录入',
  `address_list` text COMMENT '执行器地址列表，多地址逗号分隔',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_group`
--

LOCK TABLES `xxl_job_group` WRITE;
/*!40000 ALTER TABLE `xxl_job_group` DISABLE KEYS */;
INSERT INTO `xxl_job_group` VALUES (1,'lumira-server','Lumira 后端执行器',0,NULL,'2026-06-24 00:26:02');
/*!40000 ALTER TABLE `xxl_job_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_info`
--

DROP TABLE IF EXISTS `xxl_job_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_info` (
  `id` int NOT NULL AUTO_INCREMENT,
  `job_group` int NOT NULL COMMENT '执行器主键ID',
  `job_desc` varchar(255) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `author` varchar(64) DEFAULT NULL COMMENT '作者',
  `alarm_email` varchar(255) DEFAULT NULL COMMENT '报警邮件',
  `schedule_type` varchar(50) NOT NULL DEFAULT 'NONE' COMMENT '调度类型',
  `schedule_conf` varchar(128) DEFAULT NULL COMMENT '调度配置，值含义取决于调度类型',
  `misfire_strategy` varchar(50) NOT NULL DEFAULT 'DO_NOTHING' COMMENT '调度过期策略',
  `executor_route_strategy` varchar(50) DEFAULT NULL COMMENT '执行器路由策略',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '任务handler',
  `executor_param` text COMMENT '任务参数',
  `executor_block_strategy` varchar(50) DEFAULT NULL COMMENT '阻塞处理策略',
  `executor_timeout` int NOT NULL DEFAULT '0' COMMENT '任务执行超时时间，单位秒',
  `executor_fail_retry_count` int NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `glue_type` varchar(50) NOT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) DEFAULT NULL COMMENT 'GLUE备注',
  `glue_updatetime` datetime DEFAULT NULL COMMENT 'GLUE更新时间',
  `child_jobid` varchar(255) DEFAULT NULL COMMENT '子任务ID，多个逗号分隔',
  `trigger_status` tinyint NOT NULL DEFAULT '0' COMMENT '调度状态：0-停止，1-运行',
  `trigger_last_time` bigint NOT NULL DEFAULT '0' COMMENT '上次调度时间',
  `trigger_next_time` bigint NOT NULL DEFAULT '0' COMMENT '下次调度时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_info`
--

LOCK TABLES `xxl_job_info` WRITE;
/*!40000 ALTER TABLE `xxl_job_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `xxl_job_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_lock`
--

DROP TABLE IF EXISTS `xxl_job_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_lock` (
  `lock_name` varchar(50) NOT NULL COMMENT '锁名称',
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_lock`
--

LOCK TABLES `xxl_job_lock` WRITE;
/*!40000 ALTER TABLE `xxl_job_lock` DISABLE KEYS */;
INSERT INTO `xxl_job_lock` VALUES ('schedule_lock');
/*!40000 ALTER TABLE `xxl_job_lock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_log`
--

DROP TABLE IF EXISTS `xxl_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_group` int NOT NULL COMMENT '执行器主键ID',
  `job_id` int NOT NULL COMMENT '任务，主键ID',
  `executor_address` varchar(255) DEFAULT NULL COMMENT '执行器地址，本次执行的地址',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '任务handler',
  `executor_param` text COMMENT '任务参数',
  `executor_sharding_param` varchar(20) DEFAULT NULL COMMENT '任务分片参数，格式如 1/2',
  `executor_fail_retry_count` int NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `trigger_time` datetime DEFAULT NULL COMMENT '调度-时间',
  `trigger_code` int NOT NULL COMMENT '调度-结果',
  `trigger_msg` text COMMENT '调度-日志',
  `handle_time` datetime DEFAULT NULL COMMENT '执行-时间',
  `handle_code` int NOT NULL COMMENT '执行-状态',
  `handle_msg` text COMMENT '执行-日志',
  `alarm_status` tinyint NOT NULL DEFAULT '0' COMMENT '告警状态：0-默认、1-无需告警、2-告警成功、3-告警失败',
  PRIMARY KEY (`id`),
  KEY `I_trigger_time` (`trigger_time`),
  KEY `I_handle_code` (`handle_code`),
  KEY `I_jobgroup` (`job_group`),
  KEY `I_jobid` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_log`
--

LOCK TABLES `xxl_job_log` WRITE;
/*!40000 ALTER TABLE `xxl_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `xxl_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_log_report`
--

DROP TABLE IF EXISTS `xxl_job_log_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_log_report` (
  `id` int NOT NULL AUTO_INCREMENT,
  `trigger_day` datetime DEFAULT NULL COMMENT '调度-时间',
  `running_count` int NOT NULL DEFAULT '0' COMMENT '运行中-日志数量',
  `suc_count` int NOT NULL DEFAULT '0' COMMENT '执行成功-日志数量',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '执行失败-日志数量',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_trigger_day` (`trigger_day`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_log_report`
--

LOCK TABLES `xxl_job_log_report` WRITE;
/*!40000 ALTER TABLE `xxl_job_log_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `xxl_job_log_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_logglue`
--

DROP TABLE IF EXISTS `xxl_job_logglue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_logglue` (
  `id` int NOT NULL AUTO_INCREMENT,
  `job_id` int NOT NULL COMMENT '任务，主键ID',
  `glue_type` varchar(50) DEFAULT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) NOT NULL COMMENT 'GLUE备注',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_logglue`
--

LOCK TABLES `xxl_job_logglue` WRITE;
/*!40000 ALTER TABLE `xxl_job_logglue` DISABLE KEYS */;
/*!40000 ALTER TABLE `xxl_job_logglue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_registry`
--

DROP TABLE IF EXISTS `xxl_job_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_registry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registry_group` varchar(50) NOT NULL,
  `registry_key` varchar(255) NOT NULL,
  `registry_value` varchar(255) NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_g_k_v` (`registry_group`,`registry_key`,`registry_value`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_registry`
--

LOCK TABLES `xxl_job_registry` WRITE;
/*!40000 ALTER TABLE `xxl_job_registry` DISABLE KEYS */;
/*!40000 ALTER TABLE `xxl_job_registry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xxl_job_user`
--

DROP TABLE IF EXISTS `xxl_job_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xxl_job_user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(100) NOT NULL COMMENT '密码加密信息',
  `token` varchar(100) DEFAULT NULL COMMENT '登录token',
  `role` tinyint NOT NULL COMMENT '角色：0-普通用户、1-管理员',
  `permission` varchar(255) DEFAULT NULL COMMENT '权限：执行器ID列表，多个逗号分割',
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_username` (`username`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xxl_job_user`
--

LOCK TABLES `xxl_job_user` WRITE;
/*!40000 ALTER TABLE `xxl_job_user` DISABLE KEYS */;
INSERT INTO `xxl_job_user` VALUES (1,'admin','8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92',NULL,1,NULL);
/*!40000 ALTER TABLE `xxl_job_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'lumira'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 16:59:52
