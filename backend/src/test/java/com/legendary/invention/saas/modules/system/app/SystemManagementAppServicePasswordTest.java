package com.legendary.invention.saas.modules.system.app;

import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.service.AuthSessionStore;
import com.legendary.invention.saas.infrastructure.security.service.PasswordPolicyService;
import com.legendary.invention.saas.infrastructure.security.service.SecuritySettingsService;
import com.legendary.invention.saas.infrastructure.security.service.SecuritySettingsService.SecuritySettingsSnapshot;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.auth.app.AuthAppService;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.plugin.app.PluginManagementAppService;
import com.legendary.invention.saas.modules.system.dto.ProfileDTO;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import com.legendary.invention.saas.modules.tenant.domain.TenantDomainService;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

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
                jdbcTemplate,
                null,
                null,
                new StubUserDomainService(user),
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                operationAuditService,
                null,
                passwordPolicyService
        );

        CurrentUser currentUser = buildCurrentUser();
        ProfileDTO.PasswordUpdateRequest request = buildRequest("OldPass1!", "NewPass1!", "NewPass1!");

        boolean updated = service.updateCurrentUserPassword(currentUser, request);

        assertTrue(updated);
        assertEquals("NewPass1!", passwordPolicyService.validatedPassword);
        assertEquals(
                "update sys_user set password_hash = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                jdbcTemplate.lastSql
        );
        assertEquals("NewPass1!", jdbcTemplate.lastArgs[0]);
        assertEquals(2001L, jdbcTemplate.lastArgs[1]);
        assertEquals(2001L, jdbcTemplate.lastArgs[3]);
        assertEquals(2001L, authSessionStore.revokedUserId);
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
                jdbcTemplate,
                null,
                null,
                new StubUserDomainService(user),
                null,
                null,
                null,
                null,
                new PlainPasswordEncoder(),
                authSessionStore,
                null,
                new RecordingOperationAuditService(),
                null,
                passwordPolicyService
        );

        CurrentUser currentUser = buildCurrentUser();
        ProfileDTO.PasswordUpdateRequest request = buildRequest("WrongPass1!", "NewPass1!", "NewPass1!");

        assertThrows(BizException.class, () -> service.updateCurrentUserPassword(currentUser, request));
        assertEquals(null, passwordPolicyService.validatedPassword);
        assertEquals(null, jdbcTemplate.lastSql);
        assertEquals(null, authSessionStore.revokedUserId);
    }

    private static SysUserEntity buildUser(String passwordHash) {
        SysUserEntity user = new SysUserEntity();
        user.setId(2001L);
        user.setUsername("admin");
        user.setPasswordHash(passwordHash);
        user.setDeleted(0);
        return user;
    }

    private static CurrentUser buildCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setSessionId("session-1");
        currentUser.setCurrentTenantId(1001L);
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

        private StubUserDomainService(SysUserEntity user) {
            super(null);
            this.user = user;
        }

        @Override
        public Optional<SysUserEntity> findById(Long userId) {
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
        private String excludedSessionId;
        private boolean publishChange;

        private RecordingAuthSessionStore() {
            super(null, null, null);
        }

        @Override
        public void revokeUserSessionsExcept(Long userId, String excludedSessionId, boolean publishChange) {
            this.revokedUserId = userId;
            this.excludedSessionId = excludedSessionId;
            this.publishChange = publishChange;
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
            super(null);
        }

        @Override
        public void log(Long tenantId, Long userId, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
            this.lastMessage = detailMessage;
        }
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
