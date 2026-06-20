package com.lumira.payment.service;

import com.lumira.common.web.TraceContext;
import com.lumira.common.web.security.audit.SecurityAuditEvent;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class PaymentWebhookTenantResolver {

    private final PaymentManagementAppService paymentManagementAppService;
    private final SecurityAuditEventService securityAuditEventService;

    public PaymentWebhookTenantResolver(PaymentManagementAppService paymentManagementAppService,
                                        SecurityAuditEventService securityAuditEventService) {
        this.paymentManagementAppService = paymentManagementAppService;
        this.securityAuditEventService = securityAuditEventService;
    }

    public Long resolveTenantId(String providerCode, String payload, Map<String, String> headers) {
        try {
            return paymentManagementAppService.resolveWebhookTenantId(providerCode, payload, headers);
        } catch (RuntimeException exception) {
            recordRejected(providerCode, payload, headers, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private void recordRejected(String providerCode, String payload, Map<String, String> headers, String reasonCode) {
        if (securityAuditEventService == null) {
            return;
        }
        securityAuditEventService.record(SecurityAuditEvent.builder("WEBHOOK_TENANT_RESOLVE_FAILED", "HIGH", "DENIED")
                .requestId(TraceContext.getRequestId())
                .traceId(TraceContext.getTraceId())
                .resourceCode("payment_webhook")
                .actionCode("resolve_tenant")
                .reasonCode(StringUtils.hasText(reasonCode) ? reasonCode : "TENANT_RESOLVE_FAILED")
                .message("Payment webhook tenant resolution failed")
                .metadata(Map.of(
                        "providerCode", providerCode == null ? "" : providerCode,
                        "payloadHash", sha256(payload),
                        "headerKeys", headers == null ? java.util.Set.of() : headers.keySet()
                ))
                .build());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ignored) {
            return "";
        }
    }
}
