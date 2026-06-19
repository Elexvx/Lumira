package com.lumira.file.event;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FileOutboxMetricsService {

    private static final long SNAPSHOT_CACHE_TTL_MS = 15_000L;

    private final JdbcTemplate jdbcTemplate;
    private volatile OutboxMetricsSnapshot cachedSnapshot;
    private volatile long cachedSnapshotUntilMillis;

    public FileOutboxMetricsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long recordedBacklog() {
        return snapshot().recordedBacklog();
    }

    public long failedBacklog() {
        return snapshot().failedBacklog();
    }

    public long deadLetterCount() {
        return snapshot().deadLetterCount();
    }

    public long processingTaskPendingBacklog() {
        return snapshot().processingTaskPendingBacklog();
    }

    public long processingTaskFailedBacklog() {
        return snapshot().processingTaskFailedBacklog();
    }

    public long processingTaskDeadLetterCount() {
        return snapshot().processingTaskDeadLetterCount();
    }

    public OutboxMetricsSnapshot snapshot() {
        long now = System.currentTimeMillis();
        OutboxMetricsSnapshot cached = cachedSnapshot;
        if (cached != null && now < cachedSnapshotUntilMillis) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            cached = cachedSnapshot;
            if (cached != null && now < cachedSnapshotUntilMillis) {
                return cached;
            }
            OutboxMetricsSnapshot snapshot = loadSnapshot();
            cachedSnapshot = snapshot;
            cachedSnapshotUntilMillis = now + SNAPSHOT_CACHE_TTL_MS;
            return snapshot;
        }
    }

    private OutboxMetricsSnapshot loadSnapshot() {
        Map<String, Object> outboxRow = firstRow(
                """
                        select coalesce(sum(case when dispatch_status = 'RECORDED' then 1 else 0 end), 0) as recorded_backlog,
                               coalesce(sum(case when dispatch_status = 'FAILED' then 1 else 0 end), 0) as failed_backlog,
                               coalesce(sum(case when dispatch_status = 'DEAD_LETTER' then 1 else 0 end), 0) as dead_letter_count
                        from platform_event_outbox
                        where deleted = 0 and source_type = ?
                        """,
                FilePlatformEventTypes.SOURCE_FILE
        );
        Map<String, Object> taskRow = firstRow(
                """
                        select coalesce(sum(case when status = 'PENDING' then 1 else 0 end), 0) as pending_backlog,
                               coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as failed_backlog,
                               coalesce(sum(case when status = 'DEAD_LETTER' then 1 else 0 end), 0) as dead_letter_count
                        from file_processing_task
                        where deleted = 0
                        """
        );
        return new OutboxMetricsSnapshot(
                longValue(outboxRow.get("recorded_backlog")),
                longValue(outboxRow.get("failed_backlog")),
                longValue(outboxRow.get("dead_letter_count")),
                longValue(taskRow.get("pending_backlog")),
                longValue(taskRow.get("failed_backlog")),
                longValue(taskRow.get("dead_letter_count"))
        );
    }

    private Map<String, Object> firstRow(String sql, Object... args) {
        List<Map<String, Object>> rows = args == null || args.length == 0
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, args);
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

    public record OutboxMetricsSnapshot(
            long recordedBacklog,
            long failedBacklog,
            long deadLetterCount,
            long processingTaskPendingBacklog,
            long processingTaskFailedBacklog,
            long processingTaskDeadLetterCount
    ) {
    }
}
