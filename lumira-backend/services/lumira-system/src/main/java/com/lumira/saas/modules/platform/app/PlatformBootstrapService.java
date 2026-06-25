package com.lumira.saas.modules.platform.app;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlatformBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrapService.class);

    private static final String PLATFORM_CONTEXT = "platform";
    private static final String PLATFORM_RUNTIME_APPEARANCE_SCOPE = "runtime-appearance";
    private static final String PUBLIC_BOOTSTRAP_CACHE_KEY = "public-bootstrap";
    private static final String RUNTIME_APPEARANCE_CACHE_KEY = "runtime-appearance";
    private static final Duration PUBLIC_BOOTSTRAP_CACHE_TTL = Duration.ofSeconds(10);
    private static final int PUBLIC_BOOTSTRAP_CACHE_MAX_ENTRIES = 256;
    private static final Duration RUNTIME_APPEARANCE_VERSION_CACHE_TTL = Duration.ofSeconds(30);
    private static final int RUNTIME_APPEARANCE_VERSION_CACHE_MAX_ENTRIES = 512;
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private final SystemManagementAppService systemManagementAppService;
    private final SystemVerificationAppService systemVerificationAppService;
    private final ReadModelVersionService readModelVersionService;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private volatile PublicBootstrapCache publicBootstrapCache;
    private final Cache<String, CompletableFuture<SystemVO.PublicBootstrapVO>> publicBootstrapLoadInFlight;
    private final Cache<String, Long> runtimeAppearanceVersionCache;
    private final Cache<String, CompletableFuture<Long>> runtimeAppearanceVersionLoadInFlight;

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
                .maximumSize(PUBLIC_BOOTSTRAP_CACHE_MAX_ENTRIES)
                .expireAfterWrite(PUBLIC_BOOTSTRAP_CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.runtimeAppearanceVersionCache = CacheBuilder.newBuilder()
                .maximumSize(RUNTIME_APPEARANCE_VERSION_CACHE_MAX_ENTRIES)
                .expireAfterWrite(RUNTIME_APPEARANCE_VERSION_CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.runtimeAppearanceVersionLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(RUNTIME_APPEARANCE_VERSION_CACHE_MAX_ENTRIES)
                .expireAfterWrite(RUNTIME_APPEARANCE_VERSION_CACHE_TTL.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public SystemVO.PublicBootstrapVO getPublicBootstrap() {
        PublicBootstrapCache current = publicBootstrapCache;
        long now = System.currentTimeMillis();
        if (current != null && current.payload != null && now < current.expiresAtMillis) {
            recordBootstrapCacheHit();
            return current.payload;
        }
        return getPublicBootstrapWithSingleFlight(current);
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

    private SystemVO.PublicBootstrapVO getPublicBootstrapWithSingleFlight(PublicBootstrapCache staleCache) {
        try {
            CompletableFuture<SystemVO.PublicBootstrapVO> inFlight = publicBootstrapLoadInFlight.get(
                    PUBLIC_BOOTSTRAP_CACHE_KEY,
                    () -> CompletableFuture.supplyAsync(() -> buildPublicBootstrap(staleCache), BLOCKING_IO_EXECUTOR)
            );
            return inFlight.join();
        } catch (CompletionException exception) {
            publicBootstrapLoadInFlight.invalidate(PUBLIC_BOOTSTRAP_CACHE_KEY);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            log.warn("Failed to load public bootstrap with single-flight path", cause);
            return fallbackPublicBootstrap(staleCache);
        } catch (ExecutionException exception) {
            publicBootstrapLoadInFlight.invalidate(PUBLIC_BOOTSTRAP_CACHE_KEY);
            log.warn("Failed to load public bootstrap with single-flight", exception);
            return fallbackPublicBootstrap(staleCache);
        }
    }

    private SystemVO.PublicBootstrapVO buildPublicBootstrap(PublicBootstrapCache staleCache) {
        long runtimeAppearanceVersion = loadRuntimeAppearanceVersion(staleCache);
        if (staleCache != null
                && staleCache.payload != null
                && staleCache.runtimeAppearanceVersion == runtimeAppearanceVersion
        ) {
            recordBootstrapCacheRefresh();
            publicBootstrapCache = staleCache.withExpiresAt(System.currentTimeMillis() + PUBLIC_BOOTSTRAP_CACHE_TTL.toMillis());
            return staleCache.payload;
        }
        recordBootstrapCacheMiss();

        SystemVO.PublicBootstrapVO bootstrap = new SystemVO.PublicBootstrapVO();
        CompletableFuture<SystemVO.BrandingSettingsVO> brandingFuture = CompletableFuture.supplyAsync(
                systemManagementAppService::getPublicBrandingSettings,
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<SystemVO.SecuritySettingsVO> securityFuture = CompletableFuture.supplyAsync(
                systemManagementAppService::getPublicSecuritySettings,
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<SystemVO.AgreementSettingsVO> agreementFuture = CompletableFuture.supplyAsync(
                systemManagementAppService::getPublicAgreementSettings,
                BLOCKING_IO_EXECUTOR
        );
        CompletableFuture<SystemVO.LoginCapabilitiesVO> loginCapabilitiesFuture = CompletableFuture.supplyAsync(
                systemVerificationAppService::loadLoginCapabilities,
                BLOCKING_IO_EXECUTOR
        );

        bootstrap.setBrandingSettings(brandingFuture.join());
        bootstrap.setSecuritySettings(securityFuture.join());
        bootstrap.setAgreementSettings(agreementFuture.join());
        bootstrap.setLoginCapabilities(loginCapabilitiesFuture.join());
        publicBootstrapCache = new PublicBootstrapCache(
                runtimeAppearanceVersion,
                bootstrap,
                System.currentTimeMillis() + PUBLIC_BOOTSTRAP_CACHE_TTL.toMillis()
        );
        return bootstrap;
    }

    private long loadRuntimeAppearanceVersion(PublicBootstrapCache staleCache) {
        Long cachedVersion = runtimeAppearanceVersionCache.getIfPresent(RUNTIME_APPEARANCE_CACHE_KEY);
        if (cachedVersion != null) {
            return cachedVersion;
        }

        try {
            CompletableFuture<Long> inFlight = runtimeAppearanceVersionLoadInFlight.get(
                    RUNTIME_APPEARANCE_CACHE_KEY,
                    () -> CompletableFuture.supplyAsync(
                            () -> {
                                Long version = readModelVersionService.currentVersion(
                                        PLATFORM_CONTEXT,
                                        PLATFORM_RUNTIME_APPEARANCE_SCOPE
                                );
                                return version == null ? 0L : version;
                            },
                            BLOCKING_IO_EXECUTOR
                    )
            );
            long version = inFlight.join();
            runtimeAppearanceVersionCache.put(RUNTIME_APPEARANCE_CACHE_KEY, version);
            return version;
        } catch (CompletionException exception) {
            runtimeAppearanceVersionLoadInFlight.invalidate(RUNTIME_APPEARANCE_CACHE_KEY);
            log.debug("Failed to load runtime-appearance version with single-flight path", exception);
            if (staleCache != null && staleCache.runtimeAppearanceVersion > 0) {
                return staleCache.runtimeAppearanceVersion;
            }
            return staleCache != null && staleCache.payload != null ? staleCache.runtimeAppearanceVersion : 0L;
        } catch (ExecutionException exception) {
            runtimeAppearanceVersionLoadInFlight.invalidate(RUNTIME_APPEARANCE_CACHE_KEY);
            log.debug("Failed to load runtime-appearance version single-flight", exception);
            if (staleCache != null && staleCache.runtimeAppearanceVersion > 0) {
                return staleCache.runtimeAppearanceVersion;
            }
            return staleCache != null && staleCache.payload != null ? staleCache.runtimeAppearanceVersion : 0L;
        }
    }

    private SystemVO.PublicBootstrapVO fallbackPublicBootstrap(PublicBootstrapCache staleCache) {
        if (staleCache != null && staleCache.payload != null) {
            return staleCache.payload;
        }
        throw new IllegalStateException("Failed to build public bootstrap payload and no stale cache available");
    }

    private static final class PublicBootstrapCache {
        private final long runtimeAppearanceVersion;
        private final long expiresAtMillis;
        private final SystemVO.PublicBootstrapVO payload;

        private PublicBootstrapCache(long runtimeAppearanceVersion, SystemVO.PublicBootstrapVO payload, long expiresAtMillis) {
            this.runtimeAppearanceVersion = runtimeAppearanceVersion;
            this.payload = payload;
            this.expiresAtMillis = expiresAtMillis;
        }

        private PublicBootstrapCache withExpiresAt(long expiresAtMillis) {
            return new PublicBootstrapCache(runtimeAppearanceVersion, payload, expiresAtMillis);
        }
    }
}
