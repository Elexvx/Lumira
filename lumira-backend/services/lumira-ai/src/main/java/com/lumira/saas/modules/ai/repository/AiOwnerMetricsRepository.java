package com.lumira.saas.modules.ai.repository;

public interface AiOwnerMetricsRepository {

    MetricsSnapshot loadSnapshot();

    record MetricsSnapshot(
            long knowledgeIndexPendingBacklog,
            long knowledgeIndexRetryableBacklog,
            long knowledgeIndexFailedBacklog,
            long knowledgeIndexDeadLetterCount,
            long vectorIndexedChunkCount,
            long localHashingChunkCount
    ) {
    }
}
