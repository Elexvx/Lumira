package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.BuiltinMockPaymentCheckoutDTO;
import com.lumira.api.payment.BuiltinMockPaymentSimulationRequestDTO;
import com.lumira.api.payment.BuiltinMockPaymentSimulationResultDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuiltinMockPaymentServiceTest {

    @Test
    void checkoutShouldBeOwnerScopedAndExposeOnlyServerControlledOptions() {
        Fixture fixture = fixture();
        PaymentOrderRow order = order();
        stubOwnedOrder(fixture.jdbcTemplate(), order);

        BuiltinMockPaymentCheckoutDTO checkout = fixture.service().checkout(currentUser(), order.getOrderNo());

        assertThat(checkout.orderNo()).isEqualTo(order.getOrderNo());
        assertThat(checkout.tradeStatus()).isEqualTo("WAIT_BUYER_PAY");
        assertThat(checkout.allowedOutcomes()).containsExactly("SUCCESS", "FAILURE", "CANCEL", "TIMEOUT");
        assertThat(checkout.delayOptions()).containsExactly(0, 5, 30, 60, 120, 300);
        assertThat(checkout.returnUrl()).isEqualTo("/competitions/register/payment-result?registrationId=81");
        verify(fixture.jdbcTemplate()).query(
                contains("created_by = ? and created_by_uuid = ?"),
                any(BeanPropertyRowMapper.class),
                eq(order.getOrderNo()),
                eq(1001L),
                eq("user-uuid-1001")
        );
    }

    @Test
    void immediateSuccessShouldReachTheWebhookWithoutDirectlyChangingTheOrder() {
        Fixture fixture = fixture();
        PaymentOrderRow order = order();
        stubOwnedOrder(fixture.jdbcTemplate(), order);
        doReturn(List.of(order)).when(fixture.jdbcTemplate()).query(
                contains("provider_code = ?"),
                any(BeanPropertyRowMapper.class),
                eq(order.getOrderNo()),
                eq(BuiltinMockPaymentAvailability.PROVIDER_CODE)
        );
        when(fixture.jdbcTemplate().update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            return sql.contains("payment_builtin_mock_callback") ? 1 : 0;
        });
        doAnswer(invocation -> {
            BuiltinMockPaymentService.CallbackRow callback = new BuiltinMockPaymentService.CallbackRow();
            callback.setId(901L);
            callback.setNotifyId(invocation.getArgument(2, String.class));
            callback.setOrderNo(order.getOrderNo());
            callback.setProviderTradeNo("mock-trade-901");
            callback.setOutcome("SUCCESS");
            callback.setTradeStatus("TRADE_SUCCESS");
            callback.setStatus("PROCESSING");
            callback.setScheduledAt(LocalDateTime.now());
            callback.setRetryCount(0);
            callback.setMaxRetry(8);
            callback.setClaimToken(invocation.getArgument(3, String.class));
            return callback;
        }).when(fixture.jdbcTemplate()).queryForObject(
                contains("from payment_builtin_mock_callback"),
                any(BeanPropertyRowMapper.class),
                anyString(),
                anyString()
        );
        when(fixture.paymentManagementAppService().getRequiredProviderSettings("builtin_mock"))
                .thenReturn(managedSettings());

        BuiltinMockPaymentSimulationResultDTO result = fixture.service().simulate(
                currentUser(),
                order.getOrderNo(),
                new BuiltinMockPaymentSimulationRequestDTO("SUCCESS", 0)
        );

        assertThat(result.order().status()).isEqualTo("PENDING");
        assertThat(result.outcome()).isEqualTo("SUCCESS");
        verify(fixture.paymentWebhookService()).handleWebhook(
                eq("builtin_mock"),
                contains("trade_status=TRADE_SUCCESS"),
                eq(Map.of())
        );
        verify(fixture.availability(), times(2)).requireEnabledForWrite();
        verify(fixture.jdbcTemplate()).update(
                contains("set status = 'DELIVERED'"),
                any(Object[].class)
        );
        verify(fixture.jdbcTemplate(), never()).update(contains("update payment_order"), any(Object[].class));
    }

    @Test
    void simulationShouldRejectDelayOutsideTheServerLimitBeforeQueueing() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service().simulate(
                currentUser(),
                "REG-MOCK-81",
                new BuiltinMockPaymentSimulationRequestDTO("SUCCESS", 301)
        ))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(fixture.jdbcTemplate(), never()).update(anyString(), any(Object[].class));
    }

    @Test
    void delayedSimulationShouldPersistWithoutCallingTheWebhookInline() {
        Fixture fixture = fixture();
        PaymentOrderRow order = order();
        stubOwnedOrder(fixture.jdbcTemplate(), order);
        when(fixture.jdbcTemplate().update(anyString(), any(Object[].class))).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).contains("insert into payment_builtin_mock_callback") ? 1 : 0
        );

        BuiltinMockPaymentSimulationResultDTO result = fixture.service().simulate(
                currentUser(),
                order.getOrderNo(),
                new BuiltinMockPaymentSimulationRequestDTO("TIMEOUT", 30)
        );

        assertThat(result.outcome()).isEqualTo("TIMEOUT");
        assertThat(result.callbackStatus()).isEqualTo("PENDING");
        assertThat(result.notifyId()).startsWith("mock-notify-");
        assertThat(result.scheduledAt()).isAfter(LocalDateTime.now().plusSeconds(20));
        verify(fixture.jdbcTemplate()).update(
                contains("insert into payment_builtin_mock_callback"),
                any(Object[].class)
        );
        verify(fixture.paymentWebhookService(), never()).handleWebhook(anyString(), anyString(), any());
    }

    @Test
    void repeatedSimulationWithTheSameOutcomeShouldReturnTheStableCallback() {
        Fixture fixture = fixture();
        PaymentOrderRow order = order();
        stubOwnedOrder(fixture.jdbcTemplate(), order);
        LocalDateTime scheduledAt = LocalDateTime.now().plusSeconds(30);
        doReturn(List.of(Map.of(
                "notify_id", "mock-notify-stable",
                "outcome", "SUCCESS",
                "status", "PENDING",
                "scheduled_at", scheduledAt
        ))).when(fixture.jdbcTemplate()).queryForList(
                contains("from payment_builtin_mock_callback"),
                any(Object[].class)
        );

        BuiltinMockPaymentSimulationResultDTO result = fixture.service().simulate(
                currentUser(),
                order.getOrderNo(),
                new BuiltinMockPaymentSimulationRequestDTO("SUCCESS", 30)
        );

        assertThat(result.notifyId()).isEqualTo("mock-notify-stable");
        assertThat(result.scheduledAt()).isEqualTo(scheduledAt);
        verify(fixture.jdbcTemplate(), never()).update(
                contains("insert into payment_builtin_mock_callback"),
                any(Object[].class)
        );
        verify(fixture.paymentWebhookService(), never()).handleWebhook(anyString(), anyString(), any());
    }

    @Test
    void dueCallbackFailureShouldReturnTheDurableClaimToTheRetryQueue() {
        Fixture fixture = fixture();
        PaymentOrderRow order = order();
        BuiltinMockPaymentService.CallbackRow callback = new BuiltinMockPaymentService.CallbackRow();
        callback.setId(902L);
        callback.setNotifyId("mock-notify-902");
        callback.setOrderNo(order.getOrderNo());
        callback.setProviderTradeNo("mock-trade-902");
        callback.setOutcome("SUCCESS");
        callback.setTradeStatus("TRADE_SUCCESS");
        callback.setStatus("PROCESSING");
        callback.setScheduledAt(LocalDateTime.now().minusSeconds(5));
        callback.setRetryCount(0);
        callback.setMaxRetry(8);
        callback.setClaimToken("claim-902");
        when(fixture.jdbcTemplate().update(anyString(), any(Object[].class))).thenReturn(1);
        doReturn(List.of(callback)).when(fixture.jdbcTemplate()).query(
                contains("status = 'PROCESSING' and claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        doReturn(List.of(order)).when(fixture.jdbcTemplate()).query(
                contains("provider_code = ?"),
                any(BeanPropertyRowMapper.class),
                eq(order.getOrderNo()),
                eq(BuiltinMockPaymentAvailability.PROVIDER_CODE)
        );
        when(fixture.paymentManagementAppService().getRequiredProviderSettings("builtin_mock"))
                .thenReturn(managedSettings());
        doThrow(new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "temporary callback failure"))
                .when(fixture.paymentWebhookService())
                .handleWebhook(eq("builtin_mock"), anyString(), eq(Map.of()));

        assertThat(fixture.service().dispatchDueCallbacks(10)).isZero();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.jdbcTemplate(), times(2)).update(sqlCaptor.capture(), any(Object[].class));
        String claimSql = sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("where id in"))
                .findFirst()
                .orElseThrow();
        assertThat(claimSql)
                .contains("where id in")
                .contains("and deleted = 0")
                .contains("status in ('PENDING', 'RETRY')")
                .contains("status = 'PROCESSING' and claim_expires_at < ?");
        verify(fixture.jdbcTemplate()).update(
                contains("set status = ?, retry_count = ?"),
                any(Object[].class)
        );
    }

    @Test
    void pluginDisableShouldCancelOnlyPendingCallbacksAndUnpaidOrders() {
        Fixture fixture = fixture();
        when(fixture.jdbcTemplate().update(anyString(), any(Object[].class))).thenReturn(1);

        fixture.service().cancelPendingForPluginDisable(1001L, "user-uuid-1001");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.jdbcTemplate(), org.mockito.Mockito.times(2))
                .update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues().get(0))
                .contains("status in ('PENDING', 'RETRY', 'PROCESSING')")
                .doesNotContain("DELIVERED");
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("provider_code = ?")
                .contains("status in ('CREATED', 'PENDING')")
                .doesNotContain("PAID", "REFUNDED");
    }

    private Fixture fixture() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentManagementAppService managementAppService = mock(PaymentManagementAppService.class);
        PaymentWebhookService webhookService = mock(PaymentWebhookService.class);
        BuiltinMockPaymentAvailability availability = mock(BuiltinMockPaymentAvailability.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(availability.isEnabled()).thenReturn(true);
        BuiltinMockPaymentService service = new BuiltinMockPaymentService(
                jdbcTemplate,
                new ObjectMapper(),
                managementAppService,
                webhookService,
                availability,
                transactionManager
        );
        return new Fixture(service, jdbcTemplate, managementAppService, webhookService, availability);
    }

    private void stubOwnedOrder(JdbcTemplate jdbcTemplate, PaymentOrderRow order) {
        doReturn(List.of(order)).when(jdbcTemplate).query(
                contains("created_by = ? and created_by_uuid = ?"),
                any(BeanPropertyRowMapper.class),
                eq(order.getOrderNo()),
                eq(1001L),
                eq("user-uuid-1001")
        );
        doReturn(List.of()).when(jdbcTemplate).query(
                contains("status = 'PROCESSING' and claim_token = ?"),
                any(BeanPropertyRowMapper.class),
                anyString()
        );
        doReturn(List.of()).when(jdbcTemplate).queryForList(
                contains("from payment_builtin_mock_callback"),
                any(Object[].class)
        );
    }

    private PaymentOrderRow order() {
        PaymentOrderRow order = new PaymentOrderRow();
        order.setId(81L);
        order.setOrderNo("REG-MOCK-81");
        order.setProviderCode(BuiltinMockPaymentAvailability.PROVIDER_CODE);
        order.setProviderOrderNo("mock-pending-81");
        order.setSubject("Competition registration");
        order.setAmountMinor(8800L);
        order.setCurrency("CNY");
        order.setStatus("PENDING");
        order.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        order.setReturnUrl("https://lumira.example/competitions/register/payment-result?registrationId=81");
        order.setRequestJson("{}");
        order.setCreatedBy(1001L);
        order.setCreatedByUuid("user-uuid-1001");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setDeleted(0);
        return order;
    }

    private PaymentProviderSettingsDTO managedSettings() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
            settings.setProviderCode("builtin_mock");
            settings.setAppId("lumira-builtin-mock-test");
            settings.setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            settings.setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            settings.setEnabled(true);
            settings.setConfigured(true);
            return settings;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "tester",
                null,
                "session-1",
                1,
                true,
                Set.of("aiadc:registration:pay")
        );
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private record Fixture(
            BuiltinMockPaymentService service,
            JdbcTemplate jdbcTemplate,
            PaymentManagementAppService paymentManagementAppService,
            PaymentWebhookService paymentWebhookService,
            BuiltinMockPaymentAvailability availability
    ) {
    }
}
