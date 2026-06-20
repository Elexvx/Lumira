package com.lumira.payment.controller;

import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.api.payment.PaymentRefundDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.payment.service.PaymentManagementAppService;
import com.lumira.payment.service.PaymentTransactionService;
import com.lumira.payment.service.PaymentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentV2ControllerTest {

    private PaymentManagementAppService paymentManagementAppService;
    private PaymentTransactionService paymentTransactionService;
    private PaymentWebhookService paymentWebhookService;
    private SecurityContextFacade securityContextFacade;
    private PermissionGuard permissionGuard;
    private PaymentV2Controller controller;

    @BeforeEach
    void setUp() {
        paymentManagementAppService = mock(PaymentManagementAppService.class);
        paymentTransactionService = mock(PaymentTransactionService.class);
        paymentWebhookService = mock(PaymentWebhookService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        permissionGuard = mock(PermissionGuard.class);
        controller = new PaymentV2Controller(
                paymentManagementAppService,
                paymentTransactionService,
                paymentWebhookService,
                securityContextFacade,
                permissionGuard
        );
    }

    @Test
    void providers_shouldAllowProtectedAdminAndDelegateToManagementService() {
        CurrentUser admin = currentUser(1001L, "admin", "payment:settings:view");
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        when(securityContextFacade.getCurrentUser()).thenReturn(admin);
        when(paymentManagementAppService.listProviderSettings(1001L)).thenReturn(List.of(settings));

        var response = controller.providers();

        assertThat(response.getData()).containsExactly(settings);
        verify(paymentManagementAppService).listProviderSettings(1001L);
        verify(permissionGuard, never()).requirePermission(admin, "payment:settings:view");
    }

    @Test
    void providers_shouldRejectNonAdminBeforeApplicationService() {
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser(42L, "alice", "payment:settings:view"));

        assertThatThrownBy(() -> controller.providers())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅超级管理员");

        verify(paymentManagementAppService, never()).listProviderSettings(1001L);
    }

    @Test
    void updateProvider_shouldDelegateWithCurrentTenantAndOperator() {
        CurrentUser admin = currentUser(1001L, "admin", "payment:settings:manage");
        PaymentProviderSettingsDTO request = new PaymentProviderSettingsDTO();
        PaymentProviderSettingsDTO result = new PaymentProviderSettingsDTO();
        result.setProviderCode("stripe");
        when(securityContextFacade.getCurrentUser()).thenReturn(admin);
        when(paymentManagementAppService.updatePaymentProviderSettings(1001L, 1001L, "stripe", request)).thenReturn(result);

        var response = controller.updateProvider("stripe", request);

        assertThat(response.getData()).isSameAs(result);
        verify(paymentManagementAppService).updatePaymentProviderSettings(1001L, 1001L, "stripe", request);
    }

    @Test
    void createOrder_shouldCheckPermissionAndDelegateToTransactionService() {
        CurrentUser currentUser = currentUser(42L, "alice", "payment:order:create");
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "会员订阅",
                9900L,
                "CNY",
                "127.0.0.1",
                null,
                null,
                Map.of("plan", "pro"),
                "idem-1"
        );
        PaymentOrderDTO order = new PaymentOrderDTO("ORD-1", "stripe", "po_1", "会员订阅", 9900L, "CNY", "PENDING", null, null, null, null, Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.createOrder(1001L, 42L, request)).thenReturn(order);

        var response = controller.createOrder(request);

        assertThat(response.getData()).isSameAs(order);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService).createOrder(1001L, 42L, request);
    }

    @Test
    void createRefund_shouldCheckPermissionAndDelegateToTransactionService() {
        CurrentUser currentUser = currentUser(42L, "alice", "payment:refund:create");
        PaymentCreateRefundRequestDTO request = new PaymentCreateRefundRequestDTO("REF-1", 100L, "CNY", "重复付款", Map.of(), "rid-1");
        PaymentRefundDTO refund = new PaymentRefundDTO("REF-1", "ORD-1", "stripe", "pr_1", 100L, "CNY", "PENDING", "重复付款", Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.createRefund(1001L, 42L, "ORD-1", request)).thenReturn(refund);

        var response = controller.createRefund("ORD-1", request);

        assertThat(response.getData()).isSameAs(refund);
        verify(permissionGuard).requirePermission(currentUser, "payment:refund:create");
        verify(paymentTransactionService).createRefund(1001L, 42L, "ORD-1", request);
    }

    @Test
    void webhook_shouldResolveTenantFromProviderIdentityAndForwardHeaders() {
        HttpServletRequest request = requestWithHeaders(Map.of(
                "X-Tenant-Id", "2002",
                "X-Signature", "sig",
                "X-Nonce", "nonce-1"
        ));
        PaymentWebhookEventDTO event = new PaymentWebhookEventDTO("stripe", "evt-1", "payment.succeeded", true, true, "ok", LocalDateTime.now(), LocalDateTime.now());
        when(paymentWebhookService.handleWebhook("stripe", "{\"eventId\":\"evt-1\"}", Map.of(
                "X-Tenant-Id", "2002",
                "X-Signature", "sig",
                "X-Nonce", "nonce-1"
        ))).thenReturn(event);

        var response = controller.webhook("stripe", "{\"eventId\":\"evt-1\"}", request);

        assertThat(response.getData()).isSameAs(event);
        verify(paymentManagementAppService, never()).resolveWebhookTenantId(anyString(), anyString(), anyMap());
        verify(paymentWebhookService).handleWebhook("stripe", "{\"eventId\":\"evt-1\"}", Map.of(
                "X-Tenant-Id", "2002",
                "X-Signature", "sig",
                "X-Nonce", "nonce-1"
        ));
    }

    @Test
    void webhook_shouldNotUseTenantHeaderAsTrustSource() {
        HttpServletRequest request = requestWithHeaders(Map.of("X-Tenant-Id", "bad", "X-Webhook-Token", "token-1"));
        PaymentWebhookEventDTO event = new PaymentWebhookEventDTO("paypal", "evt-2", "payment", false, false, "签名校验失败", LocalDateTime.now(), null);
        when(paymentWebhookService.handleWebhook("paypal", "{}", Map.of("X-Tenant-Id", "bad", "X-Webhook-Token", "token-1"))).thenReturn(event);

        var response = controller.webhook("paypal", "{}", request);

        assertThat(response.getData()).isSameAs(event);
        verify(paymentManagementAppService, never()).resolveWebhookTenantId(anyString(), anyString(), anyMap());
        verify(paymentWebhookService).handleWebhook("paypal", "{}", Map.of("X-Tenant-Id", "bad", "X-Webhook-Token", "token-1"));
    }

    private CurrentUser currentUser(Long userId, String username, String permission) {
        return new CurrentUser(userId, username, 1001L, "session-1", 1, true, Set.of(permission));
    }

    private HttpServletRequest requestWithHeaders(Map<String, String> headers) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(headers.keySet()));
        headers.forEach((name, value) -> when(request.getHeader(name)).thenReturn(value));
        return request;
    }

    private Enumeration<String> enumeration(Set<String> values) {
        return Collections.enumeration(values);
    }
}
