package com.lumira.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

@Component
public class InternalServiceTokenAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Job-Token";
    private static final String INTERNAL_PRINCIPAL_NAME = "internal-service";
    private static final int MAX_INTERNAL_TOKEN_LENGTH = 512;

    private final String systemToken;
    private final String authToken;
    private final String authSystemToken;
    private final String fileToken;
    private final String messageToken;
    private final String paymentToken;
    private final String pluginToken;
    private final String teamToken;
    private final String jobToken;

    public InternalServiceTokenAuthFilter(@Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken) {
        this(null, null, null, null, null, null, null, null, jobToken);
    }

    public InternalServiceTokenAuthFilter(
            String systemToken,
            String authToken,
            String authSystemToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String jobToken
    ) {
        this(systemToken, authToken, authSystemToken, fileToken, messageToken, paymentToken, pluginToken, null, jobToken);
    }

    @Autowired
    public InternalServiceTokenAuthFilter(
            @Value("${saas.internal.system-token:${SAAS_INTERNAL_SYSTEM_TOKEN:}}") String systemToken,
            @Value("${saas.internal.auth-token:${SAAS_INTERNAL_AUTH_TOKEN:}}") String authToken,
            @Value("${saas.internal.auth-system-token:${SAAS_INTERNAL_AUTH_SYSTEM_TOKEN:}}") String authSystemToken,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageToken,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginToken,
            @Value("${saas.internal.team-token:${SAAS_INTERNAL_TEAM_TOKEN:}}") String teamToken,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.systemToken = firstText(systemToken, "SAAS_INTERNAL_SYSTEM_TOKEN");
        this.authToken = firstText(authToken, "SAAS_INTERNAL_AUTH_TOKEN");
        this.authSystemToken = firstText(authSystemToken, "SAAS_INTERNAL_AUTH_SYSTEM_TOKEN");
        this.fileToken = firstText(fileToken, "SAAS_INTERNAL_FILE_TOKEN");
        this.messageToken = firstText(messageToken, "SAAS_INTERNAL_MESSAGE_TOKEN");
        this.paymentToken = firstText(paymentToken, "SAAS_INTERNAL_PAYMENT_TOKEN");
        this.pluginToken = firstText(pluginToken, "SAAS_INTERNAL_PLUGIN_TOKEN");
        this.teamToken = firstText(teamToken, "SAAS_INTERNAL_TEAM_TOKEN");
        this.jobToken = firstText(jobToken, "SAAS_INTERNAL_JOB_TOKEN");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isInternalServicePath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requiredToken = InternalServiceTokenPolicy.tokenForPath(
                requestUriWithQuery(request),
                systemToken,
                authToken,
                authSystemToken,
                fileToken,
                messageToken,
                paymentToken,
                pluginToken,
                teamToken,
                jobToken
        );
        if (!StringUtils.hasText(requiredToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal service token is not configured");
            return;
        }

        String requestToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!isAuthorized(requestToken, requiredToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service token");
            return;
        }

        CurrentUser internalUser = new CurrentUser(
                0L,
                INTERNAL_PRINCIPAL_NAME,
                null,
                "internal",
                0,
                false,
                Set.of()
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(internalUser, requestToken, Collections.emptyList());
        var previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    static boolean isInternalServicePath(String requestUri) {
        return requestUri != null && (requestUri.startsWith("/internal/") || requestUri.contains("/internal/"));
    }

    static boolean isAuthorized(String requestToken, String internalToken) {
        if (!isTrustedToken(requestToken) || !isTrustedToken(internalToken)) {
            return false;
        }
        return MessageDigest.isEqual(sha256(requestToken), sha256(internalToken));
    }

    private static boolean isTrustedToken(String token) {
        if (!StringUtils.hasText(token) || token.length() > MAX_INTERNAL_TOKEN_LENGTH) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch <= 32 || ch > 126) {
                return false;
            }
        }
        return true;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static String requestUriWithQuery(HttpServletRequest request) {
        String requestUri = request == null ? null : request.getRequestURI();
        String query = request == null ? null : request.getQueryString();
        if (!StringUtils.hasText(query)) {
            return requestUri;
        }
        return requestUri + "?" + query;
    }

    private static String firstText(String value, String environmentVariable) {
        if (StringUtils.hasText(value)) {
            return value;
        }
        return System.getenv(environmentVariable);
    }
}
