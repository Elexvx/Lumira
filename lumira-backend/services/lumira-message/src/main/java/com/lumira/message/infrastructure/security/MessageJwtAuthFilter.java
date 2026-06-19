package com.lumira.message.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import com.lumira.common.web.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class MessageJwtAuthFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/version",
            "/api/v1/version",
            "/api/v1/*/version",
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus"
    );

    private final JwtTokenService jwtTokenService;
    private final MessageSessionAuthenticationService sessionAuthenticationService;
    private final ObjectMapper objectMapper;

    public MessageJwtAuthFilter(
            JwtTokenService jwtTokenService,
            MessageSessionAuthenticationService sessionAuthenticationService,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return PUBLIC_PREFIXES.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            try {
                JwtTokenClaims claims = jwtTokenService.parseToken(authorization.substring(7));
                if (claims.getTokenType() == JwtTokenType.ACCESS) {
                    MessageSessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                            sessionAuthenticationService.authenticateAccessToken(authorization.substring(7));
                    CurrentUser currentUser = authenticatedAccess.currentUser();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(currentUser, authorization, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
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
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
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
}
