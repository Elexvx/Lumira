package com.lumira.payment.controller;

import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.vo.PageResponse;
import com.lumira.payment.service.PaymentManagementAppService;
import com.lumira.payment.service.PaymentTransactionService;
import com.lumira.payment.service.PaymentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrder_shouldUseCurrentUserAsOperator() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "alice", 0L, "session-1", 1, true, Set.of("payment:order:create")));
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
        when(paymentTransactionService.createOrder(currentUser, request)).thenReturn(order);

        var response = controller.createOrder(request);

        assertThat(response.getData()).isSameAs(order);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService).createOrder(currentUser, request);
    }

    @Test
    void createSandboxOrder_shouldAllowProtectedAdminAndUseSandboxTransactionService() {
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
        CurrentUser currentUser = trusted(new CurrentUser(1001L, "root-admin", 0L, "session-1", 1, true, Set.of("payment:config:update", "payment:order:create")));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-SANDBOX-1",
                "sandbox order",
                1999L,
                "USD",
                null,
                null,
                null,
                Map.of("scene", "settings-manual"),
                "sandbox-idem-1"
        );
        PaymentOrderDTO order = new PaymentOrderDTO("ORD-SANDBOX-1", "stripe", "po_1", "sandbox order", 1999L, "USD", "PENDING", null, null, null, null, Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.createSandboxOrder(currentUser, request)).thenReturn(order);

        var response = controller.createSandboxOrder(request);

        assertThat(response.getData()).isSameAs(order);
        verify(paymentTransactionService).createSandboxOrder(currentUser, request);
        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
    }

    @Test
    void manualOrders_shouldRequireViewPermissionAndReturnCurrentUsersHistory() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "alice", 0L, "session-1", 1, true, Set.of("payment:order:view")));
        PageResponse<PaymentOrderDTO> page = new PageResponse<>();
        when(paymentTransactionService.listManualOrdersForUser(currentUser, 1, 50)).thenReturn(page);

        var response = controller.manualOrders(1, 50);

        assertThat(response.getData()).isSameAs(page);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:view");
        verify(paymentTransactionService).listManualOrdersForUser(currentUser, 1, 50);
    }

    @Test
    void cancelOrder_shouldRequireCreatePermissionAndDelegateForCurrentUser() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "alice", 0L, "session-1", 1, true, Set.of("payment:order:create")));
        PaymentOrderDTO cancelled = new PaymentOrderDTO(
                "MAN-ALI-P-1-CANCEL",
                "alipay",
                "po-1",
                "manual",
                1L,
                "CNY",
                "CANCELLED",
                null,
                null,
                null,
                null,
                Map.of(),
                "ORDER_CANCELLED",
                "Payment order was cancelled before payment",
                null,
                null,
                null
        );
        when(paymentTransactionService.cancelManualPendingOrderForUser(currentUser, "MAN-ALI-P-1-CANCEL"))
                .thenReturn(cancelled);

        var response = controller.cancelOrder("MAN-ALI-P-1-CANCEL");

        assertThat(response.getData()).isSameAs(cancelled);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService).cancelManualPendingOrderForUser(currentUser, "MAN-ALI-P-1-CANCEL");
    }

    @Test
    void providers_shouldRejectAdminUsernameWithoutProtectedAdminId() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "admin", 0L, "session-1", 1, true, Set.of("payment:config:view")));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(controller::providers)
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(paymentManagementAppService, never()).listProviderSettings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providers_shouldAllowProtectedAdminWithRenamedUsername() {
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
        CurrentUser currentUser = trusted(new CurrentUser(1001L, "root-admin", 0L, "session-1", 1, true, Set.of("payment:config:view")));
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentManagementAppService.listProviderSettings(currentUser)).thenReturn(java.util.List.of(settings));

        var response = controller.providers();

        assertThat(response.getData()).containsExactly(settings);
        verify(paymentManagementAppService).listProviderSettings(currentUser);
    }

    @Test
    void providers_shouldRejectProtectedAdminWhileSimulatingRole() {
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
        CurrentUser currentUser = trusted(new CurrentUser(
                1001L,
                "root-admin",
                0L,
                "session-1",
                1,
                true,
                Set.of("payment:config:view")
        ));
        currentUser.setSimulatedRoleId(9001L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(controller::providers)
                .isInstanceOfSatisfying(com.lumira.common.exception.BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(com.lumira.common.enums.ErrorCode.FORBIDDEN));

        verify(paymentManagementAppService, never()).listProviderSettings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOrder_shouldRejectBlankUsernameBeforePermissionAndService() {
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
        CurrentUser currentUser = new CurrentUser(42L, " ", 0L, "session-1", 1, true, Set.of("payment:order:create"));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subscription",
                9900L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.createOrder(request))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService, never()).createOrder(currentUser, request);
    }

    @Test
    void createOrder_shouldRejectMissingSessionIdBeforePermissionAndService() {
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
        CurrentUser currentUser = new CurrentUser(42L, "alice", 0L, null, 1, true, Set.of("payment:order:create"));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subscription",
                9900L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.createOrder(request))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService, never()).createOrder(currentUser, request);
    }

    @Test
    void order_shouldUseCurrentUserScope() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "alice", 0L, "session-1", 1, true, Set.of("payment:order:view")));
        PaymentOrderDTO order = new PaymentOrderDTO("ORD-1", "stripe", "po_1", "subscription", 9900L, "CNY", "PENDING", null, null, null, null, Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.getOrderForUser(currentUser, "ORD-1")).thenReturn(order);

        var response = controller.order("ORD-1");

        assertThat(response.getData()).isSameAs(order);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:view");
        verify(paymentTransactionService).getOrderForUser(currentUser, "ORD-1");
    }

    @Test
    void refund_shouldUseCurrentUserScope() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "alice", 0L, "session-1", 1, true, Set.of("payment:refund:view")));
        var refund = new com.lumira.api.payment.PaymentRefundDTO("REF-1", "ORD-1", "stripe", "pr_1", 100L, "CNY", "PENDING", "duplicate", Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.getRefundForUser(currentUser, "REF-1")).thenReturn(refund);

        var response = controller.refund("REF-1");

        assertThat(response.getData()).isSameAs(refund);
        verify(permissionGuard).requirePermission(currentUser, "payment:refund:view");
        verify(paymentTransactionService).getRefundForUser(currentUser, "REF-1");
    }

    @Test
    void webhook_shouldForwardProviderHeaders() {
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
        verify(paymentWebhookService).handleWebhook("stripe", "{}", headers);
    }

    @Test
    void createOrderShouldUseAuthenticatedRequestContextWithoutControllerResolver() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "alice", 0L, "session-1", 1, true, Set.of("payment:order:create")));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subscription",
                9900L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        controller.createOrder(request);

        verify(permissionGuard).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService).createOrder(currentUser, request);
    }

    @Test
    void createOrderShouldNotResolveUserSnapshotAgain() {
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
        CurrentUser currentUser = trusted(new CurrentUser(42L, "alice", 0L, "session-1", 1, true, Set.of("payment:order:create")));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subscription",
                9900L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        controller.createOrder(request);

        verify(permissionGuard).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService).createOrder(currentUser, request);
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

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("permissions-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList())
        );
        return currentUser;
    }

}
