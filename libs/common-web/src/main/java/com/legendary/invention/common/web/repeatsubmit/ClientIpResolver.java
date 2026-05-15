package com.legendary.invention.common.web.repeatsubmit;

import com.legendary.invention.common.web.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    private final WebProperties webProperties;

    public ClientIpResolver(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (!webProperties.isTrustForwardedHeaders()) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return normalizeIp(forwardedFor.split(",")[0]);
        }
        String forwarded = request.getHeader("Forwarded");
        if (StringUtils.hasText(forwarded)) {
            for (String part : forwarded.split(";")) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                    return normalizeIp(trimmed.substring(4));
                }
            }
        }
        return remoteAddr;
    }

    private String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("[")) {
            int closingBracket = trimmed.indexOf(']');
            if (closingBracket > 0) {
                trimmed = trimmed.substring(1, closingBracket);
            }
        }
        if (trimmed.contains(":") && trimmed.indexOf(':') == trimmed.lastIndexOf(':') && trimmed.contains(".")) {
            trimmed = trimmed.substring(0, trimmed.lastIndexOf(':'));
        }
        return "unknown".equalsIgnoreCase(trimmed) ? "" : trimmed;
    }
}
