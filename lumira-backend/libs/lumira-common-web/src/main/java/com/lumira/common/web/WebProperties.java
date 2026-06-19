package com.lumira.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "saas.web")
public class WebProperties {

    private List<String> corsAllowedOrigins = new ArrayList<>();
    private List<String> corsAllowedOriginPatterns = new ArrayList<>();
    private boolean trustForwardedHeaders = false;
    private List<String> trustedProxyCidrs = new ArrayList<>();

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public List<String> getCorsAllowedOriginPatterns() {
        return corsAllowedOriginPatterns;
    }

    public void setCorsAllowedOriginPatterns(List<String> corsAllowedOriginPatterns) {
        this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
    }

    public boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    public void setTrustForwardedHeaders(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    public List<String> getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(List<String> trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs;
    }
}
