ALTER TABLE `ai_knowledge_document`
  ADD COLUMN `index_retry_count` int NOT NULL DEFAULT 0 AFTER `chunk_count`,
  ADD COLUMN `index_next_retry_at` datetime DEFAULT NULL AFTER `index_retry_count`,
  ADD COLUMN `index_last_error` varchar(512) DEFAULT NULL AFTER `index_next_retry_at`;

CREATE INDEX `idx_ai_knowledge_document_index_retry`
  ON `ai_knowledge_document` (`status`, `is_deleted`, `index_next_retry_at`, `update_time`, `id`);
