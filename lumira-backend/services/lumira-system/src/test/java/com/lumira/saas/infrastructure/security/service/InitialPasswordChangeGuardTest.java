package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class InitialPasswordChangeGuardTest {

    @Test
    void shouldCacheExplicitPasswordChangeDecisionPerSession() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);

        assertTrue(guard.requiresPasswordChange(currentUser));
        assertTrue(guard.requiresPasswordChange(currentUser));

        assertEquals(1, queryOperations.queryCount);
    }

    @Test
    void shouldRecheckDatabaseWhenSessionRequiresPasswordChange() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setRequiresPasswordChange(true);

        assertTrue(guard.requiresPasswordChange(currentUser));
        assertEquals(1, queryOperations.queryCount);

        currentUser.setRequiresPasswordChange(false);
        assertFalse(guard.requiresPasswordChange(currentUser));
        assertEquals(1, queryOperations.queryCount);
    }

    @Test
    void shouldAllowResolvedCredentialEvenWhenSessionStillRequiresPasswordChange() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(0);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setRequiresPasswordChange(true);

        assertFalse(guard.requiresPasswordChange(currentUser));
        assertEquals(1, queryOperations.queryCount);
    }

    @Test
    void shouldReloadWhenSessionVersionChanges() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);

        assertTrue(guard.requiresPasswordChange(buildAdminUser("session-1", 1)));
        assertTrue(guard.requiresPasswordChange(buildAdminUser("session-1", 2)));

        assertEquals(2, queryOperations.queryCount);
    }

    @Test
    void shouldApplyExplicitRequirementToAnyTrustedUser() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");

        assertTrue(guard.requiresPasswordChange(currentUser));

        assertEquals(1, queryOperations.queryCount);
        assertArrayEquals(new Object[]{2001L, "user-uuid-2001"}, queryOperations.lastArgs);
    }

    @Test
    void shouldSkipUntrustedAdminLikeUser() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setAuthenticated(false);

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
    }

    @Test
    void shouldSkipAdminUserWithMissingSessionVersion() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", null);

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
    }

    @Test
    void shouldSkipAdminUserWithMissingUserUuid() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setUserUuid(" ");

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
    }

    @Test
    void shouldQueryExplicitCredentialStateByTrustedIdentity() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);

        assertTrue(guard.requiresPasswordChange(currentUser));

        assertTrue(queryOperations.lastSql.contains("password_change_required"));
        assertTrue(queryOperations.lastSql.contains("where user_id = ?"));
        assertTrue(queryOperations.lastSql.contains("and user_uuid = ?"));
        assertTrue(queryOperations.lastSql.contains("credential_type = 'PASSWORD'"));
        assertArrayEquals(new Object[]{1001L, "admin-user-1001"}, queryOperations.lastArgs);
    }

    @Test
    void shouldRequirePasswordChangeForRenamedUserWhenCredentialFlagIsSet() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(1);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setUsername("root-admin");

        assertTrue(guard.requiresPasswordChange(currentUser));

        assertEquals(1, queryOperations.queryCount);
    }

    @Test
    void shouldFailClosedWhenCredentialStateIsUnavailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations(null);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations);

        BizException exception = assertThrows(BizException.class,
                () -> guard.requiresPasswordChange(buildAdminUser("session-1", 1)));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals("Password change guard is unavailable", exception.getMessage());
        assertEquals(1, queryOperations.queryCount);
    }

    private static CurrentUser buildAdminUser(String sessionId, Integer sessionVersion) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("admin-user-1001");
        currentUser.setUsername("admin");
        currentUser.setSessionId(sessionId);
        currentUser.setSessionVersion(sessionVersion);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final Integer passwordChangeRequired;
        private int queryCount;
        private String lastSql = "";
        private Object[] lastArgs = new Object[0];

        private RecordingQueryOperations(Integer passwordChangeRequired) {
            this.passwordChangeRequired = passwordChangeRequired;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount += 1;
            lastSql = sql;
            lastArgs = args == null ? new Object[0] : args.clone();
            return requiredType.cast(passwordChangeRequired);
        }
    }
}
