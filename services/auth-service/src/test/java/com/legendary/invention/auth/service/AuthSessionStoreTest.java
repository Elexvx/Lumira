package com.legendary.invention.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.auth.model.AuthSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionStoreTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private AuthSessionStore authSessionStore;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        authSessionStore = new AuthSessionStore(redisTemplate, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void saveShouldSerializeInstantFields() {
        AuthSession session = buildSession(Instant.now().plusSeconds(3600));

        assertDoesNotThrow(() -> authSessionStoreObjectMapper().writeValueAsString(session));
        authSessionStore.save(session, false);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("saas:session:s-1"), payloadCaptor.capture(), ttlCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("\"sessionId\":\"s-1\""));
        assertTrue(payloadCaptor.getValue().contains("\"loginTime\""));
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ZERO) > 0);
    }

    @Test
    void saveShouldNormalizeNonPositiveTtl() {
        AuthSession session = buildSession(Instant.now().minusSeconds(5));

        authSessionStore.save(session, false);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("saas:session:s-1"), any(String.class), ttlCaptor.capture());
        assertTrue(ttlCaptor.getValue().compareTo(Duration.ZERO) > 0);
    }

    private AuthSession buildSession(Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId("s-1");
        session.setUserId(1L);
        session.setUsername("admin");
        session.setCurrentTenantId(1001L);
        session.setLoginTime(Instant.parse("2026-05-06T00:00:00Z"));
        session.setLastActivityAt(Instant.parse("2026-05-06T00:01:00Z"));
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
        session.setClientType("WEB");
        session.setLoginIp("127.0.0.1");
        session.setUserAgent("Mozilla/5.0");
        session.setRefreshTokenId("refresh-1");
        return session;
    }

    private ObjectMapper authSessionStoreObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
