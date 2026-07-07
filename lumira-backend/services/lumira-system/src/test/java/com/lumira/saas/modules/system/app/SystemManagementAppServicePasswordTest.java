package com.lumira.saas.modules.system.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService.SecuritySettingsSnapshot;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserAccount;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dto.ProfileDTO;
import com.lumira.saas.modules.system.plugin.SystemPluginViewService;
import com.lumira.saas.modules.system.role.app.SystemRoleManagementAppService;
import com.lumira.saas.modules.system.user.app.SystemUserManagementAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.user.domain.UserDomainService;
import com.lumira.saas.modules.user.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemManagementAppServicePasswordTest {

    @Test
    void shouldUpdatePasswordAndRevokeOtherSessions() {
        SysUserEntity user = buildUser("OldPass1!");
        RecordingAuthSessionStore authSessionStore = new RecordingAuthSessionStore();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingPasswordPolicyService passwordPolicyService = new RecordingPasswordPolicyService();
        RecordingOperationAuditService operationAuditService = new RecordingOperationAuditService();
        SystemManagementAppService service = new SystemManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                new StubUserDomainService(user),
                null,
                null,
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                operationAuditService,
                null,
                passwordPolicyService,
                new StubIamUserService(jdbcTemplate)
        );

        CurrentUser currentUser = buildCurrentUser();
        ProfileDTO.PasswordUpdateRequest request = buildRequest("OldPass1!", "NewPass1!", "NewPass1!");

        boolean updated = service.updateCurrentUserPassword(currentUser, request);

        assertTrue(updated);
        assertEquals("NewPass1!", passwordPolicyService.validatedPassword);
        assertEquals(
                "update sys_user set password_hash = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                jdbcTemplate.lastSql
        );
        assertEquals("NewPass1!", jdbcTemplate.lastArgs[0]);
        assertEquals(2001L, jdbcTemplate.lastArgs[1]);
        assertEquals("user-uuid-2001", jdbcTemplate.lastArgs[2]);
        assertEquals(2001L, jdbcTemplate.lastArgs[4]);
        assertEquals("user-uuid-2001", jdbcTemplate.lastArgs[5]);
        assertEquals(2001L, authSessionStore.resolvedPasswordChangeUserId);
        assertEquals("user-uuid-2001", authSessionStore.resolvedPasswordChangeUserUuid);
        assertEquals("session-1", authSessionStore.resolvedPasswordChangeSessionId);
        assertTrue(authSessionStore.resolvedPasswordChangePublishChange);
        assertEquals(2001L, authSessionStore.revokedUserId);
        assertEquals("user-uuid-2001", authSessionStore.revokedUserUuid);
        assertEquals("session-1", authSessionStore.excludedSessionId);
        assertTrue(authSessionStore.publishChange);
        assertNotNull(operationAuditService.lastMessage);
    }

    @Test
    void shouldRejectWrongCurrentPasswordBeforeUpdatingSessions() {
        SysUserEntity user = buildUser("OldPass1!");
        RecordingAuthSessionStore authSessionStore = new RecordingAuthSessionStore();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingPasswordPolicyService passwordPolicyService = new RecordingPasswordPolicyService();
        SystemManagementAppService service = new SystemManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                new StubUserDomainService(user),
                null,
                null,
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                new RecordingOperationAuditService(),
                null,
                passwordPolicyService,
                new StubIamUserService(jdbcTemplate)
        );

        CurrentUser currentUser = buildCurrentUser();
        ProfileDTO.PasswordUpdateRequest request = buildRequest("WrongPass1!", "NewPass1!", "NewPass1!");

        assertThrows(BizException.class, () -> service.updateCurrentUserPassword(currentUser, request));
        assertEquals(null, passwordPolicyService.validatedPassword);
        assertEquals(null, jdbcTemplate.lastSql);
        assertEquals(null, authSessionStore.revokedUserId);
    }

    @Test
    void shouldRejectUnauthenticatedUserBeforeUserLookup() {
        SysUserEntity user = buildUser("OldPass1!");
        StubUserDomainService userDomainService = new StubUserDomainService(user);
        RecordingAuthSessionStore authSessionStore = new RecordingAuthSessionStore();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingPasswordPolicyService passwordPolicyService = new RecordingPasswordPolicyService();
        SystemManagementAppService service = new SystemManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                null,
                null,
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                new RecordingOperationAuditService(),
                null,
                passwordPolicyService,
                new StubIamUserService(jdbcTemplate)
        );
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setAuthenticated(false);
        ProfileDTO.PasswordUpdateRequest request = buildRequest("OldPass1!", "NewPass1!", "NewPass1!");

        BizException exception = assertThrows(BizException.class, () -> service.updateCurrentUserPassword(currentUser, request));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, userDomainService.findByIdCalls);
        assertEquals(null, passwordPolicyService.validatedPassword);
        assertEquals(null, jdbcTemplate.lastSql);
        assertEquals(null, authSessionStore.revokedUserId);
    }

    @Test
    void shouldRejectUserWithoutSessionVersionBeforeUserLookup() {
        SysUserEntity user = buildUser("OldPass1!");
        StubUserDomainService userDomainService = new StubUserDomainService(user);
        RecordingAuthSessionStore authSessionStore = new RecordingAuthSessionStore();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingPasswordPolicyService passwordPolicyService = new RecordingPasswordPolicyService();
        SystemManagementAppService service = new SystemManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                null,
                null,
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                new RecordingOperationAuditService(),
                null,
                passwordPolicyService,
                new StubIamUserService(jdbcTemplate)
        );
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setSessionVersion(null);
        ProfileDTO.PasswordUpdateRequest request = buildRequest("OldPass1!", "NewPass1!", "NewPass1!");

        BizException exception = assertThrows(BizException.class, () -> service.updateCurrentUserPassword(currentUser, request));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, userDomainService.findByIdCalls);
        assertEquals(null, passwordPolicyService.validatedPassword);
        assertEquals(null, jdbcTemplate.lastSql);
        assertEquals(null, authSessionStore.revokedUserId);
    }

    @Test
    void shouldRejectMismatchedSessionUserUuidBeforePasswordUpdate() {
        SysUserEntity user = buildUser("OldPass1!");
        StubUserDomainService userDomainService = new StubUserDomainService(user);
        RecordingAuthSessionStore authSessionStore = new RecordingAuthSessionStore();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingPasswordPolicyService passwordPolicyService = new RecordingPasswordPolicyService();
        SystemManagementAppService service = new SystemManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                null,
                null,
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                new RecordingOperationAuditService(),
                null,
                passwordPolicyService,
                new StubIamUserService(jdbcTemplate)
        );
        CurrentUser currentUser = buildCurrentUser();
        currentUser.setUserUuid("other-user-uuid");
        ProfileDTO.PasswordUpdateRequest request = buildRequest("OldPass1!", "NewPass1!", "NewPass1!");

        BizException exception = assertThrows(BizException.class, () -> service.updateCurrentUserPassword(currentUser, request));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(1, userDomainService.findByIdCalls);
        assertEquals(null, passwordPolicyService.validatedPassword);
        assertEquals(null, jdbcTemplate.lastSql);
        assertEquals(null, authSessionStore.revokedUserId);
    }

    @Test
    void shouldRejectNullPasswordRequestBeforeUserLookup() {
        SysUserEntity user = buildUser("OldPass1!");
        StubUserDomainService userDomainService = new StubUserDomainService(user);
        RecordingAuthSessionStore authSessionStore = new RecordingAuthSessionStore();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingPasswordPolicyService passwordPolicyService = new RecordingPasswordPolicyService();
        SystemManagementAppService service = new SystemManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                null,
                null,
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                new RecordingOperationAuditService(),
                null,
                passwordPolicyService,
                new StubIamUserService(jdbcTemplate)
        );

        BizException exception = assertThrows(BizException.class, () -> service.updateCurrentUserPassword(buildCurrentUser(), null));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals(0, userDomainService.findByIdCalls);
        assertEquals(null, passwordPolicyService.validatedPassword);
        assertEquals(null, jdbcTemplate.lastSql);
        assertEquals(null, authSessionStore.revokedUserId);
    }

    @Test
    void shouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        SysUserEntity user = buildUser("OldPass1!");
        StubUserDomainService userDomainService = new StubUserDomainService(user);
        RecordingAuthSessionStore authSessionStore = new RecordingAuthSessionStore();
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingPasswordPolicyService passwordPolicyService = new RecordingPasswordPolicyService();
        SystemManagementAppService service = strictService(
                new MyBatisQueryOperations(jdbcTemplate),
                userDomainService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                new RecordingOperationAuditService(),
                null,
                passwordPolicyService,
                new StubIamUserService(jdbcTemplate),
                null,
                null,
                null
        );
        ProfileDTO.PasswordUpdateRequest request = buildRequest("OldPass1!", "NewPass1!", "NewPass1!");

        BizException exception = assertThrows(BizException.class, () -> service.updateCurrentUserPassword(buildCurrentUser(), request));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals(0, userDomainService.findByIdCalls);
        assertEquals(null, passwordPolicyService.validatedPassword);
        assertEquals(null, jdbcTemplate.lastSql);
        assertEquals(null, authSessionStore.revokedUserId);
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
        return new SystemManagementAppService(
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
    }

    private static SysUserEntity buildUser(String passwordHash) {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUuid("user-uuid-2001");
        user.setUsername("admin");
        user.setPasswordHash(passwordHash);
        user.setDeleted(0);
        return user;
    }

    private static CurrentUser buildCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setUsername("admin");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private static ProfileDTO.PasswordUpdateRequest buildRequest(String currentPassword, String newPassword, String confirmPassword) {
        ProfileDTO.PasswordUpdateRequest request = new ProfileDTO.PasswordUpdateRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    private static final class StubUserDomainService extends UserDomainService {
        private final SysUserEntity user;
        private int findByIdCalls;

        private StubUserDomainService(SysUserEntity user) {
            super(null);
            this.user = user;
        }

        @Override
        public Optional<SysUserEntity> findById(Long userId) {
            findByIdCalls += 1;
            return Optional.ofNullable(user);
        }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String lastSql;
        private Object[] lastArgs;

        @Override
        public int update(String sql, Object... args) {
            this.lastSql = sql;
            this.lastArgs = args;
            return 1;
        }
    }

    private static final class RecordingAuthSessionStore extends AuthSessionStore {
        private Long revokedUserId;
        private String revokedUserUuid;
        private String excludedSessionId;
        private boolean publishChange;
        private Long resolvedPasswordChangeUserId;
        private String resolvedPasswordChangeUserUuid;
        private String resolvedPasswordChangeSessionId;
        private boolean resolvedPasswordChangePublishChange;

        private RecordingAuthSessionStore() {
            super(null, null, null);
        }

        @Override
        public void revokeUserSessionsExcept(Long userId, String userUuid, String excludedSessionId, boolean publishChange) {
            this.revokedUserId = userId;
            this.revokedUserUuid = userUuid;
            this.excludedSessionId = excludedSessionId;
            this.publishChange = publishChange;
        }

        @Override
        public void markPasswordChangeResolved(Long userId, String userUuid, String sessionId, boolean publishChange) {
            this.resolvedPasswordChangeUserId = userId;
            this.resolvedPasswordChangeUserUuid = userUuid;
            this.resolvedPasswordChangeSessionId = sessionId;
            this.resolvedPasswordChangePublishChange = publishChange;
        }
    }

    private static final class StubIamUserService extends IamUserService {
        private StubIamUserService(JdbcTemplate jdbcTemplate) {
            super(new MyBatisQueryOperations(jdbcTemplate));
        }

        @Override
        public Optional<IamUserAccount.CredentialView> findActiveCredential(Long userId, String userUuid, String credentialType) {
            return Optional.empty();
        }

        @Override
        public void upsertPasswordCredential(Long userId, String userUuid, String passwordHash) {
        }
    }

    private static final class RecordingPasswordPolicyService extends PasswordPolicyService {
        private String validatedPassword;

        private RecordingPasswordPolicyService() {
            super(new SecuritySettingsService(null, null) {
                @Override
                public SecuritySettingsSnapshot loadSettings() {
                    return new SecuritySettingsSnapshot(
                            1800L,
                            3600L,
                            604800L,
                            true,
                            false,
                            "IMAGE",
                            10L,
                            5L,
                            5L,
                            300L,
                            60L,
                            8L,
                            true,
                            true,
                            true,
                            false
                    );
                }
            });
        }

        @Override
        public void validatePassword(String password) {
            validatedPassword = password;
            super.validatePassword(password);
        }
    }

    private static final class RecordingOperationAuditService extends OperationAuditService {
        private String lastMessage;

        private RecordingOperationAuditService() {
            super(null, objectProvider(null));
        }

        @Override
        public void log(Long userId, String userUuid, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
            this.lastMessage = detailMessage;
        }
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

    private static final class PlainPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword == null ? null : rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword != null && rawPassword.toString().equals(encodedPassword);
        }
    }
}
