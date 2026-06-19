package com.lumira.common.web.security;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ErrorResponseSanitizer {

    private final RuntimeEnvironmentService runtimeEnvironmentService;
    private final SensitiveErrorMessageSanitizer sensitiveErrorMessageSanitizer;

    public ErrorResponseSanitizer(
            RuntimeEnvironmentService runtimeEnvironmentService,
            SensitiveErrorMessageSanitizer sensitiveErrorMessageSanitizer
    ) {
        this.runtimeEnvironmentService = runtimeEnvironmentService;
        this.sensitiveErrorMessageSanitizer = sensitiveErrorMessageSanitizer;
    }

    public SanitizedError sanitize(BizException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        String safeUserMessage = safeUserMessage(exception.getUserMessage(), errorCode);
        if (runtimeEnvironmentService.isProduction()) {
            return new SanitizedError(safeUserMessage, safeUserMessage);
        }
        String debugMessage = StringUtils.hasText(exception.getMessage())
                ? sensitiveErrorMessageSanitizer.sanitize(exception.getMessage())
                : safeUserMessage;
        return new SanitizedError(StringUtils.hasText(debugMessage) ? debugMessage : safeUserMessage, safeUserMessage);
    }

    public String sanitizeValidationMessage(String message, ErrorCode fallbackCode) {
        String fallback = safeUserMessage(null, fallbackCode);
        if (!StringUtils.hasText(message)) {
            return fallback;
        }
        String sanitized = sensitiveErrorMessageSanitizer.sanitize(message);
        if (runtimeEnvironmentService.isProduction() && looksInternal(message)) {
            return fallback;
        }
        return StringUtils.hasText(sanitized) ? sanitized : fallback;
    }

    private boolean looksInternal(String value) {
        String lower = value.toLowerCase();
        return lower.contains("exception")
                || lower.contains("jdbc:")
                || lower.contains("select ")
                || lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("authorization")
                || lower.contains("cookie");
    }

    private String safeUserMessage(String userMessage, ErrorCode errorCode) {
        if (StringUtils.hasText(userMessage)) {
            return sensitiveErrorMessageSanitizer.sanitize(userMessage);
        }
        if (StringUtils.hasText(errorCode.getDefaultUserMessage())) {
            return errorCode.getDefaultUserMessage();
        }
        return errorCode.getDefaultMessage();
    }

    public record SanitizedError(String message, String userMessage) {
    }
}
