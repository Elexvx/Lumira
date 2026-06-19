package com.lumira.saas.modules.architecture.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OwnerReadModelMetricsServiceTest {

    @Test
    void ownerMetrics_shouldExposeLatestReadModelVersions() {
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        when(readModelVersionService.latestVersion("IAM", "permission-snapshot")).thenReturn(9L);
        when(readModelVersionService.latestVersion("platform", "runtime-appearance")).thenReturn(4L);

        var service = new OwnerReadModelMetricsService(readModelVersionService);

        assertThat(service.iamPermissionSnapshotLatestVersion()).isEqualTo(9L);
        assertThat(service.platformRuntimeAppearanceLatestVersion()).isEqualTo(4L);
    }

    @Test
    void ownerMetrics_shouldUseZeroWhenReadModelVersionDoesNotExist() {
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);

        var service = new OwnerReadModelMetricsService(readModelVersionService);

        assertThat(service.iamPermissionSnapshotLatestVersion()).isZero();
        assertThat(service.platformRuntimeAppearanceLatestVersion()).isZero();
    }

    @Test
    void ownerMetrics_shouldExposeRuntimeMicrometerValues() {
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        when(readModelVersionService.latestRebuiltAt("platform", "runtime-appearance")).thenReturn(LocalDateTime.now().minusSeconds(2));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OwnerRuntimeMetrics runtimeMetrics = new OwnerRuntimeMetrics(meterRegistry);

        runtimeMetrics.recordIamPermissionSnapshot(true, Duration.ofMillis(12));
        runtimeMetrics.recordIamPermissionSnapshot(false, Duration.ofMillis(30));
        runtimeMetrics.recordIamPermissionSnapshotRoleIdsQuery();
        runtimeMetrics.recordIamPermissionSnapshotPermissionsQuery();
        runtimeMetrics.recordIamPermissionSnapshotRolePermissionsQuery();
        runtimeMetrics.recordIamPermissionSnapshotDepartmentsQuery();
        runtimeMetrics.recordIamPermissionSnapshotDescendantQuery();
        runtimeMetrics.recordIamPermissionSnapshotDataScopeQuery();
        runtimeMetrics.recordIamPermissionSnapshotDefaultHomeQuery();
        runtimeMetrics.recordIamPermissionSnapshotInvalidation(Duration.ofMillis(8));
        runtimeMetrics.recordPlatformConfigRead(Duration.ofMillis(18));
        runtimeMetrics.recordPlatformConfigCacheMiss();
        runtimeMetrics.recordPlatformConfigCacheHit();
        runtimeMetrics.recordPlatformConfigCacheHit();
        runtimeMetrics.recordPlatformBootstrapCacheMiss();
        runtimeMetrics.recordPlatformBootstrapCacheHit();
        runtimeMetrics.recordPlatformBootstrapCacheRefresh();
        runtimeMetrics.recordPlatformBootstrap(Duration.ofMillis(42));
        runtimeMetrics.recordPlatformAuditWriteSuccess();
        runtimeMetrics.recordPlatformAuditWriteFailure();

        var service = new OwnerReadModelMetricsService(readModelVersionService, meterRegistry);

        assertThat(service.iamPermissionSnapshotP95Millis()).isGreaterThan(0.0);
        assertThat(service.iamPermissionSnapshotCacheHitRatio()).isEqualTo(0.5);
        assertThat(service.iamPermissionSnapshotInvalidationLagMillis()).isGreaterThan(0.0);
        assertThat(service.platformConfigReadP95Millis()).isGreaterThan(0.0);
        assertThat(service.platformConfigCacheHitRatio()).isEqualTo(2.0 / 3.0);
        assertThat(service.platformBootstrapCacheHitRatio()).isEqualTo(0.5);
        assertThat(service.platformBootstrapCacheHitCount()).isEqualTo(1.0);
        assertThat(service.platformBootstrapCacheMissCount()).isEqualTo(1.0);
        assertThat(service.platformBootstrapCacheRefreshCount()).isEqualTo(1.0);
        assertThat(service.platformReadModelVersionLagMillis()).isGreaterThanOrEqualTo(2_000.0);
        assertThat(service.platformBootstrapP95Millis()).isGreaterThan(0.0);
        assertThat(service.platformAuditWriteFailureRate()).isEqualTo(0.5);
        assertThat(service.iamPermissionSnapshotRoleIdsQueryCount()).isEqualTo(1.0);
        assertThat(service.iamPermissionSnapshotPermissionsQueryCount()).isEqualTo(1.0);
        assertThat(service.iamPermissionSnapshotRolePermissionsQueryCount()).isEqualTo(1.0);
        assertThat(service.iamPermissionSnapshotDepartmentsQueryCount()).isEqualTo(1.0);
        assertThat(service.iamPermissionSnapshotDescendantQueryCount()).isEqualTo(1.0);
        assertThat(service.iamPermissionSnapshotDataScopeQueryCount()).isEqualTo(1.0);
        assertThat(service.iamPermissionSnapshotDefaultHomeQueryCount()).isEqualTo(1.0);
    }
}
