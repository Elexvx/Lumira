package com.lumira.plugin.infrastructure.security;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.constant.HeaderConstants;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class PluginJwtAuthFilter extends OncePerRequestFilter {

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
    private final AuthInternalApi authInternalApi;

    public PluginJwtAuthFilter(JwtTokenService jwtTokenService, AuthInternalApi authInternalApi) {
        this.jwtTokenService = jwtTokenService;
        this.authInternalApi = authInternalApi;
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
                    CurrentUserDTO snapshot = authInternalApi.currentUser(claims.getSessionId());
                    if (snapshot != null) {
                        CurrentUser currentUser = new CurrentUser(
                                snapshot.userId(),
                                snapshot.username(),
                                PlatformConstants.PLATFORM_TENANT_ID,
                                snapshot.sessionId(),
                                snapshot.sessionVersion(),
                                true,
                                snapshot.permissions() == null ? Set.of() : snapshot.permissions().stream().collect(Collectors.toUnmodifiableSet())
                        );
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(currentUser, authorization, List.of());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
