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

    private final String internalToken;
    private final String systemToken;
    private final String authToken;
    private final String fileToken;
    private final String messageToken;
    private final String paymentToken;
    private final String pluginToken;
    private final String jobToken;

    public InternalServiceTokenAuthFilter(@Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken) {
        this(internalToken, null, null, null, null, null, null, null);
    }

    @Autowired
    public InternalServiceTokenAuthFilter(
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken,
            @Value("${saas.internal.system-token:${SAAS_INTERNAL_SYSTEM_TOKEN:}}") String systemToken,
            @Value("${saas.internal.auth-token:${SAAS_INTERNAL_AUTH_TOKEN:}}") String authToken,
            @Value("${saas.internal.file-token:${SAAS_INTERNAL_FILE_TOKEN:}}") String fileToken,
            @Value("${saas.internal.message-token:${SAAS_INTERNAL_MESSAGE_TOKEN:}}") String messageToken,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken,
            @Value("${saas.internal.plugin-token:${SAAS_INTERNAL_PLUGIN_TOKEN:}}") String pluginToken,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.internalToken = internalToken;
        this.systemToken = systemToken;
        this.authToken = authToken;
        this.fileToken = fileToken;
        this.messageToken = messageToken;
        this.paymentToken = paymentToken;
        this.pluginToken = pluginToken;
        this.jobToken = jobToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isInternalServicePath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requiredToken = InternalServiceTokenPolicy.tokenForPath(
                request.getRequestURI(),
                internalToken,
                systemToken,
                authToken,
                fileToken,
                messageToken,
                paymentToken,
                pluginToken,
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
                true,
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
        if (!StringUtils.hasText(requestToken) || !StringUtils.hasText(internalToken)) {
            return false;
        }
        return MessageDigest.isEqual(sha256(requestToken), sha256(internalToken));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
