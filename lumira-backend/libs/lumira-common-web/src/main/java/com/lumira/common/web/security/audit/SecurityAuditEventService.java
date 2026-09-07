package com.lumira.common.web.security.audit;

import com.lumira.api.audit.SecurityAuditRecord;
import com.lumira.api.audit.port.SecurityAuditWritePort;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.web.security.SensitiveErrorMessageSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SecurityAuditEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditEventService.class);
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final ObjectProvider<SecurityAuditWritePort> auditWritePortProvider;
    private final SensitiveErrorMessageSanitizer sanitizer;
    private final ObjectProvider<ClientIpResolver> clientIpResolverProvider;

    public SecurityAuditEventService(
            ObjectProvider<SecurityAuditWritePort> auditWritePortProvider,
            SensitiveErrorMessageSanitizer sanitizer,
            ObjectProvider<ClientIpResolver> clientIpResolverProvider
    ) {
        this.auditWritePortProvider = auditWritePortProvider;
        this.sanitizer = sanitizer;
        this.clientIpResolverProvider = clientIpResolverProvider;
    }

    public void record(SecurityAuditEvent event) {
        if (event == null || !StringUtils.hasText(event.eventType())) {
            return;
        }
        SecurityAuditEvent sanitized = sanitize(event);
        SecurityAuditWritePort auditWritePort = auditWritePortProvider.getIfAvailable();
        if (auditWritePort == null) {
            log.warn("Security audit event without jdbc eventType={} severity={} reason={} requestId={}",
                    sanitized.eventType(), sanitized.severity(), sanitized.reasonCode(), sanitized.requestId());
            return;
        }
        try {
            auditWritePort.write(new SecurityAuditRecord(
                    sanitized.userId(),
                    sanitized.employeeId(),
                    sanitized.eventType(),
                    defaultString(sanitized.severity(), "WARN"),
                    sanitized.sourceIp(),
                    limit(sanitized.userAgent(), MAX_USER_AGENT_LENGTH),
                    sanitized.requestId(),
                    sanitized.traceId(),
                    sanitized.resourceCode(),
                    sanitized.actionCode(),
                    sanitized.targetId(),
                    defaultString(sanitized.result(), "DENIED"),
                    sanitized.reasonCode(),
                    limit(sanitized.message(), MAX_MESSAGE_LENGTH),
                    sanitized.metadata()));
        } catch (RuntimeException ex) {
            log.warn("Security audit insert failed eventType={} requestId={} reason={}",
                    sanitized.eventType(), sanitized.requestId(), sanitizer.sanitize(ex.getMessage()));
        }
    }

    public void record(HttpServletRequest request, SecurityAuditEvent.Builder builder) {
        if (builder == null) {
            return;
        }
        ClientIpResolver resolver = clientIpResolverProvider.getIfAvailable();
        String sourceIp = resolver != null && request != null ? resolver.resolve(request) : null;
        record(builder
                .sourceIp(sourceIp)
                .userAgent(request == null ? null : request.getHeader("User-Agent"))
                .requestId(TraceContext.getRequestId())
                .traceId(TraceContext.getTraceId())
                .build());
    }

    private SecurityAuditEvent sanitize(SecurityAuditEvent event) {
        Map<String, Object> sanitizedMetadata = null;
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            event.metadata().forEach((key, value) -> metadata.put(sanitizer.sanitize(String.valueOf(key)), sanitizeValue(value)));
            sanitizedMetadata = metadata;
        }
        return new SecurityAuditEvent(
                event.userId(),
                event.employeeId(),
                sanitizer.sanitize(event.eventType()),
                sanitizer.sanitize(event.severity()),
                sanitizer.sanitize(event.sourceIp()),
                sanitizer.sanitize(event.userAgent()),
                sanitizer.sanitize(defaultString(event.requestId(), TraceContext.getRequestId())),
                sanitizer.sanitize(defaultString(event.traceId(), TraceContext.getTraceId())),
                sanitizer.sanitize(event.resourceCode()),
                sanitizer.sanitize(event.actionCode()),
                sanitizer.sanitize(event.targetId()),
                sanitizer.sanitize(event.result()),
                sanitizer.sanitize(event.reasonCode()),
                sanitizer.sanitize(event.message()),
                sanitizedMetadata);
    }

    private Object sanitizeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return sanitizer.sanitize(String.valueOf(value));
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
