package com.lumira.payment.service;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;

@Service("paymentInternalApi")
@Primary
public class PaymentInternalApiService implements PaymentInternalApi {

    private static final int MAX_ORDER_NO_LENGTH = 64;

    private final PaymentTransactionService paymentTransactionService;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;

    public PaymentInternalApiService(
            PaymentTransactionService paymentTransactionService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this.paymentTransactionService = paymentTransactionService;
        this.systemInternalApiProvider = systemInternalApiProvider;
    }

    @Override
    public PaymentOrderDTO createOrder(Long operatorId, String operatorUuid, PaymentCreateOrderRequestDTO request) {
        requireRequest(request);
        return paymentTransactionService.createOrder(resolveTrustedOperator(operatorId, operatorUuid), request);
    }

    @Override
    public PaymentOrderDTO getOrder(Long operatorId, String operatorUuid, String orderNo) {
        CurrentUser operator = resolveTrustedOperator(operatorId, operatorUuid);
        return paymentTransactionService.getOrderForUser(operator.getUserId(), operator.getUserUuid(), requireOrderNo(orderNo));
    }

    private CurrentUser resolveTrustedOperator(Long operatorId, String operatorUuid) {
        if (operatorId == null || operatorId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Valid operator is required");
        }
        if (!StringUtils.hasText(operatorUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator userUuid is required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider == null ? null : systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted operator resolver is unavailable");
        }
        SystemUserSnapshotDTO snapshot = systemInternalApi.findUserIdentityById(operatorId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(operatorId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator does not exist");
        }
        if (!StringUtils.hasText(snapshot.userUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator userUuid is required");
        }
        if (!snapshot.userUuid().trim().equals(operatorUuid.trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator identity mismatch");
        }
        if (!StringUtils.hasText(snapshot.username())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator username is required");
        }
        if (!StringUtils.hasText(snapshot.status()) || !"ENABLED".equalsIgnoreCase(snapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator is disabled");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(operatorId, snapshot.userUuid().trim());
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Operator permissions are unavailable");
        }
        CurrentUser operator = new CurrentUser(
                snapshot.userId(),
                snapshot.username().trim(),
                null,
                "internal-payment",
                1,
                true,
                permissionSnapshot.permissions() == null ? java.util.Set.of() : java.util.Set.copyOf(new LinkedHashSet<>(permissionSnapshot.permissions())),
                permissionSnapshot.roleIds() == null ? java.util.Set.of() : java.util.Set.copyOf(new LinkedHashSet<>(permissionSnapshot.roleIds())),
                permissionSnapshot.primaryDeptId(),
                permissionSnapshot.deptIds() == null ? java.util.Set.of() : java.util.Set.copyOf(new LinkedHashSet<>(permissionSnapshot.deptIds())),
                permissionSnapshot.descendantDeptIds() == null ? java.util.Set.of() : java.util.Set.copyOf(new LinkedHashSet<>(permissionSnapshot.descendantDeptIds())),
                permissionSnapshot.dataScopes() == null ? java.util.List.of() : java.util.List.copyOf(permissionSnapshot.dataScopes())
        );
        operator.setUserUuid(snapshot.userUuid().trim());
        operator.setPermissionsVersion(permissionSnapshot.version().trim());
        operator.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(operator)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted operator is required");
        }
        return operator;
    }

    private PaymentCreateOrderRequestDTO requireRequest(PaymentCreateOrderRequestDTO request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment order request is required");
        }
        return request;
    }

    private String requireOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "orderNo is required");
        }
        String normalized = orderNo.trim();
        if (normalized.length() > MAX_ORDER_NO_LENGTH) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "orderNo is too long");
        }
        return normalized;
    }
}
