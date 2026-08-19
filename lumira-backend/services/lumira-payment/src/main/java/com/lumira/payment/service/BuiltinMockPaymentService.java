package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.BuiltinMockPaymentCheckoutDTO;
import com.lumira.api.payment.BuiltinMockPaymentSimulationRequestDTO;
import com.lumira.api.payment.BuiltinMockPaymentSimulationResultDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class BuiltinMockPaymentService {

    private static final List<String> ALLOWED_OUTCOMES = List.of("SUCCESS", "FAILURE", "CANCEL", "TIMEOUT");
    private static final List<Integer> DELAY_OPTIONS = List.of(0, 5, 30, 60, 120, 300);
    private static final int MAX_DELAY_SECONDS = 300;
    private static final int MAX_CALLBACK_RETRY = 8;
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentWebhookService paymentWebhookService;
    private final BuiltinMockPaymentAvailability availability;
    private final PaymentActorResolver actorResolver;
    private final TransactionTemplate transactionTemplate;

    public BuiltinMockPaymentService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentManagementAppService paymentManagementAppService,
            PaymentWebhookService paymentWebhookService,
            BuiltinMockPaymentAvailability availability,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.paymentManagementAppService = paymentManagementAppService;
        this.paymentWebhookService = paymentWebhookService;
        this.availability = availability;
        this.actorResolver = new PaymentActorResolver();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public BuiltinMockPaymentCheckoutDTO checkout(CurrentUser currentUser, String orderNo) {
        availability.requireEnabledForWrite();
        PaymentActorResolver.Actor actor = actorResolver.requireAuthenticated(currentUser);
        PaymentOrderRow order = requireOwnedOrder(actor, orderNo);
        requireBuiltinMockOrder(order);
        CallbackSnapshot callback = findCallbackByOrderNo(order.getOrderNo());
        return toCheckout(order, callback);
    }

    public BuiltinMockPaymentSimulationResultDTO simulate(
            CurrentUser currentUser,
            String orderNo,
            BuiltinMockPaymentSimulationRequestDTO request
    ) {
        PaymentActorResolver.Actor actor = actorResolver.requireAuthenticated(currentUser);
        String outcome = normalizeOutcome(request == null ? null : request.outcome());
        int delaySeconds = normalizeDelay(request == null ? null : request.callbackDelaySeconds());
        EnqueuedCallback enqueued = transactionTemplate.execute(status -> enqueueSimulation(actor, orderNo, outcome, delaySeconds));
        if (enqueued == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Mock payment simulation could not be queued");
        }
        if (delaySeconds == 0) {
            dispatchCallback(enqueued.notifyId());
        }
        PaymentOrderRow refreshedOrder = requireOwnedOrder(actor, orderNo);
        CallbackSnapshot callback = findCallbackByNotifyId(enqueued.notifyId());
        return new BuiltinMockPaymentSimulationResultDTO(
                toOrderDto(refreshedOrder),
                outcome,
                callback == null ? "PENDING" : callback.status(),
                enqueued.notifyId(),
                enqueued.scheduledAt(),
                safeReturnUrl(refreshedOrder.getReturnUrl())
        );
    }

    public int dispatchDueCallbacks(int limit) {
        if (!availability.isEnabled()) {
            return 0;
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update payment_builtin_mock_callback
                        set status = 'PROCESSING', claim_token = ?, claim_expires_at = ?, updated_at = ?
                        where id in (
                            select id from (
                                select id
                                from payment_builtin_mock_callback
                                where deleted = 0
                                  and (
                                    (status in ('PENDING', 'RETRY') and coalesce(next_retry_at, scheduled_at) <= ?)
                                    or (status = 'PROCESSING' and claim_expires_at < ?)
                                  )
                                order by scheduled_at asc, id asc
                                limit ?
                            ) due_callbacks
                        )
                          and deleted = 0
                          and (
                            (status in ('PENDING', 'RETRY') and coalesce(next_retry_at, scheduled_at) <= ?)
                            or (status = 'PROCESSING' and claim_expires_at < ?)
                          )
                        """,
                claimToken,
                now.plusMinutes(2),
                now,
                now,
                now,
                normalizedLimit,
                now,
                now
        );
        List<CallbackRow> callbacks = jdbcTemplate.query(
                """
                        select id, notify_id as notifyId, order_no as orderNo,
                               provider_trade_no as providerTradeNo, outcome, trade_status as tradeStatus,
                               status, scheduled_at as scheduledAt, next_retry_at as nextRetryAt,
                               retry_count as retryCount, max_retry as maxRetry, claim_token as claimToken,
                               claim_expires_at as claimExpiresAt, payload_json as payloadJson,
                               last_error as lastError, processed_at as processedAt
                        from payment_builtin_mock_callback
                        where deleted = 0 and status = 'PROCESSING' and claim_token = ?
                        order by scheduled_at asc, id asc
                        """,
                new BeanPropertyRowMapper<>(CallbackRow.class),
                claimToken
        );
        int delivered = 0;
        for (CallbackRow callback : callbacks) {
            if (dispatchClaimedCallback(callback)) {
                delivered += 1;
            }
        }
        return delivered;
    }

    public void cancelPendingForPluginDisable(Long operatorId, String operatorUuid) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update payment_builtin_mock_callback
                        set status = 'CANCELLED', last_error = 'PLUGIN_DISABLED', claim_token = null,
                            claim_expires_at = null, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where deleted = 0 and status in ('PENDING', 'RETRY', 'PROCESSING')
                        """,
                operatorId,
                operatorUuid,
                now
        );
        jdbcTemplate.update(
                """
                        update payment_order
                        set status = 'CANCELLED', payment_url = null, failure_code = 'PLUGIN_DISABLED',
                            failure_message = 'Built-in mock payment was disabled before payment completed',
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where provider_code = ? and deleted = 0 and status in ('CREATED', 'PENDING')
                        """,
                operatorId,
                operatorUuid,
                now,
                BuiltinMockPaymentAvailability.PROVIDER_CODE
        );
    }

    private EnqueuedCallback enqueueSimulation(
            PaymentActorResolver.Actor actor,
            String orderNo,
            String outcome,
            int delaySeconds
    ) {
        availability.requireEnabledForWrite();
        PaymentOrderRow order = requireOwnedOrderForUpdate(actor, orderNo);
        requireBuiltinMockOrder(order);
        if (order.getExpiresAt() != null && !order.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Mock payment order has expired");
        }
        if (!List.of("CREATED", "PENDING").contains(order.getStatus())) {
            CallbackSnapshot existing = findCallbackByOrderNo(order.getOrderNo());
            if (existing != null && outcome.equals(existing.outcome())) {
                return new EnqueuedCallback(existing.notifyId(), existing.scheduledAt());
            }
            throw new BizException(ErrorCode.BIZ_ERROR, "Current mock payment order status cannot be simulated");
        }
        CallbackSnapshot existing = findCallbackByOrderNo(order.getOrderNo());
        if (existing != null) {
            if (outcome.equals(existing.outcome())) {
                return new EnqueuedCallback(existing.notifyId(), existing.scheduledAt());
            }
            throw new BizException(ErrorCode.BIZ_ERROR, "A simulation result has already been submitted for this order");
        }
        String notifyId = "mock-notify-" + UUID.randomUUID().toString().replace("-", "");
        String providerTradeNo = "mock-trade-" + UUID.randomUUID().toString().replace("-", "");
        String tradeStatus = "SUCCESS".equals(outcome) ? "TRADE_SUCCESS" : "TRADE_CLOSED";
        LocalDateTime scheduledAt = LocalDateTime.now().plusSeconds(delaySeconds);
        try {
            int inserted = jdbcTemplate.update(
                    """
                            insert into payment_builtin_mock_callback (
                                notify_id, order_no, provider_trade_no, outcome, trade_status, status,
                                scheduled_at, next_retry_at, retry_count, max_retry,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, 'PENDING', ?, ?, 0, ?, ?, ?, ?, ?, 0)
                            """,
                    notifyId,
                    order.getOrderNo(),
                    providerTradeNo,
                    outcome,
                    tradeStatus,
                    scheduledAt,
                    scheduledAt,
                    MAX_CALLBACK_RETRY,
                    actor.userId(),
                    actor.userUuid(),
                    actor.userId(),
                    actor.userUuid()
            );
            if (inserted != 1) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Mock payment simulation changed, please retry");
            }
        } catch (DuplicateKeyException exception) {
            CallbackSnapshot concurrent = findCallbackByOrderNo(order.getOrderNo());
            if (concurrent != null && outcome.equals(concurrent.outcome())) {
                return new EnqueuedCallback(concurrent.notifyId(), concurrent.scheduledAt());
            }
            throw new BizException(ErrorCode.BIZ_ERROR, "A simulation result has already been submitted for this order");
        }
        return new EnqueuedCallback(notifyId, scheduledAt);
    }

    private void dispatchCallback(String notifyId) {
        if (!availability.isEnabled()) {
            return;
        }
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int claimed = jdbcTemplate.update(
                """
                        update payment_builtin_mock_callback
                        set status = 'PROCESSING', claim_token = ?, claim_expires_at = ?, updated_at = ?
                        where notify_id = ? and deleted = 0 and status in ('PENDING', 'RETRY')
                          and coalesce(next_retry_at, scheduled_at) <= ?
                        """,
                claimToken,
                now.plusMinutes(2),
                now,
                notifyId,
                now
        );
        if (claimed != 1) {
            return;
        }
        CallbackRow callback = jdbcTemplate.queryForObject(
                """
                        select id, notify_id as notifyId, order_no as orderNo,
                               provider_trade_no as providerTradeNo, outcome, trade_status as tradeStatus,
                               status, scheduled_at as scheduledAt, next_retry_at as nextRetryAt,
                               retry_count as retryCount, max_retry as maxRetry, claim_token as claimToken,
                               claim_expires_at as claimExpiresAt, payload_json as payloadJson,
                               last_error as lastError, processed_at as processedAt
                        from payment_builtin_mock_callback
                        where notify_id = ? and deleted = 0 and status = 'PROCESSING' and claim_token = ?
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CallbackRow.class),
                notifyId,
                claimToken
        );
        dispatchClaimedCallback(callback);
    }

    private boolean dispatchClaimedCallback(CallbackRow callback) {
        if (callback == null) {
            return false;
        }
        if (!availability.isEnabled()) {
            markCallbackCancelled(callback, "PLUGIN_DISABLED");
            return false;
        }
        try {
            Boolean delivered = transactionTemplate.execute(status -> {
                availability.requireEnabledForWrite();
                PaymentOrderRow order = requireOrder(callback.getOrderNo());
                PaymentProviderSettingsDTO settings = paymentManagementAppService.getRequiredProviderSettings(
                        BuiltinMockPaymentAvailability.PROVIDER_CODE
                );
                String payload = buildSignedPayload(callback, order, settings);
                paymentWebhookService.handleWebhook(BuiltinMockPaymentAvailability.PROVIDER_CODE, payload, Map.of());
                LocalDateTime processedAt = LocalDateTime.now();
                int updated = jdbcTemplate.update(
                        """
                                update payment_builtin_mock_callback
                                set status = 'DELIVERED', payload_json = ?, last_error = null, processed_at = ?,
                                    claim_token = null, claim_expires_at = null, updated_at = ?
                                where id = ? and notify_id = ? and status = 'PROCESSING' and claim_token = ? and deleted = 0
                                """,
                        payload,
                        processedAt,
                        processedAt,
                        callback.getId(),
                        callback.getNotifyId(),
                        callback.getClaimToken()
                );
                if (updated != 1) {
                    throw new BizException(ErrorCode.BIZ_ERROR, "Mock payment callback claim changed, please retry");
                }
                return Boolean.TRUE;
            });
            return Boolean.TRUE.equals(delivered);
        } catch (Exception exception) {
            markCallbackRetry(callback, rootMessage(exception));
            return false;
        }
    }

    private String buildSignedPayload(
            CallbackRow callback,
            PaymentOrderRow order,
            PaymentProviderSettingsDTO settings
    ) {
        if (!StringUtils.hasText(settings.getAppId()) || !StringUtils.hasText(settings.getPrivateKey())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Managed mock payment signing configuration is incomplete");
        }
        Map<String, String> fields = new TreeMap<>();
        fields.put("app_id", settings.getAppId());
        fields.put("charset", "utf-8");
        fields.put("mock_outcome", callback.getOutcome());
        fields.put("notify_id", callback.getNotifyId());
        fields.put("notify_time", LocalDateTime.now().format(ALIPAY_TIME));
        fields.put("notify_type", "trade_status_sync");
        fields.put("out_trade_no", order.getOrderNo());
        fields.put("subject", order.getSubject());
        fields.put("total_amount", BigDecimal.valueOf(order.getAmountMinor(), 2).toPlainString());
        fields.put("trade_no", callback.getProviderTradeNo());
        fields.put("trade_status", callback.getTradeStatus());
        fields.put("version", "1.0");
        if ("FAILURE".equals(callback.getOutcome())) {
            fields.put("sub_code", "ACQ.SYSTEM_ERROR");
            fields.put("sub_msg", "Built-in mock payment failure");
        }
        String signContent = fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        fields.put("sign_type", "RSA2");
        fields.put("sign", sign(settings.getPrivateKey(), signContent));
        return fields.entrySet().stream()
                .map(entry -> formEncode(entry.getKey()) + "=" + formEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String sign(String privateKey, String content) {
        try {
            String normalized = privateKey
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized))
            ));
            signer.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Unable to sign built-in mock payment callback");
        }
    }

    private void markCallbackRetry(CallbackRow callback, String error) {
        int retryCount = callback.getRetryCount() == null ? 1 : callback.getRetryCount() + 1;
        int maxRetry = callback.getMaxRetry() == null ? MAX_CALLBACK_RETRY : callback.getMaxRetry();
        String nextStatus = retryCount >= maxRetry ? "DEAD" : "RETRY";
        LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(Math.min(300L, 5L * (1L << Math.min(retryCount, 6))));
        jdbcTemplate.update(
                """
                        update payment_builtin_mock_callback
                        set status = ?, retry_count = ?, next_retry_at = ?, last_error = ?,
                            claim_token = null, claim_expires_at = null, updated_at = ?
                        where id = ? and notify_id = ? and status = 'PROCESSING' and claim_token = ? and deleted = 0
                        """,
                nextStatus,
                retryCount,
                nextRetryAt,
                truncate(error, 1024),
                LocalDateTime.now(),
                callback.getId(),
                callback.getNotifyId(),
                callback.getClaimToken()
        );
    }

    private void markCallbackCancelled(CallbackRow callback, String reason) {
        jdbcTemplate.update(
                """
                        update payment_builtin_mock_callback
                        set status = 'CANCELLED', last_error = ?, claim_token = null,
                            claim_expires_at = null, updated_at = ?
                        where id = ? and notify_id = ? and status = 'PROCESSING' and claim_token = ? and deleted = 0
                        """,
                reason,
                LocalDateTime.now(),
                callback.getId(),
                callback.getNotifyId(),
                callback.getClaimToken()
        );
    }

    private PaymentOrderRow requireOwnedOrder(PaymentActorResolver.Actor actor, String orderNo) {
        List<PaymentOrderRow> rows = jdbcTemplate.query(
                orderSelect() + " where order_no = ? and created_by = ? and created_by_uuid = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                normalizeOrderNo(orderNo),
                actor.userId(),
                actor.userUuid()
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return rows.getFirst();
    }

    private PaymentOrderRow requireOwnedOrderForUpdate(PaymentActorResolver.Actor actor, String orderNo) {
        List<PaymentOrderRow> rows = jdbcTemplate.query(
                orderSelect() + " where order_no = ? and created_by = ? and created_by_uuid = ? and deleted = 0 limit 1 for update",
                new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                normalizeOrderNo(orderNo),
                actor.userId(),
                actor.userUuid()
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return rows.getFirst();
    }

    private PaymentOrderRow requireOrder(String orderNo) {
        List<PaymentOrderRow> rows = jdbcTemplate.query(
                orderSelect() + " where order_no = ? and provider_code = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                normalizeOrderNo(orderNo),
                BuiltinMockPaymentAvailability.PROVIDER_CODE
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return rows.getFirst();
    }

    private String orderSelect() {
        return """
                select id, order_no as orderNo, provider_code as providerCode,
                       provider_order_no as providerOrderNo, subject, amount_minor as amountMinor,
                       currency, status, payment_url as paymentUrl, client_ip as clientIp,
                       notify_url as notifyUrl, return_url as returnUrl, request_json as requestJson,
                       response_json as responseJson, idempotency_key as idempotencyKey,
                       failure_code as failureCode, failure_message as failureMessage,
                       expires_at as expiresAt, paid_at as paidAt, created_by as createdBy,
                       created_by_uuid as createdByUuid, created_at as createdAt,
                       updated_by as updatedBy, updated_at as updatedAt, deleted
                from payment_order
                """;
    }

    private CallbackSnapshot findCallbackByOrderNo(String orderNo) {
        return findCallback("order_no", orderNo);
    }

    private CallbackSnapshot findCallbackByNotifyId(String notifyId) {
        return findCallback("notify_id", notifyId);
    }

    private CallbackSnapshot findCallback(String column, String value) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select notify_id, outcome, status, scheduled_at from payment_builtin_mock_callback where "
                        + column + " = ? and deleted = 0 order by id desc limit 1",
                value
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        Object scheduled = row.get("scheduled_at");
        LocalDateTime scheduledAt = scheduled instanceof java.sql.Timestamp timestamp
                ? timestamp.toLocalDateTime()
                : scheduled instanceof LocalDateTime dateTime ? dateTime : null;
        return new CallbackSnapshot(
                text(row.get("notify_id")),
                text(row.get("outcome")),
                text(row.get("status")),
                scheduledAt
        );
    }

    private BuiltinMockPaymentCheckoutDTO toCheckout(PaymentOrderRow order, CallbackSnapshot callback) {
        List<String> outcomes = List.of("CREATED", "PENDING").contains(order.getStatus()) && callback == null
                ? ALLOWED_OUTCOMES
                : List.of();
        return new BuiltinMockPaymentCheckoutDTO(
                order.getOrderNo(),
                nullableProviderOrderNo(order.getProviderOrderNo()),
                order.getSubject(),
                order.getAmountMinor(),
                order.getCurrency(),
                order.getStatus(),
                tradeStatus(order.getStatus()),
                order.getExpiresAt(),
                safeReturnUrl(order.getReturnUrl()),
                callback == null ? null : callback.status(),
                callback == null ? null : callback.outcome(),
                callback == null ? null : callback.scheduledAt(),
                outcomes,
                DELAY_OPTIONS,
                MAX_DELAY_SECONDS,
                "模拟环境，不会产生真实扣款"
        );
    }

    private PaymentOrderDTO toOrderDto(PaymentOrderRow row) {
        return new PaymentOrderDTO(
                row.getOrderNo(), row.getProviderCode(), nullableProviderOrderNo(row.getProviderOrderNo()), row.getSubject(),
                row.getAmountMinor(), row.getCurrency(), row.getStatus(), row.getPaymentUrl(), row.getClientIp(),
                row.getNotifyUrl(), safeReturnUrl(row.getReturnUrl()), parseMetadata(row.getRequestJson()),
                row.getFailureCode(), row.getFailureMessage(), row.getCreatedAt(), row.getUpdatedAt(), row.getPaidAt()
        );
    }

    private String nullableProviderOrderNo(String providerOrderNo) {
        return StringUtils.hasText(providerOrderNo) ? providerOrderNo.trim() : null;
    }

    private Map<String, Object> parseMetadata(String requestJson) {
        if (!StringUtils.hasText(requestJson)) {
            return Map.of();
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(requestJson, Map.class);
            Object metadata = parsed.get("metadata");
            if (!(metadata instanceof Map<?, ?> values)) {
                return Map.of();
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String tradeStatus(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ROOT)) {
            case "PAID", "SUCCESS", "SETTLED" -> "TRADE_SUCCESS";
            case "CANCELLED", "EXPIRED", "REFUNDED" -> "TRADE_CLOSED";
            case "FAILED" -> "TRADE_FAILED";
            default -> "WAIT_BUYER_PAY";
        };
    }

    private String safeReturnUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String path = uri.getPath();
            if (!"/competitions/register/payment-result".equals(path)
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                return null;
            }
            String query = uri.getRawQuery();
            return path + (StringUtils.hasText(query) ? "?" + query : "");
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void requireBuiltinMockOrder(PaymentOrderRow order) {
        if (order == null || !BuiltinMockPaymentAvailability.PROVIDER_CODE.equals(order.getProviderCode())) {
            throw new BizException(ErrorCode.NOT_FOUND, "Built-in mock payment order does not exist");
        }
    }

    private String normalizeOutcome(String outcome) {
        String normalized = StringUtils.hasText(outcome) ? outcome.trim().toUpperCase(Locale.ROOT) : "";
        if (!ALLOWED_OUTCOMES.contains(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Unsupported mock payment outcome");
        }
        return normalized;
    }

    private int normalizeDelay(Integer delaySeconds) {
        int normalized = delaySeconds == null ? 0 : delaySeconds;
        if (normalized < 0 || normalized > MAX_DELAY_SECONDS) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Mock callback delay must be between 0 and 300 seconds");
        }
        return normalized;
    }

    private String normalizeOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "orderNo is required");
        }
        String normalized = orderNo.trim();
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "orderNo is invalid");
        }
        return normalized;
    }

    private String formEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "Mock callback failed";
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record EnqueuedCallback(String notifyId, LocalDateTime scheduledAt) {
    }

    private record CallbackSnapshot(String notifyId, String outcome, String status, LocalDateTime scheduledAt) {
    }

    public static class CallbackRow {
        private Long id;
        private String notifyId;
        private String orderNo;
        private String providerTradeNo;
        private String outcome;
        private String tradeStatus;
        private String status;
        private LocalDateTime scheduledAt;
        private LocalDateTime nextRetryAt;
        private Integer retryCount;
        private Integer maxRetry;
        private String claimToken;
        private LocalDateTime claimExpiresAt;
        private String payloadJson;
        private String lastError;
        private LocalDateTime processedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNotifyId() { return notifyId; }
        public void setNotifyId(String notifyId) { this.notifyId = notifyId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getProviderTradeNo() { return providerTradeNo; }
        public void setProviderTradeNo(String providerTradeNo) { this.providerTradeNo = providerTradeNo; }
        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public String getTradeStatus() { return tradeStatus; }
        public void setTradeStatus(String tradeStatus) { this.tradeStatus = tradeStatus; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
        public LocalDateTime getNextRetryAt() { return nextRetryAt; }
        public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
        public Integer getMaxRetry() { return maxRetry; }
        public void setMaxRetry(Integer maxRetry) { this.maxRetry = maxRetry; }
        public String getClaimToken() { return claimToken; }
        public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
        public LocalDateTime getClaimExpiresAt() { return claimExpiresAt; }
        public void setClaimExpiresAt(LocalDateTime claimExpiresAt) { this.claimExpiresAt = claimExpiresAt; }
        public String getPayloadJson() { return payloadJson; }
        public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
        public LocalDateTime getProcessedAt() { return processedAt; }
        public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    }
}
