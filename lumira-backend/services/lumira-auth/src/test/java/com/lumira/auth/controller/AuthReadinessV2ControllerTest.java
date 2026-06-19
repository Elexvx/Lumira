package com.lumira.auth.controller;

import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthSessionStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthReadinessV2ControllerTest {

    @Test
    void readiness_shouldExposeAuthSplitGateContract() {
        AuthSessionStore sessionStore = mock(AuthSessionStore.class);
        AuthAppService authAppService = mock(AuthAppService.class);
        when(sessionStore.hitRatio()).thenReturn(0.8);
        when(sessionStore.hits()).thenReturn(8L);
        when(sessionStore.misses()).thenReturn(2L);
        when(sessionStore.saves()).thenReturn(3L);
        when(sessionStore.removes()).thenReturn(1L);
        when(sessionStore.corruptPayloads()).thenReturn(0L);
        when(authAppService.permissionSnapshotVersionCacheHitRatio()).thenReturn(0.5);
        when(authAppService.permissionSnapshotVersionCacheHits()).thenReturn(5L);
        when(authAppService.permissionSnapshotVersionCacheMisses()).thenReturn(5L);
        when(authAppService.permissionSnapshotVersionCacheRefreshes()).thenReturn(2L);
        when(authAppService.permissionSnapshotVersionCacheFallbacks()).thenReturn(1L);
        when(authAppService.authBootstrapCacheAlignmentRejects()).thenReturn(0L);
        AuthReadinessV2Controller controller = new AuthReadinessV2Controller(sessionStore, authAppService);

        var response = controller.readiness();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        var readiness = response.getData();
        assertThat(readiness.context()).isEqualTo("Auth");
        assertThat(readiness.ownerModule()).isEqualTo("auth-service");
        assertThat(readiness.status()).isEqualTo("READY_WITH_BLOCKERS");
        assertThat(readiness.apiContracts()).contains(
                "/api/v2/auth/readiness",
                "/api/v2/auth/login",
                "/api/v2/auth/current-user",
                "/api/v2/auth/bootstrap"
        );
		assertThat(readiness.healthChecks()).contains("auth.redis.session-store", "auth.iam-permission-snapshot");
        assertThat(readiness.metrics()).contains(
                "auth.session_store.hit_ratio",
                "auth.current_user.p95",
                "auth.permission_snapshot_version_cache.hit_ratio",
                "auth.permission_snapshot_version_cache.fallbacks",
                "auth.bootstrap_cache.hit_ratio",
                "auth.bootstrap_cache.refreshes",
                "auth.bootstrap_cache.alignment_rejects"
        );
        assertThat(readiness.blockers()).anySatisfy(blocker -> assertThat(blocker).contains("IAM snapshot"));

        var health = controller.health().getData();
        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.healthChecks())
                .extracting(check -> check.name())
                .contains("auth.redis.session-store", "auth.jwt.signing");

        var metrics = controller.metrics().getData();
        assertThat(metrics.status()).isEqualTo("METRICS_DECLARED");
        assertThat(metrics.metrics())
                .extracting(metric -> metric.name())
                .contains(
                        "auth.session_store.hit_ratio",
                        "auth.session_store.hits",
                        "auth.session_store.misses",
                        "auth.bootstrap.p95",
                        "auth.permission_snapshot_version_cache.hit_ratio",
                        "auth.permission_snapshot_version_cache.fallbacks",
                        "auth.bootstrap_cache.hit_ratio",
                        "auth.bootstrap_cache.refreshes",
                        "auth.bootstrap_cache.alignment_rejects"
                );
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.session_store.hit_ratio");
            assertThat(metric.value()).isEqualTo(0.8);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.session_store.hits");
            assertThat(metric.value()).isEqualTo(8.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.permission_snapshot_version_cache.hit_ratio");
            assertThat(metric.value()).isEqualTo(0.5);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.permission_snapshot_version_cache.fallbacks");
            assertThat(metric.value()).isEqualTo(1.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.login.p95");
            assertThat(metric.value()).isEqualTo(0.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.current_user.p95");
            assertThat(metric.value()).isEqualTo(0.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.bootstrap.p95");
            assertThat(metric.value()).isEqualTo(0.0);
        });
        assertThat(metrics.metrics()).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("auth.refresh_token.p95");
            assertThat(metric.value()).isEqualTo(0.0);
        });
    }
}
