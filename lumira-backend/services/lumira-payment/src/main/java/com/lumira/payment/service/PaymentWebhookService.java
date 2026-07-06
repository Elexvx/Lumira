package com.lumira.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.security.audit.SecurityAuditEvent;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.payment.domain.model.PaymentDomainModels.PaymentOrderAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final long WEBHOOK_REPLAY_WINDOW_SECONDS = 600L;
    private static final int MAX_WEBHOOK_PAYLOAD_BYTES = 256 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentProviderCatalog providerCatalog;
    private final PaymentOutboxService outboxService;
    private final DomainEventPublisher domainEventPublisher;
    private final SecurityAuditEventService securityAuditEventService;

    @Autowired
    public PaymentWebhookService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentManagementAppService paymentManagementAppService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService,
            @Qualifier("paymentDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            SecurityAuditEventService securityAuditEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.paymentManagementAppService = paymentManagementAppService;
        this.providerCatalog = providerCatalog;
        this.outboxService = outboxService;
        this.domainEventPublisher = domainEventPublisher;
        this.securityAuditEventService = securityAuditEventService;
    }

    public PaymentWebhookService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentManagementAppService paymentManagementAppService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService,
            @Qualifier("paymentDomainEventPublisher") DomainEventPublisher domainEventPublisher
    ) {
        this(jdbcTemplate, objectMapper, paymentManagementAppService, providerCatalog, outboxService, domainEventPublisher, null);
    }

    @Transactional
    public PaymentWebhookEventDTO handleWebhook(String providerCode, String payload, Map<String, String> headers) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        String normalizedPayload = StringUtils.hasText(payload) ? payload.trim() : "{}";
        if (normalizedPayload.getBytes(StandardCharsets.UTF_8).length > MAX_WEBHOOK_PAYLOAD_BYTES) {
            recordRejectedWebhook(providerCode, "PAYLOAD_TOO_LARGE", normalizedPayload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook payload too large", "Webhook request is invalid");
        }
        Map<String, String> payloadFields = parsePayloadFields(normalizedPayload);
        PaymentProviderSettingsDTO settings = paymentManagementAppService.getRequiredProviderSettings(providerCode);
        String eventId = resolveEventId(headers, normalizedPayload);
        String eventType = resolveEventType(headers, normalizedPayload);
        String signature = resolveSignature(headers, normalizedPayload);
        String timestamp = resolveHeader(headers, "timestamp", "X-Timestamp");
        String nonce = resolveHeader(headers, "nonce", "X-Nonce");
        if ("alipay".equals(providerCatalog.normalize(providerCode))) {
            eventId = firstText(payloadFields, "notify_id", "trade_no", "out_trade_no");
            eventType = firstText(payloadFields, "notify_type", "trade_status");
            signature = firstText(payloadFields, "sign");
            timestamp = firstText(payloadFields, "notify_time", "gmt_payment");
        }

        if (!verifySignature(definition, settings, normalizedPayload, payloadFields, signature, timestamp, nonce)) {
            recordRejectedWebhook(providerCode, "SIGNATURE_INVALID", normalizedPayload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook signature invalid", "Webhook request is invalid");
        }
        if (!isFreshTimestamp(timestamp)) {
            recordRejectedWebhook(providerCode, "TIMESTAMP_EXPIRED", normalizedPayload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook timestamp expired", "Webhook request is invalid");
        }
        requireWebhookEventIdentity(providerCode, eventId, eventType, normalizedPayload, headers);

        PaymentWebhookEventRow existing = findWebhookEvent(providerCode, eventId);
        if (existing != null) {
            return toDto(existing);
        }

        if (StringUtils.hasText(nonce) && isNonceReplayed(providerCode, nonce)) {
            recordRejectedWebhook(providerCode, "NONCE_REPLAY", normalizedPayload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook replay rejected", "Webhook request is invalid");
        }

        PaymentWebhookEventRow row = new PaymentWebhookEventRow();
        row.setProviderCode(providerCatalog.normalize(providerCode));
        row.setEventId(eventId);
        row.setEventType(eventType);
        row.setNonce(nonce);
        row.setRequestTimestamp(timestamp);
        row.setPayloadJson(normalizedPayload);
        row.setSignature(signature);
        row.setSignatureValid(1);
        row.setProcessed(0);
        row.setProcessMessage("PENDING");
        row.setReceivedAt(LocalDateTime.now());
        row.setCreatedBy(null);
        row.setUpdatedBy(null);
        row.setDeleted(0);

        int inserted = jdbcTemplate.update(
                """
                        insert into payment_webhook_event (
                            provider_code, event_id, event_type, nonce, request_timestamp, payload_json,
                            signature, signature_valid, processed, process_message, received_at, created_by,
                            created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, 0)
                        """,
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
                null,
                null,
                null,
                null
        );
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Payment webhook event changed during insert");
        }

        String processMessage = applyEvent(providerCode, normalizedPayload, eventType);
        if (!markProcessed(providerCode, eventId, eventType, processMessage)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Payment webhook event was changed during processing");
        }
        row.setProcessed(1);
        row.setProcessMessage(processMessage);
        row.setProcessedAt(LocalDateTime.now());

        outboxService.recordAfterCommit(
                null,
                "payment",
                "payment.webhook.received",
                providerCode + ":" + eventId,
                Map.of(
                        "providerCode", providerCode,
                        "eventId", eventId,
                        "eventType", eventType,
                        "signatureValid", true
                )
        );

        return new PaymentWebhookEventDTO(
                providerCode,
                eventId,
                eventType,
                true,
                true,
                row.getProcessMessage(),
                row.getReceivedAt(),
                row.getProcessedAt()
        );
    }

    private void requireWebhookEventIdentity(
            String providerCode,
            String eventId,
            String eventType,
            String payload,
            Map<String, String> headers
    ) {
        if (!StringUtils.hasText(eventId)) {
            recordRejectedWebhook(providerCode, "EVENT_ID_MISSING", payload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook event id is required", "Webhook request is invalid");
        }
        if (eventId.trim().length() > 128) {
            recordRejectedWebhook(providerCode, "EVENT_ID_TOO_LONG", payload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook event id is invalid", "Webhook request is invalid");
        }
        if (!StringUtils.hasText(eventType)) {
            recordRejectedWebhook(providerCode, "EVENT_TYPE_MISSING", payload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook event type is required", "Webhook request is invalid");
        }
        if (eventType.trim().length() > 128) {
            recordRejectedWebhook(providerCode, "EVENT_TYPE_TOO_LONG", payload, headers);
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook event type is invalid", "Webhook request is invalid");
        }
    }

    private void recordRejectedWebhook(String providerCode, String reason, String payload, Map<String, String> headers) {
        log.warn(
                "Rejected payment webhook providerCode={} reason={} payloadHash={} headerKeys={}",
                providerCatalog.normalize(providerCode),
                reason,
                sha256(payload),
                headers == null ? java.util.Set.of() : headers.keySet()
        );
        if (securityAuditEventService != null) {
            securityAuditEventService.record(SecurityAuditEvent.builder("WEBHOOK_" + reason, "HIGH", "DENIED")
                    .requestId(TraceContext.getRequestId())
                    .traceId(TraceContext.getTraceId())
                    .resourceCode("payment_webhook")
                    .actionCode("receive")
                    .reasonCode(reason)
                    .message("Payment webhook rejected")
                    .metadata(Map.of(
                            "providerCode", providerCatalog.normalize(providerCode),
                            "payloadHash", sha256(payload),
                            "headerKeys", headers == null ? java.util.Set.of() : headers.keySet()
                    ))
                    .build());
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizeText(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            return "";
        }
    }

    private String applyEvent(String providerCode, String payload, String eventType) {
        String normalizedProvider = providerCatalog.normalize(providerCode);
        Map<String, String> payloadFields = parsePayloadFields(payload);
        String normalizedEvent = normalizeText(eventType).toLowerCase(Locale.ROOT);
        if (normalizedEvent.contains("refund")) {
            String refundNo = extractField(payload, "refundNo", "refund_no", "id");
            if (StringUtils.hasText(refundNo)) {
                PaymentRefundRow refund = findRefundForWebhook(normalizedProvider, refundNo.trim());
                if (refund == null) {
                    throw new BizException(ErrorCode.NOT_FOUND, "Payment refund does not exist");
                }
                int refundUpdated = jdbcTemplate.update(
                        """
                                update payment_refund
                                set status = 'REFUNDED', refunded_at = ?, updated_at = ?, updated_by = ?, updated_by_uuid = ?, deleted = 0
                                where id = ?
                                  and refund_no = ?
                                  and order_no = ?
                                  and provider_code = ?
                                  and amount_minor = ?
                                  and created_by = ?
                                  and created_by_uuid = ?
                                  and status = ?
                                  and deleted = 0
                                  and status not in ('REFUNDED', 'FAILED')
                                """,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null,
                        null,
                        refund.getId(),
                        refundNo.trim(),
                        refund.getOrderNo(),
                        normalizedProvider,
                        refund.getAmountMinor(),
                        refund.getCreatedBy(),
                        refund.getCreatedByUuid(),
                        refund.getStatus()
                );
                if (refundUpdated != 1) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "Payment refund state changed during webhook processing");
                }
            }
            return "Refund webhook processed";
        }

        if ("alipay".equals(normalizedProvider)) {
            String tradeStatus = firstText(payloadFields, "trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return "Alipay trade status ignored: " + normalizeText(tradeStatus);
            }
        }

        String orderNo = extractField(payload, "orderNo", "order_no", "merchantOrderNo", "out_trade_no", "id");
        if (StringUtils.hasText(orderNo)) {
            PaymentOrderRow order = findOrderForWebhook(normalizedProvider, orderNo.trim());
            assertWebhookAmountMatchesOrder(normalizedProvider, payloadFields, order);
            PaymentOrderAggregate orderAggregate = new PaymentOrderAggregate(
                    orderNo.trim(),
                    BigDecimal.valueOf(order == null || order.getAmountMinor() == null ? 1L : order.getAmountMinor(), 2),
                    order == null ? "PENDING" : order.getStatus()
            );
            orderAggregate.markPaid(extractField(payload, "providerTxnId", "transaction_id", "trade_no"));
            int updated = jdbcTemplate.update(
                    """
                            update payment_order
                            set status = 'PAID', paid_at = ?, updated_at = ?, updated_by = ?, updated_by_uuid = ?, deleted = 0
                            where id = ?
                              and order_no = ?
                              and provider_code = ?
                              and amount_minor = ?
                              and created_by = ?
                              and created_by_uuid = ?
                              and status = ?
                              and deleted = 0
                              and status not in ('PAID', 'SUCCESS', 'SETTLED')
                            """,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null,
                    null,
                    order.getId(),
                    orderNo.trim(),
                    normalizedProvider,
                    order.getAmountMinor(),
                    order.getCreatedBy(),
                    order.getCreatedByUuid(),
                    order.getStatus()
            );
            if (updated > 0) {
                domainEventPublisher.publishAll(orderAggregate.pullDomainEvents());
                markCompetitionRegistrationPaid(orderNo.trim(), order);
            }
        }
        return "Payment webhook processed";
    }

    private void markCompetitionRegistrationPaid(String orderNo, PaymentOrderRow order) {
        Long registrationId = extractRegistrationId(order == null ? null : order.getRequestJson());
        if (registrationId == null) {
            return;
        }
        String participantNo = buildParticipantNo(registrationId);
        if (!StringUtils.hasText(participantNo)) {
            return;
        }
        int updated = jdbcTemplate.update(
                """
                        update competition_registration
                        set status = 'CONFIRMED', participant_no = ?, payment_order_no = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and deleted = 0
                          and owner_user_id = ? and owner_user_uuid = ?
                          and payment_order_no = ?
                          and participant_no is null
                        """,
                participantNo,
                orderNo,
                null,
                null,
                LocalDateTime.now(),
                registrationId,
                order.getCreatedBy(),
                order.getCreatedByUuid(),
                orderNo
        );
        if (updated != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Competition registration state changed during payment webhook processing");
        }
    }

    private Long extractRegistrationId(String requestJson) {
        if (!StringUtils.hasText(requestJson)) {
            return null;
        }
        try {
            JsonNode metadata = objectMapper.readTree(requestJson).path("metadata");
            if (!"competition_registration".equals(metadata.path("bizType").asText())) {
                return null;
            }
            JsonNode registrationId = metadata.path("registrationId");
            return registrationId.canConvertToLong() ? registrationId.asLong() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildParticipantNo(Long registrationId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select concat(upper(c.code), '-', lpad((
                                select count(1) + 1
                                from competition_registration cr2
                                where cr2.competition_id = cr.competition_id
                                  and cr2.participant_no is not null
                                  and cr2.deleted = 0
                            ), 4, '0'))
                            from competition_registration cr
                            join aiadc_competition c
                              on c.id = cr.competition_id
                             and c.deleted = 0
                            where cr.id = ? and cr.deleted = 0
                            limit 1
                            """,
                    String.class,
                    registrationId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentOrderRow findOrderForWebhook(String providerCode, String orderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, order_no as orderNo, provider_code as providerCode,
                                   provider_order_no as providerOrderNo, subject, amount_minor as amountMinor, currency,
                                   status, payment_url as paymentUrl, client_ip as clientIp, notify_url as notifyUrl,
                                   return_url as returnUrl, request_json as requestJson, response_json as responseJson,
                                   idempotency_key as idempotencyKey, failure_code as failureCode, failure_message as failureMessage,
                                   expires_at as expiresAt, paid_at as paidAt, created_by as createdBy,
                                   created_by_uuid as createdByUuid, created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_order
                            where provider_code = ? and order_no = ? and deleted = 0
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                    providerCatalog.normalize(providerCode),
                    orderNo
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private boolean markProcessed(String providerCode, String eventId, String eventType, String processMessage) {
        int updated = jdbcTemplate.update(
                """
                        update payment_webhook_event
                        set processed = 1, process_message = ?, processed_at = ?, updated_at = ?, updated_by = ?, updated_by_uuid = ?, deleted = 0
                        where provider_code = ?
                          and event_id = ?
                          and event_type = ?
                          and processed = 0
                          and signature_valid = 1
                          and deleted = 0
                        """,
                new Object[]{
                        processMessage,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        null,
                        null,
                        providerCatalog.normalize(providerCode),
                        eventId,
                        eventType
                }
        );
        return updated == 1;
    }

    private PaymentRefundRow findRefundForWebhook(String providerCode, String refundNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, refund_no as refundNo, order_no as orderNo,
                                   provider_code as providerCode, provider_refund_no as providerRefundNo,
                                   amount_minor as amountMinor, currency, status, reason,
                                   request_json as requestJson, response_json as responseJson,
                                   idempotency_key as idempotencyKey, failure_code as failureCode,
                                   failure_message as failureMessage, refunded_at as refundedAt,
                                   created_by as createdBy, created_by_uuid as createdByUuid,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_refund
                            where provider_code = ? and refund_no = ? and deleted = 0
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentRefundRow.class),
                    providerCatalog.normalize(providerCode),
                    refundNo
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private boolean verifySignature(
            PaymentProviderCatalog.PaymentProviderDefinition definition,
            PaymentProviderSettingsDTO settings,
            String payload,
            Map<String, String> payloadFields,
            String signature,
            String timestamp,
            String nonce
    ) {
        if (!StringUtils.hasText(signature)) {
            return false;
        }
        String normalized = providerCatalog.normalize(definition.providerCode());
        return switch (normalized) {
            case "alipay" -> verifyRsaSignature(settings.getPublicKey(), buildAlipaySignContent(payloadFields), signature);
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
            if (timestamp.trim().contains("-")) {
                LocalDateTime parsedDateTime = LocalDateTime.parse(timestamp.trim().replace(" ", "T"));
                long delta = Math.abs(java.time.Duration.between(parsedDateTime, LocalDateTime.now()).toSeconds());
                return delta <= WEBHOOK_REPLAY_WINDOW_SECONDS;
            }
            long parsed = Long.parseLong(timestamp.trim());
            long current = Instant.now().getEpochSecond();
            long candidate = parsed > 1_000_000_000_000L ? parsed / 1000L : parsed;
            long delta = Math.abs(current - candidate);
            return delta <= WEBHOOK_REPLAY_WINDOW_SECONDS;
        } catch (Exception ex) {
            return false;
        }
    }

    private String buildAlipaySignContent(Map<String, String> fields) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!StringUtils.hasText(key)
                    || "sign".equals(key)
                    || "sign_type".equals(key)
                    || !StringUtils.hasText(value)) {
                continue;
            }
            sorted.put(key, value);
        }
        return sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private void assertWebhookAmountMatchesOrder(String providerCode, Map<String, String> fields, PaymentOrderRow order) {
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        if (!"alipay".equals(providerCode)) {
            return;
        }
        String totalAmount = firstText(fields, "total_amount", "receipt_amount", "buyer_pay_amount");
        if (!StringUtils.hasText(totalAmount)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Alipay amount is missing", "Webhook request is invalid");
        }
        long amountMinor;
        try {
            amountMinor = new BigDecimal(totalAmount.trim()).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Alipay amount is invalid", "Webhook request is invalid");
        }
        if (order.getAmountMinor() == null || order.getAmountMinor() != amountMinor) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Alipay amount does not match local order", "Webhook request is invalid");
        }
    }

    private Map<String, String> parsePayloadFields(String payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (!StringUtils.hasText(payload)) {
            return fields;
        }
        if (payload.contains("=") && !payload.trim().startsWith("{")) {
            for (String pair : payload.split("&")) {
                int index = pair.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                fields.put(urlDecode(pair.substring(0, index)), urlDecode(pair.substring(index + 1)));
            }
            return fields;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull() && value.isValueNode()) {
                    fields.put(entry.getKey(), value.asText());
                }
            });
        } catch (Exception ignored) {
            // Leave fields empty for malformed payloads; signature verification will reject them.
        }
        return fields;
    }

    private String firstText(Map<String, String> fields, String... names) {
        for (String name : names) {
            String value = fields.get(name);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return value;
        }
    }

    private boolean isNonceReplayed(String providerCode, String nonce) {
        if (!StringUtils.hasText(nonce)) {
            return false;
        }
        try {
            return !jdbcTemplate.queryForList(
                    """
                            select 1
                            from payment_webhook_event
                            where provider_code = ? and nonce = ? and deleted = 0
                              and received_at >= ?
                            limit 1
                            """,
                    Long.class,
                    providerCatalog.normalize(providerCode),
                    nonce.trim(),
                    LocalDateTime.now().minusSeconds(WEBHOOK_REPLAY_WINDOW_SECONDS)
            ).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
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
        String payloadEventId = extractField(payload, "eventId", "event_id", "id", "notification_id");
        if (StringUtils.hasText(payloadEventId)) {
            return payloadEventId.trim();
        }
        return "";
    }

    private String resolveEventType(Map<String, String> headers, String payload) {
        String payloadEventType = extractField(payload, "eventType", "event_type", "type", "topic");
        if (StringUtils.hasText(payloadEventType)) {
            return payloadEventType.trim();
        }
        return "";
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
        Map<String, String> fields = parsePayloadFields(payload);
        for (String fieldName : fieldNames) {
            String value = fields.get(fieldName);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
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

    private PaymentWebhookEventRow findWebhookEvent(String providerCode, String eventId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, provider_code as providerCode, event_id as eventId,
                                   event_type as eventType, nonce, request_timestamp as requestTimestamp, payload_json as payloadJson,
                                   signature, signature_valid as signatureValid, processed, process_message as processMessage,
                                   received_at as receivedAt, processed_at as processedAt,
                                   retry_count as retryCount, next_retry_at as nextRetryAt, created_by as createdBy,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_webhook_event
                            where provider_code = ? and event_id = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentWebhookEventRow.class),
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
