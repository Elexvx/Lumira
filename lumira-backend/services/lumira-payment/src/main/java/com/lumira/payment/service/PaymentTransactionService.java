package com.lumira.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentRefundDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.payment.domain.model.PaymentDomainModels.PaymentOrderAggregate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentTransactionService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentProviderCatalog providerCatalog;
    private final PaymentOutboxService outboxService;
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;

    public PaymentTransactionService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentManagementAppService paymentManagementAppService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService,
            @Qualifier("paymentDomainEventPublisher") DomainEventPublisher domainEventPublisher
    ) {
        this(
                jdbcTemplate,
                objectMapper,
                paymentManagementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher,
                null
        );
    }

    public PaymentTransactionService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentManagementAppService paymentManagementAppService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService,
            @Qualifier("paymentDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.paymentManagementAppService = paymentManagementAppService;
        this.providerCatalog = providerCatalog;
        this.outboxService = outboxService;
        this.domainEventPublisher = domainEventPublisher;
        this.systemInternalApiProvider = systemInternalApiProvider;
    }

    @Transactional
    public PaymentOrderDTO createOrder(CurrentUser currentUser, PaymentCreateOrderRequestDTO request) {
        return createOrder(trustedActor(currentUser), request);
    }

    private PaymentOrderDTO createOrder(Actor actor, PaymentCreateOrderRequestDTO request) {
        Long actorUserId = actor.userId();
        requireOrderRequest(request);
        PaymentProviderSettingsDTO settings = paymentManagementAppService.getRequiredProviderSettings(request.providerCode());
        if (!settings.isEnabled()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Payment provider is disabled");
        }
        validateCurrency(settings, request.currency());
        PaymentOrderAggregate orderAggregate = new PaymentOrderAggregate(
                normalizeIdentifier(request.orderNo()),
                BigDecimal.valueOf(request.amountMinor(), 2),
                "CREATED"
        );

        PaymentOrderRow existing = findOrderByIdempotencyKeyAndCreatedBy(request.idempotencyKey(), actor);
        if (existing != null) {
            return toOrderDto(existing);
        }

        PaymentOrderRow byOrderNo = findOrderByOrderNoAndCreatedBy(request.orderNo(), actor);
        if (byOrderNo != null) {
            return toOrderDto(byOrderNo);
        }
        if (findOrderByOrderNo(request.orderNo()) != null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment order already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentOrderRow row = new PaymentOrderRow();
        row.setOrderNo(normalizeIdentifier(request.orderNo()));
        row.setProviderCode(providerCatalog.normalize(request.providerCode()));
        row.setProviderOrderNo(buildProviderOrderNo(request.providerCode(), request.orderNo()));
        row.setSubject(normalizeText(request.subject()));
        row.setAmountMinor(request.amountMinor());
        row.setCurrency(normalizeText(request.currency()).toUpperCase(Locale.ROOT));
        row.setStatus("PENDING");
        row.setPaymentUrl(buildPaymentUrl(settings, row.getOrderNo()));
        row.setClientIp(normalizeText(request.clientIp()));
        row.setNotifyUrl(resolveText(request.notifyUrl(), settings.getNotifyUrl()));
        row.setReturnUrl(resolveText(request.returnUrl(), settings.getReturnUrl()));
        row.setRequestJson(serialize(Map.of(
                "providerCode", row.getProviderCode(),
                "orderNo", row.getOrderNo(),
                "subject", row.getSubject(),
                "amountMinor", row.getAmountMinor(),
                "currency", row.getCurrency(),
                "metadata", request.metadata() == null ? Map.of() : request.metadata()
        )));
        row.setResponseJson(serialize(Map.of(
                "providerCode", row.getProviderCode(),
                "providerOrderNo", row.getProviderOrderNo(),
                "paymentUrl", row.getPaymentUrl()
        )));
        row.setIdempotencyKey(resolveIdempotencyKey(request.idempotencyKey(), row.getOrderNo()));
        row.setExpiresAt(now.plusHours(2));
        row.setCreatedBy(actorUserId);
        row.setCreatedByUuid(actor.userUuid());
        row.setUpdatedBy(actorUserId);
        row.setDeleted(0);

        int orderInserted = jdbcTemplate.update(
                """
                        insert into payment_order (
                            order_no, provider_code, provider_order_no, subject, amount_minor, currency,
                            status, payment_url, client_ip, notify_url, return_url, request_json, response_json,
                            idempotency_key, expires_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                row.getOrderNo(),
                row.getProviderCode(),
                row.getProviderOrderNo(),
                row.getSubject(),
                row.getAmountMinor(),
                row.getCurrency(),
                row.getStatus(),
                row.getPaymentUrl(),
                row.getClientIp(),
                row.getNotifyUrl(),
                row.getReturnUrl(),
                row.getRequestJson(),
                row.getResponseJson(),
                row.getIdempotencyKey(),
                row.getExpiresAt(),
                actorUserId,
                actor.userUuid(),
                actorUserId,
                actor.userUuid()
        );
        requireSinglePaymentUpdate(orderInserted, "Payment order changed, please retry");

        orderAggregate.recordCreated(row.getProviderCode(), row.getCurrency(), actorUserId, actor.userUuid());
        domainEventPublisher.publishAll(orderAggregate.pullDomainEvents());
        return toOrderDto(findOrderByOrderNo(row.getOrderNo()));
    }

    @Transactional(readOnly = true)
    public PaymentOrderDTO getOrder(String orderNo) {
        PaymentOrderRow row = findOrderByOrderNo(orderNo);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return toOrderDto(row);
    }

    @Transactional(readOnly = true)
    public PaymentOrderDTO getOrderForUser(Long userId, String userUuid, String orderNo) {
        Actor actor = trustedLookupActor(userId, userUuid);
        PaymentOrderRow row = findOrderByOrderNoAndCreatedBy(orderNo, actor);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return toOrderDto(row);
    }

    @Transactional(readOnly = true)
    public PaymentOrderDTO getOrderForUser(CurrentUser currentUser, String orderNo) {
        Actor actor = trustedActor(currentUser);
        PaymentOrderRow row = findOrderByOrderNoAndCreatedBy(orderNo, actor);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return toOrderDto(row);
    }

    @Transactional
    public PaymentRefundDTO createRefund(CurrentUser currentUser, String orderNo, PaymentCreateRefundRequestDTO request) {
        return createRefund(trustedActor(currentUser), orderNo, request);
    }

    private PaymentRefundDTO createRefund(Actor actor, String orderNo, PaymentCreateRefundRequestDTO request) {
        Long actorUserId = actor.userId();
        requireRefundRequest(request);
        String normalizedOrderNo = normalizeIdentifier(orderNo);
        PaymentOrderRow order = findOrderByOrderNoAndCreatedBy(normalizedOrderNo, actor);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        if (!List.of("PAID", "SUCCESS", "SETTLED").contains(order.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Current order status does not allow refund");
        }
        if (order.getAmountMinor() != null && request.amountMinor() > order.getAmountMinor()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Refund amount cannot exceed order amount");
        }
        validateRefundCurrency(order, request.currency());

        PaymentRefundRow existing = findRefundByIdempotencyKeyAndCreatedBy(request.idempotencyKey(), actor);
        if (existing != null) {
            return toRefundDto(existing);
        }
        PaymentRefundRow byRefundNo = findRefundByRefundNoAndCreatedBy(request.refundNo(), actor);
        if (byRefundNo != null) {
            return toRefundDto(byRefundNo);
        }
        if (findRefundByRefundNo(request.refundNo()) != null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment refund already exists");
        }

        PaymentRefundRow row = new PaymentRefundRow();
        row.setRefundNo(normalizeIdentifier(request.refundNo()));
        row.setOrderNo(order.getOrderNo());
        row.setProviderCode(order.getProviderCode());
        row.setProviderRefundNo(buildProviderRefundNo(order.getProviderCode(), request.refundNo()));
        row.setAmountMinor(request.amountMinor());
        row.setCurrency(normalizeText(request.currency()).toUpperCase(Locale.ROOT));
        row.setStatus("PENDING");
        row.setReason(normalizeText(request.reason()));
        row.setRequestJson(serialize(Map.of(
                "refundNo", row.getRefundNo(),
                "orderNo", row.getOrderNo(),
                "amountMinor", row.getAmountMinor(),
                "currency", row.getCurrency(),
                "reason", row.getReason(),
                "metadata", request.metadata() == null ? Map.of() : request.metadata()
        )));
        row.setResponseJson(serialize(Map.of(
                "providerRefundNo", row.getProviderRefundNo()
        )));
        row.setIdempotencyKey(resolveIdempotencyKey(request.idempotencyKey(), row.getRefundNo()));
        row.setCreatedBy(actorUserId);
        row.setCreatedByUuid(actor.userUuid());
        row.setUpdatedBy(actorUserId);
        row.setDeleted(0);

        int refundInserted = jdbcTemplate.update(
                """
                        insert into payment_refund (
                            refund_no, order_no, provider_code, provider_refund_no, amount_minor, currency,
                            status, reason, request_json, response_json, idempotency_key, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                row.getRefundNo(),
                row.getOrderNo(),
                row.getProviderCode(),
                row.getProviderRefundNo(),
                row.getAmountMinor(),
                row.getCurrency(),
                row.getStatus(),
                row.getReason(),
                row.getRequestJson(),
                row.getResponseJson(),
                row.getIdempotencyKey(),
                actorUserId,
                actor.userUuid(),
                actorUserId,
                actor.userUuid()
        );
        requireSinglePaymentUpdate(refundInserted, "Payment refund changed, please retry");

        int orderUpdated = jdbcTemplate.update(
                """
                        update payment_order
                        set status = 'REFUNDING', updated_at = ?, updated_by = ?, updated_by_uuid = ?, deleted = 0
                        where order_no = ? and created_by = ? and created_by_uuid = ?
                          and status = ? and amount_minor = ? and currency = ? and provider_code = ?
                          and deleted = 0
                        """,
                LocalDateTime.now(),
                actorUserId,
                actor.userUuid(),
                order.getOrderNo(),
                actorUserId,
                actor.userUuid(),
                order.getStatus(),
                order.getAmountMinor(),
                order.getCurrency(),
                order.getProviderCode()
        );
        requireSinglePaymentUpdate(orderUpdated, "Payment order state changed, please retry");

        outboxService.recordAfterCommit(
                outboxUserId(actor),
                "payment",
                "payment.refund.created",
                row.getRefundNo(),
                actorPayload(actor, Map.of("refundNo", row.getRefundNo(), "orderNo", row.getOrderNo(), "amountMinor", row.getAmountMinor()))
        );
        return toRefundDto(findRefundByRefundNo(row.getRefundNo()));
    }

    private Actor trustedActor(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Valid user is required");
        }
        if (systemInternalApiProvider == null) {
            return new Actor(currentUser.getUserId(), currentUser.getUserUuid().trim());
        }
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Valid user is required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted operator resolver is unavailable");
        }
        SystemUserSnapshotDTO snapshot = systemInternalApi.findUserIdentityById(userId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator does not exist");
        }
        if (!StringUtils.hasText(snapshot.userUuid()) || !snapshot.userUuid().trim().equals(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator identity mismatch");
        }
        if (!StringUtils.hasText(snapshot.status()) || !"ENABLED".equalsIgnoreCase(snapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator is disabled");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(userId, snapshot.userUuid().trim());
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator permissions are unavailable");
        }
        return new Actor(snapshot.userId(), snapshot.userUuid().trim());
    }

    private Actor trustedLookupActor(Long userId, String userUuid) {
        Long actorUserId = requireUserId(userId);
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        String normalizedUuid = userUuid.trim();
        String resolvedUuid = resolveUserUuid(actorUserId);
        if (!StringUtils.hasText(resolvedUuid) || !resolvedUuid.trim().equals(normalizedUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        return new Actor(actorUserId, normalizedUuid);
    }

    private String resolveUserUuid(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select uuid from sys_user where id = ? and deleted = 0 and status = 'ENABLED' limit 1",
                    String.class,
                    userId
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Map<String, Object> actorPayload(Actor actor, Map<String, Object> payload) {
        if (!StringUtils.hasText(actor.userUuid())) {
            return payload;
        }
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(payload);
        enriched.put("userUuid", actor.userUuid());
        return enriched;
    }

    private Long outboxUserId(Actor actor) {
        return actor != null && StringUtils.hasText(actor.userUuid()) ? actor.userId() : null;
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Valid user is required");
        }
        return userId;
    }

    private void requireSinglePaymentUpdate(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }

    private record Actor(Long userId, String userUuid) {
    }

    private void requireOrderRequest(PaymentCreateOrderRequestDTO request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment order request is required");
        }
        normalizeIdentifier(request.providerCode());
        normalizeIdentifier(request.orderNo());
        requireText(request.subject(), "Payment subject");
        requirePositiveAmount(request.amountMinor(), "Payment amount");
        requireCurrency(request.currency());
    }

    private void requireRefundRequest(PaymentCreateRefundRequestDTO request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment refund request is required");
        }
        normalizeIdentifier(request.refundNo());
        requirePositiveAmount(request.amountMinor(), "Refund amount");
        requireCurrency(request.currency());
        requireText(request.reason(), "Refund reason");
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, name + " is required");
        }
    }

    private void requirePositiveAmount(Long amountMinor, String name) {
        if (amountMinor == null || amountMinor <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, name + " must be greater than zero");
        }
    }

    private void requireCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Currency is required");
        }
    }

    @Transactional(readOnly = true)
    public PaymentRefundDTO getRefund(String refundNo) {
        PaymentRefundRow row = findRefundByRefundNo(refundNo);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment refund does not exist");
        }
        return toRefundDto(row);
    }

    @Transactional(readOnly = true)
    public PaymentRefundDTO getRefundForUser(Long userId, String userUuid, String refundNo) {
        Actor actor = trustedLookupActor(userId, userUuid);
        PaymentRefundRow row = findRefundByRefundNoAndCreatedBy(refundNo, actor);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment refund does not exist");
        }
        return toRefundDto(row);
    }

    @Transactional(readOnly = true)
    public PaymentRefundDTO getRefundForUser(CurrentUser currentUser, String refundNo) {
        Actor actor = trustedActor(currentUser);
        PaymentRefundRow row = findRefundByRefundNoAndCreatedBy(refundNo, actor);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment refund does not exist");
        }
        return toRefundDto(row);
    }

    private PaymentOrderRow findOrderByOrderNo(String orderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, order_no as orderNo, provider_code as providerCode,
                                   provider_order_no as providerOrderNo, subject, amount_minor as amountMinor,
                                   currency, status, payment_url as paymentUrl, client_ip as clientIp,
                                   notify_url as notifyUrl, return_url as returnUrl, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   expires_at as expiresAt, paid_at as paidAt, created_by as createdBy,
                                   created_by_uuid as createdByUuid,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt,
                                   deleted
                            from payment_order
                            where order_no = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                    normalizeIdentifier(orderNo)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentOrderRow findOrderByOrderNoAndCreatedBy(String orderNo, Actor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || !StringUtils.hasText(actor.userUuid())) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, order_no as orderNo, provider_code as providerCode,
                                   provider_order_no as providerOrderNo, subject, amount_minor as amountMinor,
                                   currency, status, payment_url as paymentUrl, client_ip as clientIp,
                                   notify_url as notifyUrl, return_url as returnUrl, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   expires_at as expiresAt, paid_at as paidAt, created_by as createdBy,
                                   created_by_uuid as createdByUuid,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt,
                                   deleted
                            from payment_order
                            where order_no = ? and created_by = ? and created_by_uuid = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                    normalizeIdentifier(orderNo),
                    actor.userId(),
                    actor.userUuid()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentOrderRow findOrderByIdempotencyKeyAndCreatedBy(String idempotencyKey, Actor actor) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || !StringUtils.hasText(actor.userUuid())) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, order_no as orderNo, provider_code as providerCode,
                                   provider_order_no as providerOrderNo, subject, amount_minor as amountMinor,
                                   currency, status, payment_url as paymentUrl, client_ip as clientIp,
                                   notify_url as notifyUrl, return_url as returnUrl, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   expires_at as expiresAt, paid_at as paidAt, created_by as createdBy,
                                   created_by_uuid as createdByUuid,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt,
                                   deleted
                            from payment_order
                            where idempotency_key = ? and created_by = ? and created_by_uuid = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                    idempotencyKey.trim(),
                    actor.userId(),
                    actor.userUuid()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentRefundRow findRefundByRefundNo(String refundNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, refund_no as refundNo, order_no as orderNo,
                                   provider_code as providerCode, provider_refund_no as providerRefundNo,
                                   amount_minor as amountMinor, currency, status, reason, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   refunded_at as refundedAt, created_by as createdBy, created_by_uuid as createdByUuid,
                                   created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_refund
                            where refund_no = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentRefundRow.class),
                    normalizeIdentifier(refundNo)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentRefundRow findRefundByRefundNoAndCreatedBy(String refundNo, Actor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || !StringUtils.hasText(actor.userUuid())) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, refund_no as refundNo, order_no as orderNo,
                                   provider_code as providerCode, provider_refund_no as providerRefundNo,
                                   amount_minor as amountMinor, currency, status, reason, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   refunded_at as refundedAt, created_by as createdBy, created_by_uuid as createdByUuid,
                                   created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_refund
                            where refund_no = ? and created_by = ? and created_by_uuid = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentRefundRow.class),
                    normalizeIdentifier(refundNo),
                    actor.userId(),
                    actor.userUuid()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentRefundRow findRefundByIdempotencyKeyAndCreatedBy(String idempotencyKey, Actor actor) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || !StringUtils.hasText(actor.userUuid())) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, refund_no as refundNo, order_no as orderNo,
                                   provider_code as providerCode, provider_refund_no as providerRefundNo,
                                   amount_minor as amountMinor, currency, status, reason, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   refunded_at as refundedAt, created_by as createdBy, created_by_uuid as createdByUuid,
                                   created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_refund
                            where idempotency_key = ? and created_by = ? and created_by_uuid = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentRefundRow.class),
                    idempotencyKey.trim(),
                    actor.userId(),
                    actor.userUuid()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentOrderDTO toOrderDto(PaymentOrderRow row) {
        if (row == null) {
            return null;
        }
        return new PaymentOrderDTO(
                row.getOrderNo(),
                row.getProviderCode(),
                row.getProviderOrderNo(),
                row.getSubject(),
                row.getAmountMinor(),
                row.getCurrency(),
                row.getStatus(),
                row.getPaymentUrl(),
                row.getClientIp(),
                row.getNotifyUrl(),
                row.getReturnUrl(),
                parseMap(row.getRequestJson()),
                row.getFailureCode(),
                row.getFailureMessage(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getPaidAt()
        );
    }

    private PaymentRefundDTO toRefundDto(PaymentRefundRow row) {
        if (row == null) {
            return null;
        }
        return new PaymentRefundDTO(
                row.getRefundNo(),
                row.getOrderNo(),
                row.getProviderCode(),
                row.getProviderRefundNo(),
                row.getAmountMinor(),
                row.getCurrency(),
                row.getStatus(),
                row.getReason(),
                parseMap(row.getRequestJson()),
                row.getFailureCode(),
                row.getFailureMessage(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getRefundedAt()
        );
    }

    private void validateCurrency(PaymentProviderSettingsDTO settings, String currency) {
        if (!StringUtils.hasText(currency)) {
            return;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        String configuredCurrency = StringUtils.hasText(settings.getCurrency()) ? settings.getCurrency().trim().toUpperCase(Locale.ROOT) : null;
        if (configuredCurrency != null && !configuredCurrency.equals(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Order currency does not match payment configuration");
        }
    }

    private void validateRefundCurrency(PaymentOrderRow order, String currency) {
        if (order == null || !StringUtils.hasText(currency)) {
            return;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        String orderCurrency = StringUtils.hasText(order.getCurrency()) ? order.getCurrency().trim().toUpperCase(Locale.ROOT) : null;
        if (orderCurrency != null && !orderCurrency.equals(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Refund currency does not match original order");
        }
    }

    private String buildProviderOrderNo(String providerCode, String orderNo) {
        return providerCatalog.normalize(providerCode) + "-" + normalizeIdentifier(orderNo) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildProviderRefundNo(String providerCode, String refundNo) {
        return providerCatalog.normalize(providerCode) + "-refund-" + normalizeIdentifier(refundNo) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String buildPaymentUrl(PaymentProviderSettingsDTO settings, String orderNo) {
        if (StringUtils.hasText(settings.getApiBaseUrl())) {
            return resolveText(settings.getApiBaseUrl(), "") + "/checkout/" + orderNo;
        }
        return "/api/v1/payment/orders/" + orderNo;
    }

    private String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Identifier cannot be empty");
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Identifier length cannot exceed 64 characters");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveText(String candidate, String fallback) {
        return StringUtils.hasText(candidate) ? candidate.trim() : fallback;
    }

    private String resolveIdempotencyKey(String idempotencyKey, String fallback) {
        return StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : fallback;
    }

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("raw", json);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Payment transaction serialization failed", ex);
        }
    }
}
