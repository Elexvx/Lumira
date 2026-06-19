package com.lumira.saas.modules.ai.app;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

class AiOwnerMetricsServiceTest {

    @Test
    void metricsShouldReuseAggregatedSnapshot() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 2L,
                        "retryable_backlog", 3L,
                        "failed_backlog", 4L,
                        "dead_letter_count", 5L
                )))
                .thenReturn(List.of(Map.of(
                        "vector_indexed_chunk_count", 6L,
                        "local_hashing_chunk_count", 7L
                )));
        AiOwnerMetricsService metricsService = new AiOwnerMetricsService(jdbcTemplate);

        assertThat(metricsService.knowledgeIndexPendingBacklog()).isEqualTo(2L);
        assertThat(metricsService.knowledgeIndexRetryableBacklog()).isEqualTo(3L);
        assertThat(metricsService.knowledgeIndexFailedBacklog()).isEqualTo(4L);
        assertThat(metricsService.knowledgeIndexDeadLetterCount()).isEqualTo(5L);
        assertThat(metricsService.vectorIndexedChunkCount()).isEqualTo(6L);
        assertThat(metricsService.localHashingChunkCount()).isEqualTo(7L);
        verify(jdbcTemplate, times(2)).queryForList(anyString());
        verify(jdbcTemplate, never()).queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Long>>any());
    }

    @Test
    void snapshotShouldAggregateMetricsInTwoQueries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 2L,
                        "retryable_backlog", 3L,
                        "failed_backlog", 4L,
                        "dead_letter_count", 5L
                )))
                .thenReturn(List.of(Map.of(
                        "vector_indexed_chunk_count", 6L,
                        "local_hashing_chunk_count", 7L
                )));
        AiOwnerMetricsService metricsService = new AiOwnerMetricsService(jdbcTemplate);

        AiOwnerMetricsService.OwnerMetricsSnapshot snapshot = metricsService.snapshot();

        assertThat(snapshot.knowledgeIndexPendingBacklog()).isEqualTo(2L);
        assertThat(snapshot.knowledgeIndexRetryableBacklog()).isEqualTo(3L);
        assertThat(snapshot.knowledgeIndexFailedBacklog()).isEqualTo(4L);
        assertThat(snapshot.knowledgeIndexDeadLetterCount()).isEqualTo(5L);
        assertThat(snapshot.vectorIndexedChunkCount()).isEqualTo(6L);
        assertThat(snapshot.localHashingChunkCount()).isEqualTo(7L);
    }

    @Test
    void snapshotShouldReuseCachedValuesWithinTtl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(Map.of(
                        "pending_backlog", 2L,
                        "retryable_backlog", 3L,
                        "failed_backlog", 4L,
                        "dead_letter_count", 5L
                )))
                .thenReturn(List.of(Map.of(
                        "vector_indexed_chunk_count", 6L,
                        "local_hashing_chunk_count", 7L
                )));
        AiOwnerMetricsService metricsService = new AiOwnerMetricsService(jdbcTemplate);

        AiOwnerMetricsService.OwnerMetricsSnapshot first = metricsService.snapshot();
        AiOwnerMetricsService.OwnerMetricsSnapshot second = metricsService.snapshot();

        assertThat(second).isSameAs(first);
        verify(jdbcTemplate, times(2)).queryForList(anyString());
    }
}
