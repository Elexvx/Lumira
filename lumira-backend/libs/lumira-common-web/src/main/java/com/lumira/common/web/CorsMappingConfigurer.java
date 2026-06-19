package com.lumira.common.web;

import com.lumira.common.constant.HeaderConstants;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

public final class CorsMappingConfigurer {

    private static final String[] DEFAULT_ALLOWED_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"};
    private static final String[] DEFAULT_EXPOSED_HEADERS = {HeaderConstants.REQUEST_ID, HeaderConstants.TRACE_ID};

    private CorsMappingConfigurer() {
    }

    public static void addDefaultCorsMapping(CorsRegistry registry, WebProperties webProperties) {
        CorsRegistration registration = registry.addMapping("/**")
                .allowedMethods(DEFAULT_ALLOWED_METHODS)
                .allowedHeaders("*")
                .exposedHeaders(DEFAULT_EXPOSED_HEADERS);
        if (webProperties.getCorsAllowedOrigins() != null && !webProperties.getCorsAllowedOrigins().isEmpty()) {
            registration.allowedOrigins(webProperties.getCorsAllowedOrigins().toArray(new String[0]));
        } else if (webProperties.getCorsAllowedOriginPatterns() != null && !webProperties.getCorsAllowedOriginPatterns().isEmpty()) {
            registration.allowedOriginPatterns(webProperties.getCorsAllowedOriginPatterns().toArray(new String[0]));
        }
    }
}
