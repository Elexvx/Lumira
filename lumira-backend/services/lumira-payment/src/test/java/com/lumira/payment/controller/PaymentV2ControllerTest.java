package com.lumira.payment.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.api.payment.PaymentRefundDTO;
import com.lumira.api.payment.PaymentWebhookEventDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.exception.BizException;
import com.lumira.common.enums.ErrorCode;
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
        CurrentUser admin = currentUser(1001L, "admin", 0L, "payment:config:view");
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        when(securityContextFacade.getCurrentUser()).thenReturn(admin);
        when(paymentManagementAppService.listProviderSettings(admin)).thenReturn(List.of(settings));

        var response = controller.providers();

        assertThat(response.getData()).containsExactly(settings);
        verify(paymentManagementAppService).listProviderSettings(admin);
        verify(permissionGuard, never()).requirePermission(admin, "payment:config:view");
    }

    @Test
    void providers_shouldRejectNonAdminBeforeApplicationService() {
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser(42L, "alice", "payment:config:view"));

        assertThatThrownBy(() -> controller.providers())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅超级管理员");

        verify(paymentManagementAppService, never()).listProviderSettings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providers_shouldRejectAdminUsernameWithoutProtectedAdminId() {
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser(42L, "admin", "payment:config:view"));

        assertThatThrownBy(() -> controller.providers())
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(paymentManagementAppService, never()).listProviderSettings(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void providers_shouldAllowProtectedAdminWithRenamedUsername() {
        CurrentUser admin = currentUser(1001L, "root-admin", 0L, "payment:config:view");
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        when(securityContextFacade.getCurrentUser()).thenReturn(admin);
        when(paymentManagementAppService.listProviderSettings(admin)).thenReturn(List.of(settings));

        var response = controller.providers();

        assertThat(response.getData()).containsExactly(settings);
        verify(paymentManagementAppService).listProviderSettings(admin);
    }

    @Test
    void updateProvider_shouldDelegateWithOperator() {
        CurrentUser admin = currentUser(1001L, "admin", null, "payment:config:update");
        PaymentProviderSettingsDTO request = new PaymentProviderSettingsDTO();
        PaymentProviderSettingsDTO result = new PaymentProviderSettingsDTO();
        result.setProviderCode("stripe");
        when(securityContextFacade.getCurrentUser()).thenReturn(admin);
        when(paymentManagementAppService.updatePaymentProviderSettings(admin, "stripe", request)).thenReturn(result);

        var response = controller.updateProvider("stripe", request);

        assertThat(response.getData()).isSameAs(result);
        verify(paymentManagementAppService).updatePaymentProviderSettings(admin, "stripe", request);
    }

    @Test
    void createOrder_shouldCheckPermissionAndDelegateToTransactionService() {
        CurrentUser currentUser = currentUser(42L, "alice", 0L, "payment:order:create");
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
        when(paymentTransactionService.createOrder(currentUser, request)).thenReturn(order);

        var response = controller.createOrder(request);

        assertThat(response.getData()).isSameAs(order);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService).createOrder(currentUser, request);
    }

    @Test
    void createOrder_shouldRejectUnauthenticatedUserBeforePermissionAndService() {
        CurrentUser currentUser = new CurrentUser(42L, "alice", 1001L, "session-1", 1, false, Set.of("payment:order:create"));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subject",
                100L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.createOrder(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService, never()).createOrder(currentUser, request);
    }

    @Test
    void createOrder_shouldRejectBlankUsernameBeforePermissionAndService() {
        CurrentUser currentUser = currentUser(42L, " ", "payment:order:create");
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subject",
                100L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.createOrder(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService, never()).createOrder(currentUser, request);
    }

    @Test
    void createOrder_shouldRejectMissingSessionVersionBeforePermissionAndService() {
        CurrentUser currentUser = new CurrentUser(42L, "alice", 1001L, "session-1", null, true, Set.of("payment:order:create"));
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subject",
                100L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.createOrder(request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService, never()).createOrder(currentUser, request);
    }

    @Test
    void order_shouldQueryWithinCurrentUserScope() {
        CurrentUser currentUser = currentUser(42L, "alice", "payment:order:view");
        PaymentOrderDTO order = new PaymentOrderDTO("ORD-1", "stripe", "po_1", "subject", 100L, "CNY", "PENDING", null, null, null, null, Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.getOrderForUser(currentUser, "ORD-1")).thenReturn(order);

        var response = controller.order("ORD-1");

        assertThat(response.getData()).isSameAs(order);
        verify(permissionGuard).requirePermission(currentUser, "payment:order:view");
        verify(paymentTransactionService).getOrderForUser(currentUser, "ORD-1");
        verify(paymentTransactionService, never()).getOrder("ORD-1");
    }

    @Test
    void createRefund_shouldCheckPermissionAndDelegateToTransactionService() {
        CurrentUser currentUser = currentUser(42L, "alice", "payment:refund:create");
        PaymentCreateRefundRequestDTO request = new PaymentCreateRefundRequestDTO("REF-1", 100L, "CNY", "重复付款", Map.of(), "rid-1");
        PaymentRefundDTO refund = new PaymentRefundDTO("REF-1", "ORD-1", "stripe", "pr_1", 100L, "CNY", "PENDING", "重复付款", Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.createRefund(currentUser, "ORD-1", request)).thenReturn(refund);

        var response = controller.createRefund("ORD-1", request);

        assertThat(response.getData()).isSameAs(refund);
        verify(permissionGuard).requirePermission(currentUser, "payment:refund:create");
        verify(paymentTransactionService).createRefund(currentUser, "ORD-1", request);
    }

    @Test
    void refund_shouldQueryWithinCurrentUserScope() {
        CurrentUser currentUser = currentUser(42L, "alice", "payment:refund:view");
        PaymentRefundDTO refund = new PaymentRefundDTO("REF-1", "ORD-1", "stripe", "pr_1", 100L, "CNY", "PENDING", "duplicate", Map.of(), null, null, null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(paymentTransactionService.getRefundForUser(currentUser, "REF-1")).thenReturn(refund);

        var response = controller.refund("REF-1");

        assertThat(response.getData()).isSameAs(refund);
        verify(permissionGuard).requirePermission(currentUser, "payment:refund:view");
        verify(paymentTransactionService).getRefundForUser(currentUser, "REF-1");
        verify(paymentTransactionService, never()).getRefund("REF-1");
    }

    @Test
    void webhook_shouldForwardProviderHeaders() {
        HttpServletRequest request = requestWithHeaders(Map.of(
                "X-Signature", "sig",
                "X-Nonce", "nonce-1"
        ));
        PaymentWebhookEventDTO event = new PaymentWebhookEventDTO("stripe", "evt-1", "payment.succeeded", true, true, "ok", LocalDateTime.now(), LocalDateTime.now());
        when(paymentWebhookService.handleWebhook("stripe", "{\"eventId\":\"evt-1\"}", Map.of(
                "X-Signature", "sig",
                "X-Nonce", "nonce-1"
        ))).thenReturn(event);

        var response = controller.webhook("stripe", "{\"eventId\":\"evt-1\"}", request);

        assertThat(response).isEqualTo("success");
        verify(paymentWebhookService).handleWebhook("stripe", "{\"eventId\":\"evt-1\"}", Map.of(
                "X-Signature", "sig",
                "X-Nonce", "nonce-1"
        ));
    }

    @Test
    void webhook_shouldOnlyNeedProviderHeader() {
        HttpServletRequest request = requestWithHeaders(Map.of("X-Webhook-Token", "token-1"));
        PaymentWebhookEventDTO event = new PaymentWebhookEventDTO("paypal", "evt-2", "payment", false, false, "签名校验失败", LocalDateTime.now(), null);
        when(paymentWebhookService.handleWebhook("paypal", "{}", Map.of("X-Webhook-Token", "token-1"))).thenReturn(event);

        var response = controller.webhook("paypal", "{}", request);

        assertThat(response).isEqualTo("success");
        verify(paymentWebhookService).handleWebhook("paypal", "{}", Map.of("X-Webhook-Token", "token-1"));
    }

    @Test
    void createOrderShouldRejectTrustedUserWhenResolverIsUnavailable() {
        PaymentV2Controller strictController = new PaymentV2Controller(
                paymentManagementAppService,
                paymentTransactionService,
                paymentWebhookService,
                securityContextFacade,
                permissionGuard,
                null
        );
        CurrentUser currentUser = currentUser(42L, "alice", 0L, "payment:order:create");
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subject",
                100L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> strictController.createOrder(request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");

        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService, never()).createOrder(currentUser, request);
    }

    @Test
    void createOrder_shouldRejectTrustedUserWhenLiveUsernameIsUnavailable() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PaymentV2Controller strictController = new PaymentV2Controller(
                paymentManagementAppService,
                paymentTransactionService,
                paymentWebhookService,
                securityContextFacade,
                permissionGuard,
                systemInternalApi
        );
        CurrentUser currentUser = currentUser(42L, "alice", 0L, "payment:order:create");
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                "stripe",
                "ORD-1",
                "subject",
                100L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-1"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(systemInternalApi.findUserIdentityById(42L)).thenReturn(
                new SystemUserSnapshotDTO(42L, "user-uuid-42", " ", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThatThrownBy(() -> strictController.createOrder(request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");

        verify(permissionGuard, never()).requirePermission(currentUser, "payment:order:create");
        verify(paymentTransactionService, never()).createOrder(currentUser, request);
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
    }

    private CurrentUser currentUser(Long userId, String username, String permission) {
        return currentUser(userId, username, 1001L, permission);
    }

    private CurrentUser currentUser(Long userId, String username, Long legacyScopeId, String permission) {
        CurrentUser currentUser = new CurrentUser(userId, username, legacyScopeId, "session-1", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-" + userId);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
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
