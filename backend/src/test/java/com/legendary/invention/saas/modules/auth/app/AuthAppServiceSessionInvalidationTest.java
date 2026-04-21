package com.legendary.invention.saas.modules.auth.app;

import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.model.AuthSession;
import com.legendary.invention.saas.infrastructure.security.model.TokenClaims;
import com.legendary.invention.saas.infrastructure.security.model.TokenType;
import com.legendary.invention.saas.infrastructure.security.service.AuthSessionStore;
import com.legendary.invention.saas.infrastructure.security.service.CaptchaService;
import com.legendary.invention.saas.infrastructure.security.service.JwtTokenService;
import com.legendary.invention.saas.infrastructure.security.service.LoginProtectionService;
import com.legendary.invention.saas.infrastructure.security.service.SecuritySettingsService;
import com.legendary.invention.saas.modules.audit.app.LoginAuditService;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.plugin.app.PluginManagementAppService;
import com.legendary.invention.saas.modules.tenant.domain.TenantDomainService;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthAppServiceSessionInvalidationTest {

    @Test
    void shouldRemoveRefreshSessionWhenTokenIdDoesNotMatch() throws Exception {
        StubAuthSessionStore authSessionStore = new StubAuthSessionStore();
        StubSecuritySettingsService securitySettingsService = new StubSecuritySettingsService();

        AuthAppService service = new AuthAppService(
                null,
                null,
                null,
                authSessionStore,
                null,
                null,
                null,
                null,
                securitySettingsService,
                null,
                null,
                null
        );

        AuthSession session = buildSession();
        TokenClaims tokenClaims = buildClaims(session);
        tokenClaims.setTokenId("other-refresh-token");

        Method method = AuthAppService.class.getDeclaredMethod(
                "validateSessionForRefresh",
                AuthSession.class,
                TokenClaims.class
        );
        method.setAccessible(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(service, session, tokenClaims)
        );
        assertInstanceOf(BizException.class, exception.getCause());
        assertSame(session, authSessionStore.removedSession);
    }

    private AuthSession buildSession() {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(2001L);
        session.setUsername("admin");
        session.setCurrentTenantId(1001L);
        session.setLoginTime(Instant.now().minusSeconds(60));
        session.setLastActivityAt(Instant.now().minusSeconds(60));
        session.setExpireTime(Instant.now().plusSeconds(3600));
        session.setSessionVersion(1);
        session.setRefreshTokenId("refresh-token");
        return session;
    }

    private TokenClaims buildClaims(AuthSession session) {
        TokenClaims claims = new TokenClaims();
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUsername(session.getUsername());
        claims.setCurrentTenantId(session.getCurrentTenantId());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setTokenId(session.getRefreshTokenId());
        claims.setTokenType(TokenType.REFRESH);
        return claims;
    }

    private static final class StubAuthSessionStore extends AuthSessionStore {
        private final Map<String, AuthSession> sessions = new HashMap<>();
        private AuthSession removedSession;
        private boolean removedPublishChange;

        private StubAuthSessionStore() {
            super(null, null, null);
        }

        @Override
        public Optional<AuthSession> findBySessionId(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public void remove(AuthSession session, boolean publishChange) {
            removedSession = session;
            removedPublishChange = publishChange;
            sessions.remove(session.getSessionId());
        }

        @Override
        public void save(AuthSession session) {
            sessions.put(session.getSessionId(), session);
        }
    }

    private static final class StubSecuritySettingsService extends SecuritySettingsService {
        private StubSecuritySettingsService() {
            super(null, null);
        }

        @Override
        public boolean isAllowMultiDeviceLogin() {
            return true;
        }
    }
}
