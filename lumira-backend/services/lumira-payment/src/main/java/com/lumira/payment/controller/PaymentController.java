package com.lumira.payment.controller;

import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.api.payment.PaymentRefundDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
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

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {
    private static final Long PROTECTED_ADMIN_ID = 1001L;
    private static final String PROTECTED_ADMIN_USERNAME = "admin";

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
        requireView();
        Long tenantId = currentTenantId();
        return ApiResponse.success(paymentManagementAppService.listProviderSettings(tenantId), TraceContext.getRequestId());
    }

    @GetMapping("/providers/{providerCode}")
    public ApiResponse<PaymentProviderSettingsDTO> provider(@PathVariable String providerCode) {
        requireView();
        Long tenantId = currentTenantId();
        return ApiResponse.success(paymentManagementAppService.paymentProviderSettings(tenantId, providerCode), TraceContext.getRequestId());
    }

    @PutMapping("/providers/{providerCode}")
    @RepeatSubmit
    public ApiResponse<PaymentProviderSettingsDTO> updateProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody PaymentProviderSettingsDTO request
    ) {
        requireManage();
        var currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(
                paymentManagementAppService.updatePaymentProviderSettings(currentUser.getCurrentTenantId(), currentUser.getUserId(), providerCode, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{providerCode}/test")
    @RepeatSubmit
    public ApiResponse<PaymentProviderTestResultDTO> testProvider(@PathVariable String providerCode) {
        requireTest();
        var currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(
                paymentManagementAppService.testPaymentProvider(currentUser.getCurrentTenantId(), currentUser.getUserId(), providerCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/orders")
    @RepeatSubmit
    public ApiResponse<PaymentOrderDTO> createOrder(@Valid @RequestBody PaymentCreateOrderRequestDTO request) {
        requireOrderManage();
        var currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(
                paymentTransactionService.createOrder(currentUser.getCurrentTenantId(), currentUser.getUserId(), request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<PaymentOrderDTO> order(@PathVariable String orderNo) {
        requireOrderView();
        var currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(paymentTransactionService.getOrder(currentUser.getCurrentTenantId(), orderNo), TraceContext.getRequestId());
    }

    @PostMapping("/orders/{orderNo}/refunds")
    @RepeatSubmit
    public ApiResponse<PaymentRefundDTO> createRefund(
            @PathVariable String orderNo,
            @Valid @RequestBody PaymentCreateRefundRequestDTO request
    ) {
        requireRefundManage();
        var currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(
                paymentTransactionService.createRefund(currentUser.getCurrentTenantId(), currentUser.getUserId(), orderNo, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/refunds/{refundNo}")
    public ApiResponse<PaymentRefundDTO> refund(@PathVariable String refundNo) {
        requireRefundView();
        var currentUser = securityContextFacade.getCurrentUser();
        return ApiResponse.success(paymentTransactionService.getRefund(currentUser.getCurrentTenantId(), refundNo), TraceContext.getRequestId());
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

    private void requireView() {
        var currentUser = securityContextFacade.getCurrentUser();
        requireSettingsAdmin(currentUser);
    }

    private void requireManage() {
        var currentUser = securityContextFacade.getCurrentUser();
        requireSettingsAdmin(currentUser);
    }

    private void requireTest() {
        var currentUser = securityContextFacade.getCurrentUser();
        requireSettingsAdmin(currentUser);
    }

    private void requireOrderManage() {
        var currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, "payment:order:create");
    }

    private void requireOrderView() {
        var currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, "payment:order:view");
    }

    private void requireRefundManage() {
        var currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, "payment:refund:create");
    }

    private void requireRefundView() {
        var currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, "payment:refund:view");
    }

    private Long currentTenantId() {
        return securityContextFacade.getCurrentUser().getCurrentTenantId();
    }

    private void requireAny(com.lumira.common.security.CurrentUser currentUser, String... permissionKeys) {
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

    private void requireSettingsAdmin(com.lumira.common.security.CurrentUser currentUser) {
        if (currentUser != null
                && (PROTECTED_ADMIN_ID.equals(currentUser.getUserId())
                || (currentUser.getUsername() != null && PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(currentUser.getUsername().trim())))) {
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
}
