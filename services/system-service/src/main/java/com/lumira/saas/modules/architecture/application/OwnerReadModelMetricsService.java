package com.lumira.saas.modules.architecture.application;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OwnerReadModelMetricsService {

    private static final String CONTEXT_IAM = "IAM";
    private static final String CONTEXT_PLATFORM = "platform";
    private static final String SCOPE_PERMISSION_SNAPSHOT = "permission-snapshot";
    private static final String SCOPE_RUNTIME_APPEARANCE = "runtime-appearance";

    private final ReadModelVersionService readModelVersionService;
    private final MeterRegistry meterRegistry;

    public OwnerReadModelMetricsService(ReadModelVersionService readModelVersionService) {
        this(readModelVersionService, (MeterRegistry) null);
    }

    @Autowired
    public OwnerReadModelMetricsService(ReadModelVersionService readModelVersionService, ObjectProvider<MeterRegistry> meterRegistry) {
        this(readModelVersionService, meterRegistry.getIfAvailable());
    }

    OwnerReadModelMetricsService(ReadModelVersionService readModelVersionService, MeterRegistry meterRegistry) {
        this.readModelVersionService = readModelVersionService;
        this.meterRegistry = meterRegistry;
    }

    public long iamPermissionSnapshotLatestVersion() {
        return versionOrZero(readModelVersionService.latestVersion(CONTEXT_IAM, SCOPE_PERMISSION_SNAPSHOT));
    }

    public long platformRuntimeAppearanceLatestVersion() {
        return versionOrZero(readModelVersionService.latestVersion(CONTEXT_PLATFORM, SCOPE_RUNTIME_APPEARANCE));
    }

    public double iamPermissionSnapshotP95Millis() {
        return timerP95Millis(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT);
    }

    public double iamPermissionSnapshotCacheHitRatio() {
        return ratio(
                counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_CACHE_HIT),
                counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_CACHE_MISS)
        );
    }

    public double iamPermissionSnapshotRoleIdsQueryCount() {
        return counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_ROLE_IDS_QUERY);
    }

    public double iamPermissionSnapshotPermissionsQueryCount() {
        return counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_PERMISSIONS_QUERY);
    }

    public double iamPermissionSnapshotRolePermissionsQueryCount() {
        return counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_ROLE_PERMISSIONS_QUERY);
    }

    public double iamPermissionSnapshotDepartmentsQueryCount() {
        return counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DEPARTMENTS_QUERY);
    }

    public double iamPermissionSnapshotDescendantQueryCount() {
        return counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DESCENDANT_QUERY);
    }

    public double iamPermissionSnapshotDataScopeQueryCount() {
        return counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DATA_SCOPE_QUERY);
    }

    public double iamPermissionSnapshotDefaultHomeQueryCount() {
        return counterCount(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_DEFAULT_HOME_QUERY);
    }

    public double iamPermissionSnapshotInvalidationLagMillis() {
        return timerP95Millis(OwnerRuntimeMetrics.IAM_PERMISSION_SNAPSHOT_INVALIDATION);
    }

    public double platformConfigReadP95Millis() {
        return timerP95Millis(OwnerRuntimeMetrics.PLATFORM_CONFIG_READ);
    }

    public double platformConfigCacheHitRatio() {
        return ratio(
                counterCount(OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_HIT),
                counterCount(OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_MISS)
        );
    }

    public double platformBootstrapCacheHitRatio() {
        return ratio(
                counterCount(OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_HIT),
                counterCount(OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_MISS)
        );
    }

    public double platformBootstrapCacheHitCount() {
        return counterCount(OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_HIT);
    }

    public double platformBootstrapCacheMissCount() {
        return counterCount(OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_MISS);
    }

    public double platformBootstrapCacheRefreshCount() {
        return counterCount(OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_REFRESH);
    }

    public double platformBootstrapP95Millis() {
        return timerP95Millis(OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP);
    }

    public double platformAuditWriteFailureRate() {
        return ratio(
                counterCount(OwnerRuntimeMetrics.PLATFORM_AUDIT_WRITE_FAILURE),
                counterCount(OwnerRuntimeMetrics.PLATFORM_AUDIT_WRITE_SUCCESS)
        );
    }

    public double platformReadModelVersionLagMillis() {
        LocalDateTime rebuiltAt = readModelVersionService.latestRebuiltAt(CONTEXT_PLATFORM, SCOPE_RUNTIME_APPEARANCE);
        if (rebuiltAt == null) {
            return 0.0;
        }
        long millis = Duration.between(rebuiltAt, LocalDateTime.now()).toMillis();
        return Math.max(0L, millis);
    }

    private long versionOrZero(Long version) {
        return version == null ? 0L : version;
    }

    private double timerP95Millis(String name) {
        if (meterRegistry == null) {
            return 0.0;
        }
        Timer timer = meterRegistry.find(name).timer();
        if (timer == null || timer.count() == 0) {
            return 0.0;
        }
        for (var percentile : timer.takeSnapshot().percentileValues()) {
            if (Double.compare(percentile.percentile(), 0.95) == 0) {
                return percentile.value(TimeUnit.MILLISECONDS);
            }
        }
        return timer.max(TimeUnit.MILLISECONDS);
    }

    private double counterCount(String name) {
        if (meterRegistry == null) {
            return 0.0;
        }
        Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double ratio(double numerator, double denominatorRemainder) {
        double denominator = numerator + denominatorRemainder;
        return denominator <= 0 ? 0.0 : numerator / denominator;
    }
}
