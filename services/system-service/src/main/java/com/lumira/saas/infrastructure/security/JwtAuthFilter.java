package com.lumira.saas.infrastructure.security;

import com.lumira.saas.common.constant.HeaderConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.InitialPasswordChangeGuard;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.lumira.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String AUTH_BIZ_EXCEPTION_ATTR = "auth.bizException";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionAuthenticationService sessionAuthenticationService;
    private final AuthSessionStore authSessionStore;
    private final InitialPasswordChangeGuard initialPasswordChangeGuard;
    private final ObjectMapper objectMapper;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;

    @Autowired
    public JwtAuthFilter(
            SessionAuthenticationService sessionAuthenticationService,
            AuthSessionStore authSessionStore,
            InitialPasswordChangeGuard initialPasswordChangeGuard,
            ObjectMapper objectMapper
    ) {
        this(sessionAuthenticationService, authSessionStore, initialPasswordChangeGuard, objectMapper, null);
    }

    public JwtAuthFilter(
            SessionAuthenticationService sessionAuthenticationService,
            AuthSessionStore authSessionStore,
            InitialPasswordChangeGuard initialPasswordChangeGuard,
            ObjectMapper objectMapper,
            OwnerRuntimeMetrics ownerRuntimeMetrics
    ) {
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.authSessionStore = authSessionStore;
        this.initialPasswordChangeGuard = initialPasswordChangeGuard;
        this.objectMapper = objectMapper;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

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
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess = sessionAuthenticationService.authenticateAccessToken(token);
            AuthSession session = authenticatedAccess.session();
            boolean sessionStateUpdated = authenticatedAccess.sessionStateUpdated();
            Instant now = Instant.now();
            CurrentUser currentUser = authenticatedAccess.currentUser();
            setAuthentication(currentUser);
            if (initialPasswordChangeGuard.requiresPasswordChange(currentUser) && !isPasswordChangeAllowedRequest(request)) {
                writeForbiddenResponse(request, response);
                return;
            }
            if (sessionAuthenticationService.shouldPersistActivity(session, now)) {
                session.setLastActivityAt(now);
                sessionStateUpdated = true;
            }
            if (sessionStateUpdated) {
                authSessionStore.save(session);
                if (ownerRuntimeMetrics != null) {
                    ownerRuntimeMetrics.recordAuthSessionActivityRefresh();
                }
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

    private boolean isPasswordChangeAllowedRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return ("GET".equalsIgnoreCase(method) && (
                "/api/v2/auth/current-user".equals(path)
                        || "/api/v1/auth/current-user".equals(path)
                        || "/api/auth/current-user".equals(path)
                        || "/api/v1/auth/passkeys".equals(path)))
                || ("POST".equalsIgnoreCase(method) && (
                "/api/v2/auth/session/keepalive".equals(path)
                        || "/api/v1/auth/session/keepalive".equals(path)
                        || "/api/auth/session/keepalive".equals(path)))
                || ("POST".equalsIgnoreCase(method) && (
                "/api/v1/auth/passkeys/registration/options".equals(path)
                        || "/api/v1/auth/passkeys/registration/complete".equals(path)))
                || ("PUT".equalsIgnoreCase(method) && (
                "/api/v2/profile/password".equals(path)
                        || "/api/v1/profile/password".equals(path)))
                || ("POST".equalsIgnoreCase(method) && (
                "/api/v2/auth/logout".equals(path)
                        || "/api/v1/auth/logout".equals(path)
                        || "/api/auth/logout".equals(path)))
                || ("POST".equalsIgnoreCase(method) && (
                "/api/v2/auth/refresh-token".equals(path)
                        || "/api/v1/auth/refresh-token".equals(path)
                        || "/api/auth/refresh-token".equals(path)))
                || path.startsWith("/api/health")
                || path.startsWith("/api/version")
                || path.startsWith("/actuator/");
    }

    private void setAuthentication(CurrentUser authenticatedUser) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void writeUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response, BizException exception)
            throws IOException {
        response.setStatus(exception.getErrorCode().getHttpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getUserMessage(),
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void writeForbiddenResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(
                ErrorCode.FORBIDDEN,
                "当前账号仍在使用初始密码，请先修改密码",
                "当前账号仍在使用初始密码，请先修改密码",
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
