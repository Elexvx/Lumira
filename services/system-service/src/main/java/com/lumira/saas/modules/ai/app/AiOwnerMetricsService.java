package com.lumira.saas.modules.ai.app;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiOwnerMetricsService {

    private static final long SNAPSHOT_CACHE_TTL_MS = 15_000L;

    private final JdbcTemplate jdbcTemplate;
    private volatile OwnerMetricsSnapshot cachedSnapshot;
    private volatile long cachedSnapshotUntilMillis;

    public AiOwnerMetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        long now = System.currentTimeMillis();
        OwnerMetricsSnapshot cached = cachedSnapshot;
        if (cached != null && now < cachedSnapshotUntilMillis) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            cached = cachedSnapshot;
            if (cached != null && now < cachedSnapshotUntilMillis) {
                return cached;
            }
            OwnerMetricsSnapshot snapshot = loadSnapshot();
            cachedSnapshot = snapshot;
            cachedSnapshotUntilMillis = now + SNAPSHOT_CACHE_TTL_MS;
            return snapshot;
        }
    }

    private OwnerMetricsSnapshot loadSnapshot() {
        Map<String, Object> documentStats = firstRow("""
                select coalesce(sum(case when status = 'INDEXING' then 1 else 0 end), 0) as pending_backlog,
                       coalesce(sum(case when status = 'FAILED'
                                         and coalesce(index_retry_count, 0) < 5
                                         and (index_next_retry_at is null or index_next_retry_at <= now())
                                         then 1 else 0 end), 0) as retryable_backlog,
                       coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as failed_backlog,
                       coalesce(sum(case when status = 'DEAD_LETTER' then 1 else 0 end), 0) as dead_letter_count
                from ai_knowledge_document
                where is_deleted = 0
                  and file_id is not null
                """);
        Map<String, Object> chunkStats = firstRow("""
                select coalesce(sum(case when embedding_vector_json is not null and vector_indexed_at is not null then 1 else 0 end), 0) as vector_indexed_chunk_count,
                       coalesce(sum(case when embedding_model = 'local-hashing-v1' and embedding_vector_json is not null then 1 else 0 end), 0) as local_hashing_chunk_count
                from ai_knowledge_chunk
                where is_deleted = 0
                """);
        return new OwnerMetricsSnapshot(
                longValue(documentStats.get("pending_backlog")),
                longValue(documentStats.get("retryable_backlog")),
                longValue(documentStats.get("failed_backlog")),
                longValue(documentStats.get("dead_letter_count")),
                longValue(chunkStats.get("vector_indexed_chunk_count")),
                longValue(chunkStats.get("local_hashing_chunk_count"))
        );
    }

    private Map<String, Object> firstRow(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
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
