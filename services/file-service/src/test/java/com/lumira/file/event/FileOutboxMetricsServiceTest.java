package com.lumira.file.event;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class FileOutboxMetricsServiceTest {

    @Test
    void outboxMetrics_shouldReuseAggregatedSnapshot() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from platform_event_outbox"), eq(FilePlatformEventTypes.SOURCE_FILE)))
                .thenReturn(List.of(Map.of(
                        "recorded_backlog", 4L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L
                )));
        when(jdbcTemplate.queryForList(contains("from file_processing_task")))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 7L,
                        "failed_backlog", 3L,
                        "dead_letter_count", 2L
                )));

        var service = new FileOutboxMetricsService(jdbcTemplate);

        assertThat(service.recordedBacklog()).isEqualTo(4L);
        assertThat(service.failedBacklog()).isEqualTo(2L);
        assertThat(service.deadLetterCount()).isEqualTo(1L);
        assertThat(service.processingTaskPendingBacklog()).isEqualTo(7L);
        assertThat(service.processingTaskFailedBacklog()).isEqualTo(3L);
        assertThat(service.processingTaskDeadLetterCount()).isEqualTo(2L);
        Mockito.verify(jdbcTemplate, Mockito.times(1)).queryForList(contains("from platform_event_outbox"), eq(FilePlatformEventTypes.SOURCE_FILE));
        Mockito.verify(jdbcTemplate, Mockito.times(1)).queryForList(contains("from file_processing_task"));
        Mockito.verify(jdbcTemplate, Mockito.never()).queryForObject(anyString(), eq(Long.class), anyString());
    }

    @Test
    void snapshotShouldReadOutboxAndTaskMetricsTogether() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from platform_event_outbox"), eq(FilePlatformEventTypes.SOURCE_FILE)))
                .thenReturn(List.of(Map.of(
                        "recorded_backlog", 4L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L
                )));
        when(jdbcTemplate.queryForList(contains("from file_processing_task")))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 7L,
                        "failed_backlog", 3L,
                        "dead_letter_count", 2L
                )));

        var service = new FileOutboxMetricsService(jdbcTemplate);
        FileOutboxMetricsService.OutboxMetricsSnapshot snapshot = service.snapshot();

        assertThat(snapshot.recordedBacklog()).isEqualTo(4L);
        assertThat(snapshot.failedBacklog()).isEqualTo(2L);
        assertThat(snapshot.deadLetterCount()).isEqualTo(1L);
        assertThat(snapshot.processingTaskPendingBacklog()).isEqualTo(7L);
        assertThat(snapshot.processingTaskFailedBacklog()).isEqualTo(3L);
        assertThat(snapshot.processingTaskDeadLetterCount()).isEqualTo(2L);
    }

    @Test
    void snapshotShouldReuseCachedValueWithinTtl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from platform_event_outbox"), eq(FilePlatformEventTypes.SOURCE_FILE)))
                .thenReturn(List.of(Map.of(
                        "recorded_backlog", 4L,
                        "failed_backlog", 2L,
                        "dead_letter_count", 1L
                )));
        when(jdbcTemplate.queryForList(contains("from file_processing_task")))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 7L,
                        "failed_backlog", 3L,
                        "dead_letter_count", 2L
                )));

        var service = new FileOutboxMetricsService(jdbcTemplate);
        var first = service.snapshot();
        var second = service.snapshot();

        assertThat(second).isSameAs(first);
        Mockito.verify(jdbcTemplate, Mockito.times(1)).queryForList(contains("from platform_event_outbox"), eq(FilePlatformEventTypes.SOURCE_FILE));
        Mockito.verify(jdbcTemplate, Mockito.times(1)).queryForList(contains("from file_processing_task"));
    }

    @Test
    void outboxMetrics_shouldTreatEmptySnapshotRowsAsZero() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("from platform_event_outbox"), eq(FilePlatformEventTypes.SOURCE_FILE))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("from file_processing_task"))).thenReturn(List.of());

        var service = new FileOutboxMetricsService(jdbcTemplate);

        assertThat(service.recordedBacklog()).isZero();
    }
}
