ALTER TABLE `ai_knowledge_chunk`
  ADD COLUMN `embedding_model` varchar(64) DEFAULT NULL AFTER `token_count`,
  ADD COLUMN `embedding_dim` int unsigned NOT NULL DEFAULT 0 AFTER `embedding_model`,
  ADD COLUMN `embedding_vector_json` json DEFAULT NULL AFTER `embedding_dim`,
  ADD COLUMN `vector_indexed_at` datetime DEFAULT NULL AFTER `embedding_vector_json`;

CREATE INDEX `idx_ai_knowledge_chunk_vector`
  ON `ai_knowledge_chunk` (`tenant_id`, `knowledge_base_id`, `is_deleted`, `embedding_model`, `update_time`);
