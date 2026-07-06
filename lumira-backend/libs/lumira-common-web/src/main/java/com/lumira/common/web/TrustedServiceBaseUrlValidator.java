package com.lumira.common.web;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.util.StringUtils;

public final class TrustedServiceBaseUrlValidator {

    private TrustedServiceBaseUrlValidator() {
    }

    public static String requireHttpBaseUrl(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is not configured");
        }
        String normalized = value.trim();
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(propertyName + " is invalid", exception);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException(propertyName + " must use http or https");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException(propertyName + " host is required");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalStateException(propertyName + " must not include user info");
        }
        if (StringUtils.hasText(uri.getQuery()) || StringUtils.hasText(uri.getFragment())) {
            throw new IllegalStateException(propertyName + " must not include query or fragment");
        }
        return normalized;
    }
}
