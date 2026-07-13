package com.lumira.saas.modules.system.verification;

import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatLoginSettingsServiceTest {

    @Test
    void loadSettingsShouldReuseCachedSnapshots() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "true"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", "secret-1"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "15")
                ));

        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                null
        );

        WechatLoginSettingsService.WechatLoginSettingsRecord first = service.loadSettings();
        WechatLoginSettingsService.WechatLoginSettingsRecord second = service.loadSettings();

        assertThat(first.enabled()).isTrue();
        assertThat(second.configured()).isTrue();
        assertThat(second.available()).isTrue();
        verify(mapper, times(1)).listEffectiveValues(eq("PLATFORM"), any());
    }

    @Test
    void availableShouldRequireEnabledAndConfiguredSettings() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "false"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", "secret-1"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "15")
                ));

        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                null
        );

        WechatLoginSettingsService.WechatLoginSettingsRecord settings = service.loadSettings();

        assertThat(settings.configured()).isTrue();
        assertThat(settings.available()).isFalse();
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void loadSettingsShouldTreatUndecryptableSecretAsBlank() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "true"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", "broken-secret"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "15")
                ));
        FieldCryptoService fieldCryptoService = Mockito.mock(FieldCryptoService.class);
        when(fieldCryptoService.decrypt("broken-secret")).thenThrow(new IllegalStateException("字段解密失败"));

        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                fieldCryptoService,
                null
        );

        WechatLoginSettingsService.WechatLoginSettingsRecord settings = service.loadSettings();

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.appSecret()).isBlank();
        assertThat(settings.configured()).isFalse();
        assertThat(settings.available()).isFalse();
    }

    @Test
    void updateSettingsShouldInvalidateCachedSnapshot() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "true"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", "secret-1"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "15")
                ))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "false"),
                        config("verification.wechat-login.app-id", "appid-2"),
                        config("verification.wechat-login.app-secret", "secret-2"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/new-callback"),
                        config("verification.wechat-login.state-expire-minutes", "20")
                ));

        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService
        );
        WechatLoginSettingsService.WechatLoginSettingsRecord before = service.loadSettings();
        assertThat(before.appId()).isEqualTo("appid-1");

        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.FALSE);
        request.setAppId("appid-2");
        request.setAppSecret("secret-2");
        request.setRedirectUri("https://example.com/new-callback");
        request.setStateExpireMinutes(20);

        SystemVO.WechatLoginSettingsVO updated = service.updateSettings(trustedOperator(9L), request);

        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getAppId()).isEqualTo("appid-2");
        verify(mapper, times(2)).listEffectiveValues(eq("PLATFORM"), any());
        ArgumentCaptor<SysConfigEntity> captor = ArgumentCaptor.forClass(SysConfigEntity.class);
        verify(mapper, times(5)).upsertPlatformConfig(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(entity -> {
            assertThat(entity.getCreatedBy()).isEqualTo(9L);
            assertThat(entity.getCreatedByUuid()).isEqualTo("operator-uuid-9");
            assertThat(entity.getUpdatedBy()).isEqualTo(9L);
            assertThat(entity.getUpdatedByUuid()).isEqualTo("operator-uuid-9");
        });
        verify(readModelVersionService).bump("platform", "public-bootstrap", "wechat-settings-update");
    }

    @Test
    void updateSettingsShouldRejectEnablingIncompleteConfiguration() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "false"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", ""),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "10")
                ));

        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService
        );
        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.TRUE);

        assertThatThrownBy(() -> service.updateSettings(trustedOperator(9L), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(exception.getMessage()).contains("AppSecret");
                });
        verify(mapper, never()).upsertPlatformConfig(any());
        verify(readModelVersionService, never()).bump(any(), any(), any());
    }

    @Test
    void updateSettingsShouldRejectNullRequestBeforeLoadingSettings() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService
        );

        assertThatThrownBy(() -> service.updateSettings(trustedOperator(9L), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(mapper, never()).listEffectiveValues(eq("PLATFORM"), any());
        verify(mapper, never()).upsertPlatformConfig(any());
        verify(readModelVersionService, never()).bump(any(), any(), any());
    }

    @Test
    void updateSettingsShouldRejectRevokedSessionTicketBeforeLoadingSettings() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        SessionAuthenticationService sessionAuthenticationService = Mockito.mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-9", 9L, "operator-uuid-9", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService,
                sessionAuthenticationService
        );
        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateSettings(trustedOperator(9L), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(mapper, never()).listEffectiveValues(eq("PLATFORM"), any());
        verify(mapper, never()).upsertPlatformConfig(any());
        verify(readModelVersionService, never()).bump(any(), any(), any());
    }

    @Test
    void updateSettingsShouldRejectOperatorWithoutManagePermissionAfterTrustedRefresh() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        SessionAuthenticationService sessionAuthenticationService = Mockito.mock(SessionAuthenticationService.class);
        CurrentUser refreshedOperator = new CurrentUser(
                9L,
                "operator-live",
                "session-9",
                1,
                true,
                Set.of("system:verification:view"),
                Set.of(),
                null,
                Set.of(),
                Set.of(),
                List.of()
        );
        refreshedOperator.setUserUuid("operator-uuid-9");
        refreshedOperator.setPermissionsVersion("permissions-2");
        when(sessionAuthenticationService.authenticateSessionTicket("session-9", 9L, "operator-uuid-9", null, 1, "permissions-1"))
                .thenReturn(new SessionAuthenticationService.AuthenticatedAccess(refreshedOperator, null, false));
        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService,
                sessionAuthenticationService
        );
        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateSettings(trustedOperator(9L), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(exception.getMessage()).contains("Missing permission: system:verification:manage");
                });

        verify(mapper, never()).listEffectiveValues(eq("PLATFORM"), any());
        verify(mapper, never()).upsertPlatformConfig(any());
        verify(readModelVersionService, never()).bump(any(), any(), any());
    }

    @Test
    void requireTrustedOperatorShouldNormalizeInvalidSimulatedRoleIdBeforeSessionRefresh() throws Exception {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        SessionAuthenticationService sessionAuthenticationService = Mockito.mock(SessionAuthenticationService.class);
        CurrentUser refreshedOperator = trustedOperator(9L);
        refreshedOperator.setPermissionsVersion("permissions-2");
        when(sessionAuthenticationService.authenticateSessionTicket("session-9", 9L, "operator-uuid-9", null, 1, "permissions-1"))
                .thenReturn(new SessionAuthenticationService.AuthenticatedAccess(refreshedOperator, null, false));
        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService,
                sessionAuthenticationService
        );
        CurrentUser operator = trustedOperator(9L);
        operator.setSimulatedRoleId(0L);
        Method method = WechatLoginSettingsService.class.getDeclaredMethod("requireTrustedOperator", CurrentUser.class);
        method.setAccessible(true);

        CurrentUser result = (CurrentUser) method.invoke(service, operator);

        assertThat(result.getSimulatedRoleId()).isNull();
        assertThat(operator.getSimulatedRoleId()).isNull();
        verify(sessionAuthenticationService).authenticateSessionTicket("session-9", 9L, "operator-uuid-9", null, 1, "permissions-1");
    }

    @Test
    void updateSettingsShouldRejectTrustedOperatorWhenResolverIsUnavailableInStrictMode() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService,
                null
        );
        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.FALSE);

        assertThatThrownBy(() -> service.updateSettings(trustedOperator(9L), request))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception).hasMessageContaining("Trusted user resolver is unavailable");
                });

        verify(mapper, never()).listEffectiveValues(eq("PLATFORM"), any());
        verify(mapper, never()).upsertPlatformConfig(any());
        verify(readModelVersionService, never()).bump(any(), any(), any());
    }

    @Test
    void settingsMutationShouldNotExposeNumericOnlyOperatorOperations() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);

        assertThat(Arrays.stream(WechatLoginSettingsService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(WechatLoginSettingsService.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("resetSettings(java.lang.Long")
                        || signature.contains("updateSettings(java.lang.Long"))
                .toList())
                .isEmpty();

        verify(mapper, never()).upsertPlatformConfig(any());
        verify(readModelVersionService, never()).bump(any(), any(), any());
    }

    @Test
    void loadSettingsReloadsWhenPublicBootstrapVersionChanges() throws Exception {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = Mockito.mock(ReadModelVersionService.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "true"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", "secret-1"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "15")
                ))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "false"),
                        config("verification.wechat-login.app-id", "appid-2"),
                        config("verification.wechat-login.app-secret", "secret-2"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/new-callback"),
                        config("verification.wechat-login.state-expire-minutes", "20")
                ));
        when(readModelVersionService.currentVersion("platform", "public-bootstrap"))
                .thenReturn(11L, 12L);

        WechatLoginSettingsService service = new WechatLoginSettingsService(
                mapper,
                new WechatLoginProperties(),
                cryptoService(),
                readModelVersionService
        );

        WechatLoginSettingsService.WechatLoginSettingsRecord before = service.loadSettings();
        Thread.sleep(2100L);
        WechatLoginSettingsService.WechatLoginSettingsRecord after = service.loadSettings();

        assertThat(before.appId()).isEqualTo("appid-1");
        assertThat(after.appId()).isEqualTo("appid-2");
        verify(mapper, times(2)).listEffectiveValues(eq("PLATFORM"), any());
        verify(readModelVersionService, times(2)).currentVersion("platform", "public-bootstrap");
    }

    private static FieldCryptoService cryptoService() {
        FieldCryptoService fieldCryptoService = Mockito.mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return fieldCryptoService;
    }

    private static SysConfigEntity config(String key, String value) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        return entity;
    }

    private static CurrentUser trustedOperator(Long userId) {
        CurrentUser currentUser = new CurrentUser(
                userId,
                "operator",
                "session-" + userId,
                1,
                true,
                Set.of("system:verification:manage"),
                Set.of(),
                null,
                Set.of(),
                Set.of(),
                List.of()
        );
        currentUser.setUserUuid("operator-uuid-" + userId);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
