package com.lumira.saas.modules.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.auth.LoginCodeCompleteRequest;
import com.lumira.api.auth.PasswordResetCompleteRequest;
import com.lumira.api.auth.PasswordResetChallengeRequest;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.auth.VerificationBindRequest;
import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.CaptchaService;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.passkey.PasskeyCredentialAppService;
import com.lumira.saas.modules.system.internal.app.InternalSystemApplicationService;
import com.lumira.saas.modules.system.internal.infrastructure.JdbcInternalSystemRepository;
import com.lumira.saas.modules.auth.vo.LoginCodeChallengeVO;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.verification.WechatLoginSettingsService;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import com.lumira.api.system.LoginAuditRecordRequestDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import com.lumira.api.system.WechatLoginUserRequestDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.api.system.VerificationVerificationDTO;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalSystemControllerTest {

    private static InternalSystemApplicationService applicationService(MyBatisQueryOperations database) {
        return new InternalSystemApplicationService(new JdbcInternalSystemRepository(database));
    }

    private final UserDomainService userDomainService = mock(UserDomainService.class);
    private final MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
    private final IamUserService iamUserService = mock(IamUserService.class);
    private final PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
    private final SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
    private final PasskeyCredentialAppService passkeyCredentialAppService = mock(PasskeyCredentialAppService.class);
    private final LoginAuditService loginAuditService = mock(LoginAuditService.class);
    private final OperationAuditService operationAuditService = mock(OperationAuditService.class);
    private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final InternalSystemController controller = new InternalSystemController(
            userDomainService,
            iamUserService,
            permissionSnapshotService,
            mock(CaptchaService.class),
            verificationAppService,
            mock(WechatLoginSettingsService.class),
            passkeyCredentialAppService,
            applicationService(jdbcTemplate),
            passwordEncoder,
            loginAuditService,
            operationAuditService,
            mock(SecuritySettingsService.class),
            mock(PasswordPolicyService.class),
            authSessionStore,
            mock(ReadModelVersionService.class)
    );

    @Test
    void wechatAndDefaultRoleUpsertsShouldNotRewriteUserIdentity() throws Exception {
        String controllerSource = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/controller/InternalSystemController.java"));
        String repositorySource = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/internal/infrastructure/JdbcInternalSystemRepository.java"));

        assertThat(controllerSource)
                .doesNotContain("MyBatisQueryOperations")
                .doesNotContain("SqlRow")
                .doesNotContain("BeanPropertyRowMapper")
                .doesNotContain("jdbcTemplate")
                .doesNotContain("select ")
                .doesNotContain("insert ")
                .doesNotContain("update sys_")
                .doesNotContain("from sys_")
                .contains("requireWechatBindingOwnedByUser(user.getId(), userUuid, request)")
                .contains("requireWechatBindingAvailableForRegistration(normalizedRequest.unionid(), normalizedRequest.openid())")
                .contains("Wechat user changed, please retry")
                .contains("Wechat account is unavailable")
                .contains("Login code user changed, please retry")
                .contains("Wechat profile changed, please retry")
                .contains("requireDefaultRoleGranted(user.getId(), userUuid, roleId)")
                .contains("Default role binding changed, please retry");
        assertThat(repositorySource).doesNotContain("on duplicate key update user_id = values(user_id)");
        assertThat(repositorySource).doesNotContain("user_uuid = values(user_uuid),");
        assertThat(repositorySource).contains("unionid = case when user_id = values(user_id) and user_uuid = values(user_uuid)");
        assertThat(repositorySource).contains("updated_by = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id)");
        assertThat(repositorySource).contains("and u.status = 'ENABLED'");
        assertThat(repositorySource).contains("and b.user_uuid = ?");
        assertThat(repositorySource).contains("where ur.user_id = ?");
        assertThat(repositorySource).contains("and ur.user_uuid = ?");
        assertThat(repositorySource).contains("and ur.role_id = ?");
    }

    @Test
    void pluginPermissionUpsertsShouldBindPluginAndServicePrincipalContext() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/saas/modules/system/internal/infrastructure/JdbcInternalSystemRepository.java"));

        assertThat(source)
                .doesNotContain("plugin_code = values(plugin_code),")
                .doesNotContain("updated_by_uuid = values(updated_by_uuid),")
                .contains("source_type = 'PLUGIN' and plugin_code = values(plugin_code)")
                .contains("permission_name = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid)")
                .contains("permission_group = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid)")
                .contains("updated_by_uuid = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid)")
                .contains("deleted = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid)")
                .contains("updated_by_uuid = case when role_id = values(role_id) and permission_key = values(permission_key) and updated_by_uuid = values(updated_by_uuid)")
                .contains("deleted = case when role_id = values(role_id) and permission_key = values(permission_key) and updated_by_uuid = values(updated_by_uuid)");
    }

    @BeforeEach
    void authenticateInternalServiceByDefault() {
        authenticateInternalService();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalRequestRejectsMissingInternalServicePrincipal() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(controller::requireInternalServicePrincipal)
                .isInstanceOf(com.lumira.common.exception.BizException.class);
    }

    @Test
    void internalRequestAcceptsInternalServicePrincipal() {
        authenticateInternalService();

        controller.requireInternalServicePrincipal();
    }

    @Test
    void smtpRuntimeConfigValuesOnlyQueriesSmtpKeys() {
        MyBatisQueryOperations localJdbcTemplate = mock(MyBatisQueryOperations.class, invocation -> {
            if ("queryForList".equals(invocation.getMethod().getName())) {
                return List.of(
                        Map.of("config_key", "smtp.password", "config_value", "secret"),
                        Map.of("config_key", "smtp.host", "config_value", "smtp.example.com")
                );
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        InternalSystemController localController = new InternalSystemController(
                userDomainService,
                mock(IamUserService.class),
                mock(PermissionSnapshotService.class),
                mock(CaptchaService.class),
                mock(SystemVerificationAppService.class),
                mock(WechatLoginSettingsService.class),
                mock(PasskeyCredentialAppService.class),
                applicationService(localJdbcTemplate),
                mock(PasswordEncoder.class),
                mock(LoginAuditService.class),
                mock(OperationAuditService.class),
                mock(SecuritySettingsService.class),
                mock(PasswordPolicyService.class),
                mock(AuthSessionStore.class),
                mock(ReadModelVersionService.class)
        );

        Map<String, String> values = localController.smtpRuntimeConfigValues();

        assertThat(values)
                .containsEntry("smtp.password", "secret")
                .containsEntry("smtp.host", "smtp.example.com");
        verify(localJdbcTemplate).queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.eq("smtp.host"),
                org.mockito.ArgumentMatchers.eq("smtp.port"),
                org.mockito.ArgumentMatchers.eq("smtp.username"),
                org.mockito.ArgumentMatchers.eq("smtp.password"),
                org.mockito.ArgumentMatchers.eq("smtp.from"),
                org.mockito.ArgumentMatchers.eq("smtp.auth-enabled"),
                org.mockito.ArgumentMatchers.eq("smtp.starttls-enabled"),
                org.mockito.ArgumentMatchers.eq("smtp.ssl-enabled")
        );
    }

    @Test
    void wechatOfficialRuntimeConfigValuesOnlyQueriesWechatKeys() {
        MyBatisQueryOperations localJdbcTemplate = mock(MyBatisQueryOperations.class, invocation -> {
            if ("queryForList".equals(invocation.getMethod().getName())) {
                return List.of(
                        Map.of("config_key", "notification.wechat-official.app-secret", "config_value", "wechat-secret"),
                        Map.of("config_key", "notification.wechat-official.app-id", "config_value", "wx-app-id")
                );
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        InternalSystemController localController = new InternalSystemController(
                userDomainService,
                mock(IamUserService.class),
                mock(PermissionSnapshotService.class),
                mock(CaptchaService.class),
                mock(SystemVerificationAppService.class),
                mock(WechatLoginSettingsService.class),
                mock(PasskeyCredentialAppService.class),
                applicationService(localJdbcTemplate),
                mock(PasswordEncoder.class),
                mock(LoginAuditService.class),
                mock(OperationAuditService.class),
                mock(SecuritySettingsService.class),
                mock(PasswordPolicyService.class),
                mock(AuthSessionStore.class),
                mock(ReadModelVersionService.class)
        );

        Map<String, String> values = localController.wechatOfficialRuntimeConfigValues();

        assertThat(values)
                .containsEntry("notification.wechat-official.app-secret", "wechat-secret")
                .containsEntry("notification.wechat-official.app-id", "wx-app-id");
        verify(localJdbcTemplate).queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.eq("notification.wechat-official.enabled"),
                org.mockito.ArgumentMatchers.eq("notification.wechat-official.app-id"),
                org.mockito.ArgumentMatchers.eq("notification.wechat-official.app-secret"),
                org.mockito.ArgumentMatchers.eq("notification.wechat-official.template-id"),
                org.mockito.ArgumentMatchers.eq("notification.wechat-official.detail-url")
        );
    }

    @Test
    void aiPlatformConfigValuesOnlyQueriesAiSafeKeys() {
        MyBatisQueryOperations localJdbcTemplate = mock(MyBatisQueryOperations.class, invocation -> {
            if ("queryForList".equals(invocation.getMethod().getName())) {
                return List.of(
                        Map.of("config_key", "branding.website-name", "config_value", "Lumira"),
                        Map.of("config_key", "watermark.enabled", "config_value", "true")
                );
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        InternalSystemController localController = new InternalSystemController(
                userDomainService,
                mock(IamUserService.class),
                mock(PermissionSnapshotService.class),
                mock(CaptchaService.class),
                mock(SystemVerificationAppService.class),
                mock(WechatLoginSettingsService.class),
                mock(PasskeyCredentialAppService.class),
                applicationService(localJdbcTemplate),
                mock(PasswordEncoder.class),
                mock(LoginAuditService.class),
                mock(OperationAuditService.class),
                mock(SecuritySettingsService.class),
                mock(PasswordPolicyService.class),
                mock(AuthSessionStore.class),
                mock(ReadModelVersionService.class)
        );

        Map<String, String> values = localController.aiPlatformConfigValues(List.of(
                "branding.website-name",
                "jwt.secret",
                "watermark.enabled",
                "branding.website-name",
                " "
        ));

        assertThat(values)
                .containsEntry("branding.website-name", "Lumira")
                .containsEntry("watermark.enabled", "true");
        verify(localJdbcTemplate).queryForList(
                anyString(),
                org.mockito.ArgumentMatchers.eq("branding.website-name"),
                org.mockito.ArgumentMatchers.eq("watermark.enabled")
        );
    }

    @Test
    void aiVisibleBuiltinMenusOnlyReturnsAuthorizedTree() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(
                new PermissionSnapshotService.PermissionSnapshot(
                        "v11",
                        Set.of("dashboard:view"),
                        Set.of(3001L),
                        7L,
                        Set.of(7L),
                        Set.of(8L),
                        List.of(),
                        "/dashboard/home"
                )
        );
        when(jdbcTemplate.query(anyString(), any(BeanPropertyRowMapper.class))).thenReturn(List.of(
                menu(1L, 0L, "root", "Root", null, 1),
                menu(2L, 1L, "dashboard", "Dashboard", "dashboard:view", 1),
                menu(3L, 1L, "billing", "Billing", "billing:view", 2)
        ));

        List<MenuNodeDTO> menus = controller.aiVisibleBuiltinMenus(2001L, "user-uuid-2001");

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getMenuCode()).isEqualTo("root");
        assertThat(menus.get(0).getChildren())
                .extracting(MenuNodeDTO::getMenuCode)
                .containsExactly("dashboard");
        verify(permissionSnapshotService).loadSnapshot(2001L, "user-uuid-2001");
    }

    @Test
    void aiVisibleBuiltinMenusShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);

        assertThatThrownBy(() -> controller.aiVisibleBuiltinMenus(2001L, "user-uuid-2001"))
                .isInstanceOfSatisfying(com.lumira.common.exception.BizException.class, exception -> {
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                });
    }

    @Test
    void permissionSnapshotShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(null);

        assertThatThrownBy(() -> controller.permissionSnapshot(2001L, "user-uuid-2001"))
                .isInstanceOfSatisfying(com.lumira.common.exception.BizException.class, exception -> {
                    assertThat(exception.getMessage()).contains("Trusted user permission snapshot is unavailable");
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void userIdentitiesByIdsReturnsIdentityOnlySnapshot() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertThat(sql).doesNotContain("nickname");
                    assertThat(sql).doesNotContain("avatar_url");
                    assertThat(sql).doesNotContain("email");
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    Object mapped = rowMapper.mapRow(new SqlRow(Map.of(
                            "id", 2001L,
                            "uuid", "user-uuid",
                            "username", "bob",
                            "status", "ENABLED"
                    )), 0);
                    return List.of(mapped);
                });

        var users = controller.userIdentitiesByIds(List.of(2001L));

        assertThat(users).hasSize(1);
        assertThat(users.get(0).username()).isEqualTo("bob");
        assertThat(users.get(0).nickname()).isNull();
        assertThat(users.get(0).avatarUrl()).isNull();
        assertThat(users.get(0).email()).isNull();
        assertThat(users.get(0).passwordHash()).isNull();
    }

    @Test
    void roleNamesByIdsRejectsOversizedBatchBeforeQuery() {
        assertThatThrownBy(() -> controller.roleNamesByIds(
                java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList()
        )).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void userEmailRecipientsByIdsRejectsOversizedBatchBeforeQuery() {
        assertThatThrownBy(() -> controller.userEmailRecipientsByIds(
                java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList()
        )).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void userWechatRecipientsByIdsRejectsOversizedBatchBeforeQuery() {
        assertThatThrownBy(() -> controller.userWechatRecipientsByIds(
                java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList()
        )).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void roleUserIdentitiesRejectsInvalidRoleIdBeforeQuery() {
        assertThatThrownBy(() -> controller.roleUserIdentities(0L))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void roleUserIdentitiesReturnsEnabledIdentityOnlySnapshot() {
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                org.mockito.ArgumentMatchers.eq(3001L)
        ))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    assertThat(sql).contains("u.status = 'ENABLED'");
                    @SuppressWarnings("unchecked")
                    RowMapper<SystemUserSnapshotDTO> mapper = invocation.getArgument(1, RowMapper.class);
                    return List.of(mapper.mapRow(new SqlRow(Map.of(
                            "id", 2001L,
                            "uuid", "user-uuid-2001",
                            "username", "alice",
                            "status", "ENABLED"
                    )), 0));
                });

        assertThat(controller.roleUserIdentities(3001L))
                .singleElement()
                .satisfies(user -> {
                    assertThat(user.userId()).isEqualTo(2001L);
                    assertThat(user.userUuid()).isEqualTo("user-uuid-2001");
                    assertThat(user.username()).isEqualTo("alice");
                    assertThat(user.email()).isNull();
                    assertThat(user.nickname()).isNull();
                });
    }

    @Test
    void findUserIdentityByIdReturnsMinimalSnapshot() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid");
        user.setUsername("bob");
        user.setPasswordHash("encoded-password");
        user.setStatus("ENABLED");
        user.setEmail("bob@example.com");
        user.setNickname("Bob");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        var snapshot = controller.findUserIdentityById(2001L);

        assertThat(snapshot.userId()).isEqualTo(2001L);
        assertThat(snapshot.username()).isEqualTo("bob");
        assertThat(snapshot.email()).isNull();
        assertThat(snapshot.nickname()).isNull();
        assertThat(snapshot.passwordHash()).isNull();
    }

    @Test
    void findUserByIdReturnsMinimalSnapshotByDefault() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid");
        user.setUsername("bob");
        user.setPasswordHash("encoded-password");
        user.setStatus("ENABLED");
        user.setEmail("bob@example.com");
        user.setNickname("Bob");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        var snapshot = controller.findUserById(2001L);

        assertThat(snapshot.userId()).isEqualTo(2001L);
        assertThat(snapshot.username()).isEqualTo("bob");
        assertThat(snapshot.email()).isNull();
        assertThat(snapshot.nickname()).isNull();
        assertThat(snapshot.passwordHash()).isNull();
    }

    @Test
    void findUserProfileByIdReturnsProfileWithoutPasswordHash() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid");
        user.setUsername("bob");
        user.setPasswordHash("encoded-password");
        user.setStatus("ENABLED");
        user.setEmail("bob@example.com");
        user.setNickname("Bob");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        var snapshot = controller.findUserProfileById(2001L);

        assertThat(snapshot.userId()).isEqualTo(2001L);
        assertThat(snapshot.username()).isEqualTo("bob");
        assertThat(snapshot.email()).isEqualTo("bob@example.com");
        assertThat(snapshot.nickname()).isEqualTo("Bob");
        assertThat(snapshot.passwordHash()).isNull();
    }

    @Test
    void userHasEmailReturnsTrueOnlyForTrustedMatchingEnabledUser() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid");
        user.setUsername("bob");
        user.setStatus("ENABLED");
        user.setEmail("bob@example.com");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThat(controller.userHasEmail(2001L, "user-uuid")).isTrue();
        assertThat(controller.userHasEmail(2001L, "other-uuid")).isFalse();
        assertThat(controller.userHasEmail(2001L, " ")).isFalse();
    }

    @Test
    void userHasEmailReturnsFalseForDisabledOrMissingUser() {
        SysUserEntity disabledUser = new SysUserEntity();
        disabledUser.setId(2001L);
        disabledUser.setUuid("user-uuid");
        disabledUser.setUsername("bob");
        disabledUser.setStatus("DISABLED");
        disabledUser.setEmail("bob@example.com");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(disabledUser));
        when(userDomainService.findById(2002L)).thenReturn(Optional.empty());

        assertThat(controller.userHasEmail(2001L, "user-uuid")).isFalse();
        assertThat(controller.userHasEmail(2002L, "user-uuid")).isFalse();
    }

    @Test
    void findTargetUserUuidByIdReturnsNullWhenUserIsDisabled() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid");
        user.setStatus("DISABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThat(controller.findTargetUserUuidById(2001L)).isNull();
    }

    @Test
    void findTargetUserUuidByIdReturnsNullWhenIdentityIsIncomplete() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid(" ");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(userDomainService.findById(2002L)).thenReturn(Optional.empty());

        assertThat(controller.findTargetUserUuidById(2001L)).isNull();
        assertThat(controller.findTargetUserUuidById(2002L)).isNull();
    }

    @Test
    void findUserByIdRejectsInvalidIdBeforeLookup() {
        assertThatThrownBy(() -> controller.findUserById(0L))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(userDomainService, never()).findById(any());
    }

    @Test
    void findUserByIdRejectsSnapshotWithoutUuid() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUsername("bob");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.findUserById(2001L))
                .isInstanceOf(com.lumira.common.exception.BizException.class);
    }

    @Test
    void verifyPasswordLoginReturnsMatchWithoutExposingPasswordHash() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid");
        user.setUsername("alice");
        user.setPasswordHash("encoded-password");
        user.setStatus("ENABLED");
        when(userDomainService.findLoginUser("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded-password")).thenReturn(true);

        var verification = controller.verifyPasswordLogin("alice", "secret");

        assertThat(verification).isNotNull();
        assertThat(verification.passwordMatched()).isTrue();
        assertThat(verification.requiresPasswordChange()).isFalse();
        assertThat(verification.user()).isNotNull();
        assertThat(verification.user().passwordHash()).isNull();
    }

    @Test
    void requiresPasswordChangeReturnsExplicitCredentialState() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1001L);
        user.setUuid("user-uuid");
        user.setUsername("admin");
        user.setPasswordHash("encoded-password");
        user.setStatus("ENABLED");
        when(userDomainService.findById(1001L)).thenReturn(Optional.of(user));
        when(iamUserService.requiresPasswordChange(1001L, "user-uuid")).thenReturn(true);

        var required = controller.requiresInitialPasswordChange(1001L, "user-uuid");

        assertThat(required).isTrue();
        verify(passwordEncoder, never()).matches("123456", "encoded-password");
    }

    @Test
    void verifyPasswordLoginShouldNotRequirePasswordChangeForNonDefaultUserNamedAdmin() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2002L);
        user.setUuid("user-uuid-2002");
        user.setUsername("admin");
        user.setPasswordHash("encoded-password");
        user.setStatus("ENABLED");
        when(userDomainService.findLoginUser("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded-password")).thenReturn(true);

        var verification = controller.verifyPasswordLogin("admin", "secret");

        assertThat(verification).isNotNull();
        assertThat(verification.passwordMatched()).isTrue();
        assertThat(verification.requiresPasswordChange()).isFalse();
    }

    @Test
    void permissionSnapshotRejectsInvalidUserIdBeforeLookup() {
        assertThatThrownBy(() -> controller.permissionSnapshot(0L, "user-uuid-0"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(permissionSnapshotService);
    }

    @Test
    void permissionSnapshotRejectsMismatchedUserUuid() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.permissionSnapshot(2001L, "other-user-uuid"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(permissionSnapshotService, never()).loadSnapshot(any(), any());
    }

    @Test
    void permissionRoleSnapshotReturnsOnlyVersionAndRoleIds() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(permissionSnapshotService.loadSnapshot(2001L, "user-uuid-2001")).thenReturn(
                new PermissionSnapshotService.PermissionSnapshot(
                        "v11",
                        Set.of("system:user:view"),
                        Set.of(3001L, 3002L),
                        7L,
                        Set.of(7L),
                        Set.of(8L),
                        List.of(),
                        "/dashboard/home"
                )
        );

        var snapshot = controller.permissionRoleSnapshot(2001L, "user-uuid-2001");

        assertThat(snapshot.version()).isEqualTo("v11");
        assertThat(snapshot.roleIds()).containsExactlyInAnyOrder(3001L, 3002L);
        assertThat(snapshot.permissions()).isEmpty();
        assertThat(snapshot.primaryDeptId()).isNull();
        assertThat(snapshot.deptIds()).isEmpty();
        assertThat(snapshot.descendantDeptIds()).isEmpty();
        assertThat(snapshot.dataScopes()).isEmpty();
        assertThat(snapshot.defaultHomePath()).isNull();
    }

    @Test
    void userRoleOptionsReturnsTrustedRoleOptions() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<CurrentUserRoleOptionDTO>>any(), eq(2001L), eq("user-uuid-2001"))).thenReturn(
                List.of(new CurrentUserRoleOptionDTO(3001L, "team_operator", "Team Operator", "FUNCTIONAL", 2, "/workflows/tasks"))
        );

        var roles = controller.userRoleOptions(2001L, "user-uuid-2001");

        assertThat(roles).containsExactly(new CurrentUserRoleOptionDTO(3001L, "team_operator", "Team Operator", "FUNCTIONAL", 2, "/workflows/tasks"));
    }

    @Test
    void simulatedRolePermissionSnapshotRequiresTrustedGrantedRole() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(permissionSnapshotService.loadGrantedRoleSnapshot(2001L, "user-uuid-2001", 3001L)).thenReturn(
                new PermissionSnapshotService.PermissionSnapshot(
                        "role-v2",
                        Set.of("team:view"),
                        Set.of(3001L),
                        null,
                        Set.of(),
                        Set.of(),
                        List.of(),
                        "/team"
                )
        );

        var snapshot = controller.simulatedRolePermissionSnapshot(2001L, "user-uuid-2001", 3001L);

        assertThat(snapshot.version()).isEqualTo("role-v2");
        assertThat(snapshot.permissions()).containsExactly("team:view");
        assertThat(snapshot.roleIds()).containsExactly(3001L);
        assertThat(snapshot.defaultHomePath()).isEqualTo("/team");
        verify(permissionSnapshotService).loadGrantedRoleSnapshot(2001L, "user-uuid-2001", 3001L);
        verify(permissionSnapshotService, never()).loadRoleSnapshot(3001L);
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Boolean.class), eq(2001L), eq("user-uuid-2001"), eq(3001L));
    }

    @Test
    void simulatedRolePermissionSnapshotMapsGrantLookupFailureFromRealService() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        MyBatisQueryOperations failingJdbcTemplate = mock(MyBatisQueryOperations.class);
        when(failingJdbcTemplate.exists(
                anyString(),
                eq(2001L),
                eq("user-uuid-2001"),
                eq(3001L)
        )).thenThrow(new IllegalStateException("database unavailable"));
        PermissionSnapshotService realPermissionSnapshotService = new PermissionSnapshotService(
                failingJdbcTemplate,
                mock(CacheTemplate.class),
                new ObjectMapper().findAndRegisterModules()
        );
        InternalSystemController localController = new InternalSystemController(
                userDomainService,
                iamUserService,
                realPermissionSnapshotService,
                mock(CaptchaService.class),
                verificationAppService,
                mock(WechatLoginSettingsService.class),
                passkeyCredentialAppService,
                applicationService(failingJdbcTemplate),
                passwordEncoder,
                loginAuditService,
                operationAuditService,
                mock(SecuritySettingsService.class),
                mock(PasswordPolicyService.class),
                authSessionStore,
                mock(ReadModelVersionService.class)
        );

        BizException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class,
                () -> localController.simulatedRolePermissionSnapshot(2001L, "user-uuid-2001", 3001L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PERMISSION_SNAPSHOT_ERROR);
    }

    @Test
    void listLoginSecondFactorOptionsRejectsMismatchedUserUuid() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.listLoginSecondFactorOptions(2001L, "other-user-uuid"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(verificationAppService);
    }

    @Test
    void registerPluginPermissionsUsesServicePrincipalAuditIdentity() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenReturn(List.of(1L));

        controller.registerPluginPermissions(new PluginPermissionRegistrationRequestDTO(
                "sensitive-words",
                List.of(new PluginPermissionRegistrationRequestDTO.Permission(
                        "plugin:sensitive-words:view",
                        "View sensitive words",
                        "Sensitive Words"
                ))
        ));

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(anyString(), paramsCaptor.capture());
        List<Object[]> allParams = paramsCaptor.getAllValues();
        assertThat(allParams.get(0))
                .containsExactly(
                        "plugin:sensitive-words:view",
                        "View sensitive words",
                        "Sensitive Words",
                        "sensitive-words",
                        0L,
                        "00000000-0000-0000-0000-000000000000",
                        0L,
                        "00000000-0000-0000-0000-000000000000"
                );
        assertThat(allParams.get(1))
                .containsExactly(
                        1L,
                        "plugin:sensitive-words:view",
                        0L,
                        "00000000-0000-0000-0000-000000000000",
                        0L,
                        "00000000-0000-0000-0000-000000000000"
                );
        verify(permissionSnapshotService).invalidatePermissions();
    }

    @Test
    void registerPluginPermissionsRejectsPermissionWriteContextMismatch() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        when(jdbcTemplate.exists(anyString(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> controller.registerPluginPermissions(new PluginPermissionRegistrationRequestDTO(
                "sensitive-words",
                List.of(new PluginPermissionRegistrationRequestDTO.Permission(
                        "plugin:sensitive-words:view",
                        "View sensitive words",
                        "Sensitive Words"
                ))
        ))).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Plugin permission changed");

        verify(permissionSnapshotService, never()).invalidatePermissions();
    }

    @Test
    void passkeyCredentialAssertionRejectsBlankIdBeforeLookup() {
        assertThatThrownBy(() -> controller.passkeyCredentialAssertion(" "))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(passkeyCredentialAppService);
    }

    @Test
    void savePasskeyCredentialRejectsNullRequestBeforeServiceCall() {
        assertThatThrownBy(() -> controller.savePasskeyCredential(null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(passkeyCredentialAppService);
    }

    @Test
    void savePasskeyCredentialRejectsUserUuidMismatchBeforeServiceCall() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        PasskeyCredentialSaveRequestDTO request = new PasskeyCredentialSaveRequestDTO(
                2001L,
                "other-user-uuid",
                "handle",
                "credential",
                "public-key",
                1L,
                null,
                false,
                false,
                "Laptop"
        );

        assertThatThrownBy(() -> controller.savePasskeyCredential(request))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("identity mismatch");

        verify(passkeyCredentialAppService, never()).create(any());
    }

    @Test
    void updatePasskeyCredentialUsageRejectsInvalidCredentialIdBeforeServiceCall() {
        assertThatThrownBy(() -> controller.updatePasskeyCredentialUsage(
                new PasskeyCredentialUsageRequestDTO(0L, 2001L, "user-uuid-2001", 1L, false, false)
        )).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(passkeyCredentialAppService);
    }

    @Test
    void updatePasskeyCredentialUsageRejectsUserUuidMismatchBeforeServiceCall() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.updatePasskeyCredentialUsage(
                new PasskeyCredentialUsageRequestDTO(100L, 2001L, "other-user-uuid", 1L, false, false)
        ))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("identity mismatch");

        verify(passkeyCredentialAppService, never()).updateUsage(any());
    }

    @Test
    void passkeyCredentialsRejectsUserUuidMismatchBeforeServiceCall() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.passkeyCredentials(2001L, "other-user-uuid"))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("identity mismatch");

        verify(passkeyCredentialAppService, never()).list(any(), any());
    }

    @Test
    void renamePasskeyCredentialUsesTrustedUserIdAfterUuidCheck() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        controller.renamePasskeyCredential(88L, 2001L, "user-uuid-2001", "Laptop");

        verify(passkeyCredentialAppService).rename(88L, 2001L, "user-uuid-2001", "Laptop");
    }

    @Test
    void wechatLoginRejectsNullRequestBeforeLookup() {
        assertThatThrownBy(() -> controller.resolveWechatLoginUser(null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(userDomainService);
    }

    @Test
    void wechatLoginRejectsBlankOpenidBeforeLookup() {
        assertThatThrownBy(() -> controller.resolveWechatLoginUser(new WechatLoginUserRequestDTO(" ", "union", "snsapi_login")))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(userDomainService);
    }

    @Test
    void wechatLoginShouldRejectBindingOwnershipMismatchAfterUpsert() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("wechat-user");
        user.setStatus("ENABLED");
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of(2001L));
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));
        when(jdbcTemplate.exists(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(false);

        assertThatThrownBy(() -> controller.resolveWechatLoginUser(
                new WechatLoginUserRequestDTO("openid-1", "union-1", "snsapi_login")
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Wechat binding changed");

        verify(iamUserService, never()).updateProfile(any());
    }

    @Test
    void wechatLoginShouldRejectDisabledHistoricalBindingBeforeRegistration() {
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());
        when(jdbcTemplate.exists(
                anyString(),
                any(),
                any(),
                any()
        )).thenReturn(true);

        assertThatThrownBy(() -> controller.resolveWechatLoginUser(
                new WechatLoginUserRequestDTO("openid-disabled", "union-disabled", "snsapi_login")
        )).isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("Wechat account is unavailable");

        verify(userDomainService, never()).findLoginUser(anyString());
        verify(iamUserService, never()).syncSysUser(any(), anyString());
        verify(permissionSnapshotService, never()).invalidatePermissions();
    }

    @Test
    void loginCodeChallengeNormalizesAccountBeforeLookup() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice@example.com");
        user.setEmail("alice@example.com");
        user.setStatus("ENABLED");
        LoginCodeChallengeVO challenge = new LoginCodeChallengeVO();
        challenge.setLoginType("email");
        challenge.setChallengeId("challenge-1");
        when(iamUserService.detectIdentityType(" Alice@Example.COM ")).thenReturn(IamUserService.IDENTITY_EMAIL);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, " Alice@Example.COM ")).thenReturn("alice@example.com");
        when(userDomainService.findLoginUser("alice@example.com")).thenReturn(Optional.of(user));
        when(verificationAppService.startLoginCodeChallenge(user, "email")).thenReturn(challenge);

        var response = controller.loginCodeChallenge(" Alice@Example.COM ", " EMAIL ");

        assertThat(response.getChallengeId()).isEqualTo("challenge-1");
        verify(userDomainService).findLoginUser("alice@example.com");
        verify(verificationAppService).startLoginCodeChallenge(user, "email");
    }

    @Test
    void loginCodeChallengeRejectsMismatchedAccountBeforeLookup() {
        when(iamUserService.detectIdentityType("not-an-email")).thenReturn(IamUserService.IDENTITY_USERNAME);

        assertThatThrownBy(() -> controller.loginCodeChallenge("not-an-email", "email"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(userDomainService);
        verifyNoInteractions(verificationAppService);
    }

    @Test
    void loginCodeChallengeShouldNotAutoRegisterUnknownEmailAccount() {
        when(iamUserService.detectIdentityType("missing@example.com")).thenReturn(IamUserService.IDENTITY_EMAIL);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, "missing@example.com")).thenReturn("missing@example.com");
        when(userDomainService.findLoginUser("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.loginCodeChallenge("missing@example.com", "email"))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessage("账号不存在或暂不支持该登录方式");

        verify(userDomainService).findLoginUser("missing@example.com");
        verify(verificationAppService, never()).startLoginCodeChallenge(any(), anyString());
    }

    @Test
    void loginCodeChallengeShouldNotRevealDisabledEmailAccount() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2002L);
        user.setUuid("user-uuid-2002");
        user.setUsername("disabled@example.com");
        user.setEmail("disabled@example.com");
        user.setStatus("DISABLED");
        when(iamUserService.detectIdentityType("disabled@example.com")).thenReturn(IamUserService.IDENTITY_EMAIL);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, "disabled@example.com")).thenReturn("disabled@example.com");
        when(userDomainService.findLoginUser("disabled@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.loginCodeChallenge("disabled@example.com", "email"))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessage("账号不存在或暂不支持该登录方式");

        verify(userDomainService).findLoginUser("disabled@example.com");
        verify(verificationAppService, never()).startLoginCodeChallenge(any(), anyString());
    }

    @Test
    void loginCodeChallengeShouldStartPendingSmsChallengeForUnknownMobileAccount() {
        LoginCodeChallengeVO challenge = new LoginCodeChallengeVO();
        challenge.setLoginType("sms");
        challenge.setChallengeId("challenge-sms-pending");
        when(iamUserService.detectIdentityType("13800138000")).thenReturn(IamUserService.IDENTITY_MOBILE);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_MOBILE, "13800138000")).thenReturn("13800138000");
        when(userDomainService.findLoginUser("13800138000")).thenReturn(Optional.empty());
        when(verificationAppService.startPendingLoginCodeChallenge("13800138000", "sms")).thenReturn(challenge);

        var response = controller.loginCodeChallenge("13800138000", "sms");

        assertThat(response.getChallengeId()).isEqualTo("challenge-sms-pending");
        verify(verificationAppService).startPendingLoginCodeChallenge("13800138000", "sms");
        verify(verificationAppService, never()).startLoginCodeChallenge(any(), anyString());
    }

    @Test
    void completeLoginCodeLoginShouldRegisterPendingSmsUserAfterVerification() {
        MyBatisQueryOperations localJdbcTemplate = mock(MyBatisQueryOperations.class, invocation -> {
            String methodName = invocation.getMethod().getName();
            if ("update".equals(methodName)) {
                return 1;
            }
            if ("queryForObject".equals(methodName)) {
                return 3002L;
            }
            if ("exists".equals(methodName)) {
                return true;
            }
            if ("queryForList".equals(methodName)) {
                if (invocation.getArguments().length > 1 && invocation.getArguments()[1] == String.class) {
                    return List.of("dashboard:view");
                }
                return List.of();
            }
            if ("query".equals(methodName)) {
                String sql = String.valueOf(invocation.getArguments()[0]);
                if (sql.contains("from sys_role")) {
                    @SuppressWarnings("unchecked")
                    com.lumira.saas.infrastructure.persistence.mybatis.ResultSetExtractor<Object> extractor =
                            (com.lumira.saas.infrastructure.persistence.mybatis.ResultSetExtractor<Object>) invocation.getArguments()[1];
                    return extractor.extractData(new com.lumira.saas.infrastructure.persistence.mybatis.SqlRowCursor(List.of(
                            Map.of("id", 3001L, "role_code", "commonuser", "role_type", "BUSINESS")
                    )));
                }
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        InternalSystemController localController = new InternalSystemController(
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                mock(CaptchaService.class),
                verificationAppService,
                mock(WechatLoginSettingsService.class),
                passkeyCredentialAppService,
                applicationService(localJdbcTemplate),
                passwordEncoder,
                loginAuditService,
                operationAuditService,
                mock(SecuritySettingsService.class),
                mock(PasswordPolicyService.class),
                authSessionStore,
                mock(ReadModelVersionService.class)
        );
        authenticateInternalService();
        SysUserEntity createdUser = new SysUserEntity();
        createdUser.setId(3002L);
        createdUser.setUuid("user-uuid-3002");
        createdUser.setUsername("sms_13800138000");
        createdUser.setMobile("13800138000");
        createdUser.setStatus("ENABLED");
        when(verificationAppService.completePendingLoginCodeLoginIfPresent(any())).thenReturn(Optional.of(
                new SystemVerificationAppService.PendingLoginCodeVerification("13800138000", "sms", "验证成功")
        ));
        when(iamUserService.detectIdentityType("13800138000")).thenReturn(IamUserService.IDENTITY_MOBILE);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_MOBILE, "13800138000")).thenReturn("13800138000");
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(userDomainService.findLoginUser("13800138000")).thenReturn(Optional.empty());
        when(userDomainService.findLoginUser("sms_13800138000")).thenReturn(Optional.empty());
        when(userDomainService.findById(3002L)).thenReturn(Optional.of(createdUser));

        VerificationVerificationDTO response = localController.completeLoginCodeLogin(
                new LoginCodeCompleteRequest("challenge-1", "123456")
        );

        assertThat(response.verified()).isTrue();
        assertThat(response.userId()).isEqualTo(3002L);
        assertThat(response.userUuid()).isEqualTo("user-uuid-3002");
        verify(permissionSnapshotService).invalidatePermissions();
        verify(iamUserService).createUserWithIdentity(createdUser, "13800138000", "LOGIN_CODE_REGISTER");
        verify(verificationAppService, never()).completeLoginCodeLogin(any());
    }

    @Test
    void completePasswordResetShouldRevokeExistingUserSessionsAfterPasswordUpdate() {
        PasswordResetCompleteRequest request = new PasswordResetCompleteRequest("challenge-1", "123456", "NewPassword!234");
        when(verificationAppService.completeLoginCodeLogin(any())).thenReturn(systemVerification(true, 42L, "user-uuid-42"));
        when(passwordEncoder.encode("NewPassword!234")).thenReturn("encoded-password");
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(controller.completePasswordReset(request)).isTrue();

        verify(iamUserService).upsertPasswordCredential(42L, "user-uuid-42", "encoded-password");
        verify(authSessionStore).revokeUserSessions(42L, "user-uuid-42", true);
    }

    @Test
    void unbindVerificationProviderShouldRejectMismatchedFactorCodeBeforeDelegating() {
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest("email", "challenge-1", "123456");

        assertThatThrownBy(() -> controller.unbindVerificationProvider(42L, "user-uuid-42", "totp", request))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(verificationAppService);
        verifyNoInteractions(userDomainService);
    }

    @Test
    void unbindVerificationProviderShouldRequireVerifiedChallengeBeforeDelegating() {
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest("totp", "challenge-1", "123456");
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUuid("user-uuid-42");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user));
        when(verificationAppService.unbind(42L, "user-uuid-42", "totp", "challenge-1", "123456")).thenReturn(true);

        assertThat(controller.unbindVerificationProvider(42L, "user-uuid-42", "totp", request)).isTrue();

        verify(verificationAppService).unbind(42L, "user-uuid-42", "totp", "challenge-1", "123456");
    }

    @Test
    void bindVerificationProviderShouldDelegateVerificationRequest() {
        VerificationBindRequest request = new VerificationBindRequest("Password!23", null, null, null);
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUuid("user-uuid-42");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user));
        when(verificationAppService.bind(42L, "user-uuid-42", "totp", "Password!23", null, null, null))
                .thenReturn(new com.lumira.saas.modules.system.vo.SystemVO.VerificationChallengeVO());

        controller.bindVerificationProvider(42L, "user-uuid-42", "totp", request);

        verify(verificationAppService).bind(42L, "user-uuid-42", "totp", "Password!23", null, null, null);
    }

    @Test
    void loginAuditRejectsInvalidUserIdBeforeDelegating() {
        assertThatThrownBy(() -> controller.recordLoginAudit(new LoginAuditRecordRequestDTO(
                0L,
                "invalid-uuid",
                "alice",
                "PASSWORD",
                "SUCCESS",
                null,
                "127.0.0.1",
                "agent"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(loginAuditService);
    }

    @Test
    void loginAuditUsesTrustedUserSnapshotWhenUserUuidMatches() {
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUuid("real-uuid");
        user.setUsername("real-alice");
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user));

        controller.recordLoginAudit(new LoginAuditRecordRequestDTO(
                42L,
                "real-uuid",
                "fake-alice",
                "PASSWORD",
                "SUCCESS",
                null,
                "127.0.0.1",
                "agent"
        ));

        verify(loginAuditService).log(
                42L,
                "real-uuid",
                "real-alice",
                "PASSWORD",
                "SUCCESS",
                null,
                "127.0.0.1",
                "agent"
        );
    }

    @Test
    void loginAuditShouldNotTrustUserUuidWithoutUserId() {
        controller.recordLoginAudit(new LoginAuditRecordRequestDTO(
                null,
                "forged-user-uuid",
                "system-job",
                "PASSWORD",
                "FAIL",
                "bad credentials",
                "127.0.0.1",
                "agent"
        ));

        verify(loginAuditService).log(
                null,
                null,
                "system-job",
                "PASSWORD",
                "FAIL",
                "bad credentials",
                "127.0.0.1",
                "agent"
        );
        verifyNoInteractions(userDomainService);
    }

    @Test
    void loginAuditRejectsUserUuidMismatchBeforeDelegating() {
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUuid("real-uuid");
        user.setUsername("real-alice");
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.recordLoginAudit(new LoginAuditRecordRequestDTO(
                42L,
                "fake-uuid",
                "fake-alice",
                "PASSWORD",
                "SUCCESS",
                null,
                "127.0.0.1",
                "agent"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(loginAuditService);
    }

    @Test
    void loginAuditRejectsKnownUserWithoutUuidBeforeDelegating() {
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUsername("real-alice");
        when(userDomainService.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.recordLoginAudit(new LoginAuditRecordRequestDTO(
                42L,
                "fake-uuid",
                "fake-alice",
                "PASSWORD",
                "SUCCESS",
                null,
                "127.0.0.1",
                "agent"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(loginAuditService);
    }

    @Test
    void loginAuditRejectsUnknownUserBeforeDelegating() {
        when(userDomainService.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.recordLoginAudit(new LoginAuditRecordRequestDTO(
                42L,
                "fake-uuid",
                "fake-alice",
                "PASSWORD",
                "SUCCESS",
                null,
                "127.0.0.1",
                "agent"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(loginAuditService);
    }

    @Test
    void operationAuditRejectsInvalidUserIdBeforeDelegating() {
        assertThatThrownBy(() -> controller.recordOperationAudit(new OperationAuditRecordRequestDTO(
                -1L,
                "invalid-uuid",
                "alice",
                "system",
                "update",
                "UPDATE",
                "SUCCESS",
                "detail"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(operationAuditService);
    }

    @Test
    void operationAuditUsesTrustedUserSnapshotWhenUserUuidMatches() {
        SysUserEntity user = new SysUserEntity();
        user.setId(77L);
        user.setUuid("operator-uuid");
        user.setUsername("operator");
        when(userDomainService.findById(77L)).thenReturn(Optional.of(user));

        controller.recordOperationAudit(new OperationAuditRecordRequestDTO(
                77L,
                "operator-uuid",
                "fake-operator",
                "system",
                "update",
                "UPDATE",
                "SUCCESS",
                "detail"
        ));

        verify(operationAuditService).log(
                77L,
                "operator-uuid",
                "operator",
                "system",
                "update",
                "UPDATE",
                "SUCCESS",
                "detail"
        );
    }

    @Test
    void operationAuditShouldNotTrustUserUuidWithoutUserId() {
        controller.recordOperationAudit(new OperationAuditRecordRequestDTO(
                null,
                "forged-user-uuid",
                "system-job",
                "system",
                "sync",
                "CREATE",
                "SUCCESS",
                "detail"
        ));

        verify(operationAuditService).log(
                null,
                null,
                "system-job",
                "system",
                "sync",
                "CREATE",
                "SUCCESS",
                "detail"
        );
        verifyNoInteractions(userDomainService);
    }

    @Test
    void operationAuditRejectsUserUuidMismatchBeforeDelegating() {
        SysUserEntity user = new SysUserEntity();
        user.setId(77L);
        user.setUuid("operator-uuid");
        user.setUsername("operator");
        when(userDomainService.findById(77L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.recordOperationAudit(new OperationAuditRecordRequestDTO(
                77L,
                "fake-uuid",
                "fake-operator",
                "system",
                "update",
                "UPDATE",
                "SUCCESS",
                "detail"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(operationAuditService);
    }

    @Test
    void operationAuditRejectsKnownUserWithoutUuidBeforeDelegating() {
        SysUserEntity user = new SysUserEntity();
        user.setId(77L);
        user.setUsername("operator");
        when(userDomainService.findById(77L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.recordOperationAudit(new OperationAuditRecordRequestDTO(
                77L,
                "fake-uuid",
                "fake-operator",
                "system",
                "update",
                "UPDATE",
                "SUCCESS",
                "detail"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(operationAuditService);
    }

    @Test
    void operationAuditRejectsUnknownUserBeforeDelegating() {
        when(userDomainService.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.recordOperationAudit(new OperationAuditRecordRequestDTO(
                77L,
                "fake-uuid",
                "fake-operator",
                "system",
                "update",
                "UPDATE",
                "SUCCESS",
                "detail"
        ))).isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(operationAuditService);
    }

    @Test
    void verificationProviderRejectsInvalidUserIdBeforeLookup() {
        assertThatThrownBy(() -> controller.verificationProvider(-1L, "user-uuid-1", "email"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verifyNoInteractions(verificationAppService);
    }

    @Test
    void verificationProviderRejectsUserUuidMismatchBeforeLookup() {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("alice");
        user.setStatus("ENABLED");
        when(userDomainService.findById(2001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.verificationProvider(2001L, "other-user-uuid", "email"))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("identity mismatch");

        verifyNoInteractions(verificationAppService);
    }

    @Test
    void passwordResetChallengeShouldNotRevealUnknownAccount() {
        when(userDomainService.findLoginUser("missing-user")).thenReturn(Optional.empty());
        when(iamUserService.detectIdentityType("missing@example.com")).thenReturn(IamUserService.IDENTITY_EMAIL);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, "missing@example.com")).thenReturn("missing@example.com");

        assertThatThrownBy(() -> controller.passwordResetChallenge(
                new PasswordResetChallengeRequest("missing-user", "email", "missing@example.com")
        ))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessage("账号或绑定的联系方式不匹配");

        verify(verificationAppService, never()).startLoginCodeChallenge(any(), anyString());
    }

    @Test
    void passwordResetChallengeShouldNotRevealBoundContactMismatch() {
        SysUserEntity user = new SysUserEntity();
        user.setId(42L);
        user.setUuid("user-uuid-42");
        user.setUsername("jane");
        user.setStatus("ENABLED");
        user.setEmail("jane@example.com");
        when(userDomainService.findLoginUser("jane")).thenReturn(Optional.of(user));
        when(iamUserService.detectIdentityType("other@example.com")).thenReturn(IamUserService.IDENTITY_EMAIL);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, "other@example.com")).thenReturn("other@example.com");
        when(iamUserService.detectIdentityType("jane@example.com")).thenReturn(IamUserService.IDENTITY_EMAIL);
        when(iamUserService.normalizeIdentifier(IamUserService.IDENTITY_EMAIL, "jane@example.com")).thenReturn("jane@example.com");

        assertThatThrownBy(() -> controller.passwordResetChallenge(
                new PasswordResetChallengeRequest("jane", "email", "other@example.com")
        ))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessage("账号或绑定的联系方式不匹配");

        verify(verificationAppService, never()).startLoginCodeChallenge(any(), anyString());
    }

    private com.lumira.saas.modules.system.vo.SystemVO.MenuVO menu(
            Long id,
            Long parentId,
            String menuCode,
            String menuName,
            String permissionKey,
            Integer sortNo
    ) {
        com.lumira.saas.modules.system.vo.SystemVO.MenuVO menu = new com.lumira.saas.modules.system.vo.SystemVO.MenuVO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuCode(menuCode);
        menu.setMenuName(menuName);
        menu.setPermissionKey(permissionKey);
        menu.setSortNo(sortNo);
        return menu;
    }

    private com.lumira.saas.modules.system.vo.SystemVO.VerificationVerificationVO systemVerification(boolean verified, Long userId, String userUuid) {
        com.lumira.saas.modules.system.vo.SystemVO.VerificationVerificationVO verification = new com.lumira.saas.modules.system.vo.SystemVO.VerificationVerificationVO();
        verification.setVerified(verified);
        verification.setUserId(userId);
        verification.setUserUuid(userUuid);
        verification.setMessage("ok");
        return verification;
    }

    private void authenticateInternalService() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", null, "internal", 0, false, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(internalService, "internal-token", java.util.Set.of())
        );
    }
}
