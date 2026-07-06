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
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;

import java.lang.reflect.Field;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyString;
class SystemPlatformSettingsAppServiceTest {

    @Test
    void platformConfigUpsertShouldBindConfigKeyScopeAndDeletedFlag() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/system/app/SystemPlatformSettingsAppService.java"));

        assertThat(source)
                .contains("and config_key = ?")
                .contains("and config_scope = 'PLATFORM'")
                .contains("and is_system = 0")
                .contains("and deleted = 0")
                .contains("Platform config changed, please retry")
                .doesNotContain("updated_at = ?, deleted = 0");
    }

    @Test
    void brandingSettingsSingleFlightCachesByRuntimeVersion() {
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
        assertThat(queryOperations.queryForListCount()).isEqualTo(1);
        verify(readModelVersionService, times(1)).currentVersion("platform", "runtime-appearance");
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_MISS)).isEqualTo(1.0);
        assertThat(counterCount(meterRegistry, OwnerRuntimeMetrics.PLATFORM_CONFIG_CACHE_HIT)).isEqualTo(1.0);
        assertThat(first.getCopyrightStartYear()).isEqualTo(2020);
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
        assertThat(queryOperations.queryForListCount()).isEqualTo(1);

        clearRuntimeVersionCaches(service);
        SystemVO.BrandingSettingsVO third = service.getBrandingSettings(currentUser);

        assertThat(third.getWebsiteName()).isEqualTo("Lumira");
        assertThat(queryOperations.queryForListCount()).isEqualTo(2);
        verify(readModelVersionService, times(2)).currentVersion("platform", "runtime-appearance");
        assertThat(System.identityHashCode(first)).isNotEqualTo(System.identityHashCode(third));
    }

    @Test
    void brandingSettingsSingleFlightBuildsOnceUnderConcurrentMisses() throws Exception {
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
        assertThat(queryOperations.queryForListCount()).isEqualTo(1);
        verify(readModelVersionService, times(1)).currentVersion("platform", "runtime-appearance");
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
        verify(readModelVersionService, times(0)).bump("platform", "runtime-appearance", "branding-update");
        verify(readModelVersionService, times(0)).bump("platform", "public-bootstrap", "branding-update");
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
        verify(readModelVersionService).bump("platform", "runtime-appearance", "branding-update");
        verify(readModelVersionService).bump("platform", "public-bootstrap", "branding-update");
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
        verify(readModelVersionService, times(0)).bump("platform", "runtime-appearance", "branding-update");
        verify(readModelVersionService, times(0)).bump("platform", "public-bootstrap", "branding-update");
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
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "admin-live", "ENABLED"));
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
        return new SystemPlatformSettingsAppService(
                queryOperations,
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

    private static void clearRuntimeVersionCaches(SystemPlatformSettingsAppService service) throws Exception {
        Field runtimeVersionCacheField = SystemPlatformSettingsAppService.class.getDeclaredField("runtimeAppearanceVersionCache");
        runtimeVersionCacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<String, Long> runtimeVersionCache =
                (com.google.common.cache.Cache<String, Long>) runtimeVersionCacheField.get(service);
        runtimeVersionCache.invalidateAll();

        Field inFlightField = SystemPlatformSettingsAppService.class.getDeclaredField("runtimeAppearanceVersionLoadInFlight");
        inFlightField.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.google.common.cache.Cache<String, CompletableFuture<Long>> inFlight =
                (com.google.common.cache.Cache<String, CompletableFuture<Long>>) inFlightField.get(service);
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
