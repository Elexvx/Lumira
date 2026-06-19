package com.lumira.saas.modules.system.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.plugin.vo.PluginVO;
import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import com.lumira.saas.modules.system.profile.vo.ProfileFieldSettingVO;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
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
    void dashboardSummaryShouldAssembleIndependentReadsInParallelFriendlyWay() {
        TestEnvironment env = new TestEnvironment();
        CurrentUser currentUser = buildCurrentUser();
        SystemVO.DashboardSummaryVO dashboard = env.service.dashboardSummary(currentUser);

        assertThat(dashboard.getCurrentUser().getUsername()).isEqualTo("jane");
        assertThat(dashboard.getMenuCount()).isEqualTo(12);
        assertThat(dashboard.getPermissionCount()).isEqualTo(2);
        assertThat(dashboard.getTenantPlugins()).hasSize(1);
        assertThat(dashboard.getRecentLoginLogs()).hasSize(1);
        assertThat(dashboard.getRecentOperationLogs()).hasSize(1);

        verify(env.userDomainService, times(1)).findById(42L);
        verify(env.systemPluginViewService, times(1)).availablePlugins(1001L);
        verify(env.systemVerificationAppService, times(0)).loadLoginCapabilities(1001L);
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
        verify(env.systemVerificationAppService, times(1)).isContactBindAvailable(1001L, "mobile");
        verify(env.systemVerificationAppService, times(1)).isContactBindAvailable(1001L, "email");
    }

    private static CurrentUser buildCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(42L);
        currentUser.setUsername("jane");
        currentUser.setCurrentTenantId(1001L);
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

    private static final class TestEnvironment {
        private final MyBatisQueryOperations jdbcTemplate = new MyBatisQueryOperations() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                String normalized = sql.toLowerCase();
                if (normalized.contains("from sys_menu")) {
                    return requiredType.cast(Long.valueOf(12L));
                }
                if (normalized.contains("from iam_user_profile")) {
                    return requiredType.cast("{\"customProfileValues\":{\"nickname\":\"Agent\",\"title\":\"Lead\"}}");
                }
                if (normalized.contains("from sys_user_tenant_profile")) {
                    return requiredType.cast("zh-CN");
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
            SysUserEntity user = new SysUserEntity();
            user.setId(42L);
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

            PluginVO.TenantPluginVO plugin = new PluginVO.TenantPluginVO();
            plugin.setPluginCode("core");
            plugin.setPluginName("核心插件");
            when(systemPluginViewService.availablePlugins(1001L)).thenReturn(List.of(plugin));

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

            when(systemVerificationAppService.isContactBindAvailable(1001L, "mobile")).thenReturn(true);
            when(systemVerificationAppService.isContactBindAvailable(1001L, "email")).thenReturn(false);
        }
    }
}
