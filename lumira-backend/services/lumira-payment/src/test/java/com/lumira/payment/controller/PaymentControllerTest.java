package com.lumira.payment.controller;

import com.lumira.api.payment.PaymentWebhookEventDTO;
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
