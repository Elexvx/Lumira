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
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.payment.domain.model.PaymentDomainModels.PaymentOrderAggregate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String SANDBOX_ENVIRONMENT = "SANDBOX";
    private static final String PERMISSION_PAYMENT_ORDER_CREATE = "payment:order:create";
    private static final String PERMISSION_PAYMENT_ORDER_VIEW = "payment:order:view";
    private static final String PERMISSION_PAYMENT_REFUND_CREATE = "payment:refund:create";
    private static final String PERMISSION_PAYMENT_REFUND_VIEW = "payment:refund:view";
    private static final String PENDING_PROVIDER_ORDER_NO = "";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentProviderCatalog providerCatalog;
    private final PaymentOutboxService outboxService;
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;
    private final PaymentActorResolver actorResolver;
    private final AlipayPagePayService alipayPagePayService;
    private final WechatPayV3Service wechatPayV3Service;
    private BuiltinMockPaymentAvailability builtinMockPaymentAvailability;

    @Autowired
    public PaymentTransactionService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentManagementAppService paymentManagementAppService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService,
            @Qualifier("paymentDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            WechatPayV3Service wechatPayV3Service
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.paymentManagementAppService = paymentManagementAppService;
        this.providerCatalog = providerCatalog;
        this.outboxService = outboxService;
        this.domainEventPublisher = domainEventPublisher;
        this.systemInternalApiProvider = systemInternalApiProvider;
        this.actorResolver = new PaymentActorResolver();
        this.alipayPagePayService = new AlipayPagePayService(objectMapper);
        this.wechatPayV3Service = wechatPayV3Service;
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
        this(
                jdbcTemplate,
                objectMapper,
                paymentManagementAppService,
                providerCatalog,
                outboxService,
                domainEventPublisher,
                systemInternalApiProvider,
                new WechatPayV3Service(objectMapper)
        );
    }

    @Autowired(required = false)
    void setBuiltinMockPaymentAvailability(BuiltinMockPaymentAvailability availability) {
        this.builtinMockPaymentAvailability = availability;
    }

    @Transactional
    public PaymentOrderDTO createOrder(CurrentUser currentUser, PaymentCreateOrderRequestDTO request) {
        return createOrder(
                trustedActor(currentUser, PERMISSION_PAYMENT_ORDER_CREATE),
                request,
                resolveProviderSettings(request, false)
        );
    }

    @Transactional
    public PaymentOrderDTO createSandboxOrder(CurrentUser currentUser, PaymentCreateOrderRequestDTO request) {
        return createOrder(
                trustedActor(currentUser, PERMISSION_PAYMENT_ORDER_CREATE),
                request,
                resolveProviderSettings(request, true)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderDTO> listSandboxOrders(CurrentUser currentUser, int pageNo, int pageSize) {
        trustedActor(currentUser, PERMISSION_PAYMENT_ORDER_VIEW);
        int normalizedPageNo = Math.max(pageNo, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (normalizedPageNo - 1) * normalizedPageSize;
        Long total = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from payment_order
                        where provider_code = ? and order_no like 'SBX-%' and deleted = 0
                        """,
                Long.class,
                "alipay"
        );
        List<PaymentOrderDTO> records = jdbcTemplate.query(
                """
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
                        where provider_code = ? and order_no like 'SBX-%' and deleted = 0
                        order by created_at desc, id desc
                        limit ? offset ?
                        """,
                new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                "alipay",
                normalizedPageSize,
                offset
        ).stream().map(this::toOrderDto).toList();
        PageResponse<PaymentOrderDTO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(records);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentOrderDTO> listManualOrdersForUser(
            CurrentUser currentUser,
            int pageNo,
            int pageSize
    ) {
        Actor actor = trustedActor(currentUser, PERMISSION_PAYMENT_ORDER_VIEW);
        int normalizedPageNo = Math.max(pageNo, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (normalizedPageNo - 1) * normalizedPageSize;
        Long total = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from payment_order
                        where created_by = ? and created_by_uuid = ?
                          and (order_no like 'MAN-%' or order_no like 'SBX-%')
                          and deleted = 0
                        """,
                Long.class,
                actor.userId(),
                actor.userUuid()
        );
        List<PaymentOrderDTO> records = jdbcTemplate.query(
                """
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
                        where created_by = ? and created_by_uuid = ?
                          and (order_no like 'MAN-%' or order_no like 'SBX-%')
                          and deleted = 0
                        order by created_at desc, id desc
                        limit ? offset ?
                        """,
                new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                actor.userId(),
                actor.userUuid(),
                normalizedPageSize,
                offset
        ).stream().map(this::toOrderDto).toList();
        PageResponse<PaymentOrderDTO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(records);
        return response;
    }

    private PaymentOrderDTO createOrder(Actor actor, PaymentCreateOrderRequestDTO request, PaymentProviderSettingsDTO settings) {
        Long actorUserId = actor.userId();
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
        row.setProviderOrderNo(PENDING_PROVIDER_ORDER_NO);
        row.setSubject(normalizeText(request.subject()));
        row.setAmountMinor(request.amountMinor());
        row.setCurrency(normalizeText(request.currency()).toUpperCase(Locale.ROOT));
        row.setStatus("PENDING");
        row.setClientIp(normalizeText(request.clientIp()));
        row.setNotifyUrl(resolveText(request.notifyUrl(), settings.getNotifyUrl()));
        row.setReturnUrl(resolveText(request.returnUrl(), settings.getReturnUrl()));
        row.setPaymentUrl(buildPaymentUrl(settings, row, request.metadata()));
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

    private PaymentProviderSettingsDTO resolveProviderSettings(PaymentCreateOrderRequestDTO request, boolean sandboxOnly) {
        requireOrderRequest(request);
        if (BuiltinMockPaymentAvailability.PROVIDER_CODE.equals(providerCatalog.normalize(request.providerCode()))) {
            if (builtinMockPaymentAvailability == null) {
                throw new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "内置模拟支付插件未启用");
            }
            builtinMockPaymentAvailability.requireEnabledForWrite();
        }
        PaymentProviderSettingsDTO settings = paymentManagementAppService.getRequiredProviderSettings(request.providerCode());
        if (sandboxOnly) {
            requireSandboxEnvironment(settings);
        }
        return settings;
    }

    private void requireSandboxEnvironment(PaymentProviderSettingsDTO settings) {
        String normalizedEnvironment = settings.getEnvironment() == null
                ? ""
                : settings.getEnvironment().trim().toUpperCase(Locale.ROOT);
        if (!SANDBOX_ENVIRONMENT.equals(normalizedEnvironment)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Manual payment orders are sandbox-only");
        }
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
        Actor actor = trustedActor(currentUser, PERMISSION_PAYMENT_ORDER_VIEW);
        PaymentOrderRow row = findOrderByOrderNoAndCreatedBy(orderNo, actor);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return toOrderDto(row);
    }

    @Transactional
    public PaymentOrderDTO cancelPendingOrderForUser(CurrentUser currentUser, String orderNo) {
        Actor actor = trustedActor(currentUser, PERMISSION_PAYMENT_ORDER_CREATE);
        PaymentOrderRow row = findOrderByOrderNoAndCreatedBy(normalizeIdentifier(orderNo), actor);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
        return cancelPendingOrder(actor, row);
    }

    @Transactional
    public PaymentOrderDTO cancelManualPendingOrderForUser(CurrentUser currentUser, String orderNo) {
        Actor actor = trustedActor(currentUser, PERMISSION_PAYMENT_ORDER_CREATE);
        PaymentOrderRow row = findOrderByOrderNoAndCreatedBy(normalizeIdentifier(orderNo), actor);
        if (row == null || !isManualOrderNo(row.getOrderNo())) {
            throw new BizException(ErrorCode.NOT_FOUND, "Manual payment order does not exist");
        }
        return cancelPendingOrder(actor, row);
    }

    private PaymentOrderDTO cancelPendingOrder(Actor actor, PaymentOrderRow row) {
        if (List.of("PAID", "SUCCESS", "SETTLED", "REFUNDING", "REFUNDED").contains(row.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Paid payment orders cannot be cancelled");
        }
        if ("CANCELLED".equals(row.getStatus())) {
            return toOrderDto(row);
        }
        if (!List.of("CREATED", "PENDING").contains(row.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Only created or pending payment orders can be cancelled");
        }
        if ("alipay".equalsIgnoreCase(row.getProviderCode())) {
            alipayPagePayService.closeTrade(
                    paymentManagementAppService.getRequiredProviderSettings(row.getProviderCode()),
                    row.getOrderNo()
            );
        } else if ("wechat_pay".equalsIgnoreCase(row.getProviderCode())) {
            wechatPayV3Service.closePayment(
                    paymentManagementAppService.getRequiredProviderSettings(row.getProviderCode()),
                    row.getOrderNo()
            );
        }
        int updated = jdbcTemplate.update(
                """
                        update payment_order
                        set status = 'CANCELLED', payment_url = null, failure_code = 'ORDER_CANCELLED',
                            failure_message = 'Payment order was cancelled before payment',
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and order_no = ? and created_by = ? and created_by_uuid = ?
                          and status in ('CREATED', 'PENDING') and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), LocalDateTime.now(),
                row.getId(), row.getOrderNo(), actor.userId(), actor.userUuid()
        );
        requireSinglePaymentUpdate(updated, "Payment order state changed, please retry");
        return toOrderDto(findOrderByOrderNo(row.getOrderNo()));
    }

    private boolean isManualOrderNo(String orderNo) {
        return orderNo != null && (orderNo.startsWith("MAN-") || orderNo.startsWith("SBX-"));
    }

    @Transactional
    public PaymentRefundDTO createRefund(CurrentUser currentUser, String orderNo, PaymentCreateRefundRequestDTO request) {
        return createRefund(trustedActor(currentUser, PERMISSION_PAYMENT_REFUND_CREATE), orderNo, request);
    }

    private PaymentRefundDTO createRefund(Actor actor, String orderNo, PaymentCreateRefundRequestDTO request) {
        Long actorUserId = actor.userId();
        requireRefundRequest(request);
        String normalizedOrderNo = normalizeIdentifier(orderNo);
        PaymentOrderRow providerOrder = findOrderByOrderNoAndCreatedBy(normalizedOrderNo, actor);
        if (providerOrder != null
                && BuiltinMockPaymentAvailability.PROVIDER_CODE.equals(providerCatalog.normalize(providerOrder.getProviderCode()))) {
            return createBuiltinMockRefund(actor, normalizedOrderNo, request);
        }
        PaymentOrderRow order = providerOrder;
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

        outboxService.record(
                outboxUserId(actor),
                "payment",
                "payment.refund.created",
                row.getRefundNo(),
                actorPayload(actor, Map.of("refundNo", row.getRefundNo(), "orderNo", row.getOrderNo(), "amountMinor", row.getAmountMinor()))
        );
        return toRefundDto(findRefundByRefundNo(row.getRefundNo()));
    }

    private PaymentRefundDTO createBuiltinMockRefund(
            Actor actor,
            String orderNo,
            PaymentCreateRefundRequestDTO request
    ) {
        PaymentOrderRow order = findOrderByOrderNoAndCreatedByForUpdate(orderNo, actor);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payment order does not exist");
        }
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
        if (!List.of("PAID", "SUCCESS", "SETTLED").contains(order.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Current order status does not allow refund");
        }
        validateRefundCurrency(order, request.currency());
        Long refundedAmount = jdbcTemplate.queryForObject(
                """
                        select coalesce(sum(amount_minor), 0)
                        from payment_refund
                        where order_no = ? and provider_code = ? and status = 'REFUNDED'
                          and created_by = ? and created_by_uuid = ? and deleted = 0
                        """,
                Long.class,
                order.getOrderNo(),
                BuiltinMockPaymentAvailability.PROVIDER_CODE,
                actor.userId(),
                actor.userUuid()
        );
        long alreadyRefunded = refundedAmount == null ? 0L : refundedAmount;
        long requestedAmount = request.amountMinor();
        long orderAmount = order.getAmountMinor() == null ? 0L : order.getAmountMinor();
        if (requestedAmount > orderAmount - alreadyRefunded) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Cumulative refund amount cannot exceed paid amount");
        }
        LocalDateTime now = LocalDateTime.now();
        String refundNo = normalizeIdentifier(request.refundNo());
        String providerRefundNo = buildProviderRefundNo(order.getProviderCode(), refundNo);
        String requestJson = serialize(Map.of(
                "refundNo", refundNo,
                "orderNo", order.getOrderNo(),
                "amountMinor", requestedAmount,
                "currency", normalizeText(request.currency()).toUpperCase(Locale.ROOT),
                "reason", normalizeText(request.reason()),
                "metadata", request.metadata() == null ? Map.of() : request.metadata()
        ));
        String responseJson = serialize(Map.of(
                "providerRefundNo", providerRefundNo,
                "mock", true,
                "refundedAmountMinor", requestedAmount
        ));
        int inserted = jdbcTemplate.update(
                """
                        insert into payment_refund (
                            refund_no, order_no, provider_code, provider_refund_no, amount_minor, currency,
                            status, reason, request_json, response_json, idempotency_key, refunded_at,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'REFUNDED', ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                refundNo,
                order.getOrderNo(),
                BuiltinMockPaymentAvailability.PROVIDER_CODE,
                providerRefundNo,
                requestedAmount,
                normalizeText(request.currency()).toUpperCase(Locale.ROOT),
                normalizeText(request.reason()),
                requestJson,
                responseJson,
                resolveIdempotencyKey(request.idempotencyKey(), refundNo),
                now,
                actor.userId(),
                actor.userUuid(),
                actor.userId(),
                actor.userUuid()
        );
        requireSinglePaymentUpdate(inserted, "Payment refund changed, please retry");
        long cumulativeRefunded = alreadyRefunded + requestedAmount;
        String nextOrderStatus = cumulativeRefunded == orderAmount ? "REFUNDED" : "PAID";
        int updated = jdbcTemplate.update(
                """
                        update payment_order
                        set status = ?, updated_at = ?, updated_by = ?, updated_by_uuid = ?
                        where id = ? and order_no = ? and provider_code = ? and amount_minor = ?
                          and created_by = ? and created_by_uuid = ? and status = ? and deleted = 0
                        """,
                nextOrderStatus,
                now,
                actor.userId(),
                actor.userUuid(),
                order.getId(),
                order.getOrderNo(),
                BuiltinMockPaymentAvailability.PROVIDER_CODE,
                order.getAmountMinor(),
                actor.userId(),
                actor.userUuid(),
                order.getStatus()
        );
        requireSinglePaymentUpdate(updated, "Payment order state changed, please retry");
        outboxService.record(
                outboxUserId(actor),
                "payment",
                "payment.refund.completed",
                refundNo,
                actorPayload(actor, Map.of(
                        "refundNo", refundNo,
                        "orderNo", order.getOrderNo(),
                        "amountMinor", requestedAmount,
                        "cumulativeAmountMinor", cumulativeRefunded,
                        "fullRefund", cumulativeRefunded == orderAmount
                ))
        );
        return toRefundDto(findRefundByRefundNo(refundNo));
    }

    private Actor trustedActor(CurrentUser currentUser, String requiredPermission) {
        PaymentActorResolver.Actor actor = actorResolver.require(currentUser, requiredPermission);
        return new Actor(actor.userId(), actor.userUuid());
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
            SystemInternalApi systemInternalApi = systemInternalApiProvider == null
                    ? null
                    : systemInternalApiProvider.getIfAvailable();
            if (systemInternalApi == null) {
                return null;
            }
            String userUuid = systemInternalApi.findTargetUserUuidById(userId);
            return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
        } catch (RuntimeException ignored) {
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
        Actor actor = trustedActor(currentUser, PERMISSION_PAYMENT_REFUND_VIEW);
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

    private PaymentOrderRow findOrderByOrderNoAndCreatedByForUpdate(String orderNo, Actor actor) {
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
                            for update
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
                nullableProviderOrderNo(row.getProviderOrderNo()),
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

    private String nullableProviderOrderNo(String providerOrderNo) {
        return StringUtils.hasText(providerOrderNo) ? providerOrderNo.trim() : null;
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

    private String buildProviderRefundNo(String providerCode, String refundNo) {
        return providerCatalog.normalize(providerCode) + "-refund-" + normalizeIdentifier(refundNo) + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String buildPaymentUrl(
            PaymentProviderSettingsDTO settings,
            PaymentOrderRow order,
            Map<String, Object> metadata
    ) {
        if ("alipay".equals(order.getProviderCode())) {
            return alipayPagePayService.buildPagePayUrl(
                    settings,
                    order.getOrderNo(),
                    order.getSubject(),
                    order.getAmountMinor(),
                    order.getNotifyUrl(),
                    order.getReturnUrl()
            );
        }
        if ("wechat_pay".equals(order.getProviderCode())) {
            return wechatPayV3Service.createPayment(
                    settings,
                    order.getOrderNo(),
                    order.getSubject(),
                    order.getAmountMinor(),
                    order.getCurrency(),
                    order.getNotifyUrl(),
                    order.getClientIp(),
                    metadata == null ? Map.of() : metadata
            ).paymentUrl();
        }
        if (BuiltinMockPaymentAvailability.PROVIDER_CODE.equals(order.getProviderCode())) {
            return "/mock-payment/checkout?orderNo="
                    + java.net.URLEncoder.encode(order.getOrderNo(), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (StringUtils.hasText(settings.getApiBaseUrl())) {
            return resolveText(settings.getApiBaseUrl(), "") + "/checkout/" + order.getOrderNo();
        }
        return "/api/v1/payment/orders/" + order.getOrderNo();
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
