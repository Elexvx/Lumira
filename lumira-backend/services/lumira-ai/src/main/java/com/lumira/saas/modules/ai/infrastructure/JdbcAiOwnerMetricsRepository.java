package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.modules.ai.repository.AiOwnerMetricsRepository;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiOwnerMetricsRepository implements AiOwnerMetricsRepository {

    private final JdbcTemplate database;

    public JdbcAiOwnerMetricsRepository(JdbcTemplate database) {
        this.database = database;
    }

    @Override
    public MetricsSnapshot loadSnapshot() {
        Map<String, Object> documentStats = firstRow("""
                select coalesce(sum(case when status = 'INDEXING' then 1 else 0 end), 0) as pending_backlog,
                       coalesce(sum(case when status = 'FAILED'
                                         and coalesce(index_retry_count, 0) < 5
                                         and (index_next_retry_at is null or index_next_retry_at <= now())
                                         then 1 else 0 end), 0) as retryable_backlog,
                       coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as failed_backlog,
                       coalesce(sum(case when status = 'DEAD_LETTER' then 1 else 0 end), 0) as dead_letter_count
                from ai_knowledge_document
                where is_deleted = 0 and file_id is not null
                """);
        Map<String, Object> chunkStats = firstRow("""
                select coalesce(sum(case when embedding_vector_json is not null and vector_indexed_at is not null then 1 else 0 end), 0) as vector_indexed_chunk_count,
                       coalesce(sum(case when embedding_model = 'local-hashing-v1' and embedding_vector_json is not null then 1 else 0 end), 0) as local_hashing_chunk_count
                from ai_knowledge_chunk
                where is_deleted = 0
                """);
        return new MetricsSnapshot(
                longValue(documentStats.get("pending_backlog")),
                longValue(documentStats.get("retryable_backlog")),
                longValue(documentStats.get("failed_backlog")),
                longValue(documentStats.get("dead_letter_count")),
                longValue(chunkStats.get("vector_indexed_chunk_count")),
                longValue(chunkStats.get("local_hashing_chunk_count"))
        );
    }

    private Map<String, Object> firstRow(String sql) {
        List<Map<String, Object>> rows = database.queryForList(sql);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private long longValue(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
