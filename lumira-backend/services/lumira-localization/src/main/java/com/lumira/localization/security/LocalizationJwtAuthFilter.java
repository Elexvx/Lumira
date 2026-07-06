package com.lumira.localization.security;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.security.AuthenticationTrustSupport;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class LocalizationJwtAuthFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/localization/runtime/**",
            "/api/version",
            "/api/v1/version",
            "/api/v1/*/version",
            "/actuator/health",
            "/actuator/info",
            "/api/health"
    );
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_BEARER_TOKEN_LENGTH = 8 * 1024;
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SAFE_BEARER_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._~+/=-]+$");
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private final JwtTokenService jwtTokenService;
    private final AuthInternalApi authInternalApi;

    public LocalizationJwtAuthFilter(JwtTokenService jwtTokenService, AuthInternalApi authInternalApi) {
        this.jwtTokenService = jwtTokenService;
        this.authInternalApi = authInternalApi;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));
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

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            try {
                String token = authorization.substring(BEARER_PREFIX.length()).trim();
                if (!isTrustedBearerToken(token)) {
                    throw new IllegalArgumentException("Bearer token is invalid");
                }
                JwtTokenClaims claims = jwtTokenService.parseToken(token);
                if (isTrustedAccessClaims(claims)) {
                    String trustedSessionId = normalizeSessionId(claims.getSessionId());
                    CurrentUserDTO snapshot = authInternalApi.currentUser(
                            trustedSessionId,
                            claims.getUserId(),
                            claims.getUserUuid(),
                            claims.getSessionVersion(),
                            claims.getPermissionsVersion(),
                            normalizeSimulatedRoleId(claims.getSimulatedRoleId())
                    );
                    if (isTrustedSnapshot(snapshot, claims, trustedSessionId)) {
                        CurrentUser currentUser = new CurrentUser(
                                snapshot.userId(),
                                snapshot.username(),
                                snapshot.sessionId(),
                                snapshot.sessionVersion(),
                                true,
                                snapshot.permissions() == null ? Set.of() : snapshot.permissions().stream().collect(Collectors.toUnmodifiableSet()),
                                snapshot.roleIds() == null ? Set.of() : snapshot.roleIds().stream().collect(Collectors.toUnmodifiableSet()),
                                snapshot.primaryDeptId(),
                                snapshot.deptIds() == null ? Set.of() : snapshot.deptIds().stream().collect(Collectors.toUnmodifiableSet()),
                                snapshot.descendantDeptIds() == null ? Set.of() : snapshot.descendantDeptIds().stream().collect(Collectors.toUnmodifiableSet()),
                                snapshot.dataScopes() == null ? List.of() : snapshot.dataScopes()
                        );
                        currentUser.setUserUuid(snapshot.userUuid());
                        currentUser.setSimulatedRoleId(snapshot.simulatedRoleId());
                        currentUser.setPermissionsVersion(snapshot.permissionsVersion());
                        currentUser.setRequiresPasswordChange(snapshot.requiresPasswordChange());
                        currentUser.setDefaultHomePath(snapshot.defaultHomePath());
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

    private boolean isTrustedAccessClaims(JwtTokenClaims claims) {
        return claims != null
                && claims.getTokenType() == JwtTokenType.ACCESS
                && normalizeSessionId(claims.getSessionId()) != null
                && claims.getUserId() != null
                && claims.getUserId() > 0
                && StringUtils.hasText(claims.getUserUuid())
                && StringUtils.hasText(claims.getUsername())
                && claims.getSessionVersion() != null
                && claims.getSessionVersion() > 0
                && isTrustedSimulatedRoleId(claims.getSimulatedRoleId())
                && StringUtils.hasText(claims.getPermissionsVersion());
    }

    private boolean isTrustedSnapshot(CurrentUserDTO snapshot, JwtTokenClaims claims, String trustedSessionId) {
        return snapshot != null
                && snapshot.userId() != null
                && snapshot.userId() > 0
                && snapshot.userId().equals(claims.getUserId())
                && StringUtils.hasText(snapshot.userUuid())
                && snapshot.userUuid().trim().equals(claims.getUserUuid().trim())
                && StringUtils.hasText(snapshot.username())
                && trustedSessionId != null
                && trustedSessionId.equals(normalizeSessionId(snapshot.sessionId()))
                && Objects.equals(normalizeSimulatedRoleId(snapshot.simulatedRoleId()), normalizeSimulatedRoleId(claims.getSimulatedRoleId()))
                && snapshot.sessionVersion() != null
                && snapshot.sessionVersion() > 0
                && snapshot.sessionVersion().equals(claims.getSessionVersion())
                && StringUtils.hasText(snapshot.permissionsVersion())
                && snapshot.permissionsVersion().trim().equals(claims.getPermissionsVersion().trim());
    }

    private boolean isTrustedBearerToken(String token) {
        return StringUtils.hasText(token)
                && token.length() <= MAX_BEARER_TOKEN_LENGTH
                && SAFE_BEARER_TOKEN_PATTERN.matcher(token).matches();
    }

    private String normalizeSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String normalized = sessionId.trim();
        if (normalized.length() > MAX_SESSION_ID_LENGTH
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            return null;
        }
        return normalized;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private boolean isTrustedSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId > 0;
    }
}
