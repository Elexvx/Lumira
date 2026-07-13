package com.lumira.saas.modules.ai.app;

import com.lumira.saas.modules.ai.repository.AiOwnerMetricsRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOwnerMetricsServiceTest {

    @Test
    void snapshotShouldExposeDatabaseMetrics() {
        AiOwnerMetricsRepository repository = repositoryReturningSnapshot();
        AiOwnerMetricsService metricsService = new AiOwnerMetricsService(repository);

        AiOwnerMetricsService.OwnerMetricsSnapshot snapshot = metricsService.snapshot();

        assertThat(snapshot.knowledgeIndexPendingBacklog()).isEqualTo(2L);
        assertThat(snapshot.knowledgeIndexRetryableBacklog()).isEqualTo(3L);
        assertThat(snapshot.knowledgeIndexFailedBacklog()).isEqualTo(4L);
        assertThat(snapshot.knowledgeIndexDeadLetterCount()).isEqualTo(5L);
        assertThat(snapshot.vectorIndexedChunkCount()).isEqualTo(6L);
        assertThat(snapshot.localHashingChunkCount()).isEqualTo(7L);
        verify(repository).loadSnapshot();
    }

    @Test
    void snapshotShouldReloadFromDatabaseInsteadOfKeepingBusinessDataInMemory() {
        AiOwnerMetricsRepository repository = repositoryReturningSnapshot();
        AiOwnerMetricsService metricsService = new AiOwnerMetricsService(repository);

        metricsService.snapshot();
        metricsService.snapshot();

        verify(repository, times(2)).loadSnapshot();
    }

    private AiOwnerMetricsRepository repositoryReturningSnapshot() {
        AiOwnerMetricsRepository repository = mock(AiOwnerMetricsRepository.class);
        when(repository.loadSnapshot()).thenReturn(new AiOwnerMetricsRepository.MetricsSnapshot(
                2L, 3L, 4L, 5L, 6L, 7L
        ));
        return repository;
    }
}
