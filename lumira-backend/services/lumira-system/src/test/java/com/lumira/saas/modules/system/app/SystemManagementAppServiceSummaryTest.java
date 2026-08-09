package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.dto.ProfileDTO;
import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;

class SystemManagementAppServiceSummaryTest {

    @Test
    void defaultAdminProtectionShouldBindToFixedAdminUserId() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/lumira/saas/modules/system/user/app/SystemUserManagementAppService.java")
        );

        assertThat(source).contains("return DEFAULT_ADMIN_USER_ID.equals(userId);");
        assertThat(source).doesNotContain("DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)");
    }

    @Test
    void dashboardSummaryShouldAssembleIndependentReadsInParallelFriendlyWay() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        SystemVO.DashboardSummaryVO dashboard = env.service.dashboardSummary(currentUser);

        assertThat(dashboard.getCurrentUser().getUsername()).isEqualTo("jane");
        assertThat(dashboard.getMenuCount()).isEqualTo(12);
        assertThat(dashboard.getPermissionCount()).isEqualTo(2);
        assertThat(dashboard.getAvailablePlugins()).hasSize(1);
        assertThat(dashboard.getRecentLoginLogs()).hasSize(1);
        assertThat(dashboard.getRecentOperationLogs()).hasSize(1);

        verify(env.userDomainService, times(1)).findById(42L);
        verify(env.systemPluginViewService, times(1)).availablePlugins();
        verify(env.systemVerificationAppService, times(0)).loadLoginCapabilities();
    }

    @Test
    void profileSummaryShouldAssembleIndependentReadsInParallelFriendlyWay() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        SystemVO.ProfileSummaryVO profile = env.service.profileSummary(currentUser);

        assertThat(profile.getCurrentUser().getUsername()).isEqualTo("jane");
        assertThat(profile.getRoleNames()).containsExactly("管理员");
        assertThat(profile.getRecentLoginLogs()).hasSize(1);
        assertThat(profile.getProfileFieldSettings()).hasSize(1);
        assertThat(profile.getProfileCompletion()).isNotNull();
        assertThat(profile.getMobileBindAvailable()).isTrue();
        assertThat(profile.getEmailBindAvailable()).isFalse();

        verify(env.userDomainService, times(1)).findById(42L);
        verify(env.systemProfileSettingsAppService, times(1)).getProfileFieldSettings(org.mockito.ArgumentMatchers.any());
        verify(env.systemVerificationAppService, times(1)).isContactBindAvailable("mobile");
        verify(env.systemVerificationAppService, times(1)).isContactBindAvailable("email");
    }

    @Test
    void dashboardSummaryShouldRejectUnauthenticatedUserBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setAuthenticated(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.dashboardSummary(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(env.userDomainService, never()).findById(42L);
        verify(env.systemPluginViewService, never()).availablePlugins();
    }

    @Test
    void profileSummaryShouldRejectInvalidUserBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setUserId(0L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.profileSummary(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(env.userDomainService, never()).findById(42L);
        verify(env.systemProfileSettingsAppService, never()).getProfileFieldSettings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void profileSummaryShouldRejectUserWithoutSessionBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setSessionId(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.profileSummary(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(env.userDomainService, never()).findById(42L);
        verify(env.systemProfileSettingsAppService, never()).getProfileFieldSettings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dashboardSummaryShouldRejectDisabledTrustedUserBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        when(env.permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.dashboardSummary(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(env.userDomainService, never()).findById(42L);
        verify(env.systemPluginViewService, never()).availablePlugins();
    }

    @Test
    void dashboardSummaryShouldRejectDisabledTrustedUserIdentityBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", "jane-live", "DISABLED"));
        SystemManagementAppService service = new SystemManagementAppService(
                env.jdbcTemplate,
                env.userDomainService,
                env.permissionSnapshotService,
                systemInternalApi,
                null,
                env.systemPluginViewService,
                env.onlineSessionManagementAppService,
                env.systemVerificationAppService,
                env.systemPlatformSettingsAppService,
                env.systemProfileSettingsAppService,
                env.passwordEncoder,
                env.authSessionStore,
                env.loginAuditService,
                env.operationAuditService,
                env.securitySettingsService,
                env.passwordPolicyService,
                env.iamUserService,
                env.systemUserManagementAppService,
                env.systemRoleManagementAppService,
                env.fieldCryptoService
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboardSummary(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(env.userDomainService, never()).findById(42L);
        verify(env.permissionSnapshotService, never()).isTrustedActiveUser(42L, "user-uuid-42");
        verify(env.systemPluginViewService, never()).availablePlugins();
    }

    @Test
    void dashboardSummaryShouldRejectBlankLiveUsernameBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", " ", "ENABLED"));
        SystemManagementAppService service = new SystemManagementAppService(
                env.jdbcTemplate,
                env.userDomainService,
                env.permissionSnapshotService,
                systemInternalApi,
                null,
                env.systemPluginViewService,
                env.onlineSessionManagementAppService,
                env.systemVerificationAppService,
                env.systemPlatformSettingsAppService,
                env.systemProfileSettingsAppService,
                env.passwordEncoder,
                env.authSessionStore,
                env.loginAuditService,
                env.operationAuditService,
                env.securitySettingsService,
                env.passwordPolicyService,
                env.iamUserService,
                env.systemUserManagementAppService,
                env.systemRoleManagementAppService,
                env.fieldCryptoService
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboardSummary(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> {
                    BizException exception = (BizException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(exception.getMessage()).contains("Trusted user username is unavailable");
                });
        verify(env.userDomainService, never()).findById(42L);
        verify(env.permissionSnapshotService, never()).isTrustedActiveUser(42L, "user-uuid-42");
        verify(env.systemPluginViewService, never()).availablePlugins();
    }

    @Test
    void dashboardSummaryShouldRejectRevokedSessionTicketBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        when(sessionAuthenticationService.authenticateSessionTicket("session-42", 42L, "user-uuid-42", null, 1, "v1"))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Session expired"));
        SystemManagementAppService service = new SystemManagementAppService(
                env.jdbcTemplate,
                env.userDomainService,
                env.permissionSnapshotService,
                sessionAuthenticationService,
                env.systemPluginViewService,
                env.onlineSessionManagementAppService,
                env.systemVerificationAppService,
                env.systemPlatformSettingsAppService,
                env.systemProfileSettingsAppService,
                env.passwordEncoder,
                env.authSessionStore,
                env.loginAuditService,
                env.operationAuditService,
                env.securitySettingsService,
                env.passwordPolicyService,
                env.iamUserService,
                env.systemUserManagementAppService,
                env.systemRoleManagementAppService,
                env.fieldCryptoService
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboardSummary(buildCurrentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(env.userDomainService, never()).findById(42L);
        verify(env.systemPluginViewService, never()).availablePlugins();
    }

    @Test
    void dashboardSummaryShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        TestEnvironment env = new TestEnvironment();
        SystemManagementAppService service = strictService(
                env.jdbcTemplate,
                env.userDomainService,
                null,
                null,
                null,
                env.systemPluginViewService,
                env.onlineSessionManagementAppService,
                env.systemVerificationAppService,
                env.systemPlatformSettingsAppService,
                env.systemProfileSettingsAppService,
                env.passwordEncoder,
                env.authSessionStore,
                env.loginAuditService,
                env.operationAuditService,
                env.securitySettingsService,
                env.passwordPolicyService,
                env.iamUserService,
                env.systemUserManagementAppService,
                env.systemRoleManagementAppService,
                env.fieldCryptoService
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboardSummary(buildCurrentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(env.userDomainService, never()).findById(42L);
        verify(env.systemPluginViewService, never()).availablePlugins();
    }

    @Test
    void dashboardSummaryShouldRejectWhenTrustedPermissionSnapshotIsUnavailableBeforeUserLookup() {
        TestEnvironment env = new TestEnvironment();
        when(env.permissionSnapshotService.loadSnapshot(42L, "user-uuid-42")).thenReturn(null);
        SystemManagementAppService service = strictService(
                env.jdbcTemplate,
                env.userDomainService,
                env.permissionSnapshotService,
                null,
                null,
                env.systemPluginViewService,
                env.onlineSessionManagementAppService,
                env.systemVerificationAppService,
                env.systemPlatformSettingsAppService,
                env.systemProfileSettingsAppService,
                env.passwordEncoder,
                env.authSessionStore,
                env.loginAuditService,
                env.operationAuditService,
                env.securitySettingsService,
                env.passwordPolicyService,
                env.iamUserService,
                env.systemUserManagementAppService,
                env.systemRoleManagementAppService,
                env.fieldCryptoService
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.dashboardSummary(buildCurrentUser()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user permission snapshot is unavailable");
        verify(env.userDomainService, never()).findById(42L);
        verify(env.systemPluginViewService, never()).availablePlugins();
    }

    private static SystemManagementAppService strictService(
            MyBatisQueryOperations jdbcTemplate,
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            SystemPluginViewService systemPluginViewService,
            OnlineSessionManagementAppService onlineSessionManagementAppService,
            SystemVerificationAppService systemVerificationAppService,
            SystemPlatformSettingsAppService systemPlatformSettingsAppService,
            SystemProfileSettingsAppService systemProfileSettingsAppService,
            PasswordEncoder passwordEncoder,
            AuthSessionStore authSessionStore,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService,
            SystemUserManagementAppService systemUserManagementAppService,
            SystemRoleManagementAppService systemRoleManagementAppService,
            FieldCryptoService fieldCryptoService
    ) {
        try {
            SystemManagementAppService service = new SystemManagementAppService(
                    jdbcTemplate,
                    userDomainService,
                    permissionSnapshotService,
                    systemInternalApi,
                    sessionAuthenticationService,
                    systemPluginViewService,
                    onlineSessionManagementAppService,
                    systemVerificationAppService,
                    systemPlatformSettingsAppService,
                    systemProfileSettingsAppService,
                    passwordEncoder,
                    authSessionStore,
                    loginAuditService,
                    operationAuditService,
                    securitySettingsService,
                    passwordPolicyService,
                    iamUserService,
                    systemUserManagementAppService,
                    systemRoleManagementAppService,
                    fieldCryptoService
            );
            Field enforceField = SystemManagementAppService.class.getDeclaredField("enforceTrustedUserResolution");
            enforceField.setAccessible(true);
            enforceField.set(service, true);
            return service;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to create strict SystemManagementAppService", ex);
        }
    }

    @Test
    void sensitiveUserInfoPermissionShouldRequireTrustedSession() throws Exception {
        TestEnvironment env = new TestEnvironment();
        Method method = SystemManagementAppService.class.getDeclaredMethod("canViewSensitiveUserInfo", CurrentUser.class);
        method.setAccessible(true);
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setPermissions(Set.of("system:user:sensitive:view"));
        currentUser.setSessionVersion(null);

        assertThat(method.invoke(env.service, currentUser)).isEqualTo(false);

        currentUser.setSessionVersion(1);
        assertThat(method.invoke(env.service, currentUser)).isEqualTo(true);
    }

    @Test
    void permissionSnapshotFromCurrentUserShouldRequireTrustedSession() throws Exception {
        TestEnvironment env = new TestEnvironment();
        Method method = SystemManagementAppService.class.getDeclaredMethod("snapshotFromCurrentUser", CurrentUser.class);
        method.setAccessible(true);
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setPermissionsVersion("pv-1");
        currentUser.setSessionId(null);

        assertThat(method.invoke(env.service, currentUser)).isNull();
    }

    @Test
    void refreshTrustedCurrentUserShouldNormalizeInvalidSimulatedRoleIdBeforeSnapshotLoad() throws Exception {
        TestEnvironment env = new TestEnvironment();
        Method method = SystemManagementAppService.class.getDeclaredMethod("refreshTrustedCurrentUser", CurrentUser.class);
        method.setAccessible(true);
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setSimulatedRoleId(0L);

        method.invoke(env.service, currentUser);

        assertThat(currentUser.getSimulatedRoleId()).isNull();
        assertThat(currentUser.getPermissionsVersion()).isEqualTo("permissions-2");
        verify(env.permissionSnapshotService).loadSnapshot(42L, "user-uuid-42");
        verify(env.permissionSnapshotService, never()).loadGrantedRoleSnapshot(42L, "user-uuid-42", 0L);
    }

    @Test
    void publicSecuritySettingsShouldOnlyExposeClientNeededPolicy() {
        TestEnvironment env = new TestEnvironment();

        SystemVO.SecuritySettingsVO settings = env.service.getPublicSecuritySettings();

        assertThat(settings.getIdleTimeoutSeconds()).isNull();
        assertThat(settings.getAccessTokenExpireSeconds()).isNull();
        assertThat(settings.getRefreshTokenExpireSeconds()).isNull();
        assertThat(settings.getAllowMultiDeviceLogin()).isNull();
        assertThat(settings.getLoginDefenseWindowMinutes()).isNull();
        assertThat(settings.getLoginMaxValidationAttempts()).isNull();
        assertThat(settings.getLoginMaxFailureCount()).isNull();
        assertThat(settings.getCaptchaEnabled()).isTrue();
        assertThat(settings.getCaptchaType()).isEqualTo("IMAGE");
        assertThat(settings.getVerificationCodeExpireSeconds()).isEqualTo(300L);
        assertThat(settings.getVerificationCodeCooldownSeconds()).isEqualTo(60L);
        assertThat(settings.getPasswordMinLength()).isEqualTo(12L);
        assertThat(settings.getPasswordRequireUppercase()).isTrue();
        assertThat(settings.getPasswordRequireLowercase()).isTrue();
        assertThat(settings.getPasswordRequireSpecialCharacter()).isTrue();
        assertThat(settings.getPasswordAllowConsecutiveCharacters()).isFalse();
    }

    @Test
    void securitySettingsShouldRequireConfigViewBeforeLoadingSettings() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.getSecuritySettings(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(env.securitySettingsService, never()).loadSettings();
    }

    @Test
    void agreementSettingsShouldRequireConfigViewBeforeLoadingSettings() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.getAgreementSettings(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(env.systemPlatformSettingsAppService, never()).getAgreementSettings();
    }

    @Test
    void agreementSettingsShouldRejectWhenLiveSnapshotRevokesConfigViewBeforeLoadingSettings() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setPermissions(Set.of("system:config:view"));
        when(env.permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(env.snapshot(Set.of("dashboard:view")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.getAgreementSettings(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(env.systemPlatformSettingsAppService, never()).getAgreementSettings();
    }

    @Test
    void updateSecuritySettingsShouldRejectNullRequestBeforeLoadingSettings() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setPermissions(Set.of("system:config:update"));
        when(env.permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(env.snapshot(Set.of("system:config:update")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateSecuritySettings(currentUser, null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(env.securitySettingsService, never()).loadSettings();
        verify(env.securitySettingsService, never()).updateSettings(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(CurrentUser.class)
        );
    }

    @Test
    void updateSecuritySettingsShouldRejectWhenLiveSnapshotRevokesConfigUpdatePermissionBeforeLoadingSettings() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setPermissions(Set.of("system:config:update"));
        when(env.permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(env.snapshot(Set.of("system:config:view")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateSecuritySettings(currentUser, new SystemDTO.SecuritySettingsRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(env.securitySettingsService, never()).loadSettings();
        verify(env.securitySettingsService, never()).updateSettings(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(CurrentUser.class)
        );
    }

    @Test
    void updateCurrentUserProfileShouldRejectUnsafeAvatarUrlBeforeWrite() {
        TestEnvironment env = new TestEnvironment();
        ProfileDTO.BasicInfoUpdateRequest request = new ProfileDTO.BasicInfoUpdateRequest();
        request.setAvatarUrl("javascript:alert(1)");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateCurrentUserProfile(buildCurrentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateCurrentUserProfileShouldIgnoreOmittedContactFields() {
        TestEnvironment env = new TestEnvironment();
        ProfileDTO.BasicInfoUpdateRequest request = new ProfileDTO.BasicInfoUpdateRequest();
        request.setNickname("Jane Updated");

        CurrentUserVO updated = env.service.updateCurrentUserProfile(buildCurrentUser(), request);

        assertThat(updated).isNotNull();
        verify(env.systemVerificationAppService, never()).isContactBindAvailable("mobile");
        verify(env.systemVerificationAppService, never()).isContactBindAvailable("email");
    }

    @Test
    void updateCurrentUserProfileShouldRejectExplicitMobileChange() {
        TestEnvironment env = new TestEnvironment();
        ProfileDTO.BasicInfoUpdateRequest request = new ProfileDTO.BasicInfoUpdateRequest();
        request.setMobile("13900000000");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateCurrentUserProfile(buildCurrentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(env.systemVerificationAppService, times(1)).isContactBindAvailable("mobile");
    }

    @Test
    void updateCurrentUserEmailShouldReuseContactBindFlow() {
        TestEnvironment env = new TestEnvironment();
        when(env.systemVerificationAppService.isContactBindAvailable("email")).thenReturn(true);
        ProfileDTO.EmailUpdateRequest request = new ProfileDTO.EmailUpdateRequest();
        request.setEmail("jane+new@example.com");
        request.setChallengeId("challenge-email-1");
        request.setVerificationCode("123456");

        CurrentUserVO updated = env.service.updateCurrentUserEmail(buildCurrentUser(), request);

        assertThat(updated).isNotNull();
        verify(env.systemVerificationAppService).completeContactBind(
                42L,
                "user-uuid-42",
                "email",
                "challenge-email-1",
                "123456",
                "jane+new@example.com"
        );
        verify(env.operationAuditService).log(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("user-uuid-42"),
                org.mockito.ArgumentMatchers.eq("jane"),
                org.mockito.ArgumentMatchers.eq("profile"),
                org.mockito.ArgumentMatchers.eq("bind"),
                org.mockito.ArgumentMatchers.eq("UPDATE"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void updateCurrentUserEmailShouldLogRefreshedLiveUsername() {
        TestEnvironment env = new TestEnvironment();
        when(env.systemVerificationAppService.isContactBindAvailable("email")).thenReturn(true);
        ProfileDTO.EmailUpdateRequest request = new ProfileDTO.EmailUpdateRequest();
        request.setEmail("jane+new@example.com");
        request.setChallengeId("challenge-email-1");
        request.setVerificationCode("123456");
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setUsername("stale-jane");
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(42L))
                .thenReturn(userSnapshot(42L, "user-uuid-42", "  jane-live  ", "ENABLED"));
        when(env.permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                .thenReturn(env.snapshot(Set.of("dashboard:view", "project:view")));
        SystemManagementAppService service = new SystemManagementAppService(
                env.jdbcTemplate,
                env.userDomainService,
                env.permissionSnapshotService,
                systemInternalApi,
                null,
                env.systemPluginViewService,
                env.onlineSessionManagementAppService,
                env.systemVerificationAppService,
                env.systemPlatformSettingsAppService,
                env.systemProfileSettingsAppService,
                env.passwordEncoder,
                env.authSessionStore,
                env.loginAuditService,
                env.operationAuditService,
                env.securitySettingsService,
                env.passwordPolicyService,
                env.iamUserService,
                env.systemUserManagementAppService,
                env.systemRoleManagementAppService,
                env.fieldCryptoService
        );

        CurrentUserVO updated = service.updateCurrentUserEmail(currentUser, request);

        assertThat(updated).isNotNull();
        assertThat(currentUser.getUsername()).isEqualTo("jane-live");
        verify(env.operationAuditService).log(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("user-uuid-42"),
                org.mockito.ArgumentMatchers.eq("jane-live"),
                org.mockito.ArgumentMatchers.eq("profile"),
                org.mockito.ArgumentMatchers.eq("bind"),
                org.mockito.ArgumentMatchers.eq("UPDATE"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void profileRequestEndpointsShouldRejectNullRequestBeforeLookup() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateCurrentUserProfile(currentUser, null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateCurrentUserEmail(currentUser, null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.startCurrentUserContactBindChallenge(currentUser, null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateCurrentUserContactBinding(currentUser, null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.updateCurrentUserLocale(currentUser, null))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(env.systemVerificationAppService, never()).isContactBindAvailable(org.mockito.ArgumentMatchers.anyString());
        verify(env.systemVerificationAppService, never()).startContactBindChallenge(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(env.systemVerificationAppService, never()).completeContactBind(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void contactBindChallengeShouldUseTrustedUserUuidBoundary() {
        TestEnvironment env = new TestEnvironment();
        ProfileDTO.ContactBindChallengeRequest request = new ProfileDTO.ContactBindChallengeRequest();
        request.setContactType("mobile");
        request.setValue("13800000000");
        request.setCurrentFactorCode("totp");
        request.setCurrentChallengeId("login-challenge-1");
        request.setCurrentVerificationCode("123456");
        SystemVO.VerificationChallengeVO expected = new SystemVO.VerificationChallengeVO();
        expected.setChallengeId("challenge-1");
        when(env.systemVerificationAppService.startContactBindChallenge(42L, "user-uuid-42", "mobile", "13800000000", "totp", "login-challenge-1", "123456"))
                .thenReturn(expected);

        SystemVO.VerificationChallengeVO result = env.service.startCurrentUserContactBindChallenge(buildCurrentUser(), request);

        assertThat(result).isSameAs(expected);
        verify(env.systemVerificationAppService).startContactBindChallenge(42L, "user-uuid-42", "mobile", "13800000000", "totp", "login-challenge-1", "123456");
    }

    @Test
    void contactBindChallengeShouldRequireCurrentPasswordWhenNoBoundVerificationFactorExists() {
        TestEnvironment env = new TestEnvironment();
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUuid("user-uuid-42");
        user.setUsername("jane");
        user.setMobile(null);
        user.setEmail(null);
        when(env.userDomainService.findById(42L)).thenReturn(Optional.of(user));
        ProfileDTO.ContactBindChallengeRequest request = new ProfileDTO.ContactBindChallengeRequest();
        request.setContactType("email");
        request.setValue("jane+new@example.com");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> env.service.startCurrentUserContactBindChallenge(buildCurrentUser(), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(env.systemVerificationAppService, never()).startContactBindChallenge(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void contactBindChallengeShouldAllowWechatFirstContactBindWithoutCurrentPassword() {
        TestEnvironment env = new TestEnvironment();
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUuid("user-uuid-42");
        user.setUsername("jane");
        user.setMobile(null);
        user.setEmail(null);
        when(env.userDomainService.findById(42L)).thenReturn(Optional.of(user));
        ProfileDTO.ContactBindChallengeRequest request = new ProfileDTO.ContactBindChallengeRequest();
        request.setContactType("email");
        request.setValue("jane+new@example.com");
        SystemVO.VerificationChallengeVO expected = new SystemVO.VerificationChallengeVO();
        expected.setChallengeId("challenge-2");
        when(env.systemVerificationAppService.startContactBindChallenge(42L, "user-uuid-42", "email", "jane+new@example.com", null, null, null))
                .thenReturn(expected);
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setLoginType("WECHAT");

        SystemVO.VerificationChallengeVO result = env.service.startCurrentUserContactBindChallenge(currentUser, request);

        assertThat(result).isSameAs(expected);
        verify(env.systemVerificationAppService).startContactBindChallenge(42L, "user-uuid-42", "email", "jane+new@example.com", null, null, null);
    }

    private static CurrentUser buildCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(42L);
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setUsername("jane");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-42");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("v1");
        currentUser.setPermissions(Set.of("dashboard:view", "project:view"));
        currentUser.setRoleIds(Set.of(3L));
        currentUser.setDeptIds(Set.of());
        currentUser.setDescendantDeptIds(Set.of());
        currentUser.setDataScopes(List.of());
        currentUser.setDefaultHomePath("/dashboard/home");
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

    private static final class TestEnvironment {
        private final MyBatisQueryOperations jdbcTemplate = new MyBatisQueryOperations() {
            @Override
            public int update(String sql, Object... args) {
                return 1;
            }

            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_menu")) {
                    return requiredType.cast(Long.valueOf(12L));
                }
                if (normalized.contains("select uuid from sys_user")) {
                    return requiredType.cast("user-uuid-42");
                }
                if (normalized.contains("from sys_user_wechat_binding")) {
                    return requiredType.cast(Long.valueOf(1L));
                }
                if (normalized.contains("select locale") && normalized.contains("from iam_user_profile")) {
                    return requiredType.cast("zh-CN");
                }
                if (normalized.contains("from iam_user_profile")) {
                    return requiredType.cast("{\"customProfileValues\":{\"nickname\":\"Agent\",\"title\":\"Lead\"}}");
                }
                throw new EmptyResultDataAccessException(1);
            }

            @Override
            public <T> List<T> queryForList(String sql, Class<T> requiredType, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_user_role")) {
                    return List.of(requiredType.cast("管理员"));
                }
                return List.of();
            }

            @Override
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_menu")) {
                    return java.util.stream.IntStream.range(0, 12)
                            .mapToObj(index -> {
                                SystemVO.MenuVO row = new SystemVO.MenuVO();
                                row.setMenuCode("menu-" + index);
                                row.setPath("/menu-" + index);
                                return (T) row;
                            })
                            .toList();
                }
                if (normalized.contains("audit_login_log")) {
                    SystemVO.AuditLogVO row = new SystemVO.AuditLogVO();
                    row.setId(1L);
                    row.setUsername("jane");
                    row.setLogResult("SUCCESS");
                    return List.of((T) row);
                }
                if (normalized.contains("audit_operation_log")) {
                    SystemVO.AuditLogVO row = new SystemVO.AuditLogVO();
                    row.setId(2L);
                    row.setUsername("jane");
                    row.setLogResult("SUCCESS");
                    return List.of((T) row);
                }
                return List.of();
            }
        };
        private final UserDomainService userDomainService = mock(UserDomainService.class);
        private final PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        private final SystemPluginViewService systemPluginViewService = mock(SystemPluginViewService.class);
        private final OnlineSessionManagementAppService onlineSessionManagementAppService = mock(OnlineSessionManagementAppService.class);
        private final SystemVerificationAppService systemVerificationAppService = mock(SystemVerificationAppService.class);
        private final SystemPlatformSettingsAppService systemPlatformSettingsAppService = mock(SystemPlatformSettingsAppService.class);
        private final SystemProfileSettingsAppService systemProfileSettingsAppService = mock(SystemProfileSettingsAppService.class);
        private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
        private final LoginAuditService loginAuditService = mock(LoginAuditService.class);
        private final OperationAuditService operationAuditService = mock(OperationAuditService.class);
        private final SecuritySettingsService securitySettingsService = mock(SecuritySettingsService.class);
        private final PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        private final IamUserService iamUserService = mock(IamUserService.class);
        private final SystemUserManagementAppService systemUserManagementAppService = mock(SystemUserManagementAppService.class);
        private final SystemRoleManagementAppService systemRoleManagementAppService = mock(SystemRoleManagementAppService.class);
        private final FieldCryptoService fieldCryptoService = mock(FieldCryptoService.class);
        private final SystemManagementAppService service = new SystemManagementAppService(
                jdbcTemplate,
                userDomainService,
                permissionSnapshotService,
                systemPluginViewService,
                onlineSessionManagementAppService,
                systemVerificationAppService,
                systemPlatformSettingsAppService,
                systemProfileSettingsAppService,
                passwordEncoder,
                authSessionStore,
                loginAuditService,
                operationAuditService,
                securitySettingsService,
                passwordPolicyService,
                iamUserService,
                systemUserManagementAppService,
                systemRoleManagementAppService,
                fieldCryptoService
        );

        private TestEnvironment() {
            when(permissionSnapshotService.isTrustedActiveUser(42L, "user-uuid-42")).thenReturn(true);
            when(permissionSnapshotService.loadSnapshot(42L, "user-uuid-42"))
                    .thenReturn(snapshot(Set.of("dashboard:view", "project:view")));
            SysUserEntity user = new SysUserEntity();
            user.setId(42L);
            user.setUuid("user-uuid-42");
            user.setUsername("jane");
            user.setNickname("Jane");
            user.setRealName("Jane Doe");
            user.setAvatarUrl("/avatar.png");
            user.setMobile("13800000000");
            user.setEmail("jane@example.com");
            user.setBirthMonth("05");
            user.setGender("F");
            user.setRegion("CN");
            user.setAvailableTime("09:00-18:00");
            user.setIdCardNumber("1234567890");
            when(userDomainService.findById(42L)).thenReturn(Optional.of(user));

            PluginVO.PluginAvailabilityVO plugin = new PluginVO.PluginAvailabilityVO();
            plugin.setPluginCode("core");
            plugin.setPluginName("核心插件");
            when(systemPluginViewService.availablePlugins()).thenReturn(List.of(plugin));

            ProfileFieldSettingVO field = new ProfileFieldSettingVO();
            field.setFieldKey("nickname");
            field.setVisible(true);
            field.setWeight(10);
            when(systemProfileSettingsAppService.getProfileFieldSettings(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(field));
            when(systemProfileSettingsAppService.buildProfileCompletionSummary(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyList(),
                    org.mockito.ArgumentMatchers.anyBoolean(),
                    org.mockito.ArgumentMatchers.anyBoolean()
            )).thenReturn(new SystemVO.ProfileCompletionSummaryVO());

            when(systemVerificationAppService.isContactBindAvailable("mobile")).thenReturn(true);
            when(systemVerificationAppService.isContactBindAvailable("email")).thenReturn(false);
            when(securitySettingsService.loadSettingsFresh()).thenReturn(new SecuritySettingsService.SecuritySettingsSnapshot(
                    1800L,
                    3600L,
                    604800L,
                    true,
                    true,
                    "image",
                    10L,
                    5L,
                    3L,
                    300L,
                    60L,
                    12L,
                    true,
                    true,
                    true,
                    false
            ));
        }

        private PermissionSnapshotService.PermissionSnapshot snapshot(Set<String> permissions) {
            return new PermissionSnapshotService.PermissionSnapshot(
                    "permissions-2",
                    permissions,
                    Set.of(3L),
                    null,
                    Set.of(),
                    Set.of(),
                    List.of(),
                    "/dashboard/home"
            );
        }
    }
}
