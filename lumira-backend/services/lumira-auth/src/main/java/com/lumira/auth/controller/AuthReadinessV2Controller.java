package com.lumira.auth.controller;

import com.lumira.api.architecture.OwnerObservabilityDTO;
import com.lumira.api.architecture.OwnerReadinessDTO;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthSessionStore;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
public class AuthReadinessV2Controller {

    private final AuthSessionStore authSessionStore;
    private final AuthAppService authAppService;

    public AuthReadinessV2Controller(AuthSessionStore authSessionStore, AuthAppService authAppService) {
        this.authSessionStore = authSessionStore;
        this.authAppService = authAppService;
    }

    @GetMapping("/readiness")
    public ApiResponse<OwnerReadinessDTO> readiness() {
        return ApiResponse.success(new OwnerReadinessDTO(
                "Auth",
                "auth-service",
                "READY_WITH_BLOCKERS",
                "contract-and-observability",
                List.of(
                        "sys_user_passkey_credential",
                        "sys_user_wechat_binding",
                        "sys_verification_binding",
                        "sys_verification_challenge",
                        "Redis session keys"
                ),
                List.of(
                        "/api/v2/auth/readiness",
                        "/api/v2/auth/health",
                        "/api/v2/auth/metrics",
                        "/api/v2/auth/login-encryption-key",
                        "/api/v2/auth/login",
                        "/api/v2/auth/refresh-token",
                        "/api/v2/auth/bootstrap",
                        "/api/v2/auth/current-user",
                        "/api/v2/auth/logout",
                        "/api/v2/auth/session/keepalive",
                        "AuthInternalController.currentUserBySessionId"
                ),
                List.of(
                        "LoginSucceeded",
                        "LoginFailed",
                        "SecondFactorVerified",
                        "SessionRefreshed",
                        "SessionRevoked"
                ),
                List.of(
                        "auth.redis.session-store",
                        "auth.jwt.signing",
                        "auth.login-encryption-key",
                        "auth.iam-user-snapshot",
                        "auth.iam-permission-snapshot"
                ),
                List.of(
                        "auth.login.p95",
                        "auth.current_user.p95",
                        "auth.bootstrap.p95",
                        "auth.refresh_token.p95",
                        "auth.permission_snapshot_version_cache.hit_ratio",
                        "auth.permission_snapshot_version_cache.hits",
                        "auth.permission_snapshot_version_cache.misses",
                        "auth.permission_snapshot_version_cache.refreshes",
                        "auth.permission_snapshot_version_cache.fallbacks",
                        "auth.bootstrap_cache.hit_ratio",
                        "auth.bootstrap_cache.hits",
                        "auth.bootstrap_cache.misses",
                        "auth.bootstrap_cache.refreshes",
                        "auth.bootstrap_cache.alignment_rejects",
                        "auth.session_store.hit_ratio",
                        "auth.session_store.hits",
                        "auth.session_store.misses",
                        "auth.session_store.saves",
                        "auth.session_store.removes",
                        "auth.session_store.corrupt_payloads"
                ),
                List.of(
                        "Redis session store",
                        "IAM user snapshot",
                        "IAM permission snapshot",
                        "Platform login/security settings",
                        "verification challenge providers",
                        "JWT signing secret"
                ),
                List.of(
                        "route /api/v2/auth/* back to auth-service monolith adapter",
                        "keep Redis session key schema compatible during rollback",
                        "expire session keys by TTL without data migration",
                        "fall back current-user to IAM snapshot when session snapshot is incomplete"
                ),
                List.of(
                        "Auth v2 adapter and owner observability contract are available; passkey/verification owner table migration still needs a final split drill",
                        "cross-service timeout/degradation rules for IAM snapshot calls must be exercised before physical split"
                )
        ), TraceContext.getRequestId());
    }

