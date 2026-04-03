package com.yourcompany.saas.infrastructure.security;

import com.yourcompany.saas.common.constant.HeaderConstants;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.model.TokenClaims;
import com.yourcompany.saas.infrastructure.security.model.TokenType;
import com.yourcompany.saas.infrastructure.security.service.AuthSessionStore;
import com.yourcompany.saas.infrastructure.security.service.JwtTokenService;
import com.yourcompany.saas.infrastructure.security.service.SecuritySettingsService;
import com.yourcompany.saas.modules.iam.service.PermissionSnapshotService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_BIZ_EXCEPTION_ATTR = "auth.bizException";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SecuritySettingsService securitySettingsService;

    public JwtAuthFilter(
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HeaderConstants.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            TokenClaims claims = jwtTokenService.parseToken(token);
            if (claims.getTokenType() != TokenType.ACCESS) {
                throw new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "accessToken类型非法",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                );
            }

            AuthSession session = authSessionStore.findBySessionId(claims.getSessionId())
                    .orElseThrow(() -> new BizException(
                            ErrorCode.SESSION_EXPIRED,
                            "会话不存在或已失效",
                            ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                    ));

            validateSession(claims, session);
            setAuthentication(claims, session);
            session.setLastActivityAt(Instant.now());
            authSessionStore.save(session);
        } catch (BizException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_BIZ_EXCEPTION_ATTR, ex);
            throw new BadCredentialsException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            BizException bizException = new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "accessToken解析失败: " + ex.getMessage(),
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
            request.setAttribute(AUTH_BIZ_EXCEPTION_ATTR, bizException);
            throw new BadCredentialsException(bizException.getMessage(), ex);
        }

        filterChain.doFilter(request, response);
    }

    private void validateSession(TokenClaims claims, AuthSession session) {
        if (!session.getUserId().equals(claims.getUserId())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "token与会话不匹配",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
        if (session.getSessionVersion() == null || !session.getSessionVersion().equals(claims.getSessionVersion())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "会话版本已变更，请重新登录",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
        if (jwtTokenService.isExpired(session.getExpireTime())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "会话已过期，请重新登录",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
        Instant lastActivityAt = session.getLastActivityAt() != null ? session.getLastActivityAt() : session.getLoginTime();
        long idleTimeoutSeconds = securitySettingsService.getIdleTimeoutSeconds();
        if (lastActivityAt != null && idleTimeoutSeconds > 0) {
            Duration idleDuration = Duration.between(lastActivityAt, Instant.now());
            if (idleDuration.compareTo(Duration.ofSeconds(idleTimeoutSeconds)) >= 0) {
                throw new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "会话空闲超时，请重新登录",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                );
            }
        }
    }

    private void setAuthentication(TokenClaims claims, AuthSession session) {
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(
                session.getCurrentTenantId(),
                claims.getUserId()
        );
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(claims.getUserId());
        currentUser.setUsername(claims.getUsername());
        currentUser.setCurrentTenantId(session.getCurrentTenantId());
        currentUser.setSessionId(claims.getSessionId());
        currentUser.setSessionVersion(session.getSessionVersion());
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(snapshot.getPermissions());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
