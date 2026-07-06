package com.lumira.message.infrastructure.security;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class MessageSessionAuthenticationService {

    private static final int MAX_ACCESS_TOKEN_LENGTH = 8 * 1024;
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SAFE_ACCESS_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._~+/=-]+$");
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private final JwtTokenService jwtTokenService;
    private final AuthInternalApi authInternalApi;

    public MessageSessionAuthenticationService(JwtTokenService jwtTokenService, AuthInternalApi authInternalApi) {
        this.jwtTokenService = jwtTokenService;
        this.authInternalApi = authInternalApi;
    }

    public AuthenticatedAccess authenticateAccessToken(String token) {
        String trustedToken = requireTrustedAccessToken(token);
        JwtTokenClaims claims = jwtTokenService.parseToken(trustedToken);
        validateAccessClaims(claims);
        if (claims.getTokenType() != JwtTokenType.ACCESS) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "accessToken类型非法");
        }

        String trustedSessionId = requireTrustedSessionId(claims.getSessionId());
        CurrentUserDTO snapshot = authInternalApi.currentUser(
                trustedSessionId,
                claims.getUserId(),
                claims.getUserUuid(),
                claims.getSessionVersion(),
                claims.getPermissionsVersion(),
                normalizeSimulatedRoleId(claims.getSimulatedRoleId())
        );
        if (snapshot == null) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "会话不存在或已失效");
        }
        if (snapshot.userId() != null && !snapshot.userId().equals(claims.getUserId())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "token与会话不匹配");
        }
        if (snapshot.sessionVersion() != null && !snapshot.sessionVersion().equals(claims.getSessionVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "会话版本已变更，请重新登录");
        }
        validateSnapshot(
                snapshot,
                trustedSessionId,
                claims.getUserId(),
                claims.getSessionVersion(),
                claims.getSimulatedRoleId(),
                "token/session mismatch"
        );
        return new AuthenticatedAccess(buildCurrentUser(snapshot, claims), snapshot);
    }

    public AuthenticatedAccess authenticateSessionTicket(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
        if (userId == null || userId <= 0 || sessionVersion == null || sessionVersion <= 0) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
        }

        if (!StringUtils.hasText(userUuid) || !StringUtils.hasText(permissionsVersion)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket credentials expired");
        }

        String trustedSessionId = requireTrustedSessionId(sessionId);
        String trustedUserUuid = userUuid.trim();
        String trustedPermissionsVersion = permissionsVersion.trim();
        Long trustedSimulatedRoleId = normalizeSimulatedRoleId(simulatedRoleId);
        CurrentUserDTO snapshot = authInternalApi.currentUser(
                trustedSessionId,
                userId,
                trustedUserUuid,
                sessionVersion,
                trustedPermissionsVersion,
                trustedSimulatedRoleId
        );
        if (snapshot == null) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "会话不存在或已失效");
        }
        if (snapshot.userId() != null && !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
        }
        if (snapshot.sessionVersion() != null && !snapshot.sessionVersion().equals(sessionVersion)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
        }
        if (snapshot.userUuid() != null && !trustedUserUuid.equals(snapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket credentials expired");
        }
        if (snapshot.permissionsVersion() != null && !trustedPermissionsVersion.equals(snapshot.permissionsVersion().trim())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket credentials expired");
        }
        if (!Objects.equals(normalizeSimulatedRoleId(snapshot.simulatedRoleId()), trustedSimulatedRoleId)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket credentials expired");
        }
        validateSnapshot(
                snapshot,
                trustedSessionId,
                userId,
                sessionVersion,
                trustedSimulatedRoleId,
                "WebSocket credentials expired"
        );
        return new AuthenticatedAccess(buildCurrentUser(snapshot, null), snapshot);
    }

    public boolean isTrustedSession(
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
        try {
            authenticateSessionTicket(sessionId, userId, userUuid, simulatedRoleId, sessionVersion, permissionsVersion);
            return true;
        } catch (BizException exception) {
            return false;
        }
    }

    private String requireTrustedAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "accessToken credentials expired");
        }
        String normalized = token.trim();
        if (normalized.length() > MAX_ACCESS_TOKEN_LENGTH
                || !SAFE_ACCESS_TOKEN_PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "accessToken credentials expired");
        }
        return normalized;
    }

    private String requireTrustedSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "session credentials expired");
        }
        String normalized = sessionId.trim();
        if (normalized.length() > MAX_SESSION_ID_LENGTH
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "session credentials expired");
        }
        return normalized;
    }

    private void validateAccessClaims(JwtTokenClaims claims) {
        if (claims == null
                || !StringUtils.hasText(claims.getSessionId())
                || claims.getUserId() == null
                || claims.getUserId() <= 0
                || !StringUtils.hasText(claims.getUserUuid())
                || !StringUtils.hasText(claims.getUsername())
                || claims.getSessionVersion() == null
                || claims.getSessionVersion() <= 0
                || !isTrustedSimulatedRoleId(claims.getSimulatedRoleId())
                || !StringUtils.hasText(claims.getPermissionsVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "accessToken credentials expired");
        }
    }

    private void validateSnapshot(
            CurrentUserDTO snapshot,
            String sessionId,
            Long userId,
            Integer sessionVersion,
            Long simulatedRoleId,
            String message
    ) {
        if (snapshot.userId() == null
                || snapshot.userId() <= 0
                || !snapshot.userId().equals(userId)
                || !StringUtils.hasText(snapshot.userUuid())
                || !StringUtils.hasText(snapshot.username())
                || !StringUtils.hasText(snapshot.sessionId())
                || !snapshot.sessionId().equals(sessionId)
                || !Objects.equals(normalizeSimulatedRoleId(snapshot.simulatedRoleId()), normalizeSimulatedRoleId(simulatedRoleId))
                || snapshot.sessionVersion() == null
                || snapshot.sessionVersion() <= 0
                || !snapshot.sessionVersion().equals(sessionVersion)
                || !StringUtils.hasText(snapshot.permissionsVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, message);
        }
    }

    private CurrentUser buildCurrentUser(CurrentUserDTO snapshot, JwtTokenClaims claims) {
        Set<String> permissions = snapshot.permissions() == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(snapshot.permissions()));
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(snapshot.userId());
        currentUser.setUserUuid(snapshot.userUuid());
        currentUser.setUsername(snapshot.username());
        currentUser.setSessionId(snapshot.sessionId());
        currentUser.setSimulatedRoleId(snapshot.simulatedRoleId());
        currentUser.setSessionVersion(snapshot.sessionVersion());
        currentUser.setPermissionsVersion(snapshot.permissionsVersion());
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(permissions);
        currentUser.setRoleIds(snapshot.roleIds() == null ? Set.of() : Set.copyOf(snapshot.roleIds()));
        currentUser.setPrimaryDeptId(snapshot.primaryDeptId());
        currentUser.setDeptIds(snapshot.deptIds() == null ? Set.of() : Set.copyOf(snapshot.deptIds()));
        currentUser.setDescendantDeptIds(snapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.descendantDeptIds()));
        currentUser.setDataScopes(snapshot.dataScopes() == null ? List.of() : snapshot.dataScopes());
        currentUser.setRequiresPasswordChange(snapshot.requiresPasswordChange());
        currentUser.setDefaultHomePath(snapshot.defaultHomePath());
        return currentUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private boolean isTrustedSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId > 0;
    }

    public record AuthenticatedAccess(CurrentUser currentUser, CurrentUserDTO snapshot) {
    }
}
