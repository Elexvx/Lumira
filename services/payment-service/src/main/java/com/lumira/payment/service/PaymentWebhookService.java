package com.lumira.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.payment.domain.model.PaymentDomainModels.PaymentOrderAggregate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentWebhookService {

    private static final long WEBHOOK_REPLAY_WINDOW_SECONDS = 600L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentProviderCatalog providerCatalog;
    private final PaymentOutboxService outboxService;
    private final DomainEventPublisher domainEventPublisher;

    public PaymentWebhookService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentManagementAppService paymentManagementAppService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService,
            @Qualifier("paymentDomainEventPublisher") DomainEventPublisher domainEventPublisher
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.paymentManagementAppService = paymentManagementAppService;
        this.providerCatalog = providerCatalog;
        this.outboxService = outboxService;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public PaymentWebhookEventDTO handleWebhook(Long tenantId, String providerCode, String payload, Map<String, String> headers) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        var settings = paymentManagementAppService.getRequiredProviderSettings(tenantId, providerCode);
        String normalizedPayload = StringUtils.hasText(payload) ? payload.trim() : "{}";
        String eventId = resolveEventId(headers, normalizedPayload);
        String eventType = resolveEventType(headers, normalizedPayload);
        String signature = resolveSignature(headers, normalizedPayload);
        String timestamp = resolveHeader(headers, "timestamp", "X-Timestamp");
        String nonce = resolveHeader(headers, "nonce", "X-Nonce");

        PaymentWebhookEventRow existing = findWebhookEvent(tenantId, providerCode, eventId);
        if (existing != null) {
            return toDto(existing);
        }

        if (StringUtils.hasText(nonce) && isNonceReplayed(tenantId, providerCode, nonce)) {
            return insertRejectedWebhookEvent(tenantId, providerCode, eventId, eventType, normalizedPayload, signature, timestamp, nonce, "请求已被重放");
        }

        boolean signatureValid = verifySignature(definition, settings, normalizedPayload, signature, timestamp, nonce);
        boolean timestampFresh = isFreshTimestamp(timestamp);
        if (signatureValid && !timestampFresh) {
            signatureValid = false;
        }
        PaymentWebhookEventRow row = new PaymentWebhookEventRow();
        row.setTenantId(tenantId);
        row.setProviderCode(providerCatalog.normalize(providerCode));
        row.setEventId(eventId);
        row.setEventType(eventType);
        row.setNonce(nonce);
        row.setRequestTimestamp(timestamp);
        row.setPayloadJson(normalizedPayload);
        row.setSignature(signature);
        row.setSignatureValid(signatureValid ? 1 : 0);
        row.setProcessed(0);
        row.setProcessMessage(signatureValid ? "待处理" : (timestampFresh ? "签名校验失败" : "请求时间戳超时"));
        row.setReceivedAt(LocalDateTime.now());
        row.setCreatedBy(0L);
        row.setUpdatedBy(0L);
        row.setDeleted(0);

        jdbcTemplate.update(
                """
                        insert into payment_webhook_event (
                            tenant_id, provider_code, event_id, event_type, nonce, request_timestamp, payload_json,
                            signature, signature_valid, processed, process_message, received_at, created_by,
                            updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, 0)
                        """,
                row.getTenantId(),
                row.getProviderCode(),
                row.getEventId(),
                row.getEventType(),
                row.getNonce(),
                row.getRequestTimestamp(),
                row.getPayloadJson(),
                row.getSignature(),
                row.getSignatureValid(),
                row.getProcessMessage(),
                row.getReceivedAt(),
                0L,
                0L
        );

        if (signatureValid) {
            String processMessage = applyEvent(tenantId, providerCode, normalizedPayload, eventType);
            markProcessed(tenantId, providerCode, eventId, processMessage);
            row.setProcessed(1);
            row.setProcessMessage(processMessage);
            row.setProcessedAt(LocalDateTime.now());
        }

        outboxService.recordAfterCommit(
                tenantId,
                0L,
                "payment",
                "payment.webhook.received",
                providerCode + ":" + eventId,
                Map.of(
                        "providerCode", providerCode,
                        "eventId", eventId,
                        "eventType", eventType,
                        "signatureValid", signatureValid
                )
        );

        return new PaymentWebhookEventDTO(
                providerCode,
                eventId,
                eventType,
                signatureValid,
                signatureValid,
                row.getProcessMessage(),
                row.getReceivedAt(),
                row.getProcessedAt()
        );
    }

    private String applyEvent(Long tenantId, String providerCode, String payload, String eventType) {
        String normalizedEvent = normalizeText(eventType).toLowerCase(Locale.ROOT);
        if (normalizedEvent.contains("refund")) {
            String refundNo = extractField(payload, "refundNo", "refund_no", "id");
            if (StringUtils.hasText(refundNo)) {
                jdbcTemplate.update(
                        """
                                update payment_refund
                                set status = 'REFUNDED', refunded_at = ?, updated_at = ?, updated_by = ?, deleted = 0
                                where tenant_id = ? and refund_no = ? and deleted = 0
                                  and status not in ('REFUNDED', 'FAILED')
                                """,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        0L,
                        tenantId,
                        refundNo.trim()
                );
            }
            return "退款 webhook 已处理";
        }

        String orderNo = extractField(payload, "orderNo", "order_no", "merchantOrderNo", "out_trade_no", "id");
        if (StringUtils.hasText(orderNo)) {
            PaymentOrderRow order = findOrderForWebhook(tenantId, orderNo.trim());
            PaymentOrderAggregate orderAggregate = new PaymentOrderAggregate(
                    orderNo.trim(),
                    tenantId,
                    BigDecimal.valueOf(order == null || order.getAmountMinor() == null ? 1L : order.getAmountMinor(), 2),
                    order == null ? "PENDING" : order.getStatus()
            );
            orderAggregate.markPaid(extractField(payload, "providerTxnId", "transaction_id", "trade_no"));
            int updated = jdbcTemplate.update(
                    """
                            update payment_order
                            set status = 'PAID', paid_at = ?, updated_at = ?, updated_by = ?, deleted = 0
                            where tenant_id = ? and order_no = ? and deleted = 0
                              and status not in ('PAID', 'SUCCESS', 'SETTLED')
                            """,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    0L,
                    tenantId,
                    orderNo.trim()
            );
            if (updated > 0) {
                domainEventPublisher.publishAll(orderAggregate.pullDomainEvents());
            }
        }
        return "支付 webhook 已处理";
    }

    private PaymentOrderRow findOrderForWebhook(Long tenantId, String orderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, order_no as orderNo, provider_code as providerCode,
                                   provider_order_no as providerOrderNo, subject, amount_minor as amountMinor, currency,
                                   status, payment_url as paymentUrl, client_ip as clientIp, notify_url as notifyUrl,
                                   return_url as returnUrl, request_json as requestJson, response_json as responseJson,
                                   idempotency_key as idempotencyKey, failure_code as failureCode, failure_message as failureMessage,
                                   expires_at as expiresAt, paid_at as paidAt, created_by as createdBy, created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_order
                            where tenant_id = ? and order_no = ? and deleted = 0
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                    tenantId,
                    orderNo
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private void markProcessed(Long tenantId, String providerCode, String eventId, String processMessage) {
        jdbcTemplate.update(
                """
                        update payment_webhook_event
                        set processed = 1, process_message = ?, processed_at = ?, updated_at = ?, updated_by = ?, deleted = 0
                        where tenant_id = ? and provider_code = ? and event_id = ? and deleted = 0
                        """,
                processMessage,
                LocalDateTime.now(),
                LocalDateTime.now(),
                0L,
                tenantId,
                providerCatalog.normalize(providerCode),
                eventId
        );
    }

    private boolean verifySignature(
            PaymentProviderCatalog.PaymentProviderDefinition definition,
            com.lumira.api.payment.PaymentProviderSettingsDTO settings,
            String payload,
            String signature,
            String timestamp,
            String nonce
    ) {
        if (!StringUtils.hasText(signature)) {
            return false;
        }
        String normalized = providerCatalog.normalize(definition.providerCode());
        return switch (normalized) {
            case "alipay" -> verifyRsaSignature(settings.getPublicKey(), payload, signature);
            case "wechat_pay" -> verifyHmacSignature(settings.getApiV3Key(), buildWechatSignedString(timestamp, nonce, payload), signature);
            case "stripe" -> verifyHmacSignature(settings.getWebhookSecret(), buildStripeSignedString(timestamp, payload), signature);
            case "paypal" -> verifyHmacSignature(settings.getWebhookSecret(), payload, signature);
            default -> false;
        };
    }

    private boolean verifyHmacSignature(String secret, String payload, String signature) {
        if (!StringUtils.hasText(secret)) {
            return false;
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(expected);
            return constantTimeEquals(expectedSignature, signature.trim());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean verifyRsaSignature(String publicKeyPem, String payload, String signature) {
        if (!StringUtils.hasText(publicKeyPem)) {
            return false;
        }
        try {
            String normalizedPem = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalizedPem);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes)));
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] decodedSignature = Base64.getDecoder().decode(signature.trim());
            return verifier.verify(decodedSignature);
        } catch (Exception ex) {
            return false;
        }
    }

    private String buildWechatSignedString(String timestamp, String nonce, String payload) {
        return normalizeText(timestamp) + "\n" + normalizeText(nonce) + "\n" + payload + "\n";
    }

    private String buildStripeSignedString(String timestamp, String payload) {
        return normalizeText(timestamp) + "." + payload;
    }

    private boolean isFreshTimestamp(String timestamp) {
        if (!StringUtils.hasText(timestamp)) {
            return true;
        }
        try {
            long parsed = Long.parseLong(timestamp.trim());
            long current = Instant.now().getEpochSecond();
            long candidate = parsed > 1_000_000_000_000L ? parsed / 1000L : parsed;
            long delta = Math.abs(current - candidate);
            return delta <= WEBHOOK_REPLAY_WINDOW_SECONDS;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isNonceReplayed(Long tenantId, String providerCode, String nonce) {
        if (!StringUtils.hasText(nonce)) {
            return false;
        }
        try {
            return !jdbcTemplate.queryForList(
                    """
                            select 1
                            from payment_webhook_event
                            where tenant_id = ? and provider_code = ? and nonce = ? and deleted = 0
                              and received_at >= ?
                            limit 1
                            """,
                    Long.class,
                    tenantId,
                    providerCatalog.normalize(providerCode),
                    nonce.trim(),
                    LocalDateTime.now().minusSeconds(WEBHOOK_REPLAY_WINDOW_SECONDS)
            ).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private PaymentWebhookEventDTO insertRejectedWebhookEvent(
            Long tenantId,
            String providerCode,
            String eventId,
            String eventType,
            String payload,
            String signature,
            String timestamp,
            String nonce,
            String message
    ) {
        PaymentWebhookEventRow row = new PaymentWebhookEventRow();
        row.setTenantId(tenantId);
        row.setProviderCode(providerCatalog.normalize(providerCode));
        row.setEventId(eventId);
        row.setEventType(eventType);
        row.setNonce(nonce);
        row.setRequestTimestamp(timestamp);
        row.setPayloadJson(payload);
        row.setSignature(signature);
        row.setSignatureValid(0);
        row.setProcessed(0);
        row.setProcessMessage(message);
        row.setReceivedAt(LocalDateTime.now());
        row.setCreatedBy(0L);
        row.setUpdatedBy(0L);
        row.setDeleted(0);

        jdbcTemplate.update(
                """
                        insert into payment_webhook_event (
                            tenant_id, provider_code, event_id, event_type, nonce, request_timestamp, payload_json,
                            signature, signature_valid, processed, process_message, received_at, created_by,
                            updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, 0)
                        """,
                row.getTenantId(),
                row.getProviderCode(),
                row.getEventId(),
                row.getEventType(),
                row.getNonce(),
                row.getRequestTimestamp(),
                row.getPayloadJson(),
                row.getSignature(),
                row.getSignatureValid(),
                row.getProcessMessage(),
                row.getReceivedAt(),
                0L,
                0L
        );
        return toDto(row);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }

    private String resolveEventId(Map<String, String> headers, String payload) {
        String headerEventId = resolveHeader(headers, "event-id", "X-Event-Id", "Idempotency-Key");
        if (StringUtils.hasText(headerEventId)) {
            return headerEventId.trim();
        }
        return extractField(payload, "eventId", "event_id", "id", "notification_id");
    }

    private String resolveEventType(Map<String, String> headers, String payload) {
        String headerEventType = resolveHeader(headers, "event-type", "X-Event-Type", "Stripe-Event-Type");
        if (StringUtils.hasText(headerEventType)) {
            return headerEventType.trim();
        }
        return extractField(payload, "eventType", "event_type", "type", "topic");
    }

    private String resolveSignature(Map<String, String> headers, String payload) {
        String headerSignature = resolveHeader(headers, "signature", "X-Signature", "Stripe-Signature");
        if (StringUtils.hasText(headerSignature)) {
            return headerSignature.trim();
        }
        return extractField(payload, "signature", "sign", "signatures");
    }

    private String resolveHeader(Map<String, String> headers, String... keys) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key) && StringUtils.hasText(entry.getValue())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String extractField(String payload, String... fieldNames) {
        if (!StringUtils.hasText(payload)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (String fieldName : fieldNames) {
                JsonNode node = root.path(fieldName);
                if (!node.isMissingNode() && !node.isNull() && StringUtils.hasText(node.asText())) {
                    return node.asText();
                }
            }
        } catch (Exception ignored) {
            // Best effort fallback below.
        }
        for (String fieldName : fieldNames) {
            String candidate = "\"" + fieldName + "\"";
            int index = payload.indexOf(candidate);
            if (index >= 0) {
                int colon = payload.indexOf(':', index + candidate.length());
                if (colon > 0) {
                    int start = payload.indexOf('"', colon + 1);
                    int end = start < 0 ? -1 : payload.indexOf('"', start + 1);
                    if (start >= 0 && end > start) {
                        return payload.substring(start + 1, end);
                    }
                }
            }
        }
        return "";
    }

    private PaymentWebhookEventDTO toDto(PaymentWebhookEventRow row) {
        return new PaymentWebhookEventDTO(
                row.getProviderCode(),
                row.getEventId(),
                row.getEventType(),
                row.getSignatureValid() != null && row.getSignatureValid() == 1,
                row.getProcessed() != null && row.getProcessed() == 1,
                row.getProcessMessage(),
                row.getReceivedAt(),
                row.getProcessedAt()
        );
    }

    private PaymentWebhookEventRow findWebhookEvent(Long tenantId, String providerCode, String eventId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, provider_code as providerCode, event_id as eventId,
                                   event_type as eventType, nonce, request_timestamp as requestTimestamp, payload_json as payloadJson,
                                   signature, signature_valid as signatureValid, processed, process_message as processMessage,
                                   received_at as receivedAt, processed_at as processedAt,
                                   retry_count as retryCount, next_retry_at as nextRetryAt, created_by as createdBy,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_webhook_event
                            where tenant_id = ? and provider_code = ? and event_id = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentWebhookEventRow.class),
                    tenantId,
                    providerCatalog.normalize(providerCode),
                    eventId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
