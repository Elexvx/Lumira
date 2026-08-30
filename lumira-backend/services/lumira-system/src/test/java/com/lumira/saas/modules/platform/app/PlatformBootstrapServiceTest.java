package com.lumira.saas.modules.platform.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.update.app.PlatformUpdateMaintenanceService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformBootstrapServiceTest {

    @Test
    void automaticMaintenanceLeaseChangeInvalidatesCachedRuntimeAppearance() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        PlatformUpdateMaintenanceService updateMaintenanceService = mock(PlatformUpdateMaintenanceService.class);
        SystemVO.BrandingSettingsVO normalBranding = new SystemVO.BrandingSettingsVO();
        normalBranding.setMaintenanceModeEnabled(false);
        SystemVO.BrandingSettingsVO maintenanceBranding = new SystemVO.BrandingSettingsVO();
        maintenanceBranding.setMaintenanceModeEnabled(true);
        when(systemManagementAppService.getPublicBrandingSettings())
                .thenReturn(normalBranding, maintenanceBranding);
        when(systemManagementAppService.getPublicAgreementSettings())
                .thenReturn(new SystemVO.AgreementSettingsVO(), new SystemVO.AgreementSettingsVO());
        when(systemManagementAppService.getPublicSecuritySettings()).thenReturn(new SystemVO.SecuritySettingsVO());
        when(systemVerificationAppService.loadLoginCapabilitiesFresh()).thenReturn(new SystemVO.LoginCapabilitiesVO());
        when(readModelVersionService.currentVersions(any())).thenReturn(versions(10L, 20L));
        when(updateMaintenanceService.isAutomaticMaintenanceActive()).thenReturn(false, true);
        PlatformBootstrapService service = new PlatformBootstrapService(
                systemManagementAppService,
                systemVerificationAppService,
                readModelVersionService,
                null,
                updateMaintenanceService
        );

        SystemVO.PublicBootstrapVO normal = service.getPublicBootstrap();
        SystemVO.PublicBootstrapVO maintenance = service.getPublicBootstrap();

        assertThat(normal.getBrandingSettings().getMaintenanceModeEnabled()).isFalse();
        assertThat(maintenance.getBrandingSettings().getMaintenanceModeEnabled()).isTrue();
        assertThat(maintenance).isNotSameAs(normal);
        verify(systemManagementAppService, times(2)).getPublicBrandingSettings();
    }

    @Test
    void publicBootstrapReusesPayloadWhileVersionSignatureStable() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);

        SystemVO.BrandingSettingsVO brandingSettings = new SystemVO.BrandingSettingsVO();
        SystemVO.SecuritySettingsVO securitySettings = new SystemVO.SecuritySettingsVO();
        SystemVO.AgreementSettingsVO agreementSettings = new SystemVO.AgreementSettingsVO();
        SystemVO.WatermarkSettingsVO watermarkSettings = new SystemVO.WatermarkSettingsVO();
        SystemVO.LoginCapabilitiesVO loginCapabilities = new SystemVO.LoginCapabilitiesVO();

        when(systemManagementAppService.getPublicBrandingSettings()).thenReturn(brandingSettings);
        when(systemManagementAppService.getPublicSecuritySettings()).thenReturn(securitySettings);
        when(systemManagementAppService.getPublicAgreementSettings()).thenReturn(agreementSettings);
        when(systemManagementAppService.getPublicWatermarkSettings()).thenReturn(watermarkSettings);
        when(systemVerificationAppService.loadLoginCapabilitiesFresh()).thenReturn(loginCapabilities);
        when(readModelVersionService.currentVersions(any())).thenReturn(versions(10L, 20L), versions(10L, 20L));

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OwnerRuntimeMetrics ownerRuntimeMetrics = new OwnerRuntimeMetrics(meterRegistry);

        PlatformBootstrapService service = new PlatformBootstrapService(
                systemManagementAppService,
                systemVerificationAppService,
                readModelVersionService,
                ownerRuntimeMetrics
        );

        SystemVO.PublicBootstrapVO first = service.getPublicBootstrap();
        SystemVO.PublicBootstrapVO second = service.getPublicBootstrap();

        assertThat(first.getBrandingSettings()).isSameAs(brandingSettings);
        assertThat(first.getWatermarkSettings()).isSameAs(watermarkSettings);
        assertThat(second).isSameAs(first);
        verify(systemManagementAppService, times(1)).getPublicBrandingSettings();
        verify(systemManagementAppService, times(1)).getPublicSecuritySettings();
        verify(systemManagementAppService, times(1)).getPublicAgreementSettings();
        verify(systemManagementAppService, times(1)).getPublicWatermarkSettings();
        verify(systemVerificationAppService, times(1)).loadLoginCapabilitiesFresh();
        verify(readModelVersionService, times(2)).currentVersions(any());
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_HIT)).isEqualTo(1.0);
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_MISS)).isEqualTo(1.0);
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_BOOTSTRAP_CACHE_REFRESH)).isZero();
    }

    @Test
    void publicBootstrapReloadsWhenPublicBootstrapVersionChanges() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);

        when(systemManagementAppService.getPublicBrandingSettings())
                .thenReturn(new SystemVO.BrandingSettingsVO())
                .thenReturn(new SystemVO.BrandingSettingsVO());
        when(systemManagementAppService.getPublicSecuritySettings())
                .thenReturn(new SystemVO.SecuritySettingsVO())
                .thenReturn(new SystemVO.SecuritySettingsVO());
        when(systemManagementAppService.getPublicAgreementSettings())
                .thenReturn(new SystemVO.AgreementSettingsVO())
                .thenReturn(new SystemVO.AgreementSettingsVO());
        when(systemVerificationAppService.loadLoginCapabilitiesFresh())
                .thenReturn(new SystemVO.LoginCapabilitiesVO())
                .thenReturn(new SystemVO.LoginCapabilitiesVO());
        when(readModelVersionService.currentVersions(any())).thenReturn(versions(10L, 20L), versions(10L, 21L));

        PlatformBootstrapService service = new PlatformBootstrapService(
                systemManagementAppService,
                systemVerificationAppService,
                readModelVersionService,
                null
        );

        SystemVO.PublicBootstrapVO first = service.getPublicBootstrap();
        SystemVO.PublicBootstrapVO second = service.getPublicBootstrap();

        assertThat(second).isNotSameAs(first);
        verify(systemManagementAppService, times(1)).getPublicBrandingSettings();
        verify(systemManagementAppService, times(2)).getPublicSecuritySettings();
        verify(systemManagementAppService, times(1)).getPublicAgreementSettings();
        verify(systemVerificationAppService, times(2)).loadLoginCapabilitiesFresh();
    }

    @Test
    void publicBootstrapReloadsOnlyRuntimeAppearanceSliceWhenRuntimeVersionChanges() {
        SystemManagementAppService systemManagementAppService = mock(SystemManagementAppService.class);
        SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);

        when(systemManagementAppService.getPublicBrandingSettings())
                .thenReturn(new SystemVO.BrandingSettingsVO())
                .thenReturn(new SystemVO.BrandingSettingsVO());
        when(systemManagementAppService.getPublicSecuritySettings())
                .thenReturn(new SystemVO.SecuritySettingsVO());
        when(systemManagementAppService.getPublicAgreementSettings())
                .thenReturn(new SystemVO.AgreementSettingsVO())
                .thenReturn(new SystemVO.AgreementSettingsVO());
        when(systemVerificationAppService.loadLoginCapabilitiesFresh())
                .thenReturn(new SystemVO.LoginCapabilitiesVO());
        when(readModelVersionService.currentVersions(any())).thenReturn(versions(10L, 20L), versions(11L, 20L));

        PlatformBootstrapService service = new PlatformBootstrapService(
                systemManagementAppService,
                systemVerificationAppService,
                readModelVersionService,
                null
        );

        SystemVO.PublicBootstrapVO first = service.getPublicBootstrap();
        SystemVO.PublicBootstrapVO second = service.getPublicBootstrap();

        assertThat(second).isNotSameAs(first);
        verify(systemManagementAppService, times(2)).getPublicBrandingSettings();
        verify(systemManagementAppService, times(1)).getPublicSecuritySettings();
        verify(systemManagementAppService, times(2)).getPublicAgreementSettings();
        verify(systemVerificationAppService, times(1)).loadLoginCapabilitiesFresh();
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

        when(systemManagementAppService.getPublicBrandingSettings()).thenReturn(brandingSettings);
        when(systemManagementAppService.getPublicSecuritySettings()).thenReturn(securitySettings);
        when(systemManagementAppService.getPublicAgreementSettings()).thenReturn(agreementSettings);
        when(systemVerificationAppService.loadLoginCapabilitiesFresh()).thenReturn(loginCapabilities);
        when(readModelVersionService.currentVersions(any())).thenReturn(versions(10L, 20L));

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
            futures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    ready.await();
                    return service.getPublicBootstrap();
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

        SystemVO.PublicBootstrapVO expected = futures[0].join();
        for (CompletableFuture<SystemVO.PublicBootstrapVO> future : futures) {
            assertThat(future.join()).isSameAs(expected);
        }
        assertThat(errors.get()).isZero();
        verify(systemManagementAppService, times(1)).getPublicBrandingSettings();
        verify(systemManagementAppService, times(1)).getPublicSecuritySettings();
        verify(systemManagementAppService, times(1)).getPublicAgreementSettings();
        verify(systemVerificationAppService, times(1)).loadLoginCapabilitiesFresh();
    }

    private double counterCount(SimpleMeterRegistry meterRegistry, String counterName) {
        Counter counter = meterRegistry.find(counterName).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private Map<ReadModelVersionService.ReadModelScopeKey, Long> versions(
            Long runtimeAppearanceVersion,
            Long publicBootstrapVersion
    ) {
        return Map.of(
                new ReadModelVersionService.ReadModelScopeKey("platform", "runtime-appearance"),
                runtimeAppearanceVersion,
                new ReadModelVersionService.ReadModelScopeKey("platform", "public-bootstrap"),
                publicBootstrapVersion
        );
    }
}
