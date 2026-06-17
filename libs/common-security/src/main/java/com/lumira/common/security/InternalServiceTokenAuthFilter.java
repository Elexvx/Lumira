package com.lumira.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    public InternalServiceTokenAuthFilter(@Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isInternalServicePath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(internalToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal service token is not configured");
            return;
        }

        String requestToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!isAuthorized(requestToken, internalToken)) {
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
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
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
