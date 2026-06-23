package com.lumira.saas.modules.system.sensitive.security;

import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

final class SensitiveWordRequestSkipMatcher {

    private static final String[] EXCLUDED_PATH_PREFIXES = {
            "/api/v1/sensitive-words",
            "/api/v1/plugins",
            "/api/v1/files/upload",
            "/api/v1/auth/",
            "/api/v1/public/",
            "/api/auth/",
            "/api/public/"
    };

    private SensitiveWordRequestSkipMatcher() {
    }

    static boolean shouldSkipPath(String path) {
        if (!StringUtils.hasText(path)) {
            return true;
        }
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (containsPathPrefix(path, prefix)) {
                return true;
            }
        }
        return false;
    }

    static boolean shouldSkipMultipart(MediaType contentType) {
        return contentType != null && MediaType.MULTIPART_FORM_DATA.includes(contentType);
    }

    static boolean shouldSkipFormUrlEncoded(String contentType) {
        return !StringUtils.hasText(contentType)
                || !contentType.toLowerCase().startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    private static boolean containsPathPrefix(String path, String prefix) {
        int index = path.indexOf(prefix);
        if (index < 0) {
            return false;
        }
        int afterPrefix = index + prefix.length();
        return afterPrefix == path.length() || path.charAt(afterPrefix) == '/';
    }
}
