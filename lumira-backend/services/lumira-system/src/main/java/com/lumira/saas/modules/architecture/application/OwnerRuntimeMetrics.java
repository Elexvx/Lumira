package com.lumira.saas.modules.architecture.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OwnerRuntimeMetrics {

    public static final String IAM_PERMISSION_SNAPSHOT = "iam.permission_snapshot";
    public static final String IAM_PERMISSION_SNAPSHOT_CACHE_HIT = "iam.permission_snapshot.cache_hit";
    public static final String IAM_PERMISSION_SNAPSHOT_CACHE_MISS = "iam.permission_snapshot.cache_miss";
    public static final String IAM_PERMISSION_SNAPSHOT_ROLE_IDS_QUERY = "iam.permission_snapshot.query.role_ids";
    public static final String IAM_PERMISSION_SNAPSHOT_PERMISSIONS_QUERY = "iam.permission_snapshot.query.permissions";
    public static final String IAM_PERMISSION_SNAPSHOT_ROLE_PERMISSIONS_QUERY = "iam.permission_snapshot.query.role_permissions";
    public static final String IAM_PERMISSION_SNAPSHOT_DEPARTMENTS_QUERY = "iam.permission_snapshot.query.departments";
    public static final String IAM_PERMISSION_SNAPSHOT_DESCENDANT_QUERY = "iam.permission_snapshot.query.descendants";
    public static final String IAM_PERMISSION_SNAPSHOT_DATA_SCOPE_QUERY = "iam.permission_snapshot.query.data_scope";
    public static final String IAM_PERMISSION_SNAPSHOT_DEFAULT_HOME_QUERY = "iam.permission_snapshot.query.default_home";
    public static final String IAM_PERMISSION_SNAPSHOT_INVALIDATION = "iam.permission_snapshot.invalidation";
    public static final String PLATFORM_CONFIG_READ = "platform.config_read";
    public static final String PLATFORM_CONFIG_CACHE_HIT = "platform.config.cache_hit";
    public static final String PLATFORM_CONFIG_CACHE_MISS = "platform.config.cache_miss";
    public static final String PLATFORM_BOOTSTRAP = "platform.bootstrap";
    public static final String PLATFORM_BOOTSTRAP_CACHE_HIT = "platform.bootstrap.cache_hit";
    public static final String PLATFORM_BOOTSTRAP_CACHE_MISS = "platform.bootstrap.cache_miss";
    public static final String PLATFORM_BOOTSTRAP_CACHE_REFRESH = "platform.bootstrap.cache_refresh";
    public static final String PLATFORM_AUDIT_WRITE_SUCCESS = "platform.audit.write_success";
    public static final String PLATFORM_AUDIT_WRITE_FAILURE = "platform.audit.write_failure";
    public static final String AUTH_SESSION_AUTH = "auth.session_auth";
    public static final String AUTH_SESSION_AUTH_SUCCESS = "auth.session_auth.success";
    public static final String AUTH_SESSION_AUTH_FAILURE = "auth.session_auth.failure";
    public static final String AUTH_PERMISSION_SNAPSHOT_SESSION_HIT = "auth.permission_snapshot.session_hit";
    public static final String AUTH_PERMISSION_SNAPSHOT_ROLE_HIT = "auth.permission_snapshot.role_hit";
    public static final String AUTH_PERMISSION_SNAPSHOT_USER_LOAD = "auth.permission_snapshot.user_load";
    public static final String AUTH_SESSION_ACTIVITY_REFRESH = "auth.session_activity_refresh";

    private final MeterRegistry meterRegistry;

    public OwnerRuntimeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordIamPermissionSnapshot(boolean cacheHit, Duration duration) {
        recordTimer(IAM_PERMISSION_SNAPSHOT, duration);
        counter(cacheHit ? IAM_PERMISSION_SNAPSHOT_CACHE_HIT : IAM_PERMISSION_SNAPSHOT_CACHE_MISS).increment();
    }

    public void recordIamPermissionSnapshotInvalidation(Duration duration) {
        recordTimer(IAM_PERMISSION_SNAPSHOT_INVALIDATION, duration);
    }

    public void recordPlatformConfigRead(Duration duration) {
        recordTimer(PLATFORM_CONFIG_READ, duration);
    }

    public void recordPlatformConfigCacheHit() {
        counter(PLATFORM_CONFIG_CACHE_HIT).increment();
    }

    public void recordPlatformConfigCacheMiss() {
        counter(PLATFORM_CONFIG_CACHE_MISS).increment();
    }

    public void recordPlatformBootstrap(Duration duration) {
        recordTimer(PLATFORM_BOOTSTRAP, duration);
    }

    public void recordPlatformBootstrapCacheHit() {
        counter(PLATFORM_BOOTSTRAP_CACHE_HIT).increment();
    }

    public void recordPlatformBootstrapCacheMiss() {
        counter(PLATFORM_BOOTSTRAP_CACHE_MISS).increment();
    }

    public void recordPlatformBootstrapCacheRefresh() {
        counter(PLATFORM_BOOTSTRAP_CACHE_REFRESH).increment();
    }

    public void recordPlatformAuditWriteSuccess() {
        counter(PLATFORM_AUDIT_WRITE_SUCCESS).increment();
    }

    public void recordPlatformAuditWriteFailure() {
        counter(PLATFORM_AUDIT_WRITE_FAILURE).increment();
    }

    public void recordAuthSessionAuth(Duration duration, boolean success) {
        recordTimer(AUTH_SESSION_AUTH, duration);
        counter(success ? AUTH_SESSION_AUTH_SUCCESS : AUTH_SESSION_AUTH_FAILURE).increment();
    }

    public void recordAuthSessionAuthFailure() {
        counter(AUTH_SESSION_AUTH_FAILURE).increment();
    }

    public void recordAuthPermissionSnapshotFromSession() {
        counter(AUTH_PERMISSION_SNAPSHOT_SESSION_HIT).increment();
    }

    public void recordAuthPermissionSnapshotFromRole() {
        counter(AUTH_PERMISSION_SNAPSHOT_ROLE_HIT).increment();
    }

    public void recordAuthPermissionSnapshotFromUser() {
        counter(AUTH_PERMISSION_SNAPSHOT_USER_LOAD).increment();
    }

    public void recordAuthSessionActivityRefresh() {
        counter(AUTH_SESSION_ACTIVITY_REFRESH).increment();
    }

    public void recordIamPermissionSnapshotRoleIdsQuery() {
        counter(IAM_PERMISSION_SNAPSHOT_ROLE_IDS_QUERY).increment();
    }

    public void recordIamPermissionSnapshotPermissionsQuery() {
        counter(IAM_PERMISSION_SNAPSHOT_PERMISSIONS_QUERY).increment();
    }

    public void recordIamPermissionSnapshotRolePermissionsQuery() {
        counter(IAM_PERMISSION_SNAPSHOT_ROLE_PERMISSIONS_QUERY).increment();
    }

    public void recordIamPermissionSnapshotDepartmentsQuery() {
        counter(IAM_PERMISSION_SNAPSHOT_DEPARTMENTS_QUERY).increment();
    }

    public void recordIamPermissionSnapshotDescendantQuery() {
        counter(IAM_PERMISSION_SNAPSHOT_DESCENDANT_QUERY).increment();
    }

    public void recordIamPermissionSnapshotDataScopeQuery() {
        counter(IAM_PERMISSION_SNAPSHOT_DATA_SCOPE_QUERY).increment();
    }

    public void recordIamPermissionSnapshotDefaultHomeQuery() {
        counter(IAM_PERMISSION_SNAPSHOT_DEFAULT_HOME_QUERY).increment();
    }

    private void recordTimer(String name, Duration duration) {
        Timer.builder(name)
                .publishPercentiles(0.95)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }
}
