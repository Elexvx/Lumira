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
