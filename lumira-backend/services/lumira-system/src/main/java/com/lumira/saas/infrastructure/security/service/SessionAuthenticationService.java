package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.model.TokenClaims;
import com.lumira.saas.infrastructure.security.model.TokenType;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class SessionAuthenticationService {

    private static final long LAST_ACTIVITY_WRITE_THROTTLE_SECONDS = 30L;
    private static final long AUTHENTICATED_ACCESS_CACHE_TTL_MILLIS = 60_000L;
    private static final long AUTHENTICATED_ACCESS_CACHE_MAX_ENTRIES = 10_000L;
    private static final HexFormat HEX = HexFormat.of();
    private static final ThreadLocal<MessageDigest> SHA_256_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    });

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SecuritySettingsService securitySettingsService;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private final Cache<String, AuthenticatedAccess> authenticatedAccessCache;

    @Autowired
    public SessionAuthenticationService(
            JwtTokenService jwtTokenService,
            AuthSessionStore authSessionStore,
            PermissionSnapshotService permissionSnapshotService,
            SecuritySettingsService securitySettingsService
    ) {
        this(jwtTokenService, authSessionStore, permissionSnapshotService, securitySettingsService, null);
    }

    public SessionAuthenticationService(
            JwtTokenService jwtTokenService,
            AuthSessionStore authSessionStore,
            PermissionSnapshotService permissionSnapshotService,
            SecuritySettingsService securitySettingsService,
            OwnerRuntimeMetrics ownerRuntimeMetrics
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionStore = authSessionStore;
        this.permissionSnapshotService = permissionSnapshotService;
        this.securitySettingsService = securitySettingsService;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.authenticatedAccessCache = CacheBuilder.newBuilder()
                .maximumSize(AUTHENTICATED_ACCESS_CACHE_MAX_ENTRIES)
                .expireAfterWrite(AUTHENTICATED_ACCESS_CACHE_TTL_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    public AuthenticatedAccess authenticateAccessToken(String token) {
        long started = System.nanoTime();
        try {
            AuthenticatedAccess cached = authenticatedAccessCache.getIfPresent(tokenCacheKey(token));
            if (cached != null) {
                recordAuthSessionAuthResult(started, true);
                return cached;
            }

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
            PermissionSnapshotResolution snapshotResolution = resolvePermissionSnapshot(claims, session);
            AuthenticatedAccess authenticatedAccess = new AuthenticatedAccess(
                    buildCurrentUser(claims, session, snapshotResolution.snapshot()),
                    session,
                    snapshotResolution.sessionStateUpdated()
            );
            if (!authenticatedAccess.sessionStateUpdated()) {
                authenticatedAccessCache.put(tokenCacheKey(token), authenticatedAccess);
            }
            recordAuthSessionAuthResult(started, true);
            return authenticatedAccess;
        } catch (BizException ex) {
            recordAuthSessionAuthResult(started, false);
            throw ex;
        } catch (RuntimeException ex) {
            recordAuthSessionAuthResult(started, false);
            throw ex;
        }
    }

    public AuthenticatedAccess authenticateSessionTicket(String sessionId, Long userId, Integer sessionVersion) {
        long started = System.nanoTime();
        try {
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
            PermissionSnapshotResolution snapshotResolution = resolvePermissionSnapshot(claims, session);
            AuthenticatedAccess authenticatedAccess = new AuthenticatedAccess(
                    buildCurrentUser(claims, session, snapshotResolution.snapshot()),
                    session,
                    snapshotResolution.sessionStateUpdated()
            );
            recordAuthSessionAuthResult(started, true);
            return authenticatedAccess;
        } catch (BizException ex) {
            recordAuthSessionAuthResult(started, false);
            throw ex;
        } catch (RuntimeException ex) {
            recordAuthSessionAuthResult(started, false);
            throw ex;
        }
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
        authenticatedAccessCache.invalidateAll();
        authSessionStore.remove(session, true);
        throw new BizException(
                ErrorCode.SESSION_EXPIRED,
                message,
                ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
        );
    }

    private CurrentUser buildCurrentUser(
            TokenClaims claims,
            AuthSession session,
            PermissionSnapshotService.PermissionSnapshot snapshot
    ) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(claims.getUserId());
        currentUser.setUsername(claims.getUsername());
        currentUser.setCurrentTenantId(session.getCurrentTenantId());
        currentUser.setSimulatedRoleId(session.getSimulatedRoleId());
        currentUser.setSessionId(claims.getSessionId());
        currentUser.setSessionVersion(session.getSessionVersion());
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setRequiresPasswordChange(session.getRequiresPasswordChange());
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Collections.emptySet() : snapshot.getPermissions());
        currentUser.setRoleIds(snapshot.getRoleIds());
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds());
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds());
        currentUser.setDataScopes(snapshot.getDataScopes());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
        return currentUser;
    }

    private PermissionSnapshotResolution resolvePermissionSnapshot(TokenClaims claims, AuthSession session) {
        if (session.getSimulatedRoleId() != null) {
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordAuthPermissionSnapshotFromRole();
            }
            PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadRoleSnapshot(
                    session.getCurrentTenantId(),
                    session.getSimulatedRoleId()
            );
            return new PermissionSnapshotResolution(snapshot, false);
        }
        if (hasPermissionSnapshot(session)) {
            if (permissionSnapshotService.isSessionPermissionSnapshotCurrent(session.getCurrentTenantId(), session.getPermissionsVersion())) {
                if (ownerRuntimeMetrics != null) {
                    ownerRuntimeMetrics.recordAuthPermissionSnapshotFromSession();
                }
                return new PermissionSnapshotResolution(fromSession(session), false);
            }
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(session.getCurrentTenantId(), claims.getUserId());
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordAuthPermissionSnapshotFromUser();
        }
        hydrateSessionPermissionSnapshot(session, snapshot);
        return new PermissionSnapshotResolution(snapshot, true);
    }

    private boolean hasPermissionSnapshot(AuthSession session) {
        return session.getPermissionsVersion() != null
                && session.getPermissions() != null
                && session.getRoleIds() != null
                && session.getDeptIds() != null
                && session.getDescendantDeptIds() != null
                && session.getDataScopes() != null
                && StringUtils.hasText(session.getPermissionsVersion());
    }

    private static <T> Set<T> toSet(java.util.Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private PermissionSnapshotService.PermissionSnapshot fromSession(AuthSession session) {
        return new PermissionSnapshotService.PermissionSnapshot(
                session.getPermissionsVersion(),
                toSet(session.getPermissions()),
                toSet(session.getRoleIds()),
                session.getPrimaryDeptId(),
                toSet(session.getDeptIds()),
                toSet(session.getDescendantDeptIds()),
                session.getDataScopes(),
                session.getDefaultHomePath()
        );
    }

    private void hydrateSessionPermissionSnapshot(AuthSession session, PermissionSnapshotService.PermissionSnapshot snapshot) {
        if (session == null || snapshot == null) {
            return;
        }
        session.setPermissionsVersion(snapshot.getVersion());
        session.setPermissions(List.copyOf(snapshot.getPermissions()));
        session.setRoleIds(List.copyOf(snapshot.getRoleIds()));
        session.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        session.setDeptIds(List.copyOf(snapshot.getDeptIds()));
        session.setDescendantDeptIds(List.copyOf(snapshot.getDescendantDeptIds()));
        session.setDataScopes(snapshot.getDataScopes());
        session.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private record PermissionSnapshotResolution(PermissionSnapshotService.PermissionSnapshot snapshot, boolean sessionStateUpdated) {
    }

    private void recordAuthSessionAuthResult(long startedNanos, boolean success) {
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordAuthSessionAuth(Duration.ofNanos(System.nanoTime() - startedNanos), success);
        }
    }

    private String tokenCacheKey(String token) {
        MessageDigest digest = SHA_256_DIGEST.get();
        digest.reset();
        return HEX.formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    public record AuthenticatedAccess(CurrentUser currentUser, AuthSession session, boolean sessionStateUpdated) {

    }
}
