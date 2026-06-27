package com.lumira.saas.modules.platform.app;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class PlatformBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrapService.class);

    private static final String PLATFORM_CONTEXT = "platform";
    private static final String PLATFORM_RUNTIME_APPEARANCE_SCOPE = "runtime-appearance";
    private static final String PLATFORM_PUBLIC_BOOTSTRAP_SCOPE = "public-bootstrap";
    private static final ReadModelVersionService.ReadModelScopeKey RUNTIME_APPEARANCE_VERSION_KEY =
            new ReadModelVersionService.ReadModelScopeKey(PLATFORM_CONTEXT, PLATFORM_RUNTIME_APPEARANCE_SCOPE);
    private static final ReadModelVersionService.ReadModelScopeKey PUBLIC_BOOTSTRAP_VERSION_KEY =
            new ReadModelVersionService.ReadModelScopeKey(PLATFORM_CONTEXT, PLATFORM_PUBLIC_BOOTSTRAP_SCOPE);
    private static final Duration PUBLIC_BOOTSTRAP_IN_FLIGHT_TTL = Duration.ofSeconds(10);
    private static final int PUBLIC_BOOTSTRAP_IN_FLIGHT_MAX_ENTRIES = 256;
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private final SystemManagementAppService systemManagementAppService;
    private final SystemVerificationAppService systemVerificationAppService;
    private final ReadModelVersionService readModelVersionService;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private volatile PublicBootstrapCache publicBootstrapCache;
    private final Cache<String, CompletableFuture<SystemVO.PublicBootstrapVO>> publicBootstrapLoadInFlight;

    public PlatformBootstrapService(
            SystemManagementAppService systemManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            ReadModelVersionService readModelVersionService
    ) {
        this(systemManagementAppService, systemVerificationAppService, readModelVersionService, null);
    }

    @Autowired
    public PlatformBootstrapService(
            SystemManagementAppService systemManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.systemVerificationAppService = systemVerificationAppService;
        this.readModelVersionService = readModelVersionService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.publicBootstrapLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(PUBLIC_BOOTSTRAP_IN_FLIGHT_MAX_ENTRIES)
                .expireAfterWrite(PUBLIC_BOOTSTRAP_IN_FLIGHT_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public SystemVO.PublicBootstrapVO getPublicBootstrap() {
        PublicBootstrapCache current = publicBootstrapCache;
        PublicBootstrapVersion targetVersion = loadPublicBootstrapVersion(current);
        if (current != null && current.payload != null && current.version.equals(targetVersion)) {
            recordBootstrapCacheHit();
            return current.payload;
        }
        return getPublicBootstrapWithSingleFlight(current, targetVersion);
    }

    private void recordBootstrapCacheHit() {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordPlatformBootstrapCacheHit();
        }
    }

    private void recordBootstrapCacheMiss() {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordPlatformBootstrapCacheMiss();
        }
    }

    private void recordBootstrapCacheRefresh() {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordPlatformBootstrapCacheRefresh();
        }
    }

    private SystemVO.PublicBootstrapVO getPublicBootstrapWithSingleFlight(
            PublicBootstrapCache staleCache,
            PublicBootstrapVersion targetVersion
    ) {
        String inFlightKey = targetVersion.cacheKey();
        try {
            CompletableFuture<SystemVO.PublicBootstrapVO> inFlight = publicBootstrapLoadInFlight.get(
                    inFlightKey,
                    () -> CompletableFuture.supplyAsync(
                            () -> buildPublicBootstrap(staleCache, targetVersion),
                            BLOCKING_IO_EXECUTOR
                    )
            );
            return inFlight.join();
        } catch (CompletionException exception) {
            publicBootstrapLoadInFlight.invalidate(inFlightKey);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            log.warn("Failed to load public bootstrap with single-flight path", cause);
            return fallbackPublicBootstrap(staleCache);
        } catch (ExecutionException exception) {
            publicBootstrapLoadInFlight.invalidate(inFlightKey);
            log.warn("Failed to load public bootstrap with single-flight", exception);
            return fallbackPublicBootstrap(staleCache);
        }
    }

    private SystemVO.PublicBootstrapVO buildPublicBootstrap(
            PublicBootstrapCache staleCache,
            PublicBootstrapVersion targetVersion
    ) {
        if (staleCache != null && staleCache.payload != null && staleCache.version.equals(targetVersion)) {
            recordBootstrapCacheRefresh();
            publicBootstrapCache = staleCache;
            return staleCache.payload;
        }
        recordBootstrapCacheMiss();

        SystemVO.PublicBootstrapVO bootstrap = new SystemVO.PublicBootstrapVO();
        SystemVO.PublicBootstrapVO stalePayload = staleCache == null ? null : staleCache.payload;
        boolean runtimeAppearanceUnchanged = staleCache != null
                && stalePayload != null
                && staleCache.version.runtimeAppearanceVersion == targetVersion.runtimeAppearanceVersion;
        boolean publicBootstrapUnchanged = staleCache != null
                && stalePayload != null
                && staleCache.version.publicBootstrapVersion == targetVersion.publicBootstrapVersion;

        CompletableFuture<SystemVO.BrandingSettingsVO> brandingFuture = runtimeAppearanceUnchanged
                ? null
                : CompletableFuture.supplyAsync(
                        systemManagementAppService::getPublicBrandingSettings,
                        BLOCKING_IO_EXECUTOR
                );
        CompletableFuture<SystemVO.AgreementSettingsVO> agreementFuture = runtimeAppearanceUnchanged
                ? null
                : CompletableFuture.supplyAsync(
                        systemManagementAppService::getPublicAgreementSettings,
                        BLOCKING_IO_EXECUTOR
                );
        CompletableFuture<SystemVO.SecuritySettingsVO> securityFuture = publicBootstrapUnchanged
                ? null
                : CompletableFuture.supplyAsync(
                        systemManagementAppService::getPublicSecuritySettings,
                        BLOCKING_IO_EXECUTOR
                );
        CompletableFuture<SystemVO.LoginCapabilitiesVO> loginCapabilitiesFuture = publicBootstrapUnchanged
                ? null
                : CompletableFuture.supplyAsync(
                        systemVerificationAppService::loadLoginCapabilitiesFresh,
                        BLOCKING_IO_EXECUTOR
                );

        bootstrap.setBrandingSettings(runtimeAppearanceUnchanged ? stalePayload.getBrandingSettings() : brandingFuture.join());
        bootstrap.setAgreementSettings(runtimeAppearanceUnchanged ? stalePayload.getAgreementSettings() : agreementFuture.join());
        bootstrap.setSecuritySettings(publicBootstrapUnchanged ? stalePayload.getSecuritySettings() : securityFuture.join());
        bootstrap.setLoginCapabilities(publicBootstrapUnchanged ? stalePayload.getLoginCapabilities() : loginCapabilitiesFuture.join());
        publicBootstrapCache = new PublicBootstrapCache(targetVersion, bootstrap);
        return bootstrap;
    }

    private PublicBootstrapVersion loadPublicBootstrapVersion(PublicBootstrapCache staleCache) {
        PublicBootstrapVersion fallback = staleCache == null ? PublicBootstrapVersion.ZERO : staleCache.version;
        if (readModelVersionService == null) {
            return fallback;
        }
        try {
            Map<ReadModelVersionService.ReadModelScopeKey, Long> versions = readModelVersionService.currentVersions(
                    List.of(RUNTIME_APPEARANCE_VERSION_KEY, PUBLIC_BOOTSTRAP_VERSION_KEY)
            );
            return new PublicBootstrapVersion(
                    versionOf(versions, RUNTIME_APPEARANCE_VERSION_KEY, fallback.runtimeAppearanceVersion),
                    versionOf(versions, PUBLIC_BOOTSTRAP_VERSION_KEY, fallback.publicBootstrapVersion)
            );
        } catch (Throwable throwable) {
            log.debug("Failed to load platform public bootstrap read-model versions", throwable);
            return fallback;
        }
    }

    private long versionOf(
            Map<ReadModelVersionService.ReadModelScopeKey, Long> versions,
            ReadModelVersionService.ReadModelScopeKey scopeKey,
            long fallback
    ) {
        if (versions == null || scopeKey == null) {
            return fallback;
        }
        Long version = versions.get(scopeKey);
        return version == null ? fallback : version;
    }

    private SystemVO.PublicBootstrapVO fallbackPublicBootstrap(PublicBootstrapCache staleCache) {
        if (staleCache != null && staleCache.payload != null) {
            recordBootstrapCacheRefresh();
            return staleCache.payload;
        }
        throw new IllegalStateException("Failed to build public bootstrap payload and no stale cache available");
    }

    private static final class PublicBootstrapCache {
        private final PublicBootstrapVersion version;
        private final SystemVO.PublicBootstrapVO payload;

        private PublicBootstrapCache(PublicBootstrapVersion version, SystemVO.PublicBootstrapVO payload) {
            this.version = version;
            this.payload = payload;
        }
    }

    private record PublicBootstrapVersion(long runtimeAppearanceVersion, long publicBootstrapVersion) {
        private static final PublicBootstrapVersion ZERO = new PublicBootstrapVersion(0L, 0L);

        private String cacheKey() {
            return runtimeAppearanceVersion + ":" + publicBootstrapVersion;
        }
    }
}
