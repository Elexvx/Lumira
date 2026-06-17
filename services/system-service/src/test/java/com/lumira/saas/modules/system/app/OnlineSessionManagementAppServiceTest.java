package com.lumira.saas.modules.system.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.system.online.OnlineSessionStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineSessionManagementAppServiceTest {

    private final JdbcTemplate jdbcTemplate = new JdbcTemplate() {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return List.of();
        }
    };

    private StubAuthSessionStore authSessionStore;
    private SecuritySettingsService securitySettingsService;
    private OnlineSessionManagementAppService service;

    @BeforeEach
    void setUp() {
        authSessionStore = new StubAuthSessionStore();
        securitySettingsService = new SecuritySettingsService(null, null) {
            @Override
            public long getIdleTimeoutSeconds() {
                return 1800L;
            }

            @Override
            public boolean isAllowMultiDeviceLogin() {
                return true;
            }
        };
        service = new OnlineSessionManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                authSessionStore,
                securitySettingsService,
                new OperationAuditService(null) {
                },
                new OnlineSessionStreamService(new ObjectMapper()) {
                }
        );
    }

    @Test
    void listOnlineSessionsOnlyReturnsRecentlyActiveSessions() {
        long tenantId = 1001L;
        long userId = 2001L;
        Instant now = Instant.now();

        AuthSession activeSession = buildSession("active-session", tenantId, userId, now.minusSeconds(60), now.plusSeconds(3600));
        AuthSession staleSession = buildSession("stale-session", tenantId, userId, now.minusSeconds(3600), now.plusSeconds(3600));
        AuthSession expiredSession = buildSession("expired-session", tenantId, userId, now.minusSeconds(60), now.minusSeconds(60));

        authSessionStore.put(activeSession);
        authSessionStore.put(staleSession);
        authSessionStore.put(expiredSession);

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(userId);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(tenantId);

        var page = service.listOnlineSessions(currentUser, 1, 10);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(activeSession.getSessionId(), page.getRecords().getFirst().getSessionId());
        assertEquals(1, authSessionStore.batchLookupCounts);
        assertTrue(authSessionStore.removedSessions.contains(staleSession));
        assertTrue(authSessionStore.removedSessions.contains(expiredSession));
    }

    @Test
    void listOnlineSessionsShouldDeduplicateUsersWhenMultiDeviceLoginIsDisabled() {
        securitySettingsService = new SecuritySettingsService(null, null) {
            @Override
            public long getIdleTimeoutSeconds() {
                return 1800L;
            }

            @Override
            public boolean isAllowMultiDeviceLogin() {
                return false;
            }
        };
        service = new OnlineSessionManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                authSessionStore,
                securitySettingsService,
                new OperationAuditService(null) {
                },
                new OnlineSessionStreamService(new ObjectMapper()) {
                }
        );

        long tenantId = 1001L;
        Instant now = Instant.now();

        AuthSession newerSession = buildSession("newer-session", tenantId, 2001L, now.minusSeconds(30), now.plusSeconds(3610));
        AuthSession olderSession = buildSession("older-session", tenantId, 2001L, now.minusSeconds(60), now.plusSeconds(3600));
        AuthSession otherUserSession = buildSession("other-user-session", tenantId, 2002L, now.minusSeconds(20), now.plusSeconds(3600));

        authSessionStore.put(olderSession);
        authSessionStore.put(newerSession);
        authSessionStore.put(otherUserSession);

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(9999L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(tenantId);

        var page = service.listOnlineSessions(currentUser, 1, 10);

        assertEquals(2L, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertEquals(newerSession.getSessionId(), page.getRecords().get(0).getSessionId());
        assertEquals(otherUserSession.getSessionId(), page.getRecords().get(1).getSessionId());
        assertTrue(authSessionStore.latestSessionLookupCounts.isEmpty());
        assertEquals(1, authSessionStore.batchLookupCounts);
    }

    private static AuthSession buildSession(String sessionId, long tenantId, long userId, Instant lastActivityAt, Instant expireTime) {
        AuthSession session = new AuthSession();
        session.setSessionId(sessionId);
        session.setCurrentTenantId(tenantId);
        session.setUserId(userId);
        session.setUsername("admin");
        session.setLoginTime(lastActivityAt);
        session.setLastActivityAt(lastActivityAt);
        session.setExpireTime(expireTime);
        session.setSessionVersion(1);
        session.setClientType("WEB");
        return session;
    }

    private static final class StubAuthSessionStore extends AuthSessionStore {
        private final Map<String, AuthSession> sessions = new HashMap<>();
        private final Map<Long, Integer> latestSessionLookupCounts = new HashMap<>();
        private final List<String> sessionOrder = new ArrayList<>();
        private final List<AuthSession> removedSessions = new ArrayList<>();
        private int batchLookupCounts;

        private StubAuthSessionStore() {
            super(null, null, null);
        }

        void put(AuthSession session) {
            sessions.put(session.getSessionId(), session);
            sessionOrder.add(session.getSessionId());
        }

        @Override
        public List<String> listActiveTenantSessionIds(Long tenantId, long start, long end) {
            return sessions.values().stream()
                    .sorted((left, right) -> {
                        Instant leftExpire = left.getExpireTime();
                        Instant rightExpire = right.getExpireTime();
                        if (leftExpire == null && rightExpire == null) {
                            return 0;
                        }
                        if (leftExpire == null) {
                            return 1;
                        }
                        if (rightExpire == null) {
                            return -1;
                        }
                        return rightExpire.compareTo(leftExpire);
                    })
                    .map(AuthSession::getSessionId)
                    .toList();
        }

        @Override
        public List<String> listActiveUserSessionIds(Long userId) {
            return sessions.values().stream()
                    .filter(session -> session.getUserId() != null && session.getUserId().equals(userId))
                    .sorted((left, right) -> {
                        Instant leftExpire = left.getExpireTime();
                        Instant rightExpire = right.getExpireTime();
                        if (leftExpire == null && rightExpire == null) {
                            return 0;
                        }
                        if (leftExpire == null) {
                            return 1;
                        }
                        if (rightExpire == null) {
                            return -1;
                        }
                        return rightExpire.compareTo(leftExpire);
                    })
                    .map(AuthSession::getSessionId)
                    .toList();
        }

        @Override
        public Optional<String> findLatestActiveUserSessionId(Long userId) {
            latestSessionLookupCounts.merge(userId, 1, Integer::sum);
            return listActiveUserSessionIds(userId).stream().findFirst();
        }

        @Override
        public Optional<AuthSession> findBySessionId(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Map<String, AuthSession> findBySessionIds(List<String> sessionIds) {
            batchLookupCounts++;
            Map<String, AuthSession> result = new HashMap<>();
            for (String sessionId : sessionIds) {
                AuthSession session = sessions.get(sessionId);
                if (session != null) {
                    result.put(sessionId, session);
                }
            }
            return result;
        }

        @Override
        public void remove(AuthSession session, boolean publishChange) {
            removedSessions.add(session);
            sessions.remove(session.getSessionId());
            sessionOrder.remove(session.getSessionId());
        }
    }
}
