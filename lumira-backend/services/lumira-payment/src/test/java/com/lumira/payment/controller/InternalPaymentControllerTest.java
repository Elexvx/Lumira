package com.lumira.payment.controller;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.payment.service.PaymentInternalApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalPaymentControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalApiShouldNotExposeProviderManagementMethods() {
        assertThat(Arrays.stream(PaymentInternalApi.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.toLowerCase().contains("provider")))
                .isEmpty();
    }

    @Test
    void internalControllerShouldNotExposeProviderManagementRoutes() {
        assertThat(Arrays.stream(InternalPaymentController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PutMapping.class)
                        || Arrays.stream(method.getAnnotationsByType(GetMapping.class))
                        .anyMatch(mapping -> Arrays.stream(mapping.value()).anyMatch(value -> value.contains("providers"))))
                .map(java.lang.reflect.Method::getName))
                .isEmpty();
    }

    @Test
    void createOrderDelegatesToLocalInternalApiService() {
        PaymentInternalApiService paymentInternalApiService = mock(PaymentInternalApiService.class);
        InternalPaymentController controller = new InternalPaymentController(paymentInternalApiService);
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
        authenticateInternalService();
        when(paymentInternalApiService.createOrder(1001L, "user-uuid-1001", 9L, request)).thenReturn(order);

        PaymentOrderDTO result = controller.createOrder(1001L, "user-uuid-1001", 9L, request);

        assertThat(result).isSameAs(order);
        verify(paymentInternalApiService).createOrder(1001L, "user-uuid-1001", 9L, request);
    }

    @Test
    void getOrderRejectsMissingInternalServicePrincipalBeforeServiceLookup() {
        PaymentInternalApiService paymentInternalApiService = mock(PaymentInternalApiService.class);
        InternalPaymentController controller = new InternalPaymentController(paymentInternalApiService);

        assertThatThrownBy(() -> controller.getOrder(1001L, "user-uuid-1001", null, "ORD-1"))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(paymentInternalApiService, never()).getOrder(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private void authenticateInternalService() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", "internal", 0, false, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(internalService, "internal-token", java.util.Set.of())
        );
    }
}
