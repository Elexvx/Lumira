package com.lumira.saas.modules.system.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SystemVerificationSettingsAppServiceTest {

    @Test
    void verificationConfigWritesShouldPersistTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationSettingsAppService.java"));

        assertThat(source).contains("created_by, created_by_uuid, updated_by, updated_by_uuid");
        assertThat(source).contains("updated_by = ?, updated_by_uuid = ?");
        assertThat(source).contains("operatorUuid = currentUser.getUserUuid()");
        assertThat(source).contains("and config_key = ?");
        assertThat(source).contains("and config_scope = 'PLATFORM'");
        assertThat(source).contains("and is_system = 0");
        assertThat(source).contains("and deleted = 0");
        assertThat(source).contains("Verification config changed, please retry");
        assertThat(source).doesNotContain("updated_at = ?, deleted = 0");
    }

    @Test
    void loadLoginCapabilitiesShouldReuseCachedConfigSnapshots() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured()).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        FieldCryptoService fieldCryptoService = cryptoService();

        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                null
        );

        SystemVO.LoginCapabilitiesVO first = service.loadLoginCapabilities();
        SystemVO.LoginCapabilitiesVO second = service.loadLoginCapabilities();

        assertThat(first.getPasswordLoginAvailable()).isTrue();
        assertThat(second.getPasswordLoginAvailable()).isTrue();
        assertThat(queryOperations.queryForListCount.get()).isEqualTo(1);
        assertThat(queryOperations.updateCount.get()).isZero();
    }

    @Test
    void loadLoginCapabilitiesFreshShouldBypassCachedConfigSnapshots() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured()).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        when(wechatLoginSettingsService.loadSettingsFresh()).thenReturn(wechatSettings(false));
        FieldCryptoService fieldCryptoService = cryptoService();

        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                null
        );

        SystemVO.LoginCapabilitiesVO before = service.loadLoginCapabilities();
        queryOperations.putValue("verification.password-login.enabled", "false");
        SystemVO.LoginCapabilitiesVO after = service.loadLoginCapabilitiesFresh();

        assertThat(before.getPasswordLoginAvailable()).isTrue();
        assertThat(after.getPasswordLoginAvailable()).isFalse();
        assertThat(queryOperations.queryForListCount.get()).isEqualTo(2);
    }

    @Test
    void loadLoginCapabilitiesShouldRequireWechatEnabledAndConfigured() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured()).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false, true));
        FieldCryptoService fieldCryptoService = cryptoService();

        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                null
        );

        SystemVO.LoginCapabilitiesVO capabilities = service.loadLoginCapabilities();

        assertThat(capabilities.getWechatLoginAvailable()).isFalse();
    }


    @Test
    void updateVerificationSettingsShouldInvalidateCachedConfigSnapshots() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured()).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        FieldCryptoService fieldCryptoService = cryptoService();
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);

        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                readModelVersionService
        );

        SystemVO.LoginCapabilitiesVO before = service.loadLoginCapabilities();
        assertThat(before.getPasswordLoginAvailable()).isTrue();

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.TRUE);
        request.setEmailLoginEnabled(Boolean.FALSE);
        request.setPasswordLoginEnabled(Boolean.FALSE);
        request.setLoginModeOrder(List.of("password", "sms"));

        SystemVO.VerificationSettingsVO updated = service.updateVerificationSettings(currentUser(), request);

        assertThat(updated.getPasswordLoginEnabled()).isFalse();
        assertThat(queryOperations.updateCount.get()).isGreaterThan(0);
        verify(readModelVersionService).bump("platform", "public-bootstrap", "verification-settings-update");
    }

    @Test
    void updateVerificationSettingsShouldRejectWhenConfigInsertMisses() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(Map.of());
        queryOperations.updateResult = 0;
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured()).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                cryptoService(),
                readModelVersionService
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.TRUE);
        request.setEmailLoginEnabled(Boolean.FALSE);
        request.setPasswordLoginEnabled(Boolean.TRUE);
        request.setLoginModeOrder(List.of("password"));

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Verification config changed, please retry");
                });
        assertThat(queryOperations.updateCount.get()).isEqualTo(1);
        Mockito.verify(readModelVersionService, Mockito.never()).bump("platform", "public-bootstrap", "verification-settings-update");
    }

    @Test
    void updateVerificationSettingsShouldRequireManagePermissionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser("system:verification:view"), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void resetSmsSettingsShouldRejectWhenLiveSnapshotRevokesManagePermissionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        PermissionSnapshotService permissionSnapshotService = Mockito.mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(9L, "user-uuid-9")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(9L, "user-uuid-9"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:verification:view")));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                permissionSnapshotService
        );

        assertThatThrownBy(() -> service.resetSmsSettings(currentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldRejectDisabledTrustedUserIdentityBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        PermissionSnapshotService permissionSnapshotService = Mockito.mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(9L))
                .thenReturn(userSnapshot(9L, "user-uuid-9", "admin-live", "DISABLED"));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldRejectBlankLiveUsernameBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        PermissionSnapshotService permissionSnapshotService = Mockito.mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(9L))
                .thenReturn(userSnapshot(9L, "user-uuid-9", " ", "ENABLED"));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldRejectRevokedSessionTicketBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SessionAuthenticationService sessionAuthenticationService = Mockito.mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 9L, "user-uuid-9", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Login required"));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                Mockito.mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                null,
                null,
                null
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldRejectWhenTrustedPermissionSnapshotIsUnavailableBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        PermissionSnapshotService permissionSnapshotService = Mockito.mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(9L, "user-uuid-9")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(9L, "user-uuid-9")).thenReturn(null);
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                permissionSnapshotService,
                null,
                null
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user permission snapshot is unavailable");
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldRejectBlankUsernameBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);
        CurrentUser currentUser = currentUser("*");
        currentUser.setUsername(" ");

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateVerificationSettingsShouldRejectMissingSessionVersionBeforeDatabaseWrite() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );

        SystemDTO.VerificationSettingsRequest request = new SystemDTO.VerificationSettingsRequest();
        request.setEnabled(Boolean.FALSE);
        CurrentUser currentUser = currentUser("*");
        currentUser.setSessionVersion(null);

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateSmsSettingsShouldRejectMissingUserUuidBeforeLoadingCurrentSettings() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );
        SystemDTO.SmsVerificationSettingsRequest request = new SystemDTO.SmsVerificationSettingsRequest();
        request.setEnabled(Boolean.TRUE);
        CurrentUser currentUser = currentUser("*");
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> service.updateSmsSettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updatePasskeySettingsShouldRejectMissingPermissionsVersionBeforeLoadingCurrentSettings() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );
        SystemDTO.PasskeySettingsRequest request = new SystemDTO.PasskeySettingsRequest();
        request.setEnabled(Boolean.TRUE);
        CurrentUser currentUser = currentUser("*");
        currentUser.setPermissionsVersion(" ");

        assertThatThrownBy(() -> service.updatePasskeySettings(currentUser, request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateWechatSettingsShouldDelegateWithRefreshedLiveUsername() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        when(wechatLoginSettingsService.updateSettings(any(CurrentUser.class), any(SystemDTO.WechatLoginSettingsRequest.class)))
                .thenReturn(new SystemVO.WechatLoginSettingsVO());
        PermissionSnapshotService permissionSnapshotService = Mockito.mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(9L, "user-uuid-9")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(9L, "user-uuid-9"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:verification:manage")));
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(9L))
                .thenReturn(userSnapshot(9L, "user-uuid-9", "  admin-live  ", "ENABLED"));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser("*");
        currentUser.setUsername("admin-stale");
        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.TRUE);

        service.updateWechatSettings(currentUser, request);

        assertThat(currentUser.getUsername()).isEqualTo("admin-live");
        verify(wechatLoginSettingsService).updateSettings(currentUser, request);
    }

    @Test
    void updateWechatSettingsShouldRejectPermissionRevokedBetweenOuterAndInnerTrustedRefresh() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        SessionAuthenticationService sessionAuthenticationService = Mockito.mock(SessionAuthenticationService.class);
        CurrentUser managerAtOuterCheck = currentUser("system:verification:manage");
        managerAtOuterCheck.setUsername("admin-outer");
        managerAtOuterCheck.setPermissionsVersion("permissions-2");
        CurrentUser revokedAtInnerCheck = currentUser("system:verification:view");
        revokedAtInnerCheck.setUsername("admin-inner");
        revokedAtInnerCheck.setPermissionsVersion("permissions-3");
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 9L, "user-uuid-9", null, 1, "permissions-1"))
                .thenReturn(new SessionAuthenticationService.AuthenticatedAccess(managerAtOuterCheck, null, false));
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 9L, "user-uuid-9", null, 1, "permissions-2"))
                .thenReturn(new SessionAuthenticationService.AuthenticatedAccess(revokedAtInnerCheck, null, false));
        WechatLoginSettingsService wechatLoginSettingsService = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService,
                sessionAuthenticationService
        );
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                Mockito.mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );
        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.updateWechatSettings(currentUser("system:verification:manage"), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getMessage()).contains("Missing permission: system:verification:manage");
                });

        assertThat(queryOperations.updateCount.get()).isZero();
        verify(mapper, never()).listEffectiveValues(eq("PLATFORM"), any());
        verify(mapper, never()).upsertPlatformConfig(any());
        verify(readModelVersionService, never()).bump(any(), any(), any());
    }

    @Test
    void updateVerificationSettingsShouldRejectNullRequestBeforeDatabaseAccess() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );

        assertThatThrownBy(() -> service.updateVerificationSettings(currentUser(), null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updatePasskeySettingsShouldRejectNullRequestBeforeLoadingCurrentSettings() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );

        assertThatThrownBy(() -> service.updatePasskeySettings(currentUser(), null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
    }

    @Test
    void updateWechatSettingsShouldRejectNullRequestBeforeDelegating() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                wechatLoginSettingsService,
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class)
        );

        assertThatThrownBy(() -> service.updateWechatSettings(currentUser(), null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(queryOperations.updateCount.get()).isZero();
        assertThat(queryOperations.queryForListCount.get()).isZero();
        verify(wechatLoginSettingsService, Mockito.never()).updateSettings(Mockito.any(CurrentUser.class), Mockito.any());
    }

    @Test
    void loadLoginCapabilitiesReloadsWhenPublicBootstrapVersionChanges() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        SmtpMailService smtpMailService = Mockito.mock(SmtpMailService.class);
        when(smtpMailService.isConfigured()).thenReturn(false);
        WechatLoginSettingsService wechatLoginSettingsService = Mockito.mock(WechatLoginSettingsService.class);
        when(wechatLoginSettingsService.loadSettings()).thenReturn(wechatSettings(false));
        FieldCryptoService fieldCryptoService = cryptoService();
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        when(readModelVersionService.currentVersion("platform", "public-bootstrap"))
                .thenReturn(11L, 12L);

        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                smtpMailService,
                wechatLoginSettingsService,
                fieldCryptoService,
                readModelVersionService
        );

        SystemVO.LoginCapabilitiesVO before = service.loadLoginCapabilities();
        queryOperations.putValue("verification.password-login.enabled", "false");
        Thread.sleep(2100L);
        SystemVO.LoginCapabilitiesVO after = service.loadLoginCapabilities();

        assertThat(before.getPasswordLoginAvailable()).isTrue();
        assertThat(after.getPasswordLoginAvailable()).isFalse();
        assertThat(queryOperations.queryForListCount.get()).isEqualTo(2);
        verify(readModelVersionService, Mockito.times(2)).currentVersion("platform", "public-bootstrap");
    }

    private static FieldCryptoService cryptoService() {
        FieldCryptoService fieldCryptoService = Mockito.mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return fieldCryptoService;
    }

    private static WechatLoginSettingsService.WechatLoginSettingsRecord wechatSettings(boolean configured) {
        return wechatSettings(configured, configured);
    }

    private static WechatLoginSettingsService.WechatLoginSettingsRecord wechatSettings(boolean enabled, boolean configured) {
        return new WechatLoginSettingsService.WechatLoginSettingsRecord(
                enabled,
                configured ? "appid" : "",
                configured ? "secret" : "",
                configured ? "https://example.com/callback" : "",
                15,
                configured
        );
    }

    private CurrentUser currentUser() {
        return currentUser("*");
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(defaultConfigValues());
        PermissionSnapshotService permissionSnapshotService = Mockito.mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(9L, "user-uuid-9")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(9L, "user-uuid-9"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:verification:manage")));
        SystemInternalApi systemInternalApi = Mockito.mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(9L))
                .thenReturn(userSnapshot(9L, "user-uuid-9", "admin-live", "ENABLED"));
        SystemVerificationSettingsAppService service = new SystemVerificationSettingsAppService(
                queryOperations,
                new SystemVerificationProperties(),
                Mockito.mock(SmtpMailService.class),
                Mockito.mock(WechatLoginSettingsService.class),
                cryptoService(),
                Mockito.mock(ReadModelVersionService.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser("*");
        currentUser.setSimulatedRoleId(0L);
        Method method = SystemVerificationSettingsAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);

        method.invoke(service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        verify(permissionSnapshotService).loadSnapshot(9L, "user-uuid-9");
        verify(permissionSnapshotService, never())
                .loadGrantedRoleSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    private CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(9L);
        currentUser.setUserUuid("user-uuid-9");
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
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

    private static Map<String, String> defaultConfigValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("verification.password-login.enabled", "true");
        values.put("verification.email-login.enabled", "false");
        values.put("verification.totp.enabled", "true");
        values.put("verification.login-mode.order", "password,sms,email,wechat,passkey");
        values.put("verification.sms.enabled", "false");
        values.put("verification.sms.provider", "aliyun");
        values.put("verification.sms.sign-name", "");
        values.put("verification.sms.template-code", "");
        values.put("verification.sms.access-key-id", "");
        values.put("verification.sms.access-key-secret", "");
        values.put("verification.sms.endpoint", "");
        values.put("verification.sms.region", "");
        values.put("verification.passkey.enabled", "false");
        values.put("verification.passkey.passwordless-enabled", "false");
        values.put("verification.passkey.self-binding-enabled", "true");
        values.put("verification.passkey.rp-id", "");
        values.put("verification.passkey.rp-name", "");
        values.put("verification.passkey.allowed-origins", "");
        values.put("verification.passkey.challenge-ttl-seconds", "120");
        return values;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final Map<String, String> values;
        private final AtomicInteger queryForListCount = new AtomicInteger();
        private final AtomicInteger updateCount = new AtomicInteger();
        private int updateResult = 1;

        private RecordingQueryOperations(Map<String, String> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryForListCount.incrementAndGet();
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object arg : args) {
                if (!(arg instanceof String key) || !key.contains(".")) {
                    continue;
                }
                if (!values.containsKey(key)) {
                    continue;
                }
                rows.add(Map.of(
                        "configKey", key,
                        "configValue", values.get(key)
                ));
            }
            return rows;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount.incrementAndGet();
            if (args != null && args.length >= 3 && args[0] instanceof String key && args[2] != null) {
                values.put(key, String.valueOf(args[2]));
            }
            return updateResult;
        }

        private void putValue(String key, String value) {
            values.put(key, value);
        }
    }
}
