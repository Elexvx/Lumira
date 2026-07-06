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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class SessionAuthenticationService {

    private static final long PROTECTED_ADMIN_ID = 1001L;
    private static final String PROTECTED_ADMIN_USERNAME = "admin";
    private static final long LAST_ACTIVITY_WRITE_THROTTLE_SECONDS = 30L;
    private static final long AUTHENTICATED_ACCESS_CACHE_TTL_MILLIS = 60_000L;
    private static final long AUTHENTICATED_ACCESS_CACHE_MAX_ENTRIES = 10_000L;
    private static final int MAX_ACCESS_TOKEN_LENGTH = 8 * 1024;
    private static final Pattern SAFE_ACCESS_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._~+/=-]+$");
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
            String trustedToken = requireTrustedAccessToken(token);
            String cacheKey = tokenCacheKey(trustedToken);
            AuthenticatedAccess cached = authenticatedAccessCache.getIfPresent(cacheKey);
            if (cached != null) {
                AuthenticatedAccess revalidated = revalidateCachedAuthenticatedAccess(cached, Instant.now());
                if (revalidated != null) {
                    recordAuthSessionAuthResult(started, true);
                    return revalidated;
                }
                authenticatedAccessCache.invalidate(cacheKey);
            }

            TokenClaims claims = jwtTokenService.parseToken(trustedToken);
            validateAccessClaims(claims);
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
            requireTrustedActiveSessionUser(session);
            PermissionSnapshotResolution snapshotResolution = resolvePermissionSnapshot(claims, session);
            validateAccessSnapshot(claims, snapshotResolution.snapshot());
            AuthenticatedAccess authenticatedAccess = new AuthenticatedAccess(
                    buildCurrentUser(claims, session, snapshotResolution.snapshot()),
                    session,
                    snapshotResolution.sessionStateUpdated()
            );
            if (isCacheableAuthenticatedAccess(authenticatedAccess)) {
                authenticatedAccessCache.put(cacheKey, authenticatedAccess);
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
        if (true) {
            throw expiredCredentials();
        }
        long started = System.nanoTime();
        try {
            if (userId == null || userId <= 0 || sessionVersion == null || sessionVersion <= 0) {
                throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
            }
            String trustedSessionId = requireTrustedSessionTicketId(sessionId);
            AuthSession session = authSessionStore.findBySessionId(trustedSessionId)
                    .orElseThrow(() -> new BizException(
                            ErrorCode.SESSION_EXPIRED,
                            "会话不存在或已失效",
                            ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                    ));
            TokenClaims claims = new TokenClaims();
            claims.setSessionId(trustedSessionId);
            claims.setUserId(userId);
            claims.setUsername(session.getUsername());
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

    public AuthenticatedAccess authenticateSessionTicket(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
        long started = System.nanoTime();
        try {
            if (userId == null
                    || userId <= 0
                    || !StringUtils.hasText(userUuid)
                    || sessionVersion == null
                    || sessionVersion <= 0
                    || !StringUtils.hasText(permissionsVersion)) {
                throw expiredCredentials();
            }
            String trustedSessionId = requireTrustedSessionTicketId(sessionId);
            String trustedUserUuid = userUuid.trim();
            String trustedPermissionsVersion = permissionsVersion.trim();
            Long trustedSimulatedRoleId = normalizeSimulatedRoleId(simulatedRoleId);
            AuthSession session = authSessionStore.findBySessionId(trustedSessionId)
                    .orElseThrow(this::expiredCredentials);
            TokenClaims claims = new TokenClaims();
            claims.setSessionId(trustedSessionId);
            claims.setUserId(userId);
            claims.setUserUuid(trustedUserUuid);
            claims.setUsername(session.getUsername());
            claims.setSimulatedRoleId(trustedSimulatedRoleId);
            claims.setSessionVersion(sessionVersion);
            claims.setPermissionsVersion(trustedPermissionsVersion);
            claims.setTokenType(TokenType.ACCESS);
            validateSession(claims, session, Instant.now());
            requireTrustedActiveSessionUser(session);
            PermissionSnapshotResolution snapshotResolution = resolvePermissionSnapshot(claims, session);
            validateSessionTicketSnapshot(
                    session,
                    snapshotResolution.snapshot(),
                    trustedUserUuid,
                    trustedSimulatedRoleId,
                    trustedPermissionsVersion
            );
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

    private void validateSession(TokenClaims claims, AuthSession session, Instant now) {
        if (!session.getUserId().equals(claims.getUserId())) {
            invalidateSession(session, "token与会话不匹配");
        }
        if (!StringUtils.hasText(session.getUserUuid())) {
            invalidateSession(session, "session identity snapshot is incomplete");
        }
        if (!StringUtils.hasText(claims.getUserUuid()) || !session.getUserUuid().trim().equals(claims.getUserUuid().trim())) {
            invalidateSession(session, "token and session user identity mismatch");
        }
        if (!StringUtils.hasText(session.getUsername()) || !session.getUsername().trim().equals(claims.getUsername().trim())) {
            invalidateSession(session, "token and session identity mismatch");
        }
        if (!Objects.equals(normalizeSimulatedRoleId(session.getSimulatedRoleId()), normalizeSimulatedRoleId(claims.getSimulatedRoleId()))) {
            invalidateSession(session, "token and session role identity mismatch");
        }
        if (session.getSessionVersion() == null || !session.getSessionVersion().equals(claims.getSessionVersion())) {
            invalidateSession(session, "会话版本已变更，请重新登录");
        }
        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            String latestSessionId = authSessionStore.findLatestActiveUserSessionId(session.getUserId(), session.getUserUuid()).orElse(null);
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

    private void requireTrustedActiveSessionUser(AuthSession session) {
        if (session == null
                || session.getUserId() == null
                || !StringUtils.hasText(session.getUserUuid())
                || !permissionSnapshotService.isTrustedActiveUser(session.getUserId(), session.getUserUuid())) {
            invalidateSession(session, "Session user is disabled or no longer trusted");
        }
    }

    private void validateSessionTicketSnapshot(
            AuthSession session,
            PermissionSnapshotService.PermissionSnapshot snapshot,
            String userUuid,
            Long simulatedRoleId,
            String permissionsVersion
    ) {
        if (session == null
                || snapshot == null
                || !StringUtils.hasText(session.getUserUuid())
                || !session.getUserUuid().trim().equals(userUuid)
                || !Objects.equals(normalizeSimulatedRoleId(session.getSimulatedRoleId()), normalizeSimulatedRoleId(simulatedRoleId))
                || !StringUtils.hasText(snapshot.getVersion())
                || !snapshot.getVersion().trim().equals(permissionsVersion)) {
            throw expiredCredentials();
        }
    }

    private void validateAccessClaims(TokenClaims claims) {
        if (claims == null
                || !StringUtils.hasText(claims.getSessionId())
                || claims.getUserId() == null
                || claims.getUserId() <= 0
                || !StringUtils.hasText(claims.getUserUuid())
                || !StringUtils.hasText(claims.getUsername())
                || !isTrustedSimulatedRoleId(claims.getSimulatedRoleId())
                || claims.getSessionVersion() == null
                || claims.getSessionVersion() <= 0
                || !StringUtils.hasText(claims.getPermissionsVersion())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "accessToken凭据已失效",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
    }

    private void validateAccessSnapshot(TokenClaims claims, PermissionSnapshotService.PermissionSnapshot snapshot) {
        if (claims == null
                || snapshot == null
                || !StringUtils.hasText(claims.getPermissionsVersion())
                || !StringUtils.hasText(snapshot.getVersion())
                || !snapshot.getVersion().trim().equals(claims.getPermissionsVersion().trim())) {
            throw expiredCredentials();
        }
    }

    private String requireTrustedAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw expiredCredentials();
        }
        String normalized = token.trim();
        if (normalized.length() > MAX_ACCESS_TOKEN_LENGTH
                || !SAFE_ACCESS_TOKEN_PATTERN.matcher(normalized).matches()) {
            throw expiredCredentials();
        }
        return normalized;
    }

    private String requireTrustedSessionTicketId(String sessionId) {
        try {
            return AuthSessionTrustValidator.requireTrustedSessionId(sessionId);
        } catch (IllegalArgumentException exception) {
            throw expiredCredentials();
        }
    }

    private BizException expiredCredentials() {
        return new BizException(
                ErrorCode.SESSION_EXPIRED,
                ErrorCode.SESSION_EXPIRED.getDefaultUserMessage(),
                ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
        );
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
        currentUser.setUserUuid(session.getUserUuid());
        currentUser.setUsername(session.getUsername());
        currentUser.setSimulatedRoleId(session.getSimulatedRoleId());
        currentUser.setSessionId(claims.getSessionId());
        currentUser.setLoginType(session.getLoginType());
        currentUser.setSessionVersion(session.getSessionVersion());
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setRequiresPasswordChange(session.getRequiresPasswordChange());
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(resolveEffectivePermissions(session, snapshot));
        currentUser.setRoleIds(snapshot.getRoleIds());
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds());
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds());
        currentUser.setDataScopes(snapshot.getDataScopes());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
        return currentUser;
    }

    private Set<String> resolveEffectivePermissions(
            AuthSession session,
            PermissionSnapshotService.PermissionSnapshot snapshot
    ) {
        Set<String> permissions = snapshot.getPermissions() == null ? Collections.emptySet() : snapshot.getPermissions();
        if (!isProtectedAdminAccount(session)) {
            return permissions;
        }
        LinkedHashSet<String> effectivePermissions = new LinkedHashSet<>(permissions);
        effectivePermissions.add("*");
        return Collections.unmodifiableSet(effectivePermissions);
    }

    private boolean isProtectedAdminAccount(AuthSession session) {
        return session != null
                && PROTECTED_ADMIN_ID == session.getUserId()
                && StringUtils.hasText(session.getUsername())
                && PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(session.getUsername().trim());
    }

    private PermissionSnapshotResolution resolvePermissionSnapshot(TokenClaims claims, AuthSession session) {
        if (session.getSimulatedRoleId() != null) {
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordAuthPermissionSnapshotFromRole();
            }
            PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadRoleSnapshot(session.getSimulatedRoleId());
            return new PermissionSnapshotResolution(snapshot, false);
        }
        if (hasPermissionSnapshot(session)) {
            if (permissionSnapshotService.isSessionPermissionSnapshotCurrent(session.getPermissionsVersion())) {
                if (ownerRuntimeMetrics != null) {
                    ownerRuntimeMetrics.recordAuthPermissionSnapshotFromSession();
                }
                return new PermissionSnapshotResolution(fromSession(session), false);
            }
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(claims.getUserId(), claims.getUserUuid());
        if (ownerRuntimeMetrics != null) {
            ownerRuntimeMetrics.recordAuthPermissionSnapshotFromUser();
        }
        hydrateSessionPermissionSnapshot(session, snapshot);
        return new PermissionSnapshotResolution(snapshot, true);
    }

    private AuthenticatedAccess revalidateCachedAuthenticatedAccess(AuthenticatedAccess cached, Instant now) {
        if (cached == null || cached.currentUser() == null || cached.session() == null) {
            return null;
        }
        AuthSession currentSession = authSessionStore.findBySessionId(cached.session().getSessionId()).orElse(null);
        if (currentSession == null) {
            return null;
        }
        TokenClaims claims = buildCachedClaims(cached.currentUser());
        validateAccessClaims(claims);
        validateSession(claims, currentSession, now);
        requireTrustedActiveSessionUser(currentSession);
        if (currentSession.getSimulatedRoleId() != null || !hasPermissionSnapshot(currentSession)) {
            return null;
        }
        String trustedPermissionsVersion = normalizePermissionsVersion(currentSession.getPermissionsVersion());
        String cachedPermissionsVersion = normalizePermissionsVersion(cached.currentUser().getPermissionsVersion());
        if (trustedPermissionsVersion == null
                || cachedPermissionsVersion == null
                || !trustedPermissionsVersion.equals(cachedPermissionsVersion)
                || !permissionSnapshotService.isSessionPermissionSnapshotCurrent(trustedPermissionsVersion)) {
            return null;
        }
        return new AuthenticatedAccess(
                buildCurrentUser(claims, currentSession, fromSession(currentSession)),
                currentSession,
                false
        );
    }

    private boolean isCacheableAuthenticatedAccess(AuthenticatedAccess authenticatedAccess) {
        return authenticatedAccess != null
                && !authenticatedAccess.sessionStateUpdated()
                && authenticatedAccess.session() != null
                && authenticatedAccess.session().getSimulatedRoleId() == null
                && hasPermissionSnapshot(authenticatedAccess.session())
                && StringUtils.hasText(authenticatedAccess.currentUser() == null ? null : authenticatedAccess.currentUser().getPermissionsVersion());
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

    private TokenClaims buildCachedClaims(CurrentUser currentUser) {
        if (currentUser == null) {
            return null;
        }
        TokenClaims claims = new TokenClaims();
        claims.setSessionId(currentUser.getSessionId());
        claims.setUserId(currentUser.getUserId());
        claims.setUserUuid(currentUser.getUserUuid());
        claims.setUsername(currentUser.getUsername());
        claims.setSimulatedRoleId(normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()));
        claims.setSessionVersion(currentUser.getSessionVersion());
        claims.setPermissionsVersion(currentUser.getPermissionsVersion());
        claims.setTokenType(TokenType.ACCESS);
        return claims;
    }

    private String normalizePermissionsVersion(String permissionsVersion) {
        return StringUtils.hasText(permissionsVersion) ? permissionsVersion.trim() : null;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private boolean isTrustedSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId > 0;
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
