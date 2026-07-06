package com.lumira.payment.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentInternalApiServiceTest {

    @Test
    void getOrderShouldQueryWithinOperatorScope() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "alice", "ENABLED"));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));
        PaymentOrderDTO order = new PaymentOrderDTO("ORD-1", "stripe", "po-1", "subject", 100L, "CNY", "PENDING", null, null, null, null, Map.of(), null, null, null, null, null);
        when(transactionService.getOrderForUser(1001L, "user-uuid-1001", "ORD-1")).thenReturn(order);

        PaymentOrderDTO result = service.getOrder(1001L, "user-uuid-1001", "ORD-1");

        assertThat(result).isSameAs(order);
        verify(transactionService).getOrderForUser(1001L, "user-uuid-1001", "ORD-1");
    }

    @Test
    void getOrderRejectsInvalidOperatorBeforeServiceLookup() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(mock(SystemInternalApi.class)));

        assertThatThrownBy(() -> service.getOrder(0L, "user-uuid-0", "ORD-1"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).getOrderForUser(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getOrderRejectsDisabledOperatorBeforeServiceLookup() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "alice", "DISABLED"));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));

        assertThatThrownBy(() -> service.getOrder(1001L, "user-uuid-1001", "ORD-1"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).getOrderForUser(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getOrderRejectsMissingOperatorResolverBeforeServiceLookup() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(null));

        assertThatThrownBy(() -> service.getOrder(1001L, "user-uuid-1001", "ORD-1"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).getOrderForUser(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getOrderRejectsBlankOrderNoBeforeServiceLookup() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "alice", "ENABLED"));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));

        assertThatThrownBy(() -> service.getOrder(1001L, "user-uuid-1001", " "))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).getOrderForUser(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createOrderRejectsInvalidOperatorBeforeCreatingOrder() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(mock(SystemInternalApi.class)));

        assertThatThrownBy(() -> service.createOrder(null, "user-uuid-1001", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).createOrder(org.mockito.ArgumentMatchers.any(CurrentUser.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOrderRejectsNullRequestBeforeCreatingOrder() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "alice", "ENABLED"));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));

        assertThatThrownBy(() -> service.createOrder(1001L, "user-uuid-1001", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).createOrder(org.mockito.ArgumentMatchers.any(CurrentUser.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOrderShouldPassTrustedOperatorSnapshotToTransactionService() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "alice", "ENABLED"));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));
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
                null
        );
        PaymentOrderDTO order = new PaymentOrderDTO("ORD-1", "stripe", "po-1", "subject", 100L, "CNY", "PENDING", null, null, null, null, Map.of(), null, null, null, null, null);
        when(transactionService.createOrder(any(CurrentUser.class), any())).thenReturn(order);

        PaymentOrderDTO result = service.createOrder(1001L, "user-uuid-1001", request);

        assertThat(result).isSameAs(order);
        org.mockito.ArgumentCaptor<CurrentUser> userCaptor = org.mockito.ArgumentCaptor.forClass(CurrentUser.class);
        verify(transactionService).createOrder(userCaptor.capture(), org.mockito.ArgumentMatchers.same(request));
        assertThat(userCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
        assertThat(userCaptor.getValue().getUserUuid()).isEqualTo("user-uuid-1001");
        assertThat(userCaptor.getValue().getSessionId()).isEqualTo("internal-payment");
        assertThat(userCaptor.getValue().getSessionVersion()).isEqualTo(1);
        assertThat(userCaptor.getValue().getPermissionsVersion()).isEqualTo("perm-v1001");
        assertThat(userCaptor.getValue().getPermissions()).containsExactlyInAnyOrder("payment:order:create", "payment:order:view");
        assertThat(userCaptor.getValue().getRoleIds()).containsExactly(31L);
        assertThat(userCaptor.getValue().getDeptIds()).containsExactly(41L);
        assertThat(userCaptor.getValue().getDefaultHomePath()).isEqualTo("/payment/orders");
        assertThat(userCaptor.getValue().isAuthenticated()).isTrue();
    }

    @Test
    void createOrderRejectsOperatorSnapshotMissingUserUuidBeforeCreatingOrder() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(new SystemUserSnapshotDTO(1001L, null, "alice", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));

        assertThatThrownBy(() -> service.createOrder(1001L, "user-uuid-1001", mock(PaymentCreateOrderRequestDTO.class)))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).createOrder(org.mockito.ArgumentMatchers.any(CurrentUser.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOrderRejectsOperatorSnapshotWithoutEnabledStatusBeforeCreatingOrder() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "alice", null));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));

        assertThatThrownBy(() -> service.createOrder(1001L, "user-uuid-1001", mock(PaymentCreateOrderRequestDTO.class)))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).createOrder(org.mockito.ArgumentMatchers.any(CurrentUser.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createOrderRejectsOperatorUuidMismatchBeforeCreatingOrder() {
        PaymentTransactionService transactionService = mock(PaymentTransactionService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "alice", "ENABLED"));
        PaymentInternalApiService service = new PaymentInternalApiService(transactionService, provider(systemInternalApi));

        assertThatThrownBy(() -> service.createOrder(1001L, "other-user-uuid", mock(PaymentCreateOrderRequestDTO.class)))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(transactionService, never()).createOrder(org.mockito.ArgumentMatchers.any(CurrentUser.class), org.mockito.ArgumentMatchers.any());
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi) {
        if (systemInternalApi != null) {
            when(systemInternalApi.permissionSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> permissionSnapshot(invocation.getArgument(0, Long.class)));
        }
        ObjectProvider<SystemInternalApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(systemInternalApi);
        return provider;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(Long userId) {
        return new PermissionSnapshotDTO(
                "perm-v" + userId,
                List.of("payment:order:create", "payment:order:view"),
                List.of(31L),
                41L,
                List.of(41L),
                List.of(41L, 42L),
                List.of(),
                "/payment/orders"
        );
    }
}
