package com.lumira.common.security;

import org.springframework.util.StringUtils;

public final class InternalServiceTokenPolicy {

    private InternalServiceTokenPolicy() {
    }

    public static String tokenForPath(
            String requestUri,
            String globalToken,
            String systemToken,
            String authToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String jobToken
    ) {
        String scopedToken = scopedTokenForPath(requestUri, systemToken, authToken, fileToken, messageToken, paymentToken, pluginToken, jobToken);
        return StringUtils.hasText(scopedToken) ? scopedToken : globalToken;
    }

    static String scopedTokenForPath(
            String requestUri,
            String systemToken,
            String authToken,
            String fileToken,
            String messageToken,
            String paymentToken,
            String pluginToken,
            String jobToken
    ) {
        String path = requestUri == null ? "" : requestUri;
        if (path.contains("/internal/jobs") || path.contains("/internal/jobs/")) {
            return jobToken;
        }
        if (path.startsWith("/internal/auth") || path.contains("/internal/auth/")) {
            return authToken;
        }
        if (path.startsWith("/internal/files") || path.contains("/internal/files/") || path.contains("/file/internal/")) {
            return fileToken;
        }
        if (path.startsWith("/internal/payment") || path.contains("/internal/payment/") || path.contains("/payment/internal/")) {
            return paymentToken;
        }
        if (path.contains("/message/internal/")) {
            return messageToken;
        }
        if (path.contains("/plugin/internal/")) {
            return pluginToken;
        }
        if (path.startsWith("/internal/system") || path.contains("/internal/system/")) {
            return systemToken;
        }
        return null;
    }
}
