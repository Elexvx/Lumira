package com.lumira.saas.modules.ai.app;

import com.lumira.saas.modules.ai.repository.AiOwnerMetricsRepository;
import org.springframework.stereotype.Service;

@Service
public class AiOwnerMetricsService {

    private final AiOwnerMetricsRepository metricsRepository;

    public AiOwnerMetricsService(AiOwnerMetricsRepository metricsRepository) {
        this.metricsRepository = metricsRepository;
    }

    public long knowledgeIndexPendingBacklog() {
        return snapshot().knowledgeIndexPendingBacklog();
    }

    public long knowledgeIndexRetryableBacklog() {
        return snapshot().knowledgeIndexRetryableBacklog();
    }

    public long knowledgeIndexFailedBacklog() {
        return snapshot().knowledgeIndexFailedBacklog();
    }

    public long knowledgeIndexDeadLetterCount() {
        return snapshot().knowledgeIndexDeadLetterCount();
    }

    public long vectorIndexedChunkCount() {
        return snapshot().vectorIndexedChunkCount();
    }

    public long localHashingChunkCount() {
        return snapshot().localHashingChunkCount();
    }

    public OwnerMetricsSnapshot snapshot() {
        AiOwnerMetricsRepository.MetricsSnapshot snapshot = metricsRepository.loadSnapshot();
        return new OwnerMetricsSnapshot(
                snapshot.knowledgeIndexPendingBacklog(),
                snapshot.knowledgeIndexRetryableBacklog(),
                snapshot.knowledgeIndexFailedBacklog(),
                snapshot.knowledgeIndexDeadLetterCount(),
                snapshot.vectorIndexedChunkCount(),
                snapshot.localHashingChunkCount()
        );
    }

    public record OwnerMetricsSnapshot(
            long knowledgeIndexPendingBacklog,
            long knowledgeIndexRetryableBacklog,
            long knowledgeIndexFailedBacklog,
            long knowledgeIndexDeadLetterCount,
            long vectorIndexedChunkCount,
            long localHashingChunkCount
    ) {
    }
}