    @GetMapping("/health")
    public ApiResponse<OwnerObservabilityDTO> health() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Auth",
                "auth-service",
                "UP",
                OffsetDateTime.now(),
                List.of(
                        healthCheck("auth.redis.session-store", "CONFIGURED", "AuthSessionStore owns Redis-backed session payloads and online session indexes."),
                        healthCheck("auth.jwt.signing", "CONFIGURED", "JWT access and refresh token signing is configured by Auth."),
                        healthCheck("auth.login-encryption-key", "CONFIGURED", "Login encryption key endpoint is available before password login."),
                        healthCheck("auth.iam-user-snapshot", "CONFIGURED", "Auth reads user identity through the IAM/SystemInternalApi snapshot contract."),
                        healthCheck("auth.iam-permission-snapshot", "CONFIGURED", "Auth embeds IAM permission snapshots into session bootstrap payloads.")
                ),
                authMetrics()
        ), TraceContext.getRequestId());
    }

    @GetMapping("/metrics")
    public ApiResponse<OwnerObservabilityDTO> metrics() {
        return ApiResponse.success(new OwnerObservabilityDTO(
                "Auth",
                "auth-service",
                "METRICS_DECLARED",
                OffsetDateTime.now(),
                List.of(),
                authMetrics()
        ), TraceContext.getRequestId());
    }

    private List<OwnerObservabilityDTO.MetricDTO> authMetrics() {
        return List.of(
                metric("auth.login.p95", "timer", "milliseconds", "Login p95 latency tagged by login_type/result.", authAppService.authLoginP95Millis()),
                metric("auth.current_user.p95", "timer", "milliseconds", "Current-user p95 latency; hot path should be served from session snapshot.", authAppService.authCurrentUserP95Millis()),
                metric("auth.bootstrap.p95", "timer", "milliseconds", "Auth bootstrap p95 latency for first interactive page/restore path.", authAppService.authBootstrapP95Millis()),
                metric("auth.refresh_token.p95", "timer", "milliseconds", "Refresh-token p95 latency.", authAppService.authRefreshTokenP95Millis()),
                metric("auth.permission_snapshot_version_cache.hit_ratio", "gauge", "ratio", "Permission-snapshot version cache hit ratio.", authAppService.permissionSnapshotVersionCacheHitRatio()),
                metric("auth.permission_snapshot_version_cache.hits", "counter", "sessions", "Permission-snapshot version cache hits.", authAppService.permissionSnapshotVersionCacheHits()),
                metric("auth.permission_snapshot_version_cache.misses", "counter", "sessions", "Permission-snapshot version cache misses.", authAppService.permissionSnapshotVersionCacheMisses()),
                metric("auth.permission_snapshot_version_cache.refreshes", "counter", "sessions", "Permission-snapshot version cache refresh operations.", authAppService.permissionSnapshotVersionCacheRefreshes()),
                metric("auth.permission_snapshot_version_cache.fallbacks", "counter", "sessions", "Current-user fallbacks to system snapshot path.", authAppService.permissionSnapshotVersionCacheFallbacks()),
                metric("auth.bootstrap_cache.hit_ratio", "gauge", "ratio", "Auth bootstrap cache hit ratio.", authAppService.authBootstrapCacheHitRatio()),
                metric("auth.bootstrap_cache.hits", "counter", "sessions", "Auth bootstrap cache hits.", authAppService.authBootstrapCacheHits()),
                metric("auth.bootstrap_cache.misses", "counter", "sessions", "Auth bootstrap cache misses.", authAppService.authBootstrapCacheMisses()),
                metric("auth.bootstrap_cache.refreshes", "counter", "sessions", "Auth bootstrap cache refresh operations.", authAppService.authBootstrapCacheRefreshes()),
                metric("auth.bootstrap_cache.alignment_rejects", "counter", "sessions", "Auth bootstrap cache responses rejected due to permission version drift.", authAppService.authBootstrapCacheAlignmentRejects()),
                metric("auth.session_store.hit_ratio", "gauge", "ratio", "Auth session store hit ratio.", authSessionStore.hitRatio()),
                metric("auth.session_store.hits", "counter", "sessions", "Auth session store hits.", authSessionStore.hits()),
                metric("auth.session_store.misses", "counter", "sessions", "Auth session store misses.", authSessionStore.misses()),
                metric("auth.session_store.saves", "counter", "sessions", "Auth session payload saves.", authSessionStore.saves()),
                metric("auth.session_store.removes", "counter", "sessions", "Auth session payload removals.", authSessionStore.removes()),
                metric("auth.session_store.corrupt_payloads", "counter", "sessions", "Corrupt session payloads removed from Redis.", authSessionStore.corruptPayloads())
        );
    }

    private OwnerObservabilityDTO.HealthCheckDTO healthCheck(String name, String status, String description) {
        return new OwnerObservabilityDTO.HealthCheckDTO(name, status, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, long value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, (double) value);
    }

    private OwnerObservabilityDTO.MetricDTO metric(String name, String type, String unit, String description, double value) {
        return new OwnerObservabilityDTO.MetricDTO(name, type, unit, description, value);
    }
}
