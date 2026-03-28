package com.yourcompany.saas.infrastructure.security;

import com.yourcompany.saas.common.constant.HeaderConstants;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.model.TokenClaims;
import com.yourcompany.saas.infrastructure.security.model.TokenType;
import com.yourcompany.saas.infrastructure.security.service.AuthSessionStore;
import com.yourcompany.saas.infrastructure.security.service.JwtTokenService;
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
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;

    public JwtAuthFilter(JwtTokenService jwtTokenService, AuthSessionStore authSessionStore) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionStore = authSessionStore;
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
                throw new BizException(ErrorCode.UNAUTHORIZED, "accessToken非法");
            }

            AuthSession session = authSessionStore.findBySessionId(claims.getSessionId())
                    .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "会话不存在或已失效"));

            validateSession(claims, session);
            setAuthentication(claims, session);
        } catch (BizException ex) {
            SecurityContextHolder.clearContext();
            throw new BadCredentialsException(ex.getMessage(), ex);
        }

        filterChain.doFilter(request, response);
    }

    private void validateSession(TokenClaims claims, AuthSession session) {
        if (!session.getUserId().equals(claims.getUserId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token与会话不匹配");
        }
        if (session.getSessionVersion() == null || !session.getSessionVersion().equals(claims.getSessionVersion())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话版本已变更，请重新登录");
        }
        if (jwtTokenService.isExpired(session.getExpireTime())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话已过期，请重新登录");
        }
    }

    private void setAuthentication(TokenClaims claims, AuthSession session) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(claims.getUserId());
        currentUser.setUsername(claims.getUsername());
        currentUser.setCurrentTenantId(session.getCurrentTenantId());
        currentUser.setSessionId(claims.getSessionId());
        currentUser.setSessionVersion(session.getSessionVersion());
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Collections.emptySet());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
