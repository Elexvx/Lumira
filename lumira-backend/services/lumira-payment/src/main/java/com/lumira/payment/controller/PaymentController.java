package com.lumira.payment.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.api.payment.PaymentRefundDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.payment.service.PaymentManagementAppService;
import com.lumira.payment.service.PaymentTransactionService;
import com.lumira.payment.service.PaymentWebhookService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {
    private static final Long PROTECTED_ADMIN_ID = 1001L;
    private static final String STATUS_ENABLED = "ENABLED";

    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentWebhookService paymentWebhookService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SystemInternalApi systemInternalApi;
    private final boolean enforceTrustedUserResolution;

    public PaymentController(
            PaymentManagementAppService paymentManagementAppService,
            PaymentTransactionService paymentTransactionService,
            PaymentWebhookService paymentWebhookService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(
                paymentManagementAppService,
                paymentTransactionService,
                paymentWebhookService,
                securityContextFacade,
                permissionGuard,
                null,
                false
        );
    }

    @Autowired
    public PaymentController(
            PaymentManagementAppService paymentManagementAppService,
            PaymentTransactionService paymentTransactionService,
            PaymentWebhookService paymentWebhookService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi
    ) {
        this(
                paymentManagementAppService,
                paymentTransactionService,
                paymentWebhookService,
                securityContextFacade,
                permissionGuard,
                systemInternalApi,
                true
        );
    }

    private PaymentController(
            PaymentManagementAppService paymentManagementAppService,
            PaymentTransactionService paymentTransactionService,
            PaymentWebhookService paymentWebhookService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi,
            boolean enforceTrustedUserResolution
    ) {
        this.paymentManagementAppService = paymentManagementAppService;
        this.paymentTransactionService = paymentTransactionService;
        this.paymentWebhookService = paymentWebhookService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.systemInternalApi = systemInternalApi;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/providers")
    public ApiResponse<List<PaymentProviderSettingsDTO>> providers() {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(paymentManagementAppService.listProviderSettings(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/providers/{providerCode}")
    public ApiResponse<PaymentProviderSettingsDTO> provider(@PathVariable String providerCode) {
        CurrentUser currentUser = requireView();
        return ApiResponse.success(paymentManagementAppService.paymentProviderSettings(currentUser, providerCode), TraceContext.getRequestId());
    }

    @PutMapping("/providers/{providerCode}")
    @RepeatSubmit
    public ApiResponse<PaymentProviderSettingsDTO> updateProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody PaymentProviderSettingsDTO request
    ) {
        CurrentUser currentUser = requireManage();
        return ApiResponse.success(
                paymentManagementAppService.updatePaymentProviderSettings(currentUser, providerCode, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{providerCode}/test")
    @RepeatSubmit
    public ApiResponse<PaymentProviderTestResultDTO> testProvider(@PathVariable String providerCode) {
        CurrentUser currentUser = requireTest();
        return ApiResponse.success(
                paymentManagementAppService.testPaymentProvider(currentUser, providerCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/orders")
    @RepeatSubmit
    public ApiResponse<PaymentOrderDTO> createOrder(@Valid @RequestBody PaymentCreateOrderRequestDTO request) {
        CurrentUser currentUser = requireOrderManage();
        return ApiResponse.success(
                paymentTransactionService.createOrder(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<PaymentOrderDTO> order(@PathVariable String orderNo) {
        CurrentUser currentUser = requireOrderView();
        return ApiResponse.success(paymentTransactionService.getOrderForUser(currentUser, orderNo), TraceContext.getRequestId());
    }

    @PostMapping("/orders/{orderNo}/refunds")
    @RepeatSubmit
    public ApiResponse<PaymentRefundDTO> createRefund(
            @PathVariable String orderNo,
            @Valid @RequestBody PaymentCreateRefundRequestDTO request
    ) {
        CurrentUser currentUser = requireRefundManage();
        return ApiResponse.success(
                paymentTransactionService.createRefund(currentUser, orderNo, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/refunds/{refundNo}")
    public ApiResponse<PaymentRefundDTO> refund(@PathVariable String refundNo) {
        CurrentUser currentUser = requireRefundView();
        return ApiResponse.success(paymentTransactionService.getRefundForUser(currentUser, refundNo), TraceContext.getRequestId());
    }

    @PostMapping("/webhooks/{providerCode}")
    public ApiResponse<PaymentWebhookEventDTO> webhook(
            @PathVariable String providerCode,
            @RequestBody(required = false) String payload,
            HttpServletRequest request
    ) {
        Map<String, String> headers = extractHeaders(request);
        return ApiResponse.success(
                paymentWebhookService.handleWebhook(
                        providerCode,
                        payload,
                        headers
                ),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser requireView() {
        var currentUser = currentUser();
        requireSettingsAdmin(currentUser);
        return currentUser;
    }

    private CurrentUser requireManage() {
        var currentUser = currentUser();
        requireSettingsAdmin(currentUser);
        return currentUser;
    }

    private CurrentUser requireTest() {
        var currentUser = currentUser();
        requireSettingsAdmin(currentUser);
        return currentUser;
    }

    private CurrentUser requireOrderManage() {
        var currentUser = currentUser();
        requireAuthenticated(currentUser);
        permissionGuard.requirePermission(currentUser, "payment:order:create");
        return currentUser;
    }

    private CurrentUser requireOrderView() {
        var currentUser = currentUser();
        requireAuthenticated(currentUser);
        permissionGuard.requirePermission(currentUser, "payment:order:view");
        return currentUser;
    }

    private CurrentUser requireRefundManage() {
        var currentUser = currentUser();
        requireAuthenticated(currentUser);
        permissionGuard.requirePermission(currentUser, "payment:refund:create");
        return currentUser;
    }

    private CurrentUser requireRefundView() {
        var currentUser = currentUser();
        requireAuthenticated(currentUser);
        permissionGuard.requirePermission(currentUser, "payment:refund:view");
        return currentUser;
    }

    private void requireAny(CurrentUser currentUser, String... permissionKeys) {
        for (String permissionKey : permissionKeys) {
            try {
                permissionGuard.requirePermission(currentUser, permissionKey);
                return;
            } catch (RuntimeException ignored) {
                // Try the next permission.
            }
        }
        throw new com.lumira.common.exception.BizException(com.lumira.common.enums.ErrorCode.FORBIDDEN, "缺少权限");
    }

    private void requireSettingsAdmin(CurrentUser currentUser) {
        if (isAuthenticatedUser(currentUser)
                && PROTECTED_ADMIN_ID.equals(currentUser.getUserId())) {
            return;
        }
        throw new com.lumira.common.exception.BizException(com.lumira.common.enums.ErrorCode.FORBIDDEN, "仅超级管理员可访问设置");
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    private boolean isAuthenticatedUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private void requireAuthenticated(CurrentUser currentUser) {
        if (!isAuthenticatedUser(currentUser)) {
            throw new com.lumira.common.exception.BizException(com.lumira.common.enums.ErrorCode.UNAUTHORIZED, "Login required");
        }
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!isAuthenticatedUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshTrustedCurrentUser(currentUser);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (systemInternalApi == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        String currentUsername = StringUtils.hasText(userSnapshot.username()) ? userSnapshot.username().trim() : null;
        if (!StringUtils.hasText(currentUsername)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        Long simulatedRoleId = currentUser.getSimulatedRoleId();
        if (simulatedRoleId != null && simulatedRoleId <= 0) {
            simulatedRoleId = null;
        }
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, userSnapshot.userUuid().trim())
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, userSnapshot.userUuid().trim(), simulatedRoleId);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(currentUsername);
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }

}
