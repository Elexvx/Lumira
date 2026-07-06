package com.lumira.saas.infrastructure.security.service;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.SecurityProperties;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecuritySettingsServiceTest {

    @Test
    void loadSettingsUsesBatchQueryAndCachesSnapshot() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any())).thenReturn(List.of(
                config("security.idle-timeout-seconds", "900"),
                config("security.allow-multi-device-login", "0")
        ));

        SecuritySettingsService service = new SecuritySettingsService(mapper, new SecurityProperties());

        assertEquals(900L, service.getIdleTimeoutSeconds());
        assertFalse(service.isAllowMultiDeviceLogin());
        assertEquals(900L, service.getIdleTimeoutSeconds());

        verify(mapper, times(1)).listEffectiveValues(eq("PLATFORM"), any());
    }

    @Test
    void updateSettingsInvalidatesCachedSnapshot() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(config("security.idle-timeout-seconds", "900")))
                .thenReturn(List.of(config("security.idle-timeout-seconds", "1200")));

        SecuritySettingsService service = new SecuritySettingsService(
                mapper,
                new SecurityProperties(),
                readModelVersionService
        );
        assertEquals(900L, service.getIdleTimeoutSeconds());

        SecuritySettingsService.SecuritySettingsSnapshot request = service.loadSettings();
        request.setIdleTimeoutSeconds(1200L);
        service.updateSettings(request, trustedOperator(7L, "operator-uuid-7"));

        assertEquals(1200L, service.getIdleTimeoutSeconds());
        verify(mapper, times(2)).listEffectiveValues(eq("PLATFORM"), any());
        verify(readModelVersionService).bump("platform", "public-bootstrap", "security-update");
    }

    @Test
    void updateSettingsShouldNotExposeNumericOnlyOperatorOperation() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any())).thenReturn(List.of());

        SecuritySettingsService service = new SecuritySettingsService(mapper, new SecurityProperties());

        assertFalse(Arrays.stream(SecuritySettingsService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(SecuritySettingsService.class))
                .map(Method::toString)
                .anyMatch(signature -> signature.contains("updateSettings")
                        && signature.contains("SecuritySettingsSnapshot,java.lang.Long")));

        verify(mapper, never()).upsertPlatformConfig(any());
    }

    @Test
    void updateSettingsRequiresTrustedCurrentUser() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any())).thenReturn(List.of());

        SecuritySettingsService service = new SecuritySettingsService(mapper, new SecurityProperties());
        SecuritySettingsService.SecuritySettingsSnapshot request = service.loadSettings();

        assertThrows(BizException.class, () -> service.updateSettings(request, untrustedOperator(23L)));

        verify(mapper, never()).upsertPlatformConfig(any());
    }

    @Test
    void updateSettingsAuditsWithTrustedOperator() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any())).thenReturn(List.of());

        SecuritySettingsService service = new SecuritySettingsService(mapper, new SecurityProperties());
        SecuritySettingsService.SecuritySettingsSnapshot request = service.loadSettings();

        service.updateSettings(request, trustedOperator(23L, "operator-uuid-23"));

        ArgumentCaptor<SysConfigEntity> captor = ArgumentCaptor.forClass(SysConfigEntity.class);
        verify(mapper, times(16)).upsertPlatformConfig(captor.capture());
        captor.getAllValues().forEach(entity -> {
            assertEquals(23L, entity.getCreatedBy());
            assertEquals("operator-uuid-23", entity.getCreatedByUuid());
            assertEquals(23L, entity.getUpdatedBy());
            assertEquals("operator-uuid-23", entity.getUpdatedByUuid());
        });
    }

    @Test
    void updateSettingsShouldRejectRevokedSessionTicketBeforePersistingConfig() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-23", 23L, "operator-uuid-23", null, 1, "permissions-1"))
                .thenThrow(new BizException(com.lumira.common.enums.ErrorCode.UNAUTHORIZED, "Session expired"));

        SecuritySettingsService service = new SecuritySettingsService(
                mapper,
                new SecurityProperties(),
                null,
                sessionAuthenticationService
        );
        SecuritySettingsService.SecuritySettingsSnapshot request = new SecuritySettingsService.SecuritySettingsSnapshot();
        request.setIdleTimeoutSeconds(900L);
        request.setAccessTokenExpireSeconds(900L);
        request.setRefreshTokenExpireSeconds(1800L);
        request.setAllowMultiDeviceLogin(true);
        request.setCaptchaEnabled(true);
        request.setCaptchaType("IMAGE");
        request.setLoginDefenseWindowMinutes(10L);
        request.setLoginMaxValidationAttempts(5L);
        request.setLoginMaxFailureCount(5L);
        request.setVerificationCodeExpireSeconds(300L);
        request.setVerificationCodeCooldownSeconds(60L);
        request.setPasswordMinLength(8L);
        request.setPasswordRequireUppercase(true);
        request.setPasswordRequireLowercase(true);
        request.setPasswordRequireSpecialCharacter(true);
        request.setPasswordAllowConsecutiveCharacters(true);

        assertThrows(BizException.class, () -> service.updateSettings(request, trustedOperator(23L, "operator-uuid-23")));

        verify(mapper, never()).listEffectiveValues(eq("PLATFORM"), any());
        verify(mapper, never()).upsertPlatformConfig(any());
    }

    @Test
    void loadSettingsReloadsWhenPublicBootstrapVersionChanges() throws Exception {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        ReadModelVersionService readModelVersionService = mock(ReadModelVersionService.class);
        when(mapper.listEffectiveValues(eq("PLATFORM"), any()))
                .thenReturn(List.of(config("security.idle-timeout-seconds", "900")))
                .thenReturn(List.of(config("security.idle-timeout-seconds", "1200")));
        when(readModelVersionService.currentVersion("platform", "public-bootstrap"))
                .thenReturn(11L, 12L);

        SecuritySettingsService service = new SecuritySettingsService(
                mapper,
                new SecurityProperties(),
                readModelVersionService
        );

        assertEquals(900L, service.getIdleTimeoutSeconds());
        Thread.sleep(2100L);
        assertEquals(1200L, service.getIdleTimeoutSeconds());

        verify(mapper, times(2)).listEffectiveValues(eq("PLATFORM"), any());
        verify(readModelVersionService, times(2)).currentVersion("platform", "public-bootstrap");
    }

    private static SysConfigEntity config(String key, String value) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        return entity;
    }

    private static CurrentUser trustedOperator(Long userId, String userUuid) {
        CurrentUser currentUser = new CurrentUser(
                userId,
                "operator",
                "session-" + userId,
                1,
                true,
                Set.of("system:config:update"),
                Set.of(),
                null,
                Set.of(),
                Set.of(),
                List.of()
        );
        currentUser.setUserUuid(userUuid);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private static CurrentUser untrustedOperator(Long userId) {
        return new CurrentUser(
                userId,
                "operator",
                "session-" + userId,
                1,
                true,
                Set.of("system:config:update"),
                Set.of(),
                null,
                Set.of(),
                Set.of(),
                List.of()
        );
    }
}
