package com.lumira.message.infrastructure.security;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class MessageSessionAuthenticationService {

    private final JwtTokenService jwtTokenService;
    private final AuthInternalApi authInternalApi;

    public MessageSessionAuthenticationService(JwtTokenService jwtTokenService, AuthInternalApi authInternalApi) {
        this.jwtTokenService = jwtTokenService;
        this.authInternalApi = authInternalApi;
    }

    public AuthenticatedAccess authenticateAccessToken(String token) {
        JwtTokenClaims claims = jwtTokenService.parseToken(token);
        if (claims.getTokenType() != JwtTokenType.ACCESS) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "accessToken类型非法");
        }

        CurrentUserDTO snapshot = authInternalApi.currentUser(claims.getSessionId());
        if (snapshot == null) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "会话不存在或已失效");
        }
        if (snapshot.userId() != null && !snapshot.userId().equals(claims.getUserId())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "token与会话不匹配");
        }
        if (snapshot.sessionVersion() != null && !snapshot.sessionVersion().equals(claims.getSessionVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "会话版本已变更，请重新登录");
        }
        return new AuthenticatedAccess(buildCurrentUser(snapshot, claims), snapshot);
    }

    public AuthenticatedAccess authenticateSessionTicket(String sessionId, Long userId, Integer sessionVersion) {
        if (sessionId == null || sessionId.isBlank() || userId == null || sessionVersion == null) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
        }

        CurrentUserDTO snapshot = authInternalApi.currentUser(sessionId);
        if (snapshot == null) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "会话不存在或已失效");
        }
        if (snapshot.userId() != null && !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
        }
        if (snapshot.sessionVersion() != null && !snapshot.sessionVersion().equals(sessionVersion)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "WebSocket凭证已失效");
        }
        return new AuthenticatedAccess(buildCurrentUser(snapshot, null), snapshot);
    }

    private CurrentUser buildCurrentUser(CurrentUserDTO snapshot, JwtTokenClaims claims) {
        Set<String> permissions = snapshot.permissions() == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(snapshot.permissions()));
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(snapshot.userId());
        currentUser.setUsername(snapshot.username());
        currentUser.setCurrentTenantId(PlatformConstants.PLATFORM_TENANT_ID);
        currentUser.setSessionId(snapshot.sessionId());
        currentUser.setSessionVersion(snapshot.sessionVersion());
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(permissions);
        currentUser.setRoleIds(snapshot.roleIds() == null ? Set.of() : Set.copyOf(snapshot.roleIds()));
        currentUser.setPrimaryDeptId(snapshot.primaryDeptId());
        currentUser.setDeptIds(snapshot.deptIds() == null ? Set.of() : Set.copyOf(snapshot.deptIds()));
        currentUser.setDescendantDeptIds(snapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.descendantDeptIds()));
        currentUser.setDataScopes(snapshot.dataScopes());
        return currentUser;
    }

    public record AuthenticatedAccess(CurrentUser currentUser, CurrentUserDTO snapshot) {
    }
}
