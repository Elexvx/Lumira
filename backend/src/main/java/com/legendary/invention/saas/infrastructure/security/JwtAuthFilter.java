package com.legendary.invention.saas.infrastructure.security;

import com.legendary.invention.saas.common.constant.HeaderConstants;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.model.AuthSession;
import com.legendary.invention.saas.infrastructure.observability.TraceContext;
import com.legendary.invention.saas.infrastructure.security.service.AuthSessionStore;
import com.legendary.invention.saas.infrastructure.security.service.SessionAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.legendary.invention.saas.common.api.ApiResponse;
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
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(
            SessionAuthenticationService sessionAuthenticationService,
            AuthSessionStore authSessionStore,
            ObjectMapper objectMapper
    ) {
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.authSessionStore = authSessionStore;
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
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess = sessionAuthenticationService.authenticateAccessToken(token);
            AuthSession session = authenticatedAccess.session();
            Instant now = Instant.now();
            setAuthentication(authenticatedAccess.currentUser());
            if (sessionAuthenticationService.shouldPersistActivity(session, now)) {
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
                exception.getErrorMessage(),
                exception.getUserMessage(),
                TraceContext.getRequestId(),
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
