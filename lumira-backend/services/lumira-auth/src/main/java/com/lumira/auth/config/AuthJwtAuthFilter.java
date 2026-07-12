package com.lumira.auth.config;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.auth.model.AuthSession;
import com.lumira.auth.service.AuthSessionIdlePolicy;
import com.lumira.auth.service.AuthSessionStore;
import com.lumira.auth.service.JwtTokenService;
import com.lumira.auth.service.SecuritySettingsService;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.AccessTokenAuthenticationPort;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AuthJwtAuthFilter extends OncePerRequestFilter implements AccessTokenAuthenticationPort {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_BEARER_TOKEN_LENGTH = 8 * 1024;
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SAFE_BEARER_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._~+/=-]+$");
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;
    private final SystemInternalApi systemInternalApi;
    private final SecuritySettingsService securitySettingsService;

    public AuthJwtAuthFilter(
            JwtTokenService jwtTokenService,
            AuthSessionStore authSessionStore,
            SystemInternalApi systemInternalApi,
            SecuritySettingsService securitySettingsService
    ) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionStore = authSessionStore;
        this.systemInternalApi = systemInternalApi;
        this.securitySettingsService = securitySettingsService;
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
                CurrentUser currentUser = authenticateAccessToken(token);
                if (currentUser != null) {
                    authenticate(request, authorization, currentUser);
                }
            } catch (BizException exception) {
                SecurityContextHolder.clearContext();
                if (exception.getErrorCode() == ErrorCode.DEPENDENCY_UNAVAILABLE) {
                    response.setStatus(ErrorCode.DEPENDENCY_UNAVAILABLE.getHttpStatus());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":\"" + ErrorCode.DEPENDENCY_UNAVAILABLE.getCode()
                            + "\",\"message\":\"Authentication dependency unavailable\"}");
                    return;
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

    @Override
    public CurrentUser authenticateAccessToken(String token) {
        if (!isTrustedBearerToken(token)) {
            return null;
        }
        JwtTokenClaims claims = jwtTokenService.parseToken(token);
        if (!isTrustedAccessClaims(claims)) {
            return null;
        }
        String trustedSessionId = normalizeSessionId(claims.getSessionId());
        AuthSession session;
        try {
            session = authSessionStore.findBySessionId(trustedSessionId).orElse(null);
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Session store is unavailable");
        }
        if (!isTrustedSession(session, claims)
                || !isSessionWithinIdleTimeout(session)
                || !isTrustedActiveSessionUser(session)) {
            return null;
        }
        refreshActivity(session);
        return buildCurrentUser(session);
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
        SystemUserSnapshotDTO user;
        try {
            user = systemInternalApi.findUserById(session.getUserId());
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "User trust service is unavailable");
        }
        return user != null
                && user.userId() != null
                && user.userId().equals(session.getUserId())
                && StringUtils.hasText(user.userUuid())
                && user.userUuid().trim().equals(session.getUserUuid().trim())
                && StringUtils.hasText(user.status())
                && "ENABLED".equalsIgnoreCase(user.status().trim());
    }

    private boolean isSessionWithinIdleTimeout(AuthSession session) {
        if (!AuthSessionIdlePolicy.isIdleExpired(session, securitySettingsService.getIdleTimeoutSeconds(), Instant.now())) {
            return true;
        }
        authSessionStore.remove(session, true);
        return false;
    }

    private CurrentUser buildCurrentUser(AuthSession session) {
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
        return currentUser;
    }

    private void refreshActivity(AuthSession session) {
        Instant now = Instant.now();
        if (session.getLastActivityAt() == null
                || session.getLastActivityAt().isBefore(now.minusSeconds(60))) {
            session.setLastActivityAt(now);
            try {
                authSessionStore.save(session, false);
            } catch (RuntimeException exception) {
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Session store is unavailable");
            }
        }
    }

    private void authenticate(HttpServletRequest request, String authorization, CurrentUser currentUser) {
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
