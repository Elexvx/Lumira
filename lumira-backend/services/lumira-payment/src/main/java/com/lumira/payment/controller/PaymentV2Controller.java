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
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.payment.service.PaymentManagementAppService;
import com.lumira.payment.service.PaymentTransactionService;
import com.lumira.payment.service.PaymentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/payment")
public class PaymentV2Controller {

    private static final Long PROTECTED_ADMIN_ID = 1001L;
    private static final String PROTECTED_ADMIN_USERNAME = "admin";

    private final PaymentManagementAppService paymentManagementAppService;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentWebhookService paymentWebhookService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public PaymentV2Controller(
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
        CurrentUser currentUser = requireSettingsAdmin();
        return ApiResponse.success(
                paymentManagementAppService.listProviderSettings(currentUser.getCurrentTenantId()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/providers/{providerCode}")
    public ApiResponse<PaymentProviderSettingsDTO> provider(@PathVariable String providerCode) {
        CurrentUser currentUser = requireSettingsAdmin();
        return ApiResponse.success(
                paymentManagementAppService.paymentProviderSettings(currentUser.getCurrentTenantId(), providerCode),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/providers/{providerCode}")
    @RepeatSubmit
    public ApiResponse<PaymentProviderSettingsDTO> updateProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody PaymentProviderSettingsDTO request
    ) {
        CurrentUser currentUser = requireSettingsAdmin();
        return ApiResponse.success(
                paymentManagementAppService.updatePaymentProviderSettings(currentUser.getCurrentTenantId(), currentUser.getUserId(), providerCode, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/providers/{providerCode}/test")
    @RepeatSubmit
    public ApiResponse<PaymentProviderTestResultDTO> testProvider(@PathVariable String providerCode) {
        CurrentUser currentUser = requireSettingsAdmin();
        return ApiResponse.success(
                paymentManagementAppService.testPaymentProvider(currentUser.getCurrentTenantId(), currentUser.getUserId(), providerCode),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/orders")
    @RepeatSubmit
    public ApiResponse<PaymentOrderDTO> createOrder(@Valid @RequestBody PaymentCreateOrderRequestDTO request) {
        CurrentUser currentUser = requirePermission("payment:order:create");
        return ApiResponse.success(
                paymentTransactionService.createOrder(currentUser.getCurrentTenantId(), currentUser.getUserId(), request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<PaymentOrderDTO> order(@PathVariable String orderNo) {
        CurrentUser currentUser = requirePermission("payment:order:view");
        return ApiResponse.success(paymentTransactionService.getOrder(currentUser.getCurrentTenantId(), orderNo), TraceContext.getRequestId());
    }

    @PostMapping("/orders/{orderNo}/refunds")
    @RepeatSubmit
    public ApiResponse<PaymentRefundDTO> createRefund(
            @PathVariable String orderNo,
            @Valid @RequestBody PaymentCreateRefundRequestDTO request
    ) {
        CurrentUser currentUser = requirePermission("payment:refund:create");
        return ApiResponse.success(
                paymentTransactionService.createRefund(currentUser.getCurrentTenantId(), currentUser.getUserId(), orderNo, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/refunds/{refundNo}")
    public ApiResponse<PaymentRefundDTO> refund(@PathVariable String refundNo) {
        CurrentUser currentUser = requirePermission("payment:refund:view");
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

    private CurrentUser requireSettingsAdmin() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (PROTECTED_ADMIN_ID.equals(currentUser.getUserId())
                || (currentUser.getUsername() != null && PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(currentUser.getUsername().trim()))) {
            return currentUser;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "仅超级管理员可访问设置");
    }

    private CurrentUser requirePermission(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
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
