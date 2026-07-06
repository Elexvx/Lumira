package com.lumira.auth.config;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.auth.model.AuthSession;
import com.lumira.auth.service.AuthSessionStore;
import com.lumira.auth.service.JwtTokenService;
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
public class AuthJwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_BEARER_TOKEN_LENGTH = 8 * 1024;
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SAFE_BEARER_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._~+/=-]+$");
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;
    private final SystemInternalApi systemInternalApi;

    public AuthJwtAuthFilter(JwtTokenService jwtTokenService, AuthSessionStore authSessionStore, SystemInternalApi systemInternalApi) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionStore = authSessionStore;
        this.systemInternalApi = systemInternalApi;
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
                    authSessionStore.findBySessionId(trustedSessionId)
                            .filter(session -> isTrustedSession(session, claims))
                            .filter(this::isTrustedActiveSessionUser)
                            .ifPresent(session -> authenticate(request, authorization, session));
                }
            } catch (RuntimeException ignored) {
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

    private boolean isTrustedSession(AuthSession session, JwtTokenClaims claims) {
        return session != null
                && session.getUserId() != null
                && session.getUserId() > 0
                && session.getUserId().equals(claims.getUserId())
                && StringUtils.hasText(session.getUserUuid())
                && session.getUserUuid().trim().equals(claims.getUserUuid().trim())
                && StringUtils.hasText(session.getUsername())
                && session.getUsername().equals(claims.getUsername())
                && Objects.equals(normalizeSimulatedRoleId(session.getSimulatedRoleId()), normalizeSimulatedRoleId(claims.getSimulatedRoleId()))
                && normalizeSessionId(session.getSessionId()) != null
                && normalizeSessionId(session.getSessionId()).equals(normalizeSessionId(claims.getSessionId()))
                && session.getSessionVersion() != null
                && session.getSessionVersion() > 0
                && session.getSessionVersion().equals(claims.getSessionVersion())
                && StringUtils.hasText(session.getPermissionsVersion())
                && session.getPermissionsVersion().trim().equals(claims.getPermissionsVersion().trim());
    }

    private boolean isTrustedActiveSessionUser(AuthSession session) {
        if (session == null || session.getUserId() == null || session.getUserId() <= 0 || !StringUtils.hasText(session.getUserUuid())) {
            return false;
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(session.getUserId());
        return user != null
                && user.userId() != null
                && user.userId().equals(session.getUserId())
                && StringUtils.hasText(user.userUuid())
                && user.userUuid().trim().equals(session.getUserUuid().trim())
                && StringUtils.hasText(user.status())
                && "ENABLED".equalsIgnoreCase(user.status().trim());
    }

    private void authenticate(HttpServletRequest request, String authorization, AuthSession session) {
        CurrentUser currentUser = new CurrentUser(
                session.getUserId(),
                session.getUsername(),
                session.getSessionId(),
                session.getSessionVersion(),
                true,
                session.getPermissions() == null ? Set.of() : session.getPermissions().stream().collect(Collectors.toUnmodifiableSet()),
                session.getRoleIds() == null ? Set.of() : session.getRoleIds().stream().collect(Collectors.toUnmodifiableSet()),
                session.getPrimaryDeptId(),
                session.getDeptIds() == null ? Set.of() : session.getDeptIds().stream().collect(Collectors.toUnmodifiableSet()),
                session.getDescendantDeptIds() == null ? Set.of() : session.getDescendantDeptIds().stream().collect(Collectors.toUnmodifiableSet()),
                session.getDataScopes() == null ? List.of() : session.getDataScopes()
        );
        currentUser.setUserUuid(session.getUserUuid());
        currentUser.setSimulatedRoleId(session.getSimulatedRoleId());
        currentUser.setLoginType(session.getLoginType());
        currentUser.setPermissionsVersion(session.getPermissionsVersion());
        currentUser.setRequiresPasswordChange(session.getRequiresPasswordChange());
        currentUser.setDefaultHomePath(session.getDefaultHomePath());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, authorization, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
