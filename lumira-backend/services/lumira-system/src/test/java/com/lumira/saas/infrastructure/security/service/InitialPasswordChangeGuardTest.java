package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InitialPasswordChangeGuardTest {

    @Test
    void shouldCacheInitialPasswordDecisionPerSession() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);

        assertTrue(guard.requiresPasswordChange(currentUser));
        assertTrue(guard.requiresPasswordChange(currentUser));

        assertEquals(1, queryOperations.queryCount);
        assertEquals(1, passwordEncoder.matchesCount);
    }

    @Test
    void shouldRecheckDatabaseWhenSessionRequiresPasswordChange() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setRequiresPasswordChange(true);

        assertTrue(guard.requiresPasswordChange(currentUser));
        assertEquals(1, queryOperations.queryCount);
        assertEquals(1, passwordEncoder.matchesCount);

        currentUser.setRequiresPasswordChange(false);
        assertFalse(guard.requiresPasswordChange(currentUser));
        assertEquals(1, queryOperations.queryCount);
        assertEquals(1, passwordEncoder.matchesCount);
    }

    @Test
    void shouldAllowChangedPasswordEvenWhenSessionStillRequiresPasswordChange() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("changed-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(false);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setRequiresPasswordChange(true);

        assertFalse(guard.requiresPasswordChange(currentUser));
        assertEquals(1, queryOperations.queryCount);
        assertEquals(1, passwordEncoder.matchesCount);
    }

    @Test
    void shouldReloadWhenSessionVersionChanges() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));

        assertTrue(guard.requiresPasswordChange(buildAdminUser("session-1", 1)));
        assertTrue(guard.requiresPasswordChange(buildAdminUser("session-1", 2)));

        assertEquals(2, queryOperations.queryCount);
        assertEquals(2, passwordEncoder.matchesCount);
    }

    @Test
    void shouldSkipNonDefaultAdminUsers() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setUserId(2001L);

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
        assertEquals(0, passwordEncoder.matchesCount);
    }

    @Test
    void shouldSkipUntrustedAdminLikeUser() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setAuthenticated(false);

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
        assertEquals(0, passwordEncoder.matchesCount);
    }

    @Test
    void shouldSkipAdminUserWithMissingSessionVersion() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", null);

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
        assertEquals(0, passwordEncoder.matchesCount);
    }

    @Test
    void shouldSkipAdminUserWithMissingUserUuid() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setUserUuid(" ");

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
        assertEquals(0, passwordEncoder.matchesCount);
    }

    @Test
    void shouldQueryInitialPasswordByTrustedAdminIdentity() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);

        assertTrue(guard.requiresPasswordChange(currentUser));

        assertTrue(queryOperations.lastSql.contains("where id = ?"));
        assertTrue(queryOperations.lastSql.contains("and uuid = ?"));
        assertTrue(queryOperations.lastSql.contains("and status = 'ENABLED'"));
        assertArrayEquals(new Object[]{1001L, "admin-user-1001"}, queryOperations.lastArgs);
    }

    @Test
    void shouldRequirePasswordChangeForDefaultAdminWithRenamedUsername() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setUsername("root-admin");

        assertTrue(guard.requiresPasswordChange(currentUser));

        assertEquals(1, queryOperations.queryCount);
        assertEquals(1, passwordEncoder.matchesCount);
    }

    @Test
    void shouldRejectWhenPasswordEncoderIsUnavailable() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, emptyPasswordEncoderProvider());

        BizException exception = assertThrows(BizException.class,
                () -> guard.requiresPasswordChange(buildAdminUser("session-1", 1)));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        assertEquals("Initial password guard is unavailable", exception.getMessage());
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

    @SuppressWarnings("unchecked")
    private static ObjectProvider<PasswordEncoder> passwordEncoderProvider(PasswordEncoder passwordEncoder) {
        ObjectProvider<PasswordEncoder> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(passwordEncoder);
        when(provider.getIfAvailable()).thenReturn(passwordEncoder);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<PasswordEncoder> emptyPasswordEncoderProvider() {
        ObjectProvider<PasswordEncoder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final String passwordHash;
        private int queryCount;
        private String lastSql = "";
        private Object[] lastArgs = new Object[0];

        private RecordingQueryOperations(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount += 1;
            lastSql = sql;
            lastArgs = args == null ? new Object[0] : args.clone();
            return requiredType.cast(passwordHash);
        }
    }

    private static final class RecordingPasswordEncoder implements PasswordEncoder {
        private final boolean matches;
        private int matchesCount;

        private RecordingPasswordEncoder(boolean matches) {
            this.matches = matches;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword == null ? null : rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchesCount += 1;
            return matches;
        }
    }
}
