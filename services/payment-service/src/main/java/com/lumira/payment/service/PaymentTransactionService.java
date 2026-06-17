package com.lumira.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentRefundDTO;
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

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentTransactionService {

    private final JdbcTemplate jdbcTemplate;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentProviderCatalog providerCatalog;
    private final PaymentOutboxService outboxService;
    private final DomainEventPublisher domainEventPublisher;

    public PaymentTransactionService(
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
    public PaymentOrderDTO createOrder(Long tenantId, Long userId, PaymentCreateOrderRequestDTO request) {
        PaymentProviderSettingsDTO settings = paymentManagementAppService.getRequiredProviderSettings(tenantId, request.providerCode());
        if (!settings.isEnabled()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "支付通道已停用");
        }
        if (request.amountMinor() == null || request.amountMinor() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "支付金额必须大于 0");
        }
        validateCurrency(settings, request.currency());
        PaymentOrderAggregate orderAggregate = new PaymentOrderAggregate(
                normalizeIdentifier(request.orderNo()),
                tenantId,
                BigDecimal.valueOf(request.amountMinor(), 2),
                "CREATED"
        );

        PaymentOrderRow existing = findOrderByIdempotencyKey(tenantId, request.idempotencyKey());
        if (existing != null) {
            return toOrderDto(existing);
        }

        PaymentOrderRow byOrderNo = findOrderByOrderNo(tenantId, request.orderNo());
        if (byOrderNo != null) {
            return toOrderDto(byOrderNo);
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentOrderRow row = new PaymentOrderRow();
        row.setTenantId(tenantId);
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
        row.setCreatedBy(userId);
        row.setUpdatedBy(userId);
        row.setDeleted(0);

        jdbcTemplate.update(
                """
                        insert into payment_order (
                            tenant_id, order_no, provider_code, provider_order_no, subject, amount_minor, currency,
                            status, payment_url, client_ip, notify_url, return_url, request_json, response_json,
                            idempotency_key, expires_at, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                row.getTenantId(),
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
                userId,
                userId
        );

        orderAggregate.recordCreated(row.getProviderCode(), row.getCurrency(), userId);
        domainEventPublisher.publishAll(orderAggregate.pullDomainEvents());
        return toOrderDto(findOrderByOrderNo(tenantId, row.getOrderNo()));
    }

    @Transactional(readOnly = true)
    public PaymentOrderDTO getOrder(Long tenantId, String orderNo) {
        PaymentOrderRow row = findOrderByOrderNo(tenantId, orderNo);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付订单不存在");
        }
        return toOrderDto(row);
    }

    @Transactional
    public PaymentRefundDTO createRefund(Long tenantId, Long userId, String orderNo, PaymentCreateRefundRequestDTO request) {
        PaymentOrderRow order = findOrderByOrderNo(tenantId, orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付订单不存在");
        }
        if (!List.of("PAID", "SUCCESS", "SETTLED").contains(order.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "当前订单状态不允许退款");
        }
        if (request.amountMinor() == null || request.amountMinor() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "退款金额必须大于 0");
        }
        if (order.getAmountMinor() != null && request.amountMinor() > order.getAmountMinor()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "退款金额不能大于原订单金额");
        }
        validateRefundCurrency(order, request.currency());

        PaymentRefundRow existing = findRefundByIdempotencyKey(tenantId, request.idempotencyKey());
        if (existing != null) {
            return toRefundDto(existing);
        }
        PaymentRefundRow byRefundNo = findRefundByRefundNo(tenantId, request.refundNo());
        if (byRefundNo != null) {
            return toRefundDto(byRefundNo);
        }

        PaymentRefundRow row = new PaymentRefundRow();
        row.setTenantId(tenantId);
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
        row.setCreatedBy(userId);
        row.setUpdatedBy(userId);
        row.setDeleted(0);

        jdbcTemplate.update(
                """
                        insert into payment_refund (
                            tenant_id, refund_no, order_no, provider_code, provider_refund_no, amount_minor, currency,
                            status, reason, request_json, response_json, idempotency_key, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                row.getTenantId(),
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
                userId,
                userId
        );

        jdbcTemplate.update(
                """
                        update payment_order
                        set status = 'REFUNDING', updated_at = ?, updated_by = ?, deleted = 0
                        where tenant_id = ? and order_no = ? and deleted = 0
                        """,
                LocalDateTime.now(),
                userId,
                tenantId,
                order.getOrderNo()
        );

        outboxService.recordAfterCommit(
                tenantId,
                userId,
                "payment",
                "payment.refund.created",
                row.getRefundNo(),
                Map.of("refundNo", row.getRefundNo(), "orderNo", row.getOrderNo(), "amountMinor", row.getAmountMinor())
        );
        return toRefundDto(findRefundByRefundNo(tenantId, row.getRefundNo()));
    }

    @Transactional(readOnly = true)
    public PaymentRefundDTO getRefund(Long tenantId, String refundNo) {
        PaymentRefundRow row = findRefundByRefundNo(tenantId, refundNo);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "退款单不存在");
        }
        return toRefundDto(row);
    }

    private PaymentOrderRow findOrderByOrderNo(Long tenantId, String orderNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, order_no as orderNo, provider_code as providerCode,
                                   provider_order_no as providerOrderNo, subject, amount_minor as amountMinor,
                                   currency, status, payment_url as paymentUrl, client_ip as clientIp,
                                   notify_url as notifyUrl, return_url as returnUrl, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   expires_at as expiresAt, paid_at as paidAt, created_by as createdBy,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt,
                                   deleted
                            from payment_order
                            where tenant_id = ? and order_no = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                    tenantId,
                    normalizeIdentifier(orderNo)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentOrderRow findOrderByIdempotencyKey(Long tenantId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, order_no as orderNo, provider_code as providerCode,
                                   provider_order_no as providerOrderNo, subject, amount_minor as amountMinor,
                                   currency, status, payment_url as paymentUrl, client_ip as clientIp,
                                   notify_url as notifyUrl, return_url as returnUrl, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   expires_at as expiresAt, paid_at as paidAt, created_by as createdBy,
                                   created_at as createdAt, updated_by as updatedBy, updated_at as updatedAt,
                                   deleted
                            from payment_order
                            where tenant_id = ? and idempotency_key = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentOrderRow.class),
                    tenantId,
                    idempotencyKey.trim()
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentRefundRow findRefundByRefundNo(Long tenantId, String refundNo) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, refund_no as refundNo, order_no as orderNo,
                                   provider_code as providerCode, provider_refund_no as providerRefundNo,
                                   amount_minor as amountMinor, currency, status, reason, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   refunded_at as refundedAt, created_by as createdBy, created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_refund
                            where tenant_id = ? and refund_no = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentRefundRow.class),
                    tenantId,
                    normalizeIdentifier(refundNo)
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private PaymentRefundRow findRefundByIdempotencyKey(Long tenantId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, refund_no as refundNo, order_no as orderNo,
                                   provider_code as providerCode, provider_refund_no as providerRefundNo,
                                   amount_minor as amountMinor, currency, status, reason, request_json as requestJson,
                                   response_json as responseJson, idempotency_key as idempotencyKey,
                                   failure_code as failureCode, failure_message as failureMessage,
                                   refunded_at as refundedAt, created_by as createdBy, created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_refund
                            where tenant_id = ? and idempotency_key = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentRefundRow.class),
                    tenantId,
                    idempotencyKey.trim()
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "订单货币与支付配置不一致");
        }
    }

    private void validateRefundCurrency(PaymentOrderRow order, String currency) {
        if (order == null || !StringUtils.hasText(currency)) {
            return;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        String orderCurrency = StringUtils.hasText(order.getCurrency()) ? order.getCurrency().trim().toUpperCase(Locale.ROOT) : null;
        if (orderCurrency != null && !orderCurrency.equals(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "退款货币与原订单不一致");
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "标识不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "标识长度不能超过 64 个字符");
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
            throw new IllegalStateException("支付交易数据序列化失败", ex);
        }
    }
}
