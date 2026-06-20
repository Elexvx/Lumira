package com.lumira.common.web.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.web.security.audit.SecurityAuditEvent;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@EnableConfigurationProperties(SecurityRateLimitProperties.class)
public class SecurityRateLimitFilter extends OncePerRequestFilter {

    private final SecurityRateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final ObjectProvider<ClientIpResolver> clientIpResolverProvider;
    private final ObjectProvider<SecurityAuditEventService> auditEventServiceProvider;
    private final ObjectMapper objectMapper;

    public SecurityRateLimitFilter(
            SecurityRateLimitProperties properties,
            RateLimitService rateLimitService,
            ObjectProvider<ClientIpResolver> clientIpResolverProvider,
            ObjectProvider<SecurityAuditEventService> auditEventServiceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.clientIpResolverProvider = clientIpResolverProvider;
        this.auditEventServiceProvider = auditEventServiceProvider;
        this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitRule rule = resolveRule(request);
        if (!properties.isEnabled() || rule == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String sourceIp = resolveIp(request);
        String key = rule.name() + ":ip:" + sourceIp + ":path:" + request.getRequestURI();
        RateLimitResult result = rateLimitService.check(key, rule);
        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(ErrorCode.TRAFFIC_LIMITED.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        recordRateLimited(request, rule, sourceIp);
        ApiResponse<Void> body = ApiResponse.fail(
                ErrorCode.TRAFFIC_LIMITED,
                "Request rate limited",
                "当前访问过于频繁，请稍后再试",
                TraceContext.getRequestId(),
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private RateLimitRule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(method) && (path.endsWith("/auth/login") || path.endsWith("/login"))) {
            return properties.getLogin().toRule("login");
        }
        if ("POST".equalsIgnoreCase(method) && path.endsWith("/auth/refresh-token")) {
            return properties.getRefresh().toRule("refresh");
        }
        if ("POST".equalsIgnoreCase(method) && path.toLowerCase().contains("/payment") && path.toLowerCase().contains("webhook")) {
            return properties.getWebhook().toRule("webhook");
        }
        if ("POST".equalsIgnoreCase(method) && path.toLowerCase().contains("/plugin/gateway")) {
            return properties.getPluginGateway().toRule("plugin-gateway");
        }
        if ("POST".equalsIgnoreCase(method) && path.toLowerCase().contains("/ai/") && path.toLowerCase().contains("tool")) {
            return properties.getAiTool().toRule("ai-tool");
        }
        if ("POST".equalsIgnoreCase(method) && path.toLowerCase().contains("upload")) {
            return properties.getUpload().toRule("upload");
        }
        if ("POST".equalsIgnoreCase(method) && path.toLowerCase().contains("storage") && path.toLowerCase().contains("test")) {
            return properties.getRemoteStorageTest().toRule("remote-storage-test");
        }
        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        ClientIpResolver resolver = clientIpResolverProvider.getIfAvailable();
        return resolver == null ? request.getRemoteAddr() : resolver.resolve(request);
    }

    private void recordRateLimited(HttpServletRequest request, RateLimitRule rule, String sourceIp) {
        SecurityAuditEventService auditService = auditEventServiceProvider.getIfAvailable();
        if (auditService == null) {
            return;
        }
        auditService.record(request, SecurityAuditEvent.builder("RATE_LIMITED", "WARN", "DENIED")
                .sourceIp(sourceIp)
                .reasonCode(rule.name().toUpperCase() + "_RATE_LIMITED")
                .message("Request rejected by rate limit")
                .metadata(java.util.Map.of("rule", rule.name(), "path", request.getRequestURI())));
    }
}
