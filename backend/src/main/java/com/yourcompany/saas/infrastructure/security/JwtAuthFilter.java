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
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.modules.iam.service.PermissionSnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.yourcompany.saas.common.api.ApiResponse;
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
    private static final long LAST_ACTIVITY_WRITE_THROTTLE_SECONDS = 30L;

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SecuritySettingsService securitySettingsService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(
            JwtTokenService jwtTokenService,
            AuthSessionStore authSessionStore,
            PermissionSnapshotService permissionSnapshotService,
            SecuritySettingsService securitySettingsService,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionStore = authSessionStore;
        this.permissionSnapshotService = permissionSnapshotService;
        this.securitySettingsService = securitySettingsService;
        this.objectMapper = objectMapper;
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
                writeUnauthorizedResponse(request, response, new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "accessToken类型非法",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));
                return;
            }

            AuthSession session = authSessionStore.findBySessionId(claims.getSessionId())
                    .orElseThrow(() -> new BizException(
                            ErrorCode.SESSION_EXPIRED,
                            "会话不存在或已失效",
                            ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                    ));

            Instant now = Instant.now();
            long idleTimeoutSeconds = securitySettingsService.getIdleTimeoutSeconds();
            validateSession(claims, session, now, idleTimeoutSeconds);
            setAuthentication(claims, session);
            if (shouldPersistActivity(session, now, idleTimeoutSeconds)) {
                session.setLastActivityAt(now);
                authSessionStore.save(session);
            }
        } catch (BizException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(request, response, ex);
            return;
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            BizException bizException = new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "accessToken解析失败: " + ex.getMessage(),
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
            writeUnauthorizedResponse(request, response, bizException);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void validateSession(TokenClaims claims, AuthSession session, Instant now, long idleTimeoutSeconds) {
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
        if (lastActivityAt != null && idleTimeoutSeconds > 0) {
            Duration idleDuration = Duration.between(lastActivityAt, now);
            if (idleDuration.compareTo(Duration.ofSeconds(idleTimeoutSeconds)) >= 0) {
                invalidateSession(session, "会话空闲超时，请重新登录");
            }
        }
    }

    private boolean shouldPersistActivity(AuthSession session, Instant now, long idleTimeoutSeconds) {
        Instant lastActivityAt = session.getLastActivityAt();
        if (lastActivityAt == null) {
            return true;
        }
        Duration elapsed = Duration.between(lastActivityAt, now);
        long throttleSeconds = idleTimeoutSeconds > 0
                ? Math.min(LAST_ACTIVITY_WRITE_THROTTLE_SECONDS, Math.max(5L, idleTimeoutSeconds / 2))
                : LAST_ACTIVITY_WRITE_THROTTLE_SECONDS;
        return elapsed.compareTo(Duration.ofSeconds(throttleSeconds)) >= 0;
    }

    private void invalidateSession(AuthSession session, String message) {
        authSessionStore.remove(session, true);
        throw new BizException(
                ErrorCode.SESSION_EXPIRED,
                message,
                ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
        );
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

    private void writeUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response, BizException exception)
            throws IOException {
        response.setStatus(exception.getErrorCode().getHttpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(
                exception.getErrorCode(),
                exception.getErrorMessage(),
                exception.getUserMessage(),
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
