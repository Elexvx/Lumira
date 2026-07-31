package com.lumira.payment.controller;

import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.api.payment.PaymentRefundDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.common.vo.PageResponse;
import com.lumira.payment.service.PaymentManagementAppService;
import com.lumira.payment.service.PaymentTransactionService;
import com.lumira.payment.service.PaymentWebhookService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {
    private static final Long PROTECTED_ADMIN_ID = 1001L;

    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentWebhookService paymentWebhookService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public PaymentController(
            PaymentManagementAppService paymentManagementAppService,
            PaymentTransactionService paymentTransactionService,
            PaymentWebhookService paymentWebhookService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.paymentManagementAppService = paymentManagementAppService;
        this.paymentTransactionService = paymentTransactionService;
        this.paymentWebhookService = paymentWebhookService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
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

    @GetMapping("/manual/orders")
    public ApiResponse<PageResponse<PaymentOrderDTO>> manualOrders(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        CurrentUser currentUser = requireOrderView();
        return ApiResponse.success(
                paymentTransactionService.listManualOrdersForUser(currentUser, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/sandbox/orders")
    @RepeatSubmit
    public ApiResponse<PaymentOrderDTO> createSandboxOrder(@Valid @RequestBody PaymentCreateOrderRequestDTO request) {
        CurrentUser currentUser = requireManage();
        return ApiResponse.success(
                paymentTransactionService.createSandboxOrder(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/sandbox/orders")
    public ApiResponse<PageResponse<PaymentOrderDTO>> sandboxOrders(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        CurrentUser currentUser = requireManage();
        return ApiResponse.success(
                paymentTransactionService.listSandboxOrders(currentUser, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<PaymentOrderDTO> order(@PathVariable String orderNo) {
        CurrentUser currentUser = requireOrderView();
        return ApiResponse.success(paymentTransactionService.getOrderForUser(currentUser, orderNo), TraceContext.getRequestId());
    }

    @PostMapping("/orders/{orderNo}/cancel")
    @RepeatSubmit
    public ApiResponse<PaymentOrderDTO> cancelOrder(@PathVariable String orderNo) {
        CurrentUser currentUser = requireOrderManage();
        return ApiResponse.success(
                paymentTransactionService.cancelManualPendingOrderForUser(currentUser, orderNo),
                TraceContext.getRequestId()
        );
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
                && PROTECTED_ADMIN_ID.equals(currentUser.getUserId())
                && currentUser.getSimulatedRoleId() == null) {
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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser currentUser = authentication != null && authentication.getPrincipal() instanceof CurrentUser principal
                ? principal
                : null;
        if (!isAuthenticatedUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

}
