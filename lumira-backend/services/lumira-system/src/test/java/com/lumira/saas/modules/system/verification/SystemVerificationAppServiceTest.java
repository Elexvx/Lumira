package com.lumira.saas.modules.system.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.auth.dto.LoginCodeCompleteRequest;
import com.lumira.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.lumira.saas.modules.system.support.SmsVerificationSender;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SystemVerificationAppServiceTest {

    @Test
    void verificationStateWritesShouldPersistTrustedUserUuidAudit() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationAppService.java"));

        assertTrue(source.contains("created_by, created_by_uuid, updated_by, updated_by_uuid"));
        assertTrue(source.contains("updated_by = ?, updated_by_uuid = ?"));
        assertTrue(source.contains("requireVerificationWrite(updated, \"Verification binding changed, please retry\")"));
        assertTrue(source.contains("requireVerificationWrite(updated, \"Verification challenge changed, please retry\")"));
        assertTrue(source.contains("requireVerificationWrite(bindingUpdated, \"Verification binding changed, please retry\")"));
        assertTrue(source.contains("updated_by_uuid = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then values(updated_by_uuid) else updated_by_uuid end"));
        assertTrue(source.contains("factor_name = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code)"));
        assertTrue(source.contains("deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) and factor_code = values(factor_code) then 0 else deleted end"));
        assertTrue(source.contains("factor_code = case when user_id = values(user_id) and user_uuid = values(user_uuid)"));
        assertTrue(source.contains(") values (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"));
        assertTrue(source.contains("Long auditUserId = userId != null && userId > 0 ? userId : null;"));
        assertTrue(source.contains("String auditUserUuid = auditUserId == null ? null : userUuid;"));
    }

    @Test
    void challengeConsumptionShouldConstrainByTrustedUserUuid() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationAppService.java"));

        assertTrue(source.contains("markChallengeConsumed(ChallengeRecord challenge)"));
        assertTrue(source.contains("updated_by_uuid = ?"));
        assertTrue(source.contains("and user_id = ?"));
        assertTrue(source.contains("and user_uuid = ?"));
        assertTrue(source.contains("and factor_code = ?"));
        assertTrue(source.contains("and challenge_type = ?"));
        assertTrue(source.contains("and consumed_flag = 0"));
        assertTrue(source.contains("and expires_at = ?"));
        assertTrue(source.contains("challenge.userId()"));
        assertTrue(source.contains("challenge.userUuid()"));
        assertTrue(source.contains("if (updated <= 0)"));
    }

    @Test
    void challengeDiscardShouldConstrainByTrustedUserUuidAndChallengeType() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationAppService.java"));

        assertTrue(source.contains("discardChallenge(challengeId, user.getId(), trustedUserUuid, normalizedLoginType, CHALLENGE_TYPE_LOGIN)"));
        assertTrue(source.contains("updated_by_uuid = ?"));
        assertTrue(source.contains("and user_id = ?"));
        assertTrue(source.contains("and user_uuid = ?"));
        assertTrue(source.contains("and factor_code = ?"));
        assertTrue(source.contains("and challenge_type = ?"));
        assertTrue(source.contains("and consumed_flag = 0"));
    }

    @Test
    void totpRecoveryCodesShouldBeConsumedAfterSuccessfulVerification() throws Exception {
        SystemVerificationAppService service = service(mock(UserDomainService.class));
        Object binding = bindingRecord(null, List.of("RECOVER1", "RECOVER2"));

        Object result = invokeDeclared(service, "verifyTotpLoginCode", new Class<?>[]{binding.getClass(), String.class}, binding, "recover1");

        Method recoveryCodeUsed = result.getClass().getDeclaredMethod("recoveryCodeUsed");
        Method recoveryCodes = result.getClass().getDeclaredMethod("recoveryCodes");

        assertTrue((Boolean) recoveryCodeUsed.invoke(result));
        assertEquals(List.of("RECOVER2"), recoveryCodes.invoke(result));
    }

    @Test
    void totpEnrollmentShouldRejectRecoveryCodes() throws Exception {
        SystemVerificationAppService service = service(mock(UserDomainService.class));
        Object binding = bindingRecord(null, List.of("RECOVER1"));

        BizException exception = assertThrows(
                BizException.class,
                () -> invokeDeclared(service, "verifyTotpEnrollmentCode", new Class<?>[]{binding.getClass(), String.class}, binding, "RECOVER1")
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void totpEnrollmentShouldReturnRecoveryCodesOnlyAfterSuccessfulBindVerification() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationAppService.java"));

        assertTrue(source.contains("verifyTotpEnrollmentCode(binding, verificationCode);"));
        assertTrue(source.contains("generateRecoveryCodes("));
        assertTrue(source.contains("consumeRecoveryCode(binding.recoveryCodes(), normalizedCode)"));
        assertTrue(source.contains("markBindingRecoveryCodes("));
        assertTrue(source.contains("verificationResult(userId, normalizedFactor,"));
        assertTrue(source.contains("recoveryCodes);"));
    }

    @Test
    void startBindChallengeShouldRejectAlreadyBoundFactorBeforePersistingReplacementSecret() {
        BindingJdbcOperations jdbcTemplate = new BindingJdbcOperations(bindingRow(1001L, "user-uuid-1001", true, true));
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user(1001L, "user-uuid-1001")));
        SystemVerificationAppService service = service(jdbcTemplate, userDomainService, mock(SystemVerificationSettingsAppService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bind(1001L, "user-uuid-1001", "totp")
        );

        assertEquals(ErrorCode.BIZ_ERROR, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void startContactBindChallengeShouldRequireCurrentBoundVerificationBeforeSendingNewCode() {
        BindingJdbcOperations jdbcTemplate = new BindingJdbcOperations(bindingRow(1001L, "user-uuid-1001", true, true));
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user(1001L, "user-uuid-1001")));
        SystemVerificationAppService service = service(jdbcTemplate, userDomainService, mock(SystemVerificationSettingsAppService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.startContactBindChallenge(1001L, "user-uuid-1001", "email", "alice@example.com", null, null, null)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void startBindChallengeShouldRequireCurrentPasswordWhenNoBoundVerificationFactorExists() {
        BindingJdbcOperations jdbcTemplate = new BindingJdbcOperations(null);
        UserDomainService userDomainService = mock(UserDomainService.class);
        IamUserService iamUserService = mock(IamUserService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SysUserEntity user = user(1001L, "user-uuid-1001");
        user.setPasswordHash("encoded-password");
        user.setEmail("alice@example.com");
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user));
        when(iamUserService.findActiveCredential(1001L, "user-uuid-1001", "PASSWORD")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("Password!23", "encoded-password")).thenReturn(true);
        SystemVerificationAppService service = service(
                jdbcTemplate,
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                iamUserService,
                passwordEncoder
        );

        service.bind(1001L, "user-uuid-1001", "totp", "Password!23", null, null, null);

        assertTrue(jdbcTemplate.updateCount > 0);
    }

    @Test
    void startBindChallengeShouldRequireCurrentFactorWhenSensitiveFactorExists() {
        BindingJdbcOperations jdbcTemplate = new BindingJdbcOperations(
                null,
                List.of(
                        Map.of("configKey", "verification.totp.enabled", "configValue", "true"),
                        Map.of("configKey", "verification.email-login.enabled", "configValue", "true")
                )
        );
        UserDomainService userDomainService = mock(UserDomainService.class);
        SmtpMailService smtpMailService = mock(SmtpMailService.class);
        SysUserEntity user = user(1001L, "user-uuid-1001");
        user.setEmail("alice@example.com");
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user));
        when(smtpMailService.isConfigured()).thenReturn(true);
        SystemVerificationAppService service = service(
                jdbcTemplate,
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                smtpMailService,
                mock(IamUserService.class),
                mock(PasswordEncoder.class)
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bind(1001L, "user-uuid-1001", "totp", null, null, null, null)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void numericOnlyUserIdOverloadsShouldNotBeExposed() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);

        assertEquals(List.of(), Arrays.stream(SystemVerificationAppService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(SystemVerificationAppService.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("listProviders(java.lang.Long)")
                        || signature.contains("provider(java.lang.Long,java.lang.String)")
                        || signature.contains("startContactBindChallenge(java.lang.Long,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)")
                        || signature.contains("completeContactBind(java.lang.Long,java.lang.String,java.lang.String,java.lang.String,java.lang.String)")
                        || signature.contains("bind(java.lang.Long,java.lang.String)")
                        || signature.contains("challenge(java.lang.Long,java.lang.String)")
                        || signature.contains("unbind(java.lang.Long,java.lang.String)")
                        || signature.contains("completeBind(java.lang.Long,java.lang.String,java.lang.String,java.lang.String)")
                        || signature.contains("verifyLogin(java.lang.Long,java.lang.String,java.lang.String,java.lang.String)")
                        || signature.contains("startLoginChallenge(java.lang.Long,java.lang.String)"))
                .toList());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserProviderShouldRejectUnauthenticatedUserBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);
        CurrentUser currentUser = currentUser();
        currentUser.setAuthenticated(false);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.provider(currentUser, "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserListProvidersShouldRequireViewPermissionBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(java.util.Set.of("system:user:view"));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.listProviders(currentUser)
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserProviderShouldRequireViewPermissionBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(java.util.Set.of("system:user:view"));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.provider(currentUser, "totp")
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserProviderShouldRejectWhenLiveSnapshotRevokesViewPermissionBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", java.util.Set.of("system:user:view")));
        SystemVerificationAppService service = service(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                permissionSnapshotService
        );
        CurrentUser currentUser = currentUser();

        BizException exception = assertThrows(
                BizException.class,
                () -> service.provider(currentUser, "totp")
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserBindShouldRejectMissingUserBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);
        CurrentUser currentUser = currentUser();
        currentUser.setUserId(null);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser, "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserChallengeShouldRejectBlankUsernameBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        BizException exception = assertThrows(
                BizException.class,
                () -> service.challengeCurrentUser(currentUser, "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserProviderShouldRejectMissingSessionVersionBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.provider(currentUser, "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void getSmsSettingsShouldRequireViewPermissionBeforeReadingSettings() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationSettingsAppService settingsAppService = mock(SystemVerificationSettingsAppService.class);
        SystemVerificationAppService service = service(userDomainService, settingsAppService);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(java.util.Set.of("system:user:view"));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.getSmsSettings(currentUser)
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(settingsAppService, never()).getSmsSettings();
    }

    @Test
    void currentUserBindShouldRequireManagePermissionBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = service(userDomainService);
        CurrentUser currentUser = currentUser();
        currentUser.setPermissions(java.util.Set.of("system:verification:view"));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser, "totp")
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserBindShouldRejectWhenLiveSnapshotRevokesManagePermissionBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", java.util.Set.of("system:verification:view")));
        SystemVerificationAppService service = service(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                permissionSnapshotService
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser(), "totp")
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserBindShouldRejectDisabledTrustedUserIdentityBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "tester-live", "DISABLED"));
        SystemVerificationAppService service = service(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser(), "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserBindShouldRejectBlankLiveUsernameBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", " ", "ENABLED"));
        SystemVerificationAppService service = service(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser(), "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Trusted user username is unavailable"));
        verify(permissionSnapshotService, never()).isTrustedActiveUser(1001L, "user-uuid-1001");
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserProviderShouldRefreshLiveUsernameFromTrustedIdentity() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L))
                .thenReturn(userSnapshot(1001L, "user-uuid-1001", "  tester-live  ", "ENABLED"));
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", java.util.Set.of("system:verification:manage")));
        SystemVerificationAppService service = service(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                permissionSnapshotService,
                systemInternalApi,
                null
        );
        CurrentUser currentUser = currentUser();
        currentUser.setUsername("tester-stale");

        assertDoesNotThrow(() -> invokeDeclared(
                service,
                "refreshTrustedCurrentUser",
                new Class<?>[]{CurrentUser.class},
                currentUser
        ));

        assertEquals("tester-live", currentUser.getUsername());
        assertEquals("permissions-2", currentUser.getPermissionsVersion());
    }

    @Test
    void currentUserBindShouldRejectRevokedSessionTicketBeforeLookup() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Login required"));
        SystemVerificationAppService service = service(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                userDomainService,
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                mock(PermissionSnapshotService.class),
                sessionAuthenticationService
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser(), "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() {
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotService.PermissionSnapshot("permissions-2", Set.of("system:verification:view")));
        SystemVerificationAppService service = service(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                mock(UserDomainService.class),
                mock(SystemVerificationSettingsAppService.class),
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                permissionSnapshotService,
                null
        );
        CurrentUser currentUser = currentUser();
        currentUser.setSimulatedRoleId(0L);

        assertDoesNotThrow(() -> invokeDeclared(
                service,
                "refreshTrustedCurrentUser",
                new Class<?>[]{CurrentUser.class},
                currentUser
        ));

        assertNull(currentUser.getSimulatedRoleId());
        verify(permissionSnapshotService).loadSnapshot(1001L, "user-uuid-1001");
        verify(permissionSnapshotService, never()).loadGrantedRoleSnapshot(anyLong(), anyString(), anyLong());
    }

    @Test
    void currentUserBindShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        SystemVerificationAppService service = new SystemVerificationAppService(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                new ObjectMapper(),
                userDomainService,
                new SystemVerificationProperties(),
                mock(SmtpMailService.class),
                mock(SmsVerificationSender.class),
                mock(VerificationDeliveryAuditService.class),
                mock(SystemVerificationSettingsAppService.class),
                mock(SecuritySettingsService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                mock(FieldCryptoService.class),
                null,
                null,
                null
        );

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser(), "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Trusted user resolver is unavailable"));
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void currentUserBindShouldRejectWhenTrustedPermissionSnapshotIsUnavailableInStrictMode() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemVerificationAppService service = new SystemVerificationAppService(
                new MyBatisQueryOperations(mock(JdbcTemplate.class)),
                new ObjectMapper(),
                userDomainService,
                new SystemVerificationProperties(),
                mock(SmtpMailService.class),
                mock(SmsVerificationSender.class),
                mock(VerificationDeliveryAuditService.class),
                mock(SystemVerificationSettingsAppService.class),
                mock(SecuritySettingsService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class),
                mock(FieldCryptoService.class),
                permissionSnapshotService,
                null,
                null
        );
        when(permissionSnapshotService.isTrustedActiveUser(1001L, "user-uuid-1001")).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(1001L, "user-uuid-1001")).thenReturn(null);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.bindCurrentUser(currentUser(), "totp")
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Trusted user permission snapshot is unavailable"));
        verify(userDomainService, never()).findById(1001L);
    }

    @Test
    void completeLoginCodeLoginShouldRejectMalformedChallengeBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemVerificationAppService service = service(jdbcTemplate);
        LoginCodeCompleteRequest request = new LoginCodeCompleteRequest();
        request.setChallengeId("x".repeat(128));
        request.setVerificationCode("123456");

        BizException exception = assertThrows(
                BizException.class,
                () -> service.completeLoginCodeLogin(request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void completeSecondFactorLoginShouldRejectNullRequestBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemVerificationAppService service = service(jdbcTemplate);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.completeSecondFactorLogin(null, "127.0.0.1", "agent")
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void completeSecondFactorLoginShouldRejectOversizedFactorBeforeDatabaseAccess() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        SystemVerificationAppService service = service(jdbcTemplate);
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest();
        request.setFactorCode("totp".repeat(20));
        request.setChallengeId("0123456789abcdef0123456789abcdef");
        request.setVerificationCode("123456");

        BizException exception = assertThrows(
                BizException.class,
                () -> service.completeSecondFactorLogin(request, "127.0.0.1", "agent")
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void startLoginCodeChallengeShouldRejectDisabledUserBeforeChallengePersistence() {
        SystemVerificationAppService service = service(mock(UserDomainService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.startLoginCodeChallenge(disabledUser(1001L, "user-uuid-1001"), "email")
        );

        assertEquals(ErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
    }

    @Test
    void completeLoginCodeLoginShouldRejectDisabledChallengeUserBeforeConsuming() {
        ChallengeJdbcOperations jdbcTemplate = new ChallengeJdbcOperations(challengeRow(1001L, "sms", "LOGIN"));
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(disabledUser(1001L, "user-uuid-1001")));
        SystemVerificationAppService service = service(jdbcTemplate, userDomainService, mock(SystemVerificationSettingsAppService.class));
        LoginCodeCompleteRequest request = new LoginCodeCompleteRequest();
        request.setChallengeId("0123456789abcdef0123456789abcdef");
        request.setVerificationCode("123456");

        BizException exception = assertThrows(
                BizException.class,
                () -> service.completeLoginCodeLogin(request)
        );

        assertEquals(ErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void completeSecondFactorLoginShouldRejectDisabledChallengeUserBeforeConsuming() {
        ChallengeJdbcOperations jdbcTemplate = new ChallengeJdbcOperations(challengeRow(1001L, "sms", "LOGIN"));
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(disabledUser(1001L, "user-uuid-1001")));
        SystemVerificationAppService service = service(jdbcTemplate, userDomainService, mock(SystemVerificationSettingsAppService.class));
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest();
        request.setFactorCode("sms");
        request.setChallengeId("0123456789abcdef0123456789abcdef");
        request.setVerificationCode("123456");

        BizException exception = assertThrows(
                BizException.class,
                () -> service.completeSecondFactorLogin(request, "127.0.0.1", "agent")
        );

        assertEquals(ErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void completeContactBindShouldRejectChallengeOwnedByAnotherUserBeforeConsuming() {
        ChallengeJdbcOperations jdbcTemplate = new ChallengeJdbcOperations(challengeRow(2002L, "email", "BIND"));
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user(1001L, "user-uuid-1001")));
        SystemVerificationAppService service = service(jdbcTemplate, userDomainService, mock(SystemVerificationSettingsAppService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.completeContactBind(1001L, "user-uuid-1001", "email", "0123456789abcdef0123456789abcdef", "123456", "alice@example.com")
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void verifyLoginShouldRejectChallengeOwnedByAnotherUserBeforeCodeCheck() {
        ChallengeJdbcOperations jdbcTemplate = new ChallengeJdbcOperations(challengeRow(2002L, "sms", "LOGIN"));
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user(1001L, "user-uuid-1001")));
        SystemVerificationAppService service = service(jdbcTemplate, userDomainService, mock(SystemVerificationSettingsAppService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.verifyLogin(1001L, "user-uuid-1001", "sms", "0123456789abcdef0123456789abcdef", "123456")
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    @Test
    void verifyLoginShouldRejectChallengeOwnedBySameIdWithDifferentUuidBeforeCodeCheck() {
        ChallengeJdbcOperations jdbcTemplate = new ChallengeJdbcOperations(challengeRow(1001L, "other-user-uuid", "sms", "LOGIN"));
        UserDomainService userDomainService = mock(UserDomainService.class);
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user(1001L, "user-uuid-1001")));
        SystemVerificationAppService service = service(jdbcTemplate, userDomainService, mock(SystemVerificationSettingsAppService.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.verifyLogin(1001L, "user-uuid-1001", "sms", "0123456789abcdef0123456789abcdef", "123456")
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertEquals(0, jdbcTemplate.updateCount);
    }

    private static SystemVerificationAppService service(UserDomainService userDomainService) {
        return service(userDomainService, mock(SystemVerificationSettingsAppService.class));
    }

    private static SystemVerificationAppService service(
            UserDomainService userDomainService,
            SystemVerificationSettingsAppService settingsAppService
    ) {
        return service(new MyBatisQueryOperations(mock(JdbcTemplate.class)), userDomainService, settingsAppService);
    }

    private static SystemVerificationAppService service(MyBatisQueryOperations jdbcTemplate) {
        return service(jdbcTemplate, mock(UserDomainService.class), mock(SystemVerificationSettingsAppService.class));
    }

    private static SystemVerificationAppService service(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            SystemVerificationSettingsAppService settingsAppService
    ) {
        return service(
                jdbcTemplate,
                userDomainService,
                settingsAppService,
                mock(SmtpMailService.class),
                mock(IamUserService.class),
                mock(PasswordEncoder.class)
        );
    }

    private static SystemVerificationAppService service(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            SystemVerificationSettingsAppService settingsAppService,
            SmtpMailService smtpMailService,
            IamUserService iamUserService,
            PasswordEncoder passwordEncoder
    ) {
        return service(jdbcTemplate, userDomainService, settingsAppService, smtpMailService, iamUserService, passwordEncoder, null);
    }

    private static SystemVerificationAppService service(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            SystemVerificationSettingsAppService settingsAppService,
            SmtpMailService smtpMailService,
            IamUserService iamUserService,
            PasswordEncoder passwordEncoder,
            PermissionSnapshotService permissionSnapshotService
    ) {
        return service(jdbcTemplate, userDomainService, settingsAppService, smtpMailService, iamUserService, passwordEncoder, permissionSnapshotService, null, null);
    }

    private static SystemVerificationAppService service(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            SystemVerificationSettingsAppService settingsAppService,
            SmtpMailService smtpMailService,
            IamUserService iamUserService,
            PasswordEncoder passwordEncoder,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        return service(jdbcTemplate, userDomainService, settingsAppService, smtpMailService, iamUserService, passwordEncoder, permissionSnapshotService, null, sessionAuthenticationService);
    }

    private static SystemVerificationAppService service(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            SystemVerificationSettingsAppService settingsAppService,
            SmtpMailService smtpMailService,
            IamUserService iamUserService,
            PasswordEncoder passwordEncoder,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        try {
            Constructor<SystemVerificationAppService> constructor = SystemVerificationAppService.class.getDeclaredConstructor(
                    MyBatisQueryOperations.class,
                    ObjectMapper.class,
                    UserDomainService.class,
                    SystemVerificationProperties.class,
                    SmtpMailService.class,
                    SmsVerificationSender.class,
                    VerificationDeliveryAuditService.class,
                    SystemVerificationSettingsAppService.class,
                    SecuritySettingsService.class,
                    IamUserService.class,
                    PasswordEncoder.class,
                    FieldCryptoService.class,
                    PermissionSnapshotService.class,
                    SystemInternalApi.class,
                    SessionAuthenticationService.class,
                    boolean.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    jdbcTemplate,
                    new ObjectMapper(),
                    userDomainService,
                    new SystemVerificationProperties(),
                    smtpMailService,
                    mock(SmsVerificationSender.class),
                    mock(VerificationDeliveryAuditService.class),
                    settingsAppService,
                    mock(SecuritySettingsService.class),
                    iamUserService,
                    passwordEncoder,
                    mock(FieldCryptoService.class),
                    permissionSnapshotService,
                    systemInternalApi,
                    sessionAuthenticationService,
                    false
            );
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("tester");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(java.util.Set.of("system:verification:manage"));
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

    private static Object bindingRecord(String secretKey, List<String> recoveryCodes) throws Exception {
        Class<?> bindingRecordClass = Arrays.stream(SystemVerificationAppService.class.getDeclaredClasses())
                .filter(candidate -> "VerificationBindingRecord".equals(candidate.getSimpleName()))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = bindingRecordClass.getDeclaredConstructor(
                Long.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                List.class,
                LocalDateTime.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(1001L, "user-uuid-1001", "totp", "Authenticator", true, true, false, "***", secretKey, recoveryCodes, LocalDateTime.now());
    }

    private static Object invokeDeclared(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception nested) {
                throw nested;
            }
            throw exception;
        }
    }

    private static Map<String, Object> challengeRow(Long userId, String factorCode, String challengeType) {
        return challengeRow(userId, "user-uuid-" + userId, factorCode, challengeType);
    }

    private static Map<String, Object> challengeRow(Long userId, String userUuid, String factorCode, String challengeType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("challenge_id", "0123456789abcdef0123456789abcdef");
        row.put("user_id", userId);
        row.put("user_uuid", userUuid);
        row.put("factor_code", factorCode);
        row.put("challenge_type", challengeType);
        row.put("setup_secret", "secret");
        row.put("setup_uri", "");
        row.put("recovery_codes_json", "");
        row.put("code_hash", "hash");
        row.put("masked_contact", "***");
        row.put("debug_code", "");
        row.put("expires_at", Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));
        row.put("consumed_flag", 0);
        return row;
    }

    private static Map<String, Object> bindingRow(Long userId, String userUuid, boolean enabled, boolean bound) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", userId);
        row.put("user_uuid", userUuid);
        row.put("factor_code", "totp");
        row.put("factor_name", "2FA");
        row.put("enabled", enabled ? 1 : 0);
        row.put("bound", bound ? 1 : 0);
        row.put("email_required", 0);
        row.put("masked_contact", "***");
        row.put("secret_key", null);
        row.put("recovery_codes_json", "");
        row.put("verified_at", Timestamp.valueOf(LocalDateTime.now()));
        return row;
    }

    private static SysUserEntity user(Long userId, String userUuid) {
        SysUserEntity user = new SysUserEntity();
        user.setId(userId);
        user.setUuid(userUuid);
        user.setUsername("tester");
        user.setStatus("ENABLED");
        return user;
    }

    private static SysUserEntity disabledUser(Long userId, String userUuid) {
        SysUserEntity user = user(userId, userUuid);
        user.setStatus("DISABLED");
        return user;
    }

    private static final class ChallengeJdbcOperations extends MyBatisQueryOperations {
        private final Map<String, Object> challengeRow;
        private int updateCount;

        private ChallengeJdbcOperations(Map<String, Object> challengeRow) {
            this.challengeRow = challengeRow;
        }

        @Override
        public <T> T query(String sql, com.lumira.saas.infrastructure.persistence.mybatis.ResultSetExtractor<T> extractor, Object... args) {
            return extractor.extractData(new com.lumira.saas.infrastructure.persistence.mybatis.SqlRowCursor(List.of(challengeRow)));
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount += 1;
            return 1;
        }
    }

    private static final class BindingJdbcOperations extends MyBatisQueryOperations {
        private final Map<String, Object> bindingRow;
        private final List<Map<String, Object>> configRows;
        private int updateCount;

        private BindingJdbcOperations(Map<String, Object> bindingRow) {
            this(bindingRow, List.of(Map.of("configKey", "verification.totp.enabled", "configValue", "true")));
        }

        private BindingJdbcOperations(Map<String, Object> bindingRow, List<Map<String, Object>> configRows) {
            this.bindingRow = bindingRow;
            this.configRows = configRows == null ? List.of() : configRows;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql != null && sql.contains("from sys_config")) {
                return configRows;
            }
            if (bindingRow == null) {
                return List.of();
            }
            return List.of();
        }

        @Override
        public <T> T query(String sql, com.lumira.saas.infrastructure.persistence.mybatis.ResultSetExtractor<T> extractor, Object... args) {
            return extractor.extractData(new com.lumira.saas.infrastructure.persistence.mybatis.SqlRowCursor(
                    bindingRow == null ? List.of() : List.of(bindingRow)
            ));
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount += 1;
            return 1;
        }
    }
}
