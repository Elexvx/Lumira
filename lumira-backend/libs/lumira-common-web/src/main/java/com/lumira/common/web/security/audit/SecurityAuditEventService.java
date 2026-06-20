package com.lumira.common.web.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.web.security.SensitiveErrorMessageSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SecurityAuditEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditEventService.class);
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectMapper objectMapper;
    private final SensitiveErrorMessageSanitizer sanitizer;
    private final ObjectProvider<ClientIpResolver> clientIpResolverProvider;

    public SecurityAuditEventService(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            SensitiveErrorMessageSanitizer sanitizer,
            ObjectProvider<ClientIpResolver> clientIpResolverProvider
    ) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.sanitizer = sanitizer;
        this.clientIpResolverProvider = clientIpResolverProvider;
    }

    public void record(SecurityAuditEvent event) {
        if (event == null || !StringUtils.hasText(event.eventType())) {
            return;
        }
        SecurityAuditEvent sanitized = sanitize(event);
        JdbcTemplate operations = jdbcTemplateProvider.getIfAvailable();
        if (operations == null) {
            log.warn("Security audit event without jdbc eventType={} severity={} reason={} requestId={}",
                    sanitized.eventType(), sanitized.severity(), sanitized.reasonCode(), sanitized.requestId());
            return;
        }
        try {
            operations.update("""
                    INSERT INTO security_audit_event (
                        tenant_id, user_id, employee_id, event_type, severity, source_ip, user_agent,
                        request_id, trace_id, resource_code, action_code, target_id, result, reason_code,
                        message, metadata_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    sanitized.tenantId(),
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
                    toJson(sanitized.metadata()));
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
                event.tenantId(),
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

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            return "{\"serialization\":\"failed\"}";
        }
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
