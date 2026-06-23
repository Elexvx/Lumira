package com.lumira.payment.controller;

import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.payment.service.PaymentManagementAppService;
import com.lumira.payment.service.PaymentTransactionService;
import com.lumira.payment.service.PaymentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    @Test
    void createOrder_shouldUsePlatformTenantInsteadOfCurrentUserTenant() {
        PaymentManagementAppService paymentManagementAppService = mock(PaymentManagementAppService.class);
        PaymentTransactionService paymentTransactionService = mock(PaymentTransactionService.class);
        PaymentWebhookService paymentWebhookService = mock(PaymentWebhookService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        PaymentController controller = new PaymentController(
                paymentManagementAppService,
                paymentTransactionService,
                paymentWebhookService,
                securityContextFacade,
                permissionGuard
        );
        CurrentUser currentUser = new CurrentUser(42L, "alice", 2002L, "session-1", 1, true, Set.of("payment:order:create"));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subscription",
                9900L,
                "CNY",
                "127.0.0.1",
                null,
                null,
                Map.of("plan", "pro"),
                "idem-1"
        );
        PaymentOrderDTO order = new PaymentOrderDTO("ORD-1", "stripe", "po_1", "subscription", 9900L, "CNY", "PENDING", null, null, null, null, Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.createOrder(1001L, 42L, request)).thenReturn(order);

        var response = controller.createOrder(request);

        assertThat(response.getData()).isSameAs(order);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService).createOrder(1001L, 42L, request);
    }

    @Test
    void webhook_shouldResolveTenantFromProviderIdentityInsteadOfTenantHeader() {
        PaymentManagementAppService paymentManagementAppService = mock(PaymentManagementAppService.class);
        PaymentTransactionService paymentTransactionService = mock(PaymentTransactionService.class);
        PaymentWebhookService paymentWebhookService = mock(PaymentWebhookService.class);
        PaymentController controller = new PaymentController(
                paymentManagementAppService,
                paymentTransactionService,
                paymentWebhookService,
                mock(SecurityContextFacade.class),
                mock(PermissionGuard.class)
        );
        Map<String, String> headers = Map.of(
                "X-Tenant-Id", "9999",
                "X-Webhook-Token", "endpoint-token"
        );
        HttpServletRequest request = requestWithHeaders(headers);
        PaymentWebhookEventDTO event = new PaymentWebhookEventDTO(
                "stripe",
                "evt-1",
                "payment.succeeded",
                true,
                true,
                "ok",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(paymentWebhookService.handleWebhook("stripe", "{}", headers)).thenReturn(event);

        var response = controller.webhook("stripe", "{}", request);

        assertThat(response.getData()).isSameAs(event);
        verify(paymentManagementAppService, never()).resolveWebhookTenantId(anyString(), anyString(), anyMap());
        verify(paymentWebhookService).handleWebhook("stripe", "{}", headers);
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
