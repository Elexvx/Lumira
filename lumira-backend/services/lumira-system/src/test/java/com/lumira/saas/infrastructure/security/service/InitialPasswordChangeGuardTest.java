package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void shouldUseSessionDecisionBeforeLegacyPasswordLookup() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations("initial-hash");
        RecordingPasswordEncoder passwordEncoder = new RecordingPasswordEncoder(true);
        InitialPasswordChangeGuard guard = new InitialPasswordChangeGuard(queryOperations, passwordEncoderProvider(passwordEncoder));
        CurrentUser currentUser = buildAdminUser("session-1", 1);
        currentUser.setRequiresPasswordChange(true);

        assertTrue(guard.requiresPasswordChange(currentUser));

        currentUser.setRequiresPasswordChange(false);
        assertFalse(guard.requiresPasswordChange(currentUser));
        assertEquals(0, queryOperations.queryCount);
        assertEquals(0, passwordEncoder.matchesCount);
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
        currentUser.setUsername("operator");

        assertFalse(guard.requiresPasswordChange(currentUser));

        assertEquals(0, queryOperations.queryCount);
        assertEquals(0, passwordEncoder.matchesCount);
    }

    private static CurrentUser buildAdminUser(String sessionId, Integer sessionVersion) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setCurrentTenantId(1001L);
        currentUser.setUsername("admin");
        currentUser.setSessionId(sessionId);
        currentUser.setSessionVersion(sessionVersion);
        return currentUser;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<PasswordEncoder> passwordEncoderProvider(PasswordEncoder passwordEncoder) {
        ObjectProvider<PasswordEncoder> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(passwordEncoder);
        return provider;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private final String passwordHash;
        private int queryCount;

        private RecordingQueryOperations(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryCount += 1;
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
