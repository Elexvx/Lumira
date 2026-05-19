package com.legendary.invention.saas.infrastructure.security.service;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.model.AuthSession;
import com.legendary.invention.saas.infrastructure.security.model.TokenClaims;
import com.legendary.invention.saas.infrastructure.security.model.TokenType;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

@Component
public class SessionAuthenticationService {

    private static final long LAST_ACTIVITY_WRITE_THROTTLE_SECONDS = 30L;

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SecuritySettingsService securitySettingsService;

    public SessionAuthenticationService(
            JwtTokenService jwtTokenService,
            AuthSessionStore authSessionStore,
            PermissionSnapshotService permissionSnapshotService,
            SecuritySettingsService securitySettingsService
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionStore = authSessionStore;
        this.permissionSnapshotService = permissionSnapshotService;
        this.securitySettingsService = securitySettingsService;
    }

    public AuthenticatedAccess authenticateAccessToken(String token) {
        TokenClaims claims = jwtTokenService.parseToken(token);
        if (claims.getTokenType() != TokenType.ACCESS) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "accessToken类型非法");
        }

        AuthSession session = authSessionStore.findBySessionId(claims.getSessionId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "会话不存在或已失效",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));

        validateSession(claims, session, Instant.now());
        return new AuthenticatedAccess(buildCurrentUser(claims, session), session);
    }

    public AuthenticatedAccess authenticateSessionTicket(String sessionId, Long userId, Integer sessionVersion) {
        if (sessionId == null || sessionId.isBlank() || userId == null || sessionVersion == null) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
        }
        AuthSession session = authSessionStore.findBySessionId(sessionId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "会话不存在或已失效",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));
        TokenClaims claims = new TokenClaims();
        claims.setSessionId(sessionId);
        claims.setUserId(userId);
        claims.setUsername(session.getUsername());
        claims.setCurrentTenantId(session.getCurrentTenantId());
        claims.setSessionVersion(sessionVersion);
        claims.setTokenType(TokenType.ACCESS);
        validateSession(claims, session, Instant.now());
        return new AuthenticatedAccess(buildCurrentUser(claims, session), session);
    }

    public boolean shouldPersistActivity(AuthSession session, Instant now) {
        Instant lastActivityAt = session.getLastActivityAt();
        if (lastActivityAt == null) {
            return true;
        }

        Duration elapsed = Duration.between(lastActivityAt, now);
        long idleTimeoutSeconds = securitySettingsService.getIdleTimeoutSeconds();
        long throttleSeconds = idleTimeoutSeconds > 0
                ? Math.min(LAST_ACTIVITY_WRITE_THROTTLE_SECONDS, Math.max(5L, idleTimeoutSeconds / 2))
                : LAST_ACTIVITY_WRITE_THROTTLE_SECONDS;
        return elapsed.compareTo(Duration.ofSeconds(throttleSeconds)) >= 0;
    }

    private void validateSession(TokenClaims claims, AuthSession session, Instant now) {
        if (!session.getUserId().equals(claims.getUserId())) {
            invalidateSession(session, "token与会话不匹配");
        }
        if (session.getSessionVersion() == null || !session.getSessionVersion().equals(claims.getSessionVersion())) {
            invalidateSession(session, "会话版本已变更，请重新登录");
        }
        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            String latestSessionId = authSessionStore.findLatestActiveUserSessionId(session.getUserId()).orElse(null);
            if (latestSessionId == null || !session.getSessionId().equals(latestSessionId)) {
                invalidateSession(session, "当前账号已在其他设备登录，请重新登录");
            }
        }
        if (jwtTokenService.isExpired(session.getExpireTime())) {
            invalidateSession(session, "会话已过期，请重新登录");
        }

        Instant lastActivityAt = session.getLastActivityAt() != null ? session.getLastActivityAt() : session.getLoginTime();
        long idleTimeoutSeconds = securitySettingsService.getIdleTimeoutSeconds();
        if (lastActivityAt != null && idleTimeoutSeconds > 0) {
            Duration idleDuration = Duration.between(lastActivityAt, now);
            if (idleDuration.compareTo(Duration.ofSeconds(idleTimeoutSeconds)) >= 0) {
                invalidateSession(session, "会话空闲超时，请重新登录");
            }
        }
    }

    private void invalidateSession(AuthSession session, String message) {
        authSessionStore.remove(session, true);
        throw new BizException(
                ErrorCode.SESSION_EXPIRED,
                message,
                ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
        );
    }

    private CurrentUser buildCurrentUser(TokenClaims claims, AuthSession session) {
        PermissionSnapshotService.PermissionSnapshot snapshot = session.getSimulatedRoleId() != null
                ? permissionSnapshotService.loadRoleSnapshot(session.getCurrentTenantId(), session.getSimulatedRoleId())
                : permissionSnapshotService.loadSnapshot(session.getCurrentTenantId(), claims.getUserId());
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(claims.getUserId());
        currentUser.setUsername(claims.getUsername());
        currentUser.setCurrentTenantId(session.getCurrentTenantId());
        currentUser.setSimulatedRoleId(session.getSimulatedRoleId());
        currentUser.setSessionId(claims.getSessionId());
        currentUser.setSessionVersion(session.getSessionVersion());
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Collections.emptySet() : snapshot.getPermissions());
        return currentUser;
    }

    public record AuthenticatedAccess(CurrentUser currentUser, AuthSession session) {
    }
}
