package com.lumira.saas.modules.system.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyString;
class SystemPlatformSettingsAppServiceTest {

    @Test
    void brandingSettingsSingleFlightCachesByRuntimeVersion() {
        CurrentUser currentUser = currentUser(1L);
        Map<String, String> configValues = Map.of(
                "branding.website-name", "Lumira",
                "branding.company-name", "Acme Corp",
                "branding.copyright-start-year", "2020"
        );
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(configValues);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.getOrInitialize(1L, "platform", "runtime-appearance")).thenReturn(1L);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OwnerRuntimeMetrics ownerRuntimeMetrics = new OwnerRuntimeMetrics(meterRegistry);

        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, ownerRuntimeMetrics, mock(SmtpMailService.class));

        SystemVO.BrandingSettingsVO first = service.getBrandingSettings(currentUser);
        SystemVO.BrandingSettingsVO second = service.getBrandingSettings(currentUser);

        assertThat(first.getWebsiteName()).isEqualTo("Lumira");
        assertThat(second.getWebsiteName()).isEqualTo("Lumira");
        assertThat(queryOperations.queryForListCount()).isEqualTo(1);
        verify(readModelVersionService, times(1)).getOrInitialize(1L, "platform", "runtime-appearance");
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_MISS)).isEqualTo(1.0);
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_HIT)).isEqualTo(1.0);
        assertThat(first.getCopyrightStartYear()).isEqualTo(2020);
    }

    @Test
    void brandingSettingsReloadsWhenRuntimeVersionBumps() throws Exception {
        CurrentUser currentUser = currentUser(1L);
        Map<String, String> configValues = Map.of(
                "branding.website-name", "Lumira",
                "branding.company-name", "Acme Corp",
                "branding.copyright-start-year", "2020"
        );
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(configValues);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.getOrInitialize(1L, "platform", "runtime-appearance")).thenReturn(5L, 6L);

        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, mock(SmtpMailService.class));

        SystemVO.BrandingSettingsVO first = service.getBrandingSettings(currentUser);
        SystemVO.BrandingSettingsVO second = service.getBrandingSettings(currentUser);
        assertThat(second.getWebsiteName()).isEqualTo(first.getWebsiteName());
        assertThat(queryOperations.queryForListCount()).isEqualTo(1);

        clearRuntimeVersionCaches(service);
        SystemVO.BrandingSettingsVO third = service.getBrandingSettings(currentUser);

        assertThat(third.getWebsiteName()).isEqualTo("Lumira");
        assertThat(queryOperations.queryForListCount()).isEqualTo(2);
        verify(readModelVersionService, times(2)).getOrInitialize(1L, "platform", "runtime-appearance");
        assertThat(System.identityHashCode(first)).isNotEqualTo(System.identityHashCode(third));
    }

    @Test
    void brandingSettingsSingleFlightBuildsOnceUnderConcurrentMisses() throws Exception {
        CurrentUser currentUser = currentUser(1L);
        Map<String, String> configValues = Map.of(
                "branding.website-name", "Lumira",
                "branding.company-name", "Acme Corp",
                "branding.copyright-start-year", "2020"
        );
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(configValues);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.getOrInitialize(1L, "platform", "runtime-appearance")).thenReturn(2L);

        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, mock(SmtpMailService.class));

        int threadCount = 24;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(1);
        CompletableFuture<SystemVO.BrandingSettingsVO>[] futures = new CompletableFuture[threadCount];
        for (int index = 0; index < threadCount; index++) {
            futures[index] = CompletableFuture.supplyAsync(() -> {
                try {
                    ready.await();
                    return service.getBrandingSettings(currentUser);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }, executor);
        }
        ready.countDown();
        CompletableFuture.allOf(futures).join();
        executor.shutdown();
        assertThat(executor.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();

        SystemVO.BrandingSettingsVO expect = futures[0].join();
        for (CompletableFuture<SystemVO.BrandingSettingsVO> future : futures) {
            SystemVO.BrandingSettingsVO setting = future.join();
            assertThat(setting).isNotNull();
            assertThat(setting.getWebsiteName()).isEqualTo(expect.getWebsiteName());
        }
        assertThat(queryOperations.queryForListCount()).isEqualTo(1);
        verify(readModelVersionService, times(1)).getOrInitialize(1L, "platform", "runtime-appearance");
    }

    @Test
    void smtpSettingsUpdatesInvalidateMailConfigCache() {
        CurrentUser currentUser = currentUser(1L);
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "smtp.enabled", "true",
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.username", "mailer",
                "smtp.password", "secret",
                "smtp.from", "noreply@example.com",
                "smtp.auth-enabled", "true",
                "smtp.starttls-enabled", "true",
                "smtp.ssl-enabled", "false"
        ));
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        SmtpMailService smtpMailService = mock(SmtpMailService.class);

        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, smtpMailService);

        SystemVO.SmtpSettingsVO before = service.getSmtpSettings(currentUser);
        assertThat(before.getHost()).isEqualTo("smtp.example.com");

        SystemDTO.SmtpSettingsRequest request = new SystemDTO.SmtpSettingsRequest();
        request.setEnabled(Boolean.TRUE);
        request.setHost("smtp2.example.com");
        request.setPort(465);
        request.setUsername("mailer2");
        request.setPassword("secret2");
        request.setFrom("noreply2@example.com");
        request.setAuthEnabled(Boolean.TRUE);
        request.setStartTlsEnabled(Boolean.FALSE);
        request.setSslEnabled(Boolean.TRUE);

        service.updateSmtpSettings(currentUser, request);
        service.resetSmtpSettings(currentUser);

        verify(smtpMailService, times(2)).invalidateTenant(1L);
    }

    private static SystemPlatformSettingsAppService newService(
            RecordingQueryOperations queryOperations,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService
    ) {
        FieldCryptoService fieldCryptoService = mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return new SystemPlatformSettingsAppService(
                queryOperations,
                new OperationAuditService(null) {
                    @Override
                    public void log(
                            Long tenantId,
                            Long userId,
                            String username,
                            String moduleName,
                            String actionName,
                            String operationType,
                            String resultStatus,
                            String detailMessage
                    ) {
                    }
                },
                fieldCryptoService,
                readModelVersionService,
                ownerRuntimeMetrics,
                smtpMailService
        );
    }

    private static CurrentUser currentUser(Long tenantId) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setCurrentTenantId(tenantId);
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        return currentUser;
    }

    private static double counterCount(SimpleMeterRegistry meterRegistry, String counterName) {
        Counter counter = meterRegistry.find(counterName).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static void clearRuntimeVersionCaches(SystemPlatformSettingsAppService service) throws Exception {
        Field runtimeVersionCacheField = SystemPlatformSettingsAppService.class.getDeclaredField("runtimeAppearanceVersionCache");
        runtimeVersionCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<Long, Long> runtimeVersionCache =
                (com.google.common.cache.Cache<Long, Long>) runtimeVersionCacheField.get(service);
        runtimeVersionCache.invalidateAll();

        Field inFlightField = SystemPlatformSettingsAppService.class.getDeclaredField("runtimeAppearanceVersionLoadInFlight");
        inFlightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<Long, CompletableFuture<Long>> inFlight =
                (com.google.common.cache.Cache<Long, CompletableFuture<Long>>) inFlightField.get(service);
        inFlight.invalidateAll();

        Field configLoadInFlightField = SystemPlatformSettingsAppService.class.getDeclaredField("configLoadInFlight");
        configLoadInFlightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<String, CompletableFuture<Map<String, String>>> configLoadInFlight =
                (com.google.common.cache.Cache<String, CompletableFuture<Map<String, String>>>) configLoadInFlightField.get(service);
        configLoadInFlight.invalidateAll();
        Field configSnapshotCacheField = SystemPlatformSettingsAppService.class.getDeclaredField("configSnapshotCache");
        configSnapshotCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<String, Map<String, String>> configSnapshotCache =
                (com.google.common.cache.Cache<String, Map<String, String>>) configSnapshotCacheField.get(service);
        configSnapshotCache.invalidateAll();
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final Map<String, String> configValues;
        private final AtomicInteger queryForListCount = new AtomicInteger(0);

        private RecordingQueryOperations(Map<String, String> configValues) {
            this.configValues = new LinkedHashMap<>(configValues);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryForListCount.incrementAndGet();
            return rowsByArgs(args);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Long.class && args.length > 0 && configValues.containsKey(Objects.toString(args[0], ""))) {
                return requiredType.cast(1L);
            }
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            if (args.length >= 4 && args[1] instanceof String configKey) {
                configValues.put(configKey, Objects.toString(args[3], ""));
            }
            return 1;
        }

        int queryForListCount() {
            return queryForListCount.get();
        }

        private List<Map<String, Object>> rowsByArgs(Object... args) {
            if (args.length < 2) {
                return List.of();
            }
            int configKeyCount = Math.max(0, args.length - 2);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int index = 0; index < configKeyCount; index++) {
                String configKey = Objects.toString(args[index], "");
                if (!configValues.containsKey(configKey)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("configKey", configKey);
                row.put("configValue", configValues.get(configKey));
                rows.add(row);
            }
            return rows;
        }
    }
}
