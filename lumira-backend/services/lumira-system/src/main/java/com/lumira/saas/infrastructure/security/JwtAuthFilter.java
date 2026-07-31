package com.lumira.saas.infrastructure.security;

import com.lumira.saas.common.constant.HeaderConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.AccessTokenAuthenticationPort;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.InitialPasswordChangeGuard;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.common.web.security.audit.SecurityAuditEvent;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.lumira.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.regex.Pattern;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    public static final String AUTH_BIZ_EXCEPTION_ATTR = "auth.bizException";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_BEARER_TOKEN_LENGTH = 8 * 1024;
    private static final Pattern SAFE_BEARER_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._~+/=-]+$");

    private final SessionAuthenticationService sessionAuthenticationService;
    private final AuthSessionStore authSessionStore;
    private final InitialPasswordChangeGuard initialPasswordChangeGuard;
    private final ObjectMapper objectMapper;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private final SecurityAuditEventService securityAuditEventService;
    private ObjectProvider<AccessTokenAuthenticationPort> accessTokenAuthenticationPortProvider;
    private AccessTokenAuthenticationPort accessTokenAuthenticationPort;

    @Autowired
    public JwtAuthFilter(
            SessionAuthenticationService sessionAuthenticationService,
            AuthSessionStore authSessionStore,
            InitialPasswordChangeGuard initialPasswordChangeGuard,
            ObjectMapper objectMapper,
            SecurityAuditEventService securityAuditEventService
    ) {
        this(sessionAuthenticationService, authSessionStore, initialPasswordChangeGuard, objectMapper, null, securityAuditEventService);
    }

    public JwtAuthFilter(
            SessionAuthenticationService sessionAuthenticationService,
            AuthSessionStore authSessionStore,
            InitialPasswordChangeGuard initialPasswordChangeGuard,
            ObjectMapper objectMapper,
            OwnerRuntimeMetrics ownerRuntimeMetrics
    ) {
        this(sessionAuthenticationService, authSessionStore, initialPasswordChangeGuard, objectMapper, ownerRuntimeMetrics, null);
    }

    public JwtAuthFilter(
            SessionAuthenticationService sessionAuthenticationService,
            AuthSessionStore authSessionStore,
            InitialPasswordChangeGuard initialPasswordChangeGuard,
            ObjectMapper objectMapper,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            SecurityAuditEventService securityAuditEventService
    ) {
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.authSessionStore = authSessionStore;
        this.initialPasswordChangeGuard = initialPasswordChangeGuard;
        this.objectMapper = objectMapper;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.securityAuditEventService = securityAuditEventService;
    }

    @Autowired(required = false)
    void setAccessTokenAuthenticationPortProvider(
            ObjectProvider<AccessTokenAuthenticationPort> accessTokenAuthenticationPortProvider
    ) {
        this.accessTokenAuthenticationPortProvider = accessTokenAuthenticationPortProvider;
    }

    void setAccessTokenAuthenticationPort(AccessTokenAuthenticationPort accessTokenAuthenticationPort) {
        this.accessTokenAuthenticationPort = accessTokenAuthenticationPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (AuthenticationTrustSupport.canReuse(existingAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (existingAuthentication != null) {
            SecurityContextHolder.clearContext();
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
        if (!isTrustedBearerToken(token)) {
            SecurityContextHolder.clearContext();
            BizException bizException = new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage(),
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
            writeUnauthorizedResponse(request, response, bizException);
            return;
        }

        try {
            AccessTokenAuthenticationPort authenticationPort = accessTokenAuthenticationPort != null
                    ? accessTokenAuthenticationPort
                    : accessTokenAuthenticationPortProvider == null
                    ? null
                    : accessTokenAuthenticationPortProvider.getIfAvailable();
            if (authenticationPort != null) {
                CurrentUser currentUser = authenticationPort.authenticateAccessToken(token);
                if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
                    throw new BizException(
                            ErrorCode.SESSION_EXPIRED,
                            ErrorCode.SESSION_EXPIRED.getDefaultUserMessage(),
                            ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                    );
                }
                setAuthentication(currentUser);
                if (initialPasswordChangeGuard.requiresPasswordChange(currentUser) && !isPasswordChangeAllowedRequest(request)) {
                    writeForbiddenResponse(request, response);
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess = sessionAuthenticationService.authenticateAccessToken(token);
            AuthSession session = authenticatedAccess.session();
            boolean sessionStateUpdated = authenticatedAccess.sessionStateUpdated();
            boolean activityStateUpdated = false;
            Instant now = Instant.now();
            CurrentUser currentUser = authenticatedAccess.currentUser();
            setAuthentication(currentUser);
            if (initialPasswordChangeGuard.requiresPasswordChange(currentUser) && !isPasswordChangeAllowedRequest(request)) {
                writeForbiddenResponse(request, response);
                return;
            }
            if (sessionAuthenticationService.shouldPersistActivity(session, now)) {
                session.setLastActivityAt(now);
                activityStateUpdated = true;
            }
            if (sessionStateUpdated || activityStateUpdated) {
                boolean persisted = false;
                try {
                    authSessionStore.save(session);
                    persisted = true;
                } catch (BizException exception) {
                    if (sessionStateUpdated
                            || !activityStateUpdated
                            || exception.getErrorCode() != ErrorCode.SESSION_EXPIRED) {
                        throw exception;
                    }
                    SessionAuthenticationService.AuthenticatedAccess concurrentAccess =
                            sessionAuthenticationService.authenticateAccessToken(token);
                    currentUser = concurrentAccess.currentUser();
                    setAuthentication(currentUser);
                    if (initialPasswordChangeGuard.requiresPasswordChange(currentUser)
                            && !isPasswordChangeAllowedRequest(request)) {
                        writeForbiddenResponse(request, response);
                        return;
                    }
                }
                if (persisted && ownerRuntimeMetrics != null) {
                    ownerRuntimeMetrics.recordAuthSessionActivityRefresh();
                }
            }
        } catch (BizException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(request, response, ex);
            return;
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Access token parse failed requestId={} reason={}", TraceContext.getRequestId(), ex.getMessage(), ex);
            recordTokenParseFailed(request, ex);
            BizException bizException = new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage(),
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
            writeUnauthorizedResponse(request, response, bizException);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTrustedBearerToken(String token) {
        return token.length() <= MAX_BEARER_TOKEN_LENGTH
                && SAFE_BEARER_TOKEN_PATTERN.matcher(token).matches();
    }

    private boolean isPasswordChangeAllowedRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return ("GET".equalsIgnoreCase(method) && (
                "/api/v2/auth/current-user".equals(path)
                        || "/api/v1/auth/current-user".equals(path)
                        || "/api/auth/current-user".equals(path)
                        || "/api/v2/auth/bootstrap".equals(path)
                        || "/api/v1/auth/bootstrap".equals(path)
                        || "/api/auth/bootstrap".equals(path)
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
                || "/api/v2/runtime/version".equals(path)
                || path.startsWith("/actuator/");
    }

    private void setAuthentication(CurrentUser authenticatedUser) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void recordTokenParseFailed(HttpServletRequest request, RuntimeException exception) {
        if (securityAuditEventService == null) {
            return;
        }
        securityAuditEventService.record(request, SecurityAuditEvent.builder("TOKEN_PARSE_FAILED", "WARN", "DENIED")
                .resourceCode("auth_session")
                .actionCode("access_token_authenticate")
                .reasonCode(exception.getClass().getSimpleName())
                .message("Access token parse failed"));
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
