package com.lumira.common.web.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SensitiveErrorMessageSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*)(bearer\\s+)?[^\\s,;]+"),
            Pattern.compile("(?i)(cookie\\s*[:=]\\s*)[^\\r\\n]+"),
            Pattern.compile("(?i)((access|refresh)?token\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)((secret|password|accessKey|secretKey|api[_-]?key)\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("(?i)jdbc:[^\\s,;]+"),
            Pattern.compile("(?i)select\\s+.+?\\s+from\\s+[^\\r\\n;]+"),
            Pattern.compile("(?i)(insert|update|delete)\\s+[^\\r\\n;]+"),
            Pattern.compile("(?m)\\s+at\\s+[\\w.$]+\\([^\\r\\n]+\\)"),
            Pattern.compile("([A-Za-z]:\\\\[^\\s,;]+|/[^\\s,;]*(etc|var|home|root|opt|usr)[^\\s,;]*)"),
            Pattern.compile("(?i)https?://(localhost|metadata\\.google\\.internal|(?:10|127|169\\.254|192\\.168)\\.[^\\s,;]+|172\\.(?:1[6-9]|2\\d|3[0-1])\\.[^\\s,;]+)[^\\s,;]*")
    );

    public String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String sanitized = message;
        for (Pattern pattern : PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(match -> {
                if (match.groupCount() >= 1 && match.group(1) != null) {
                    return match.group(1) + REDACTED;
                }
                return REDACTED;
            });
        }
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
