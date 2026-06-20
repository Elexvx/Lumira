ALTER TABLE file_processing_task
    ADD COLUMN claimed_by varchar(128) NULL,
    ADD COLUMN claim_token varchar(128) NULL,
    ADD COLUMN claim_expires_at datetime NULL;

ALTER TABLE platform_event_outbox
    ADD COLUMN claimed_by varchar(128) NULL,
    ADD COLUMN claim_token varchar(128) NULL,
    ADD COLUMN claim_expires_at datetime NULL;

ALTER TABLE ai_knowledge_base
    ADD COLUMN document_count bigint NOT NULL DEFAULT 0,
    ADD COLUMN chunk_count bigint NOT NULL DEFAULT 0;

ALTER TABLE ai_knowledge_chunk
    ADD COLUMN embedding_vector_blob mediumblob NULL AFTER embedding_vector_json,
    ADD COLUMN embedding_norm double NULL AFTER embedding_vector_blob;

CREATE TABLE IF NOT EXISTS sys_department_closure (
    id bigint NOT NULL AUTO_INCREMENT,
    tenant_id bigint NOT NULL,
    ancestor_id bigint NOT NULL,
    descendant_id bigint NOT NULL,
    depth int NOT NULL,
    deleted tinyint NOT NULL DEFAULT 0,
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_closure (tenant_id, ancestor_id, descendant_id),
    KEY idx_dept_closure_descendant (tenant_id, descendant_id),
    KEY idx_dept_closure_ancestor_depth (tenant_id, ancestor_id, depth)
);

CREATE INDEX idx_sensitive_word_tenant_enabled ON sys_sensitive_word (tenant_id, enabled, deleted, normalized_word);
CREATE INDEX idx_ai_knowledge_chunk_acl ON ai_knowledge_chunk (tenant_id, knowledge_base_id, document_id, is_deleted, update_time, id);
CREATE INDEX idx_ai_knowledge_document_status ON ai_knowledge_document (tenant_id, knowledge_base_id, status, is_deleted);
CREATE INDEX idx_ai_knowledge_base_access ON ai_knowledge_base (tenant_id, owner_user_id, visibility_scope, status, is_deleted);
CREATE INDEX idx_ai_knowledge_base_acl_subject ON ai_knowledge_base_acl (tenant_id, knowledge_base_id, subject_type, subject_id, permission, is_deleted);
CREATE INDEX idx_file_processing_batch_claim ON file_processing_task (deleted, status, next_retry_at, priority, created_at, id);
CREATE INDEX idx_file_processing_claim_token ON file_processing_task (claim_token);
CREATE INDEX idx_platform_event_outbox_batch_claim ON platform_event_outbox (source_type, deleted, dispatch_status, next_retry_at, created_at, id);
CREATE INDEX idx_platform_event_outbox_claim_token ON platform_event_outbox (claim_token);
CREATE INDEX idx_ai_tool_policy_runtime ON ai_tool_policy (tenant_id, enabled, is_deleted, tool_code, action_type, risk_level);

ALTER TABLE ai_knowledge_chunk ADD FULLTEXT INDEX ft_ai_knowledge_chunk_search_text (search_text);

CREATE TABLE IF NOT EXISTS ai_knowledge_base_stats (
    tenant_id bigint unsigned NOT NULL,
    knowledge_base_id bigint unsigned NOT NULL,
    document_count bigint unsigned NOT NULL DEFAULT 0,
    chunk_count bigint unsigned NOT NULL DEFAULT 0,
    vector_indexed_chunk_count bigint unsigned NOT NULL DEFAULT 0,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, knowledge_base_id)
);

INSERT INTO ai_knowledge_base_stats (
    tenant_id, knowledge_base_id, document_count, chunk_count, vector_indexed_chunk_count, update_time
)
SELECT kb.tenant_id,
       kb.id,
       count(distinct d.id),
       count(c.id),
       sum(case when c.embedding_vector_blob is not null or c.embedding_vector_json is not null then 1 else 0 end),
       current_timestamp
from ai_knowledge_base kb
left join ai_knowledge_document d
  on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
left join ai_knowledge_chunk c
  on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
where kb.is_deleted = 0
group by kb.tenant_id, kb.id
on duplicate key update
    document_count = values(document_count),
    chunk_count = values(chunk_count),
    vector_indexed_chunk_count = values(vector_indexed_chunk_count),
    update_time = values(update_time);
