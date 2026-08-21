package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentCreateRefundRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentRefundDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.domain.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentTransactionServiceTest {

    @Test
    void listSandboxOrdersShouldReturnOnlyCloudSandboxOrdersNewestFirst() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow row = orderRow(1001L);
        row.setOrderNo("SBX-1783830650013-A6ABA9");
        row.setProviderCode("alipay");
        doReturn(1L).when(jdbcTemplate).queryForObject(any(String.class), eq(Long.class), eq("alipay"));
        doReturn(List.of(row)).when(jdbcTemplate).query(any(String.class), anyOrderRowMapper(), eq("alipay"), eq(10), eq(10));
        PaymentTransactionService service = service(jdbcTemplate);

        PageResponse<PaymentOrderDTO> page = service.listSandboxOrders(currentUser(), 2, 10);

        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(10);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(PaymentOrderDTO::orderNo)
                .containsExactly("SBX-1783830650013-A6ABA9");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), anyOrderRowMapper(), eq("alipay"), eq(10), eq(10));
        assertThat(sqlCaptor.getValue())
                .contains("provider_code = ?")
                .contains("order_no like 'SBX-%'")
                .contains("order by created_at desc, id desc")
                .contains("limit ? offset ?");
    }

    @Test
    void listManualOrdersForUserShouldPersistProductionAndSandboxHistoryForCreator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow row = orderRow(1001L);
        row.setOrderNo("MAN-ALI-P-1785154717636-1B2NDY");
        doReturn(1L).when(jdbcTemplate).queryForObject(
                any(String.class),
                eq(Long.class),
                eq(1001L),
                eq("user-uuid-1001")
        );
        doReturn(List.of(row)).when(jdbcTemplate).query(
                any(String.class),
                anyOrderRowMapper(),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(20),
                eq(20)
        );
        PaymentTransactionService service = service(jdbcTemplate);

        PageResponse<PaymentOrderDTO> page = service.listManualOrdersForUser(currentUser(), 2, 20);

        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(PaymentOrderDTO::orderNo)
                .containsExactly("MAN-ALI-P-1785154717636-1B2NDY");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                anyOrderRowMapper(),
                eq(1001L),
                eq("user-uuid-1001"),
                eq(20),
                eq(20)
        );
        assertThat(sqlCaptor.getValue())
                .contains("created_by = ? and created_by_uuid = ?")
                .contains("order_no like 'MAN-%'")
                .contains("order_no like 'SBX-%'")
                .contains("order by created_at desc, id desc");
    }

    @Test
    void cancelManualPendingOrderForUserShouldCancelOnlyCreatorsPendingManualOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow pending = orderRow(1001L);
        pending.setOrderNo("MAN-ALI-P-1-CANCEL");
        PaymentOrderRow cancelled = orderRow(1001L);
        cancelled.setOrderNo("MAN-ALI-P-1-CANCEL");
        cancelled.setStatus("CANCELLED");
        cancelled.setPaymentUrl(null);
        when(jdbcTemplate.queryForObject(
                any(String.class),
                anyOrderRowMapper(),
                eq("MAN-ALI-P-1-CANCEL"),
                eq(1001L),
                eq("user-uuid-1001")
        )).thenReturn(pending);
        when(jdbcTemplate.queryForObject(
                any(String.class),
                anyOrderRowMapper(),
                eq("MAN-ALI-P-1-CANCEL")
        )).thenReturn(cancelled);
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        PaymentTransactionService service = service(jdbcTemplate);

        PaymentOrderDTO result = service.cancelManualPendingOrderForUser(
                currentUser(),
                "MAN-ALI-P-1-CANCEL"
        );

        assertThat(result.status()).isEqualTo("CANCELLED");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("status = 'CANCELLED'")
                .contains("created_by = ? and created_by_uuid = ?")
                .contains("status in ('CREATED', 'PENDING')");
    }

    @Test
    void cancelManualPendingOrderForUserShouldRejectNonManualOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow pending = orderRow(1001L);
        pending.setOrderNo("REGISTRATION-1");
        when(jdbcTemplate.queryForObject(
                any(String.class),
                anyOrderRowMapper(),
                eq("REGISTRATION-1"),
                eq(1001L),
                eq("user-uuid-1001")
        )).thenReturn(pending);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.cancelManualPendingOrderForUser(
                currentUser(),
                "REGISTRATION-1"
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void getOrderForUserShouldConstrainByCreator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow row = orderRow(1001L);
        when(jdbcTemplate.queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"))).thenReturn(row);
        PaymentTransactionService service = service(jdbcTemplate);

        PaymentOrderDTO order = service.getOrderForUser(currentUser(), "ORD-1");

        assertThat(order.orderNo()).isEqualTo("ORD-1");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        assertThat(sqlCaptor.getValue()).contains("created_by = ? and created_by_uuid = ?");
    }

    @Test
    void getOrderForUserShouldRejectMissingOperator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getOrderForUser(null, "user-uuid-1001", "ORD-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void paymentTransactionServiceShouldNotExposeNumericOnlyUserOperations() {
        assertThat(Arrays.stream(PaymentTransactionService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(PaymentTransactionService.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("(java.lang.Long"))
                .filter(signature -> signature.contains("PaymentCreateOrderRequestDTO")
                        || signature.contains("PaymentCreateRefundRequestDTO")
                        || signature.contains("getOrderForUser(java.lang.Long,java.lang.String)")
                        || signature.contains("getRefundForUser(java.lang.Long,java.lang.String)"))
                .toList())
                .isEmpty();
    }

    @Test
    void getOrderForUserShouldRejectMissingUserUuidBeforeLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getOrderForUser(1001L, null, "ORD-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getOrderForUserShouldRejectUserUuidMismatchBeforeOrderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = enabledSystemInternalApi();
        PaymentTransactionService service = service(jdbcTemplate, systemInternalApi);

        assertThatThrownBy(() -> service.getOrderForUser(1001L, "other-uuid", "ORD-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(systemInternalApi).findTargetUserUuidById(1001L);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getOrderForUserShouldRejectDisabledLookupUserBeforeOrderQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PaymentTransactionService service = service(jdbcTemplate, systemInternalApi);

        assertThatThrownBy(() -> service.getOrderForUser(1001L, "user-uuid-1001", "ORD-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(systemInternalApi).findTargetUserUuidById(1001L);
        verify(jdbcTemplate, never()).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
    }

    @Test
    void createOrderShouldRejectUntrustedUserBeforeProviderLookup() {
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentTransactionService service = service(mock(JdbcTemplate.class), managementAppService);
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

        assertThatThrownBy(() -> service.createOrder(untrustedUser(), request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(managementAppService);
    }

    @Test
    void createOrderShouldRejectNullRequestBeforeProviderLookup() {
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentTransactionService service = service(mock(JdbcTemplate.class), managementAppService);

        assertThatThrownBy(() -> service.createOrder(currentUser(), null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(managementAppService);
    }

    @Test
    void createOrderShouldRejectBlankProviderBeforeProviderLookup() {
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentTransactionService service = service(mock(JdbcTemplate.class), managementAppService);
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                " ",
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

        assertThatThrownBy(() -> service.createOrder(currentUser(), request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(managementAppService);
    }

    @Test
    void createOrderShouldRejectUntrustedRequestContextBeforeProviderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "tester", "DISABLED"));
        PaymentTransactionService service = service(jdbcTemplate, managementAppService, mock(PaymentOutboxService.class), mock(DomainEventPublisher.class), provider(systemInternalApi));

        assertThatThrownBy(() -> service.createOrder(untrustedUser(), orderRequest("ORD-1", "idem-1")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(managementAppService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createOrderShouldRejectWhenLivePermissionsLoseCreatePermissionBeforeProviderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentTransactionService service = service(
                jdbcTemplate,
                managementAppService,
                mock(PaymentOutboxService.class),
                mock(DomainEventPublisher.class),
                provider(enabledSystemInternalApi(), "payment:refund:create")
        );

        assertThatThrownBy(() -> service.createOrder(currentUser("payment:refund:create"), orderRequest("ORD-1", "idem-1")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(managementAppService);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createOrderForTrustedOwnerShouldUseExactOwnerWithoutPaymentManagementPermission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(providerSettings());
        PaymentOrderRow existing = orderRow(1001L);
        doReturn(existing).when(jdbcTemplate).queryForObject(
                any(String.class), anyOrderRowMapper(), eq("idem-1"), eq(1001L), eq("user-uuid-1001")
        );
        PaymentTransactionService service = service(jdbcTemplate, managementAppService);

        PaymentOrderDTO order = service.createOrderForTrustedOwner(
                currentUser("aiadc:registration:pay"),
                orderRequest("ORD-1", "idem-1")
        );

        assertThat(order.orderNo()).isEqualTo("ORD-1");
        verify(jdbcTemplate).queryForObject(
                any(String.class), anyOrderRowMapper(), eq("idem-1"), eq(1001L), eq("user-uuid-1001")
        );
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void trustedOwnerOrderCallsShouldIsolateCaughtFailuresFromCallerTransactions() throws Exception {
        Method createMethod = PaymentTransactionService.class.getMethod(
                "createOrderForTrustedOwner",
                CurrentUser.class,
                PaymentCreateOrderRequestDTO.class
        );
        Method getMethod = PaymentTransactionService.class.getMethod(
                "getOrderForUser",
                Long.class,
                String.class,
                String.class
        );

        assertThat(createMethod.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(getMethod.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void createSandboxOrderShouldRejectNonSandboxProviderBeforeOrderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        var settings = providerSettings();
        settings.setEnvironment("PRODUCTION");
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(settings);
        PaymentTransactionService service = service(jdbcTemplate, managementAppService);

        assertThatThrownBy(() -> service.createSandboxOrder(currentUser(), orderRequest("ORD-1", "idem-1")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BIZ_ERROR);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createOrderShouldScopeIdempotencyLookupToCreator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(providerSettings());
        PaymentOrderRow existing = orderRow(1001L);
        doReturn(existing).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("idem-1"), eq(1001L), eq("user-uuid-1001"));
        PaymentTransactionService service = service(jdbcTemplate, managementAppService);

        PaymentOrderDTO order = service.createOrder(currentUser(), orderRequest("ORD-1", "idem-1"));

        assertThat(order.orderNo()).isEqualTo("ORD-1");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), anyOrderRowMapper(), eq("idem-1"), eq(1001L), eq("user-uuid-1001"));
        assertThat(sqlCaptor.getValue()).contains("idempotency_key = ?").contains("created_by = ? and created_by_uuid = ?");
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void createOrderShouldLeaveProviderTransactionNumberEmptyUntilSuccessfulCallback() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(providerSettings());
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("idem-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        PaymentOrderRow persisted = orderRow(1001L);
        persisted.setProviderOrderNo("");
        doThrow(new EmptyResultDataAccessException(1))
                .doReturn(persisted)
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"));
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        PaymentTransactionService service = service(
                jdbcTemplate,
                managementAppService,
                mock(PaymentOutboxService.class),
                domainEventPublisher
        );

        PaymentOrderDTO order = service.createOrder(currentUser(), orderRequest("ORD-1", "idem-1"));

        assertThat(order.orderNo()).isEqualTo("ORD-1");
        assertThat(order.providerOrderNo()).isNull();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("insert into payment_order");
        assertThat(argsCaptor.getValue()[2]).isEqualTo("");
        assertThat(String.valueOf(argsCaptor.getValue()[12])).doesNotContain("providerOrderNo");
        verify(domainEventPublisher).publishAll(any());
    }

    @Test
    void createBuiltinMockOrderShouldUseInlineCheckoutAdapterWithoutStandaloneRoute() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        BuiltinMockPaymentAvailability availability = mock(BuiltinMockPaymentAvailability.class);
        var settings = providerSettings();
        settings.setProviderCode(BuiltinMockPaymentAvailability.PROVIDER_CODE);
        settings.setEnvironment("SANDBOX");
        when(managementAppService.getRequiredProviderSettings(BuiltinMockPaymentAvailability.PROVIDER_CODE))
                .thenReturn(settings);
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("idem-mock-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("MOCK-1"), eq(1001L), eq("user-uuid-1001"));
        PaymentOrderRow persisted = orderRow(1001L);
        persisted.setOrderNo("MOCK-1");
        persisted.setProviderCode(BuiltinMockPaymentAvailability.PROVIDER_CODE);
        persisted.setPaymentUrl(null);
        doThrow(new EmptyResultDataAccessException(1))
                .doReturn(persisted)
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("MOCK-1"));
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        PaymentTransactionService service = service(
                jdbcTemplate,
                managementAppService,
                mock(PaymentOutboxService.class),
                domainEventPublisher
        );
        service.setBuiltinMockPaymentAvailability(availability);
        PaymentCreateOrderRequestDTO request = new PaymentCreateOrderRequestDTO(
                BuiltinMockPaymentAvailability.PROVIDER_CODE,
                "MOCK-1",
                "subject",
                100L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                "idem-mock-1"
        );

        PaymentOrderDTO order = service.createOrder(currentUser(), request);

        assertThat(order.providerCode()).isEqualTo(BuiltinMockPaymentAvailability.PROVIDER_CODE);
        assertThat(order.paymentUrl()).isNull();
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("insert into payment_order"), argsCaptor.capture());
        assertThat(argsCaptor.getValue()[7]).isNull();
        assertThat(String.valueOf(argsCaptor.getValue()[12])).contains("\"paymentUrl\":null");
        verify(availability).requireEnabledForWrite();
        verify(domainEventPublisher).publishAll(any());
    }

    @Test
    void createOrderShouldRejectOrderNoOwnedByAnotherUser() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(providerSettings());
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("idem-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doReturn(orderRow(2002L)).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"));
        PaymentTransactionService service = service(jdbcTemplate, managementAppService);

        assertThatThrownBy(() -> service.createOrder(currentUser(), orderRequest("ORD-1", "idem-1")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void createOrderShouldRejectWhenInsertMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        when(managementAppService.getRequiredProviderSettings("stripe")).thenReturn(providerSettings());
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("idem-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"));
        doReturn(0).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        PaymentTransactionService service = service(jdbcTemplate, managementAppService, mock(PaymentOutboxService.class), domainEventPublisher);

        assertThatThrownBy(() -> service.createOrder(currentUser(), orderRequest("ORD-1", "idem-1")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BIZ_ERROR);

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    void createRefundShouldConstrainOrderByCreator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = orderRow(1001L);
        order.setStatus("PAID");
        PaymentRefundRow refund = refundRow(1001L);
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        doReturn(order).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .doReturn(refund)
                .when(jdbcTemplate).queryForObject(any(String.class), anyRefundRowMapper(), eq("REF-1"));
        PaymentTransactionService service = service(jdbcTemplate);

        PaymentRefundDTO result = service.createRefund(
                currentUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        );

        assertThat(result.refundNo()).isEqualTo("REF-1");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        assertThat(sqlCaptor.getValue()).contains("created_by = ? and created_by_uuid = ?");
    }

    @Test
    void createRefundShouldConstrainOrderStatusUpdateByCreatorUuid() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = orderRow(1001L);
        order.setStatus("PAID");
        PaymentRefundRow refund = refundRow(1001L);
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        doReturn(order).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .doReturn(refund)
                .when(jdbcTemplate).queryForObject(any(String.class), anyRefundRowMapper(), eq("REF-1"));
        PaymentTransactionService service = service(jdbcTemplate);

        service.createRefund(
                currentUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues())
                .anySatisfy(sql -> assertThat(sql)
                        .contains("update payment_order")
                        .contains("where order_no = ? and created_by = ? and created_by_uuid = ?")
                        .contains("and status = ? and amount_minor = ? and currency = ? and provider_code = ?"));
    }

    @Test
    void createRefundShouldRejectWhenOrderStateSnapshotChanged() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = orderRow(1001L);
        order.setStatus("PAID");
        PaymentRefundRow refund = refundRow(1001L);
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        doReturn(order).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .doReturn(refund)
                .when(jdbcTemplate).queryForObject(any(String.class), anyRefundRowMapper(), eq("REF-1"));
        doReturn(1)
                .doReturn(0)
                .when(jdbcTemplate).update(any(String.class), any(Object[].class));
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createRefund(
                currentUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BIZ_ERROR);
    }

    @Test
    void createRefundShouldRejectWhenInsertMissesBeforeOrderStateWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = orderRow(1001L);
        order.setStatus("PAID");
        doReturn(order).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate).queryForObject(any(String.class), anyRefundRowMapper(), eq("REF-1"));
        doReturn(0).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createRefund(
                currentUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BIZ_ERROR);

        verify(jdbcTemplate, times(1)).update(any(String.class), any(Object[].class));
    }

    @Test
    void builtinMockPartialRefundShouldRemainPaidAndCompleteSynchronously() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentOrderRow order = builtinMockPaidOrder();
        PaymentRefundRow completed = builtinMockRefund("REF-MOCK-1", 40L);
        stubBuiltinMockRefundPersistence(jdbcTemplate, order, completed, 0L);
        PaymentTransactionService service = service(
                jdbcTemplate,
                mock(PaymentManagementAppService.class),
                outboxService
        );

        PaymentRefundDTO result = service.createRefund(
                currentUser(),
                order.getOrderNo(),
                new PaymentCreateRefundRequestDTO("REF-MOCK-1", 40L, "CNY", "partial", Map.of(), null)
        );

        assertThat(result.status()).isEqualTo("REFUNDED");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(sqlCaptor.capture(), argsCaptor.capture());
        int orderUpdateIndex = sqlCaptor.getAllValues().get(0).contains("update payment_order") ? 0 : 1;
        assertThat(argsCaptor.getAllValues().get(orderUpdateIndex)[0]).isEqualTo("PAID");
        verify(outboxService).record(
                eq(1001L),
                eq("payment"),
                eq("payment.refund.completed"),
                eq("REF-MOCK-1"),
                any()
        );
    }

    @Test
    void builtinMockCumulativeFullRefundShouldCloseOrderAsRefunded() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = builtinMockPaidOrder();
        PaymentRefundRow completed = builtinMockRefund("REF-MOCK-2", 60L);
        stubBuiltinMockRefundPersistence(jdbcTemplate, order, completed, 40L);
        PaymentTransactionService service = service(jdbcTemplate);

        PaymentRefundDTO result = service.createRefund(
                currentUser(),
                order.getOrderNo(),
                new PaymentCreateRefundRequestDTO("REF-MOCK-2", 60L, "CNY", "remaining", Map.of(), null)
        );

        assertThat(result.status()).isEqualTo("REFUNDED");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(sqlCaptor.capture(), argsCaptor.capture());
        int orderUpdateIndex = sqlCaptor.getAllValues().get(0).contains("update payment_order") ? 0 : 1;
        assertThat(argsCaptor.getAllValues().get(orderUpdateIndex)[0]).isEqualTo("REFUNDED");
    }

    @Test
    void builtinMockRefundShouldRejectCumulativeAmountAbovePaidAmount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = builtinMockPaidOrder();
        stubBuiltinMockRefundPersistence(jdbcTemplate, order, null, 70L);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createRefund(
                currentUser(),
                order.getOrderNo(),
                new PaymentCreateRefundRequestDTO("REF-MOCK-3", 40L, "CNY", "too much", Map.of(), null)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void builtinMockRefundShouldReturnExistingRefundNumberIdempotently() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = builtinMockPaidOrder();
        PaymentRefundRow completed = builtinMockRefund("REF-MOCK-4", 40L);
        doReturn(order).when(jdbcTemplate).queryForObject(
                any(String.class),
                anyOrderRowMapper(),
                eq(order.getOrderNo()),
                eq(1001L),
                eq("user-uuid-1001")
        );
        doReturn(completed).when(jdbcTemplate).queryForObject(
                any(String.class),
                anyRefundRowMapper(),
                eq("REF-MOCK-4"),
                eq(1001L),
                eq("user-uuid-1001")
        );
        PaymentTransactionService service = service(jdbcTemplate);

        PaymentRefundDTO result = service.createRefund(
                currentUser(),
                order.getOrderNo(),
                new PaymentCreateRefundRequestDTO("REF-MOCK-4", 40L, "CNY", "retry", Map.of(), null)
        );

        assertThat(result.refundNo()).isEqualTo("REF-MOCK-4");
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void createRefundShouldRejectUntrustedUserBeforeOrderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createRefund(
                untrustedUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createRefundShouldRejectWhenLivePermissionsLoseRefundCreatePermissionBeforeOrderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(
                jdbcTemplate,
                mock(PaymentManagementAppService.class),
                mock(PaymentOutboxService.class),
                mock(DomainEventPublisher.class),
                provider(enabledSystemInternalApi(), "payment:order:view")
        );

        assertThatThrownBy(() -> service.createRefund(
                currentUser("payment:order:view"),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createRefundShouldRejectNullRequestBeforeOrderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createRefund(currentUser(), "ORD-1", null))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createRefundShouldRejectBlankOrderNoBeforeOrderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createRefund(
                currentUser(),
                " ",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createRefundShouldRejectInvalidRequestBeforeOrderLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.createRefund(
                currentUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 0L, "CNY", "duplicate", Map.of(), null)
        ))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getRefundForUserShouldConstrainByCreator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentRefundRow refund = refundRow(1001L);
        when(jdbcTemplate.queryForObject(any(String.class), anyRefundRowMapper(), eq("REF-1"), eq(1001L), eq("user-uuid-1001"))).thenReturn(refund);
        PaymentTransactionService service = service(jdbcTemplate);

        PaymentRefundDTO result = service.getRefundForUser(currentUser(), "REF-1");

        assertThat(result.refundNo()).isEqualTo("REF-1");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), anyRefundRowMapper(), eq("REF-1"), eq(1001L), eq("user-uuid-1001"));
        assertThat(sqlCaptor.getValue()).contains("created_by = ? and created_by_uuid = ?");
    }

    @Test
    void getRefundForUserShouldRejectMissingOperatorBeforeQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getRefundForUser(0L, "user-uuid-0", "REF-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getRefundForUserShouldRejectMissingUserUuidBeforeLookup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(jdbcTemplate);

        assertThatThrownBy(() -> service.getRefundForUser(1001L, null, "REF-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getRefundForUserShouldRejectDisabledLookupUserBeforeRefundQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PaymentTransactionService service = service(jdbcTemplate, systemInternalApi);

        assertThatThrownBy(() -> service.getRefundForUser(1001L, "user-uuid-1001", "REF-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(systemInternalApi).findTargetUserUuidById(1001L);
        verify(jdbcTemplate, never()).queryForObject(any(String.class), anyRefundRowMapper(), eq("REF-1"), eq(1001L), eq("user-uuid-1001"));
    }

    @Test
    void getOrderForUserShouldRejectWhenLivePermissionsLoseViewPermissionBeforeQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(
                jdbcTemplate,
                mock(PaymentManagementAppService.class),
                mock(PaymentOutboxService.class),
                mock(DomainEventPublisher.class),
                provider(enabledSystemInternalApi(), "payment:refund:create")
        );

        assertThatThrownBy(() -> service.getOrderForUser(currentUser("payment:refund:create"), "ORD-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getRefundForUserShouldRejectWhenLivePermissionsLoseRefundViewPermissionBeforeQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentTransactionService service = service(
                jdbcTemplate,
                mock(PaymentManagementAppService.class),
                mock(PaymentOutboxService.class),
                mock(DomainEventPublisher.class),
                provider(enabledSystemInternalApi(), "payment:refund:create")
        );

        assertThatThrownBy(() -> service.getRefundForUser(currentUser("payment:refund:create"), "REF-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createRefundShouldScopeIdempotencyLookupToCreator() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOrderRow order = orderRow(1001L);
        order.setStatus("PAID");
        PaymentRefundRow refund = refundRow(1001L);
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        doReturn(order).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doReturn(refund).when(jdbcTemplate).queryForObject(any(String.class), anyRefundRowMapper(), eq("idem-ref-1"), eq(1001L), eq("user-uuid-1001"));
        PaymentTransactionService service = service(jdbcTemplate);

        PaymentRefundDTO result = service.createRefund(
                currentUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), "idem-ref-1")
        );

        assertThat(result.refundNo()).isEqualTo("REF-1");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), anyRefundRowMapper(), eq("idem-ref-1"), eq(1001L), eq("user-uuid-1001"));
        assertThat(sqlCaptor.getValue()).contains("idempotency_key = ?").contains("created_by = ? and created_by_uuid = ?");
        verify(jdbcTemplate, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    void createRefundShouldIncludeTrustedUserUuidInOutboxPayload() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentOrderRow order = orderRow(1001L);
        order.setStatus("PAID");
        PaymentRefundRow refund = refundRow(1001L);
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
        doReturn(order).when(jdbcTemplate).queryForObject(any(String.class), anyOrderRowMapper(), eq("ORD-1"), eq(1001L), eq("user-uuid-1001"));
        doThrow(new EmptyResultDataAccessException(1))
                .doReturn(refund)
                .when(jdbcTemplate).queryForObject(any(String.class), anyRefundRowMapper(), eq("REF-1"));
        PaymentTransactionService service = service(jdbcTemplate, mock(PaymentManagementAppService.class), outboxService);

        service.createRefund(
                currentUser(),
                "ORD-1",
                new PaymentCreateRefundRequestDTO("REF-1", 50L, "CNY", "duplicate", Map.of(), null)
        );

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).record(
                eq(1001L),
                eq("payment"),
                eq("payment.refund.created"),
                eq("REF-1"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOfSatisfying(Map.class, payload ->
                        assertThat(payload).containsEntry("userUuid", "user-uuid-1001"));
    }

    private PaymentTransactionService service(JdbcTemplate jdbcTemplate) {
        return service(jdbcTemplate, mock(PaymentManagementAppService.class));
    }

    private PaymentTransactionService service(JdbcTemplate jdbcTemplate, SystemInternalApi systemInternalApi) {
        return service(
                jdbcTemplate,
                mock(PaymentManagementAppService.class),
                mock(PaymentOutboxService.class),
                mock(DomainEventPublisher.class),
                provider(systemInternalApi)
        );
    }

    private PaymentTransactionService service(JdbcTemplate jdbcTemplate, PaymentManagementAppService managementAppService) {
        return service(jdbcTemplate, managementAppService, mock(PaymentOutboxService.class));
    }

    private PaymentTransactionService service(JdbcTemplate jdbcTemplate, PaymentManagementAppService managementAppService, PaymentOutboxService outboxService) {
        return service(jdbcTemplate, managementAppService, outboxService, mock(DomainEventPublisher.class));
    }

    private PaymentTransactionService service(JdbcTemplate jdbcTemplate, PaymentManagementAppService managementAppService, PaymentOutboxService outboxService, DomainEventPublisher domainEventPublisher) {
        return service(jdbcTemplate, managementAppService, outboxService, domainEventPublisher, provider(enabledSystemInternalApi()));
    }

    private PaymentTransactionService service(
            JdbcTemplate jdbcTemplate,
            PaymentManagementAppService managementAppService,
            PaymentOutboxService outboxService,
            DomainEventPublisher domainEventPublisher,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        return new PaymentTransactionService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                new PaymentProviderCatalog(),
                outboxService,
                domainEventPublisher,
                systemInternalApiProvider
        );
    }

    private CurrentUser currentUser() {
        return currentUser("payment:order:create", "payment:order:view", "payment:refund:create", "payment:refund:view");
    }

    private CurrentUser currentUser(String... permissions) {
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "tester",
                "session-1",
                1,
                true,
                Set.of(permissions)
        );
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private SystemInternalApi enabledSystemInternalApi() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "tester", "ENABLED"));
        when(systemInternalApi.findTargetUserUuidById(1001L)).thenReturn("user-uuid-1001");
        return systemInternalApi;
    }

    private CurrentUser untrustedUser() {
        return new CurrentUser(
                1001L,
                "tester",
                "session-1",
                1,
                true,
                Set.of("payment:order:create", "payment:order:view", "payment:refund:create", "payment:refund:view")
        );
    }

    private PaymentCreateOrderRequestDTO orderRequest(String orderNo, String idempotencyKey) {
        return new PaymentCreateOrderRequestDTO(
                "stripe",
                orderNo,
                "subject",
                100L,
                "CNY",
                null,
                null,
                null,
                Map.of(),
                idempotencyKey
        );
    }

    private com.lumira.api.payment.PaymentProviderSettingsDTO providerSettings() {
        com.lumira.api.payment.PaymentProviderSettingsDTO settings = new com.lumira.api.payment.PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        settings.setEnabled(true);
        settings.setCurrency("CNY");
        return settings;
    }

    private PaymentOrderRow orderRow(Long createdBy) {
        PaymentOrderRow row = new PaymentOrderRow();
        row.setOrderNo("ORD-1");
        row.setProviderCode("stripe");
        row.setProviderOrderNo("po-1");
        row.setSubject("subject");
        row.setAmountMinor(100L);
        row.setCurrency("CNY");
        row.setStatus("PENDING");
        row.setRequestJson("{}");
        row.setCreatedBy(createdBy);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setDeleted(0);
        return row;
    }

    private PaymentRefundRow refundRow(Long createdBy) {
        PaymentRefundRow row = new PaymentRefundRow();
        row.setRefundNo("REF-1");
        row.setOrderNo("ORD-1");
        row.setProviderCode("stripe");
        row.setProviderRefundNo("pr-1");
        row.setAmountMinor(50L);
        row.setCurrency("CNY");
        row.setStatus("PENDING");
        row.setRequestJson("{}");
        row.setCreatedBy(createdBy);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setDeleted(0);
        return row;
    }

    private PaymentOrderRow builtinMockPaidOrder() {
        PaymentOrderRow row = orderRow(1001L);
        row.setId(77L);
        row.setOrderNo("REG-1001");
        row.setProviderCode(BuiltinMockPaymentAvailability.PROVIDER_CODE);
        row.setAmountMinor(100L);
        row.setStatus("PAID");
        row.setCreatedByUuid("user-uuid-1001");
        return row;
    }

    private PaymentRefundRow builtinMockRefund(String refundNo, long amountMinor) {
        PaymentRefundRow row = refundRow(1001L);
        row.setRefundNo(refundNo);
        row.setOrderNo("REG-1001");
        row.setProviderCode(BuiltinMockPaymentAvailability.PROVIDER_CODE);
        row.setProviderRefundNo("builtin_mock-refund-" + refundNo);
        row.setAmountMinor(amountMinor);
        row.setStatus("REFUNDED");
        row.setCreatedByUuid("user-uuid-1001");
        row.setRefundedAt(LocalDateTime.now());
        return row;
    }

    private void stubBuiltinMockRefundPersistence(
            JdbcTemplate jdbcTemplate,
            PaymentOrderRow order,
            PaymentRefundRow completed,
            long alreadyRefunded
    ) {
        doReturn(order).when(jdbcTemplate).queryForObject(
                any(String.class),
                anyOrderRowMapper(),
                eq(order.getOrderNo()),
                eq(1001L),
                eq("user-uuid-1001")
        );
        doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                any(String.class),
                anyRefundRowMapper(),
                ArgumentMatchers.startsWith("REF-MOCK-"),
                eq(1001L),
                eq("user-uuid-1001")
        );
        if (completed == null) {
            doThrow(new EmptyResultDataAccessException(1)).when(jdbcTemplate).queryForObject(
                    any(String.class),
                    anyRefundRowMapper(),
                    ArgumentMatchers.startsWith("REF-MOCK-")
            );
        } else {
            doThrow(new EmptyResultDataAccessException(1))
                    .doReturn(completed)
                    .when(jdbcTemplate).queryForObject(
                            any(String.class),
                            anyRefundRowMapper(),
                            eq(completed.getRefundNo())
                    );
        }
        doReturn(alreadyRefunded).when(jdbcTemplate).queryForObject(
                contains("coalesce(sum(amount_minor), 0)"),
                eq(Long.class),
                eq(order.getOrderNo()),
                eq(BuiltinMockPaymentAvailability.PROVIDER_CODE),
                eq(1001L),
                eq("user-uuid-1001")
        );
        doReturn(1).when(jdbcTemplate).update(any(String.class), any(Object[].class));
    }

    private BeanPropertyRowMapper<PaymentOrderRow> anyOrderRowMapper() {
        return any();
    }

    private BeanPropertyRowMapper<PaymentRefundRow> anyRefundRowMapper() {
        return any();
    }

    private ObjectProvider<SystemInternalApi> provider(SystemInternalApi systemInternalApi, String... permissions) {
        if (systemInternalApi != null) {
            when(systemInternalApi.permissionSnapshot(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> permissionSnapshot(invocation.getArgument(0, Long.class), permissions));
        }
        ObjectProvider<SystemInternalApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(systemInternalApi);
        return provider;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(Long userId, String... permissions) {
        return new PermissionSnapshotDTO(
                "perm-v" + userId,
                permissions == null || permissions.length == 0
                        ? List.of("payment:order:create", "payment:order:view", "payment:refund:create", "payment:refund:view")
                        : List.of(permissions),
                List.of(31L),
                41L,
                List.of(41L),
                List.of(41L, 42L),
                List.of(),
                "/payment/orders"
        );
    }
}
