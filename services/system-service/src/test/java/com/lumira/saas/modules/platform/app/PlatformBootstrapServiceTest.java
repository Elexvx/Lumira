package com.lumira.saas.modules.platform.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.lang.reflect.Field;
import io.micrometer.core.instrument.Counter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class PlatformBootstrapServiceTest {

    @Test
    void publicBootstrapCachesForRuntimeVersionUntilEvictionWindow() throws Exception {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);

        SystemVO.BrandingSettingsVO brandingSettings = new SystemVO.BrandingSettingsVO();
        SystemVO.SecuritySettingsVO securitySettings = new SystemVO.SecuritySettingsVO();
        SystemVO.AgreementSettingsVO agreementSettings = new SystemVO.AgreementSettingsVO();
        SystemVO.LoginCapabilitiesVO loginCapabilities = new SystemVO.LoginCapabilitiesVO();

        when(systemManagementAppService.getPublicBrandingSettings(1L)).thenReturn(brandingSettings);
        when(systemManagementAppService.getPublicSecuritySettings()).thenReturn(securitySettings);
        when(systemManagementAppService.getPublicAgreementSettings()).thenReturn(agreementSettings);
        when(systemVerificationAppService.loadLoginCapabilities(1L)).thenReturn(loginCapabilities);

        when(readModelVersionService.getOrInitialize(1L, "platform", "runtime-appearance"))
                .thenReturn(10L, 11L);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OwnerRuntimeMetrics ownerRuntimeMetrics = new OwnerRuntimeMetrics(meterRegistry);

        PlatformBootstrapService service = new PlatformBootstrapService(
                systemManagementAppService,
                systemVerificationAppService,
                readModelVersionService,
                ownerRuntimeMetrics
        );

        SystemVO.PublicBootstrapVO first = service.getPublicBootstrap(1L);
        SystemVO.PublicBootstrapVO second = service.getPublicBootstrap(1L);

        assertThat(first.getBrandingSettings()).isSameAs(brandingSettings);
        assertThat(first).isSameAs(second);
        verify(readModelVersionService, times(1)).getOrInitialize(1L, "platform", "runtime-appearance");
        verify(systemManagementAppService, times(1)).getPublicBrandingSettings(1L);
        verify(systemManagementAppService, times(1)).getPublicSecuritySettings();
        verify(systemManagementAppService, times(1)).getPublicAgreementSettings();
        verify(systemVerificationAppService, times(1)).loadLoginCapabilities(1L);

        Field cacheField = PlatformBootstrapService.class.getDeclaredField("cacheByTenant");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Object> cacheByTenant = (Map<Long, Object>) cacheField.get(service);
        cacheByTenant.clear();
        Field inFlightField = PlatformBootstrapService.class.getDeclaredField("publicBootstrapLoadInFlight");
        inFlightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<Long, CompletableFuture<SystemVO.PublicBootstrapVO>> inFlightCache =
                (com.google.common.cache.Cache<Long, CompletableFuture<SystemVO.PublicBootstrapVO>>) inFlightField.get(service);
        inFlightCache.invalidateAll();
        Field versionField = PlatformBootstrapService.class.getDeclaredField("runtimeAppearanceVersionCache");
        versionField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<Long, Long> runtimeAppearanceVersionCache = (com.google.common.cache.Cache<Long, Long>) versionField.get(service);
        runtimeAppearanceVersionCache.invalidateAll();
        Field versionInFlightField = PlatformBootstrapService.class.getDeclaredField("runtimeAppearanceVersionLoadInFlight");
        versionInFlightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<Long, CompletableFuture<Long>> runtimeAppearanceVersionLoadInFlight =
                (com.google.common.cache.Cache<Long, CompletableFuture<Long>>) versionInFlightField.get(service);
        runtimeAppearanceVersionLoadInFlight.invalidateAll();

        SystemVO.PublicBootstrapVO third = service.getPublicBootstrap(1L);

        assertThat(third).isNotSameAs(first);
        verify(readModelVersionService, times(2)).getOrInitialize(1L, "platform", "runtime-appearance");
        verify(systemManagementAppService, times(2)).getPublicBrandingSettings(1L);
        verify(systemManagementAppService, times(2)).getPublicSecuritySettings();
        verify(systemManagementAppService, times(2)).getPublicAgreementSettings();
        verify(systemVerificationAppService, times(2)).loadLoginCapabilities(1L);

        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_HIT)).isEqualTo(1.0);
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_MISS)).isEqualTo(2.0);
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_REFRESH)).isZero();
    }

    @Test
    void publicBootstrapSingleFlightOnlyBuildsOnceUnderConcurrentWarmMisses() throws Exception {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);

        SystemVO.BrandingSettingsVO brandingSettings = new SystemVO.BrandingSettingsVO();
        SystemVO.SecuritySettingsVO securitySettings = new SystemVO.SecuritySettingsVO();
        SystemVO.AgreementSettingsVO agreementSettings = new SystemVO.AgreementSettingsVO();
        SystemVO.LoginCapabilitiesVO loginCapabilities = new SystemVO.LoginCapabilitiesVO();

        when(systemManagementAppService.getPublicBrandingSettings(1L)).thenReturn(brandingSettings);
        when(systemManagementAppService.getPublicSecuritySettings()).thenReturn(securitySettings);
        when(systemManagementAppService.getPublicAgreementSettings()).thenReturn(agreementSettings);
        when(systemVerificationAppService.loadLoginCapabilities(1L)).thenReturn(loginCapabilities);
        when(readModelVersionService.getOrInitialize(1L, "platform", "runtime-appearance")).thenReturn(10L);

        PlatformBootstrapService service = new PlatformBootstrapService(
                systemManagementAppService,
                systemVerificationAppService,
                readModelVersionService,
                null
        );

        CountDownLatch ready = new CountDownLatch(1);
        int threadCount = 24;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CompletableFuture<SystemVO.PublicBootstrapVO>[] futures = new CompletableFuture[threadCount];
        AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    ready.await();
                    return service.getPublicBootstrap(1L);
                } catch (InterruptedException exception) {
                    errors.incrementAndGet();
                    Thread.currentThread().interrupt();
                    return null;
                }
            }, executor);
        }
        ready.countDown();
        CompletableFuture.allOf(futures).join();
        executor.shutdown();
        assertThat(executor.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();

        SystemVO.PublicBootstrapVO expect = futures[0].join();
        for (CompletableFuture<SystemVO.PublicBootstrapVO> future : futures) {
            assertThat(future.join()).isSameAs(expect);
        }
        assertThat(errors.get()).isZero();
        verify(readModelVersionService, times(1)).getOrInitialize(1L, "platform", "runtime-appearance");
        verify(systemManagementAppService, times(1)).getPublicBrandingSettings(1L);
        verify(systemManagementAppService, times(1)).getPublicSecuritySettings();
        verify(systemManagementAppService, times(1)).getPublicAgreementSettings();
        verify(systemVerificationAppService, times(1)).loadLoginCapabilities(1L);
    }

    private double counterCount(SimpleMeterRegistry meterRegistry, String counterName) {
        Counter counter = meterRegistry.find(counterName).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
