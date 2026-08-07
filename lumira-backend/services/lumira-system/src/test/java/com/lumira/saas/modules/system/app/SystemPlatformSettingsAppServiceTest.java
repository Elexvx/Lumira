package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.system.update.app.PlatformUpdateMaintenanceService;
import com.lumira.saas.modules.system.settings.infrastructure.JdbcSystemPlatformSettingsRepository;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyString;
import static org.mockito.ArgumentMatchers.eq;
class SystemPlatformSettingsAppServiceTest {

    @Test
    void platformSettingGroupsAndDefaultsShouldBeDatabaseOwned() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("../../sql/upgrade-platform-setting-definition-persistence-v1.sql"));
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemPlatformSettingsAppService.java"));

        assertThat(sql).contains("sys_platform_setting_definition", "BRANDING", "SMTP", "WATERMARK",
                "default_value", "reset_value", "config_name", "remark");
        assertThat(source).doesNotContain("BRANDING_CONFIG_KEYS", "SMTP_CONFIG_KEYS", "WATERMARK_CONFIG_KEYS",
                "WECHAT_OFFICIAL_CONFIG_KEYS", "FLOATING_WINDOW_CONFIG_KEYS", "AGREEMENT_CONFIG_KEYS", "CacheBuilder",
                "upsertBrandingConfig", "Whether SMTP is enabled",
                "Website name shown in branding and browser title");
    }

    @Test
    void platformConfigUpsertShouldBindConfigKeyScopeAndDeletedFlag() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/system/settings/infrastructure/JdbcSystemPlatformSettingsRepository.java"));
        String appSource = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemPlatformSettingsAppService.java"));

        assertThat(source)
                .contains("select config_name as configName, remark")
                .contains("and config_key = ?")
                .contains("and config_scope = 'PLATFORM'")
                .contains("and is_system = 0")
                .contains("and deleted = 0")
                .doesNotContain("updated_at = ?, deleted = 0");
        assertThat(appSource).contains("Platform config changed, please retry")
                .doesNotContain("MyBatisQueryOperations", "CacheBuilder");
    }

    @Test
    void brandingSettingsReloadsDatabaseAuthoritativeValuesOnEveryRead() {
        CurrentUser currentUser = currentUser();
        Map<String, String> configValues = Map.of(
                "branding.website-name", "Lumira",
                "branding.company-name", "Acme Corp",
                "branding.copyright-start-year", "2020"
        );
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(configValues);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.currentVersion("platform", "runtime-appearance")).thenReturn(1L);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OwnerRuntimeMetrics ownerRuntimeMetrics = new OwnerRuntimeMetrics(meterRegistry);

        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, ownerRuntimeMetrics, mock(SmtpMailService.class));

        SystemVO.BrandingSettingsVO first = service.getBrandingSettings(currentUser);
        SystemVO.BrandingSettingsVO second = service.getBrandingSettings(currentUser);

        assertThat(first.getWebsiteName()).isEqualTo("Lumira");
        assertThat(second.getWebsiteName()).isEqualTo("Lumira");
        assertThat(queryOperations.queryForListCount()).isEqualTo(2);
        verify(readModelVersionService, never()).currentVersion("platform", "runtime-appearance");
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_MISS)).isZero();
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_HIT)).isZero();
        assertThat(first.getCopyrightStartYear()).isEqualTo(2020);
    }

    @Test
    void automaticUpdateMaintenanceOnlyChangesThePublicEffectiveSwitch() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.ofEntries(
                Map.entry("branding.website-name", "Lumira"),
                Map.entry("branding.company-name", "Acme Corp"),
                Map.entry("branding.copyright-start-year", "2020"),
                Map.entry("branding.maintenance-mode-enabled", "false"),
                Map.entry("branding.maintenance-title", "Planned update"),
                Map.entry("branding.maintenance-message", "Please retry shortly"),
                Map.entry("branding.maintenance-end-at", "2026-08-08T01:00:00Z")
        ));
        PlatformUpdateMaintenanceService updateMaintenanceService = mock(PlatformUpdateMaintenanceService.class);
        when(updateMaintenanceService.isAutomaticMaintenanceActive()).thenReturn(true);
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                mock(ReadModelVersionService.class),
                null,
                mock(SmtpMailService.class)
        );
        service.setPlatformUpdateMaintenanceService(updateMaintenanceService);

        SystemVO.BrandingSettingsVO adminSettings = service.getBrandingSettings(currentUser());
        SystemVO.BrandingSettingsVO publicSettings = service.getPublicBrandingSettings();

        assertThat(adminSettings.getMaintenanceModeEnabled()).isFalse();
        assertThat(publicSettings.getMaintenanceModeEnabled()).isTrue();
        assertThat(publicSettings.getMaintenanceTitle()).isEqualTo(adminSettings.getMaintenanceTitle());
        assertThat(publicSettings.getMaintenanceMessage()).isEqualTo(adminSettings.getMaintenanceMessage());
        assertThat(publicSettings.getMaintenanceEndAt()).isEqualTo(adminSettings.getMaintenanceEndAt());
        assertThat(publicSettings.getWebsiteName()).isEqualTo(adminSettings.getWebsiteName());
    }

    @Test
    void administratorMaintenanceIntentSurvivesWhenAutomaticLeaseIsInactive() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.ofEntries(
                Map.entry("branding.website-name", "Lumira"),
                Map.entry("branding.company-name", "Acme Corp"),
                Map.entry("branding.copyright-start-year", "2020"),
                Map.entry("branding.maintenance-mode-enabled", "true")
        ));
        PlatformUpdateMaintenanceService updateMaintenanceService = mock(PlatformUpdateMaintenanceService.class);
        when(updateMaintenanceService.isAutomaticMaintenanceActive()).thenReturn(false);
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                mock(ReadModelVersionService.class),
                null,
                mock(SmtpMailService.class)
        );
        service.setPlatformUpdateMaintenanceService(updateMaintenanceService);

        assertThat(service.getBrandingSettings(currentUser()).getMaintenanceModeEnabled()).isTrue();
        assertThat(service.getPublicBrandingSettings().getMaintenanceModeEnabled()).isTrue();
    }

    @Test
    void brandingSettingsReloadsWhenRuntimeVersionBumps() throws Exception {
        CurrentUser currentUser = currentUser();
        Map<String, String> configValues = Map.of(
                "branding.website-name", "Lumira",
                "branding.company-name", "Acme Corp",
                "branding.copyright-start-year", "2020"
        );
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(configValues);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.currentVersion("platform", "runtime-appearance")).thenReturn(5L, 6L);

        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, mock(SmtpMailService.class));

        SystemVO.BrandingSettingsVO first = service.getBrandingSettings(currentUser);
        SystemVO.BrandingSettingsVO second = service.getBrandingSettings(currentUser);
        assertThat(second.getWebsiteName()).isEqualTo(first.getWebsiteName());
        assertThat(queryOperations.queryForListCount()).isEqualTo(2);

        SystemVO.BrandingSettingsVO third = service.getBrandingSettings(currentUser);

        assertThat(third.getWebsiteName()).isEqualTo("Lumira");
        assertThat(queryOperations.queryForListCount()).isEqualTo(3);
        verify(readModelVersionService, never()).currentVersion("platform", "runtime-appearance");
        assertThat(System.identityHashCode(first)).isNotEqualTo(System.identityHashCode(third));
    }

    @Test
    void brandingSettingsConcurrentReadsRemainDatabaseAuthoritative() throws Exception {
        CurrentUser currentUser = currentUser();
        Map<String, String> configValues = Map.of(
                "branding.website-name", "Lumira",
                "branding.company-name", "Acme Corp",
                "branding.copyright-start-year", "2020"
        );
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(configValues);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.currentVersion("platform", "runtime-appearance")).thenReturn(2L);

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
        assertThat(queryOperations.queryForListCount()).isEqualTo(threadCount);
        verify(readModelVersionService, never()).currentVersion("platform", "runtime-appearance");
    }

    @Test
    void smtpSettingsUpdatesInvalidateMailConfigCache() {
        CurrentUser currentUser = currentUser();
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

        verify(smtpMailService, times(2)).invalidate();
    }

    @Test
    void updateSmtpSettingsShouldRequireUpdatePermissionBeforeDatabaseWrite() {
        CurrentUser currentUser = currentUser("system:config:view");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "smtp.enabled", "true",
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.from", "noreply@example.com"
        ));
        SmtpMailService smtpMailService = mock(SmtpMailService.class);
        SystemPlatformSettingsAppService service = newService(queryOperations, mock(ReadModelVersionService.class), null, smtpMailService);

        SystemDTO.SmtpSettingsRequest request = new SystemDTO.SmtpSettingsRequest();
        request.setHost("smtp2.example.com");

        assertThatThrownBy(() -> service.updateSmtpSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(queryOperations.updateCount()).isZero();
        verify(smtpMailService, times(0)).invalidate();
    }

    @Test
    void updateBrandingSettingsShouldRejectWhenLiveSnapshotRevokesUpdatePermissionBeforeDatabaseWrite() {
        CurrentUser currentUser = currentUser("system:config:update");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:view")));
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                readModelVersionService,
                null,
                mock(SmtpMailService.class),
                permissionSnapshotService
        );

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(readModelVersionService, times(0)).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateBrandingSettingsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        FieldCryptoService fieldCryptoService = mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        SystemPlatformSettingsAppService service = new SystemPlatformSettingsAppService(
                repository(queryOperations),
                new RecordingAuditLog(),
                fieldCryptoService,
                readModelVersionService,
                null,
                mock(SmtpMailService.class),
                null,
                null,
                null
        );
        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser("system:config:update"), request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(readModelVersionService, times(0)).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateBrandingSettingsShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        CurrentUser currentUser = currentUser("system:config:update");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        FieldCryptoService fieldCryptoService = mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);
        SystemPlatformSettingsAppService service = new SystemPlatformSettingsAppService(
                repository(queryOperations),
                new RecordingAuditLog(),
                fieldCryptoService,
                readModelVersionService,
                null,
                mock(SmtpMailService.class),
                permissionSnapshotService,
                null,
                null
        );

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                });
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(readModelVersionService, times(0)).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateSmtpSettingsShouldRejectBlankUsernameBeforeDatabaseAccess() {
        CurrentUser currentUser = currentUser("*");
        currentUser.setUsername(" ");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "smtp.enabled", "true",
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.from", "noreply@example.com"
        ));
        SmtpMailService smtpMailService = mock(SmtpMailService.class);
        SystemPlatformSettingsAppService service = newService(queryOperations, mock(ReadModelVersionService.class), null, smtpMailService);

        SystemDTO.SmtpSettingsRequest request = new SystemDTO.SmtpSettingsRequest();
        request.setHost("smtp2.example.com");

        assertThatThrownBy(() -> service.updateSmtpSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(smtpMailService, times(0)).invalidate();
    }

    @Test
    void updateSmtpSettingsShouldRejectMissingSessionVersionBeforeDatabaseAccess() {
        CurrentUser currentUser = currentUser("*");
        currentUser.setSessionVersion(null);
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "smtp.enabled", "true",
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.from", "noreply@example.com"
        ));
        SmtpMailService smtpMailService = mock(SmtpMailService.class);
        SystemPlatformSettingsAppService service = newService(queryOperations, mock(ReadModelVersionService.class), null, smtpMailService);

        SystemDTO.SmtpSettingsRequest request = new SystemDTO.SmtpSettingsRequest();
        request.setHost("smtp2.example.com");

        assertThatThrownBy(() -> service.updateSmtpSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(smtpMailService, times(0)).invalidate();
    }

    @Test
    void updateBrandingSettingsShouldRejectDisabledTrustedOperatorBeforeDatabaseWrite() {
        CurrentUser currentUser = currentUser("system:config:update");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        queryOperations.withoutTrustedOperator();
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, mock(SmtpMailService.class));

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted operator identity is required");
                });
        assertThat(queryOperations.updateCount()).isZero();
        verify(readModelVersionService, never()).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateBrandingSettingsShouldRejectDisabledTrustedUserIdentityBeforeDatabaseWrite() {
        CurrentUser currentUser = currentUser("system:config:update");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "DISABLED"));
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                readModelVersionService,
                null,
                mock(SmtpMailService.class),
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingAuditLog()
        );

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(queryOperations.updateCount()).isZero();
        verify(permissionSnapshotService, times(0)).isTrustedActiveUser(org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(readModelVersionService, times(0)).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateBrandingSettingsShouldRejectBlankLiveUsernameBeforeDatabaseWrite() {
        CurrentUser currentUser = currentUser("system:config:update");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                readModelVersionService,
                null,
                mock(SmtpMailService.class),
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingAuditLog()
        );

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        assertThat(queryOperations.updateCount()).isZero();
        verify(permissionSnapshotService, times(0)).isTrustedActiveUser(org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(readModelVersionService, times(0)).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateBrandingSettingsShouldRejectRevokedSessionTicketBeforeDatabaseWrite() {
        CurrentUser currentUser = currentUser("system:config:update");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                readModelVersionService,
                null,
                mock(SmtpMailService.class),
                null,
                sessionAuthenticationService
        );

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(readModelVersionService, times(0)).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateSmtpSettingsShouldRejectNullRequestBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "smtp.enabled", "true",
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.from", "noreply@example.com"
        ));
        SmtpMailService smtpMailService = mock(SmtpMailService.class);
        SystemPlatformSettingsAppService service = newService(queryOperations, mock(ReadModelVersionService.class), null, smtpMailService);

        assertThatThrownBy(() -> service.updateSmtpSettings(currentUser(), null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(smtpMailService, times(0)).invalidate();
    }

    @Test
    void updateBrandingSettingsShouldRejectNullRequestBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, mock(SmtpMailService.class));

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser(), null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
        verify(readModelVersionService, times(0)).bump(anyString(), anyString(), anyString());
    }

    @Test
    void testSmtpSettingsShouldRejectNullRequestBeforeLoadingConfig() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "smtp.enabled", "true",
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.from", "noreply@example.com"
        ));
        SystemPlatformSettingsAppService service = newService(queryOperations, mock(ReadModelVersionService.class), null, mock(SmtpMailService.class));

        assertThatThrownBy(() -> service.testSmtpSettings(currentUser(), null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(queryOperations.queryForListCount()).isZero();
        assertThat(queryOperations.updateCount()).isZero();
    }

    @Test
    void updateBrandingSettingsPersistsEditableFooterFields() {
        CurrentUser currentUser = currentUser();
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "branding.website-name", "Lumira",
                "branding.company-name", "Acme Corp",
                "branding.copyright-start-year", "2020",
                "branding.footer-icp", "",
                "branding.footer-police-beian", "",
                "branding.footer-copyright", ""
        ));
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(readModelVersionService.currentVersion("platform", "runtime-appearance")).thenReturn(1L);
        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, mock(SmtpMailService.class));

        assertThat(service.getBrandingSettings(currentUser).getFooterCopyright()).contains("Acme Corp");

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);
        request.setGithubLinkEnabled(Boolean.FALSE);
        request.setGithubLinkUrl("https://github.com/example/lumira");
        request.setHelpLinkEnabled(Boolean.TRUE);
        request.setHelpLinkUrl("https://docs.example.com/help");
        request.setFooterIcp("ICP-123456");
        request.setFooterPoliceBeian("Police-654321");
        request.setFooterCopyright("Custom copyright text");

        SystemVO.BrandingSettingsVO updated = service.updateBrandingSettings(currentUser, request);

        assertThat(updated.getFooterIcp()).isEqualTo("ICP-123456");
        assertThat(updated.getFooterPoliceBeian()).isEqualTo("Police-654321");
        assertThat(updated.getFooterCopyright()).isEqualTo("Custom copyright text");
        assertThat(updated.getGithubLinkEnabled()).isFalse();
        assertThat(updated.getGithubLinkUrl()).isEqualTo("https://github.com/example/lumira");
        ArgumentCaptor<String> eventKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(readModelVersionService).bump(eq("platform"), eq("runtime-appearance"), eventKeyCaptor.capture());
        verify(readModelVersionService).bump(eq("platform"), eq("public-bootstrap"), eventKeyCaptor.capture());
        assertThat(eventKeyCaptor.getAllValues())
                .hasSize(2)
                .allMatch(eventKey -> eventKey.startsWith("branding-update:"))
                .allMatch(eventKey -> eventKey.equals(eventKeyCaptor.getAllValues().get(0)));
    }

    @Test
    void maintenanceCountdownEndAtIsOptionalAndValidatedAsIsoTimestamp() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                mock(ReadModelVersionService.class),
                null,
                mock(SmtpMailService.class)
        );
        String endAt = Instant.now().plusSeconds(3600).toString();
        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setMaintenanceModeEnabled(Boolean.TRUE);
        request.setMaintenanceEndAt(endAt);

        SystemVO.BrandingSettingsVO updated = service.updateBrandingSettings(currentUser(), request);

        assertThat(updated.getMaintenanceEndAt()).isEqualTo(endAt);

        request.setMaintenanceEndAt("not-a-timestamp");
        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateBrandingSettingsRejectsWhenConfigInsertMisses() {
        CurrentUser currentUser = currentUser();
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        queryOperations.updateResult = 0;
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        SystemPlatformSettingsAppService service = newService(queryOperations, readModelVersionService, null, mock(SmtpMailService.class));

        SystemDTO.BrandingSettingsRequest request = new SystemDTO.BrandingSettingsRequest();
        request.setWebsiteName("Lumira");
        request.setCompanyName("Acme Corp");
        request.setCopyrightStartYear(2020);
        request.setGithubLinkEnabled(Boolean.FALSE);
        request.setHelpLinkEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.updateBrandingSettings(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Platform config changed, please retry");
                });
        assertThat(queryOperations.updateCount()).isEqualTo(1);
        verify(readModelVersionService, never()).bump(anyString(), anyString(), anyString());
    }

    @Test
    void updateSmtpSettingsShouldLogRefreshedLiveUsername() {
        CurrentUser currentUser = currentUser("system:config:update");
        currentUser.setUsername("stale-admin");
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of(
                "smtp.enabled", "true",
                "smtp.host", "smtp.example.com",
                "smtp.port", "587",
                "smtp.from", "noreply@example.com"
        ));
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:update")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "  admin-live  ", "ENABLED"));
        RecordingAuditLog auditLog = new RecordingAuditLog();
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                readModelVersionService,
                null,
                mock(SmtpMailService.class),
                permissionSnapshotService,
                null,
                systemInternalApi,
                auditLog
        );
        service.setConfigVersioningService(mock(SystemConfigVersioningService.class));

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

        SystemVO.SmtpSettingsVO settings = service.updateSmtpSettings(currentUser, request);

        assertThat(settings.getHost()).isEqualTo("smtp2.example.com");
        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        assertThat(auditLog.username).isEqualTo("admin-live");
    }

    private static SystemPlatformSettingsAppService newService(
            RecordingQueryOperations queryOperations,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService
    ) {
        return newService(queryOperations, readModelVersionService, ownerRuntimeMetrics, smtpMailService, null);
    }

    private static SystemPlatformSettingsRepository repository(RecordingQueryOperations database) {
        return new JdbcSystemPlatformSettingsRepository(database) {
            @Override
            public Map<String, String> findEffectiveSettingValues(String groupCode) {
                database.queryForList("select config_key from sys_platform_setting_definition where group_code = ?", groupCode);
                Map<String, String> values = new LinkedHashMap<>(platformDefaults(groupCode));
                values.putAll(database.configValues);
                return values;
            }

            @Override
            public Map<String, String> findSettingDefaults(String groupCode) {
                return platformDefaults(groupCode);
            }

            @Override
            public Map<String, String> findSettingResetValues(String groupCode) {
                Map<String, String> values = new LinkedHashMap<>(platformDefaults(groupCode));
                if ("SMTP".equals(groupCode)) values.put("smtp.enabled", "false");
                return values;
            }
        };
    }

    private static Map<String, String> platformDefaults(String groupCode) {
        return switch (groupCode) {
            case "BRANDING" -> Map.ofEntries(
                    Map.entry("branding.website-name", "Lumira"), Map.entry("branding.website-favicon-url", ""),
                    Map.entry("branding.website-logo-url", ""), Map.entry("branding.login-background-url", ""),
                    Map.entry("branding.github-link-enabled", "true"), Map.entry("branding.github-link-url", ""),
                    Map.entry("branding.help-link-enabled", "true"), Map.entry("branding.help-link-url", ""),
                    Map.entry("branding.company-name", ""), Map.entry("branding.copyright-start-year", ""),
                    Map.entry("branding.footer-icp", ""), Map.entry("branding.footer-police-beian", ""),
                    Map.entry("branding.footer-copyright", ""),
                    Map.entry("branding.maintenance-mode-enabled", "false"),
                    Map.entry("branding.maintenance-title", "马上回来，精彩不掉线"),
                    Map.entry("branding.maintenance-message", "我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。"),
                    Map.entry("branding.maintenance-end-at", ""));
            case "AGREEMENT" -> Map.of("agreement.user-agreement-markdown", "", "agreement.privacy-agreement-markdown", "");
            case "SMTP" -> Map.ofEntries(
                    Map.entry("smtp.enabled", "true"), Map.entry("smtp.host", ""), Map.entry("smtp.port", "25"),
                    Map.entry("smtp.username", ""), Map.entry("smtp.password", ""), Map.entry("smtp.from", ""),
                    Map.entry("smtp.auth-enabled", "true"), Map.entry("smtp.starttls-enabled", "true"),
                    Map.entry("smtp.ssl-enabled", "false"), Map.entry("smtp.test-subject", "SMTP test email"),
                    Map.entry("smtp.test-content", "This is a test email sent from the system SMTP settings."),
                    Map.entry("smtp.connection-timeout-ms", "5000"), Map.entry("smtp.read-timeout-ms", "5000"),
                    Map.entry("smtp.write-timeout-ms", "5000"));
            case "WECHAT_OFFICIAL" -> Map.of(
                    "notification.wechat-official.enabled", "false", "notification.wechat-official.app-id", "",
                    "notification.wechat-official.app-secret", "", "notification.wechat-official.template-id", "",
                    "notification.wechat-official.detail-url", "");
            case "WATERMARK" -> Map.ofEntries(
                    Map.entry("watermark.enabled", "false"), Map.entry("watermark.mode", "TEXT"),
                    Map.entry("watermark.text-lines", ""), Map.entry("watermark.image-url", ""),
                    Map.entry("watermark.font-color", "rgba(0,0,0,0.15)"), Map.entry("watermark.font-size", "14"),
                    Map.entry("watermark.font-weight", "normal"), Map.entry("watermark.rotate", "-22"),
                    Map.entry("watermark.gap-x", "100"), Map.entry("watermark.gap-y", "100"),
                    Map.entry("watermark.offset-x", "0"), Map.entry("watermark.offset-y", "0"),
                    Map.entry("watermark.z-index", "9"), Map.entry("watermark.opacity", "0.15"));
            case "FLOATING_WINDOW" -> Map.of(
                    "floating-window.api-docs-qr-enabled", "false", "floating-window.api-docs-qr-title", "",
                    "floating-window.api-docs-qr-image-url", "");
            default -> Map.of();
        };
    }

    private static SystemPlatformSettingsAppService newService(
            RecordingQueryOperations queryOperations,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        return newService(queryOperations, readModelVersionService, ownerRuntimeMetrics, smtpMailService, permissionSnapshotService, null);
    }

    private static SystemPlatformSettingsAppService newService(
            RecordingQueryOperations queryOperations,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return newService(
                queryOperations,
                readModelVersionService,
                ownerRuntimeMetrics,
                smtpMailService,
                permissionSnapshotService,
                sessionAuthenticationService,
                null,
                new RecordingAuditLog()
        );
    }

    private static SystemPlatformSettingsAppService newService(
            RecordingQueryOperations queryOperations,
            ReadModelVersionService readModelVersionService,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SmtpMailService smtpMailService,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            SystemInternalApi systemInternalApi,
            RecordingAuditLog auditLog
    ) {
        FieldCryptoService fieldCryptoService = mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        if (sessionAuthenticationService == null && systemInternalApi == null) {
            if (permissionSnapshotService == null) {
                        return new SystemPlatformSettingsAppService(
                                repository(queryOperations),
                        auditLog,
                        fieldCryptoService,
                        readModelVersionService,
                        ownerRuntimeMetrics,
                        smtpMailService
                );
            }
                        return new SystemPlatformSettingsAppService(
                                repository(queryOperations),
                    auditLog,
                    fieldCryptoService,
                    readModelVersionService,
                    ownerRuntimeMetrics,
                    smtpMailService,
                    permissionSnapshotService
            );
        }
                        return new SystemPlatformSettingsAppService(
                                repository(queryOperations),
                auditLog,
                fieldCryptoService,
                readModelVersionService,
                ownerRuntimeMetrics,
                smtpMailService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService
        );
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                username,
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : List.of(value).iterator();
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }
        };
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:config:update")));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "ENABLED"));
        SystemPlatformSettingsAppService service = newService(
                queryOperations,
                mock(ReadModelVersionService.class),
                null,
                mock(SmtpMailService.class),
                permissionSnapshotService,
                null,
                systemInternalApi,
                new RecordingAuditLog()
        );
        CurrentUser currentUser = currentUser("system:config:update");
        currentUser.setSimulatedRoleId(0L);
        Method method = SystemPlatformSettingsAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, org.mockito.Mockito.never())
                .loadGrantedRoleSnapshot(org.mockito.ArgumentMatchers.anyLong(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    private static CurrentUser currentUser() {
        return currentUser("*");
    }

    private static CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static double counterCount(SimpleMeterRegistry meterRegistry, String counterName) {
        Counter counter = meterRegistry.find(counterName).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static final class RecordingAuditLog extends OperationAuditService {
        private String username;

        private RecordingAuditLog() {
            super(null, objectProvider(null));
        }

        @Override
        public void log(
                Long userId,
                String userUuid,
                String username,
                String moduleName,
                String actionName,
                String operationType,
                String resultStatus,
                String detailMessage
        ) {
            this.username = username;
        }
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final Map<String, String> configValues;
        private final Map<String, Long> configIds = new LinkedHashMap<>();
        private final AtomicInteger queryForListCount = new AtomicInteger(0);
        private final AtomicInteger updateCount = new AtomicInteger(0);
        private int updateResult = 1;
        private String operatorUuid = "user-uuid-2001";

        private RecordingQueryOperations(Map<String, String> configValues) {
            this.configValues = new LinkedHashMap<>(configValues);
            long nextId = 1L;
            for (String configKey : this.configValues.keySet()) {
                configIds.put(configKey, nextId++);
            }
        }

        private void withoutTrustedOperator() {
            operatorUuid = null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryForListCount.incrementAndGet();
            if (sql.contains("select config_name as configName") && args.length == 1) {
                return List.of(Map.of("configName", Objects.toString(args[0], "")));
            }
            return rowsByArgs(args);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == String.class && sql.contains("select uuid from sys_user")) {
                if (operatorUuid == null) {
                    throw new EmptyResultDataAccessException(1);
                }
                return requiredType.cast(operatorUuid);
            }
            if (requiredType == Long.class && args.length > 0) {
                Long configId = configIds.get(Objects.toString(args[0], ""));
                if (configId != null) {
                    return requiredType.cast(configId);
                }
            }
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount.incrementAndGet();
            if (sql.contains("insert into sys_config") && args.length >= 3 && args[0] instanceof String configKey) {
                configValues.put(configKey, Objects.toString(args[2], ""));
                configIds.putIfAbsent(configKey, (long) configIds.size() + 1L);
            } else if (sql.contains("update sys_config") && args.length >= 7) {
                configValues.entrySet().stream()
                        .filter(entry -> Objects.equals(configIds.get(entry.getKey()), args[6]))
                        .findFirst()
                        .ifPresent(entry -> entry.setValue(Objects.toString(args[1], "")));
            }
            return updateResult;
        }

        int queryForListCount() {
            return queryForListCount.get();
        }

        int updateCount() {
            return updateCount.get();
        }

        private List<Map<String, Object>> rowsByArgs(Object... args) {
            if (args.length == 0) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int index = 0; index < args.length; index++) {
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
