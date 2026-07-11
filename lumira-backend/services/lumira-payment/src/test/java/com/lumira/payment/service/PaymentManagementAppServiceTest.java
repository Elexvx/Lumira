package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentManagementAppServiceTest {

    @Test
    void updateProviderSettingsShouldUseUniqueOutboxEventKeyPerUpdate() {
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        InsertSuccessJdbcTemplate jdbcTemplate = new InsertSuccessJdbcTemplate();
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );

        service.updatePaymentProviderSettings(currentUser(), "stripe", stripeSettings("first-secret"));
        service.updatePaymentProviderSettings(currentUser(), "stripe", stripeSettings("second-secret"));

        ArgumentCaptor<String> eventKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService, times(2)).recordAfterCommit(
                eq(1001L),
                eq("payment"),
                eq("payment.provider.updated"),
                eventKeyCaptor.capture(),
                any()
        );
        List<String> eventKeys = eventKeyCaptor.getAllValues();
        assertThat(eventKeys).hasSize(2);
        assertThat(eventKeys).allSatisfy(eventKey -> assertThat(eventKey).startsWith("stripe:"));
        assertThat(eventKeys.get(0)).isNotEqualTo(eventKeys.get(1));
    }

    @Test
    void updateProviderSettingsShouldIncludeTrustedUserUuidInOutboxPayload() {
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        InsertSuccessJdbcTemplate jdbcTemplate = new InsertSuccessJdbcTemplate();
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );

        service.updatePaymentProviderSettings(currentUser(), "stripe", stripeSettings("secret"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).recordAfterCommit(
                eq(1001L),
                eq("payment"),
                eq("payment.provider.updated"),
                anyString(),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue())
                .isInstanceOfSatisfying(Map.class, payload ->
                        assertThat(payload).containsEntry("userUuid", "user-uuid-1001"));
    }

    @Test
    void updateProviderSettingsShouldRejectUntrustedRequestContextBeforePersisting() {
        InsertSuccessJdbcTemplate jdbcTemplate = new InsertSuccessJdbcTemplate();
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "admin", "DISABLED"));
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                outboxService,
                provider(systemInternalApi)
        );

        CurrentUser untrusted = currentUser();
        untrusted.setUserUuid(null);
        assertThatThrownBy(() -> service.updatePaymentProviderSettings(untrusted, "stripe", stripeSettings("secret")))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verifyNoInteractions(outboxService);
    }

    @Test
    void listProviderSettingsShouldRejectWhenLivePermissionsLoseViewPermission() {
        PaymentManagementAppService service = new PaymentManagementAppService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class),
                provider(enabledSystemInternalApi(), "payment:config:update")
        );

        assertThatThrownBy(() -> service.listProviderSettings(currentUser("payment:config:update")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void paymentProviderSettingsShouldRejectWhenLivePermissionsLoseViewPermission() {
        PaymentManagementAppService service = new PaymentManagementAppService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class),
                provider(enabledSystemInternalApi(), "payment:config:update")
        );

        assertThatThrownBy(() -> service.paymentProviderSettings(currentUser("payment:config:update"), "stripe"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateProviderSettingsShouldRejectWhenLivePermissionsLoseManagePermissionBeforePersisting() {
        InsertSuccessJdbcTemplate jdbcTemplate = new InsertSuccessJdbcTemplate();
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi(), "payment:config:view")
        );

        assertThatThrownBy(() -> service.updatePaymentProviderSettings(currentUser("payment:config:view"), "stripe", stripeSettings("secret")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(jdbcTemplate.lastUpdateSql).isNull();
        verifyNoInteractions(outboxService);
    }

    @Test
    void testPaymentProviderShouldRejectWhenLivePermissionsLoseManagePermissionBeforeResultWrite() {
        PaymentManagementAppService service = new PaymentManagementAppService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class),
                provider(enabledSystemInternalApi(), "payment:config:update")
        );

        assertThatThrownBy(() -> service.testPaymentProvider(currentUser("payment:config:update"), "stripe"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void listProviderSettingsShouldRejectLegacySettingsViewPermission() {
        PaymentManagementAppService service = new PaymentManagementAppService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class),
                provider(enabledSystemInternalApi(), "payment:settings:view")
        );

        assertThatThrownBy(() -> service.listProviderSettings(currentUser("payment:settings:view")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateProviderSettingsShouldRejectLegacySettingsManagePermission() {
        PaymentManagementAppService service = new PaymentManagementAppService(
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                mock(PaymentConfigCryptoService.class),
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class),
                provider(enabledSystemInternalApi(), "payment:settings:manage")
        );

        assertThatThrownBy(() -> service.updatePaymentProviderSettings(currentUser("payment:settings:manage"), "stripe", stripeSettings("secret")))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateProviderSettingsShouldPersistTrustedUserUuidInProviderConfig() {
        InsertSuccessJdbcTemplate jdbcTemplate = new InsertSuccessJdbcTemplate();
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        doReturn("encrypted").when(cryptoService).encryptJson(any());
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );

        service.updatePaymentProviderSettings(currentUser(), "stripe", stripeSettings("secret"));

        assertThat(jdbcTemplate.lastUpdateSql)
                .contains("created_by_uuid")
                .contains("updated_by_uuid");
        assertThat(jdbcTemplate.lastUpdateArgs).contains(1001L, "user-uuid-1001");
    }

    @Test
    void providerConfigUpdatesShouldConstrainByProviderCodeAfterIdLookup() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lumira/payment/service/PaymentManagementAppService.java"));

        assertThat(source)
                .contains("where id = ? and provider_code = ? and deleted = 0")
                .contains("definition.providerCode()")
                .contains("providerCode")
                .contains("requirePaymentConfigWrite(inserted, \"Payment provider config changed, please retry\")")
                .contains("requirePaymentConfigWrite(updated, \"Payment provider config changed, please retry\")");
    }

    @Test
    void updateProviderSettingsShouldRejectWhenInitialConfigInsertMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        doReturn(null).when(jdbcTemplate).queryForObject(
                anyString(),
                anyPaymentProviderRowMapper(),
                eq("stripe")
        );
        doReturn(null).when(jdbcTemplate).queryForObject(
                contains("select id"),
                eq(Long.class),
                eq("stripe")
        );
        doReturn("encrypted").when(cryptoService).encryptJson(any());
        doReturn(0).when(jdbcTemplate).update(
                contains("insert into payment_provider_config"),
                ArgumentMatchers.<Object[]>any()
        );
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );

        assertThatThrownBy(() -> service.updatePaymentProviderSettings(currentUser(), "stripe", stripeSettings("new-secret")))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Payment provider config changed, please retry");
                });
        verifyNoInteractions(outboxService);
    }

    @Test
    void updateProviderSettingsShouldRejectWhenExistingConfigWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentProviderConfigRow row = providerRow();
        doReturn(row).when(jdbcTemplate).queryForObject(
                anyString(),
                anyPaymentProviderRowMapper(),
                eq("stripe")
        );
        doReturn(55L).when(jdbcTemplate).queryForObject(
                contains("select id"),
                eq(Long.class),
                eq("stripe")
        );
        doReturn(stripeSettings("old-secret")).when(cryptoService).decryptJson("encrypted", PaymentProviderSettingsDTO.class);
        doReturn("encrypted-updated").when(cryptoService).encryptJson(any());
        doReturn(0).when(jdbcTemplate).update(
                contains("update payment_provider_config"),
                ArgumentMatchers.<Object[]>any()
        );
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );

        assertThatThrownBy(() -> service.updatePaymentProviderSettings(currentUser(), "stripe", stripeSettings("new-secret")))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Payment provider config changed, please retry");
                });
        verifyNoInteractions(outboxService);
    }

    @Test
    void updateProviderSettingsShouldRetainMaskedSecretsAndAutoEnableCompleteConfig() {
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentProviderConfigRow row = providerRow();
        JdbcTemplate jdbcTemplate = new ExistingSuccessJdbcTemplate(row);
        PaymentProviderSettingsDTO stored = stripeSettings("stored-webhook-secret");
        stored.setSecretKey("stored-secret-key");
        doReturn(stored).when(cryptoService).decryptJson("encrypted", PaymentProviderSettingsDTO.class);
        doReturn("encrypted-updated").when(cryptoService).encryptJson(any());
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class),
                provider(enabledSystemInternalApi())
        );
        PaymentProviderSettingsDTO request = stripeSettings("********");
        request.setSecretKey("********");
        request.setEnabled(false);

        service.updatePaymentProviderSettings(currentUser(), "stripe", request);

        ArgumentCaptor<PaymentProviderSettingsDTO> storedPayload = ArgumentCaptor.forClass(PaymentProviderSettingsDTO.class);
        verify(cryptoService).encryptJson(storedPayload.capture());
        assertThat(storedPayload.getValue().getSecretKey()).isEqualTo("stored-secret-key");
        assertThat(storedPayload.getValue().getWebhookSecret()).isEqualTo("stored-webhook-secret");
        assertThat(storedPayload.getValue().isConfigured()).isTrue();
        assertThat(storedPayload.getValue().isEnabled()).isTrue();
    }

    @Test
    void testPaymentProviderShouldRejectWhenResultWriteMisses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentProviderConfigRow row = providerRow();
        doReturn(row).when(jdbcTemplate).queryForObject(
                anyString(),
                anyPaymentProviderRowMapper(),
                eq("stripe")
        );
        doReturn(55L).when(jdbcTemplate).queryForObject(
                contains("select id"),
                eq(Long.class),
                eq("stripe")
        );
        PaymentProviderSettingsDTO stored = stripeSettings("real-webhook-secret");
        stored.setApiBaseUrl(null);
        doReturn(stored).when(cryptoService).decryptJson("encrypted", PaymentProviderSettingsDTO.class);
        doReturn(0).when(jdbcTemplate).update(
                contains("update payment_provider_config"),
                ArgumentMatchers.<Object[]>any()
        );
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );

        assertThatThrownBy(() -> service.testPaymentProvider(currentUser(), "stripe"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Payment provider config changed, please retry");
                });
        verifyNoInteractions(outboxService);
    }

    @Test
    void testPaymentProviderShouldRejectWhenPersistedConfigDisappearsBeforeResultWrite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentProviderConfigRow row = providerRow();
        doReturn(row).when(jdbcTemplate).queryForObject(
                anyString(),
                anyPaymentProviderRowMapper(),
                eq("stripe")
        );
        PaymentProviderSettingsDTO stored = stripeSettings("real-webhook-secret");
        stored.setApiBaseUrl(null);
        doReturn(stored).when(cryptoService).decryptJson("encrypted", PaymentProviderSettingsDTO.class);
        doReturn(null).when(jdbcTemplate).queryForObject(
                contains("select id"),
                eq(Long.class),
                eq("stripe")
        );
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );

        assertThatThrownBy(() -> service.testPaymentProvider(currentUser(), "stripe"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Payment provider config changed, please retry");
                });
        verifyNoInteractions(outboxService);
    }

    @Test
    void paymentManagementServiceShouldNotExposeNumericOnlyOperatorOperations() {
        assertThat(Arrays.stream(PaymentManagementAppService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(PaymentManagementAppService.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("updatePaymentProviderSettings(java.lang.Long")
                        || signature.contains("testPaymentProvider(java.lang.Long")))
                .isEmpty();
    }

    @Test
    void updateProviderSettingsShouldRejectPrivateApiBaseUrlBeforePersisting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );
        PaymentProviderSettingsDTO request = stripeSettings("secret");
        request.setApiBaseUrl("http://127.0.0.1:8080");

        assertThatThrownBy(() -> service.updatePaymentProviderSettings(currentUser(), "stripe", request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(cryptoService, never()).encryptJson(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(outboxService);
    }

    @Test
    void updateProviderSettingsShouldRejectCloudMetadataApiBaseUrlBeforePersisting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );
        PaymentProviderSettingsDTO request = stripeSettings("secret");
        request.setApiBaseUrl("http://metadata.google.internal/computeMetadata/v1");

        assertThatThrownBy(() -> service.updatePaymentProviderSettings(currentUser(), "stripe", request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(cryptoService, never()).encryptJson(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(outboxService);
    }

    @Test
    void updateProviderSettingsShouldRejectCallbackUrlWithUserInfoBeforePersisting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentOutboxService outboxService = mock(PaymentOutboxService.class);
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                outboxService,
                provider(enabledSystemInternalApi())
        );
        PaymentProviderSettingsDTO request = stripeSettings("secret");
        request.setSuccessUrl("https://token@example.com/payment/success");

        assertThatThrownBy(() -> service.updatePaymentProviderSettings(currentUser(), "stripe", request))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(cryptoService, never()).encryptJson(any());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(outboxService);
    }

    @Test
    void requiredProviderSettingsShouldLoadUnmaskedSecretsForWebhookVerification() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PaymentConfigCryptoService cryptoService = mock(PaymentConfigCryptoService.class);
        PaymentProviderConfigRow row = new PaymentProviderConfigRow();
        row.setProviderCode("stripe");
        row.setProviderName("Stripe");
        row.setEnabled(1);
        row.setConfigured(1);
        row.setEnvironment("SANDBOX");
        row.setEncryptedConfigJson("encrypted");
        PaymentProviderSettingsDTO stored = stripeSettings("real-webhook-secret");
        doReturn(row).when(jdbcTemplate).queryForObject(
                anyString(),
                anyPaymentProviderRowMapper(),
                eq("stripe")
        );
        doReturn(stored).when(cryptoService).decryptJson("encrypted", PaymentProviderSettingsDTO.class);
        PaymentManagementAppService service = new PaymentManagementAppService(
                jdbcTemplate,
                new ObjectMapper(),
                cryptoService,
                new PaymentProviderCatalog(),
                mock(PaymentOutboxService.class),
                provider(enabledSystemInternalApi())
        );

        PaymentProviderSettingsDTO publicSettings = service.paymentProviderSettings(currentUser(), "stripe");
        PaymentProviderSettingsDTO requiredSettings = service.getRequiredProviderSettings("stripe");

        assertThat(publicSettings.getWebhookSecret()).isEmpty();
        assertThat(requiredSettings.getWebhookSecret()).isEqualTo("real-webhook-secret");
        assertThat(requiredSettings.getSecretKey()).isEqualTo("secret");
    }

    private PaymentProviderSettingsDTO stripeSettings(String webhookSecret) {
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setProviderCode("stripe");
        settings.setEnabled(true);
        settings.setEnvironment("SANDBOX");
        settings.setCurrency("USD");
        settings.setClientId("client");
        settings.setSecretKey("secret");
        settings.setWebhookSecret(webhookSecret);
        settings.setSandboxEnabled(true);
        return settings;
    }

    private PaymentProviderConfigRow providerRow() {
        PaymentProviderConfigRow row = new PaymentProviderConfigRow();
        row.setId(55L);
        row.setProviderCode("stripe");
        row.setProviderName("Stripe");
        row.setEnabled(1);
        row.setConfigured(1);
        row.setEnvironment("SANDBOX");
        row.setEncryptedConfigJson("encrypted");
        return row;
    }

    private CurrentUser currentUser() {
        return currentUser("payment:config:view", "payment:config:update", "payment:config:test");
    }

    private CurrentUser currentUser(String... permissions) {
        CurrentUser currentUser = new CurrentUser(1001L, "admin", null, "session-1", 1, true, Set.of(permissions));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private SystemInternalApi enabledSystemInternalApi() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(userSnapshot(1001L, "admin", "ENABLED"));
        return systemInternalApi;
    }

    private BeanPropertyRowMapper<PaymentProviderConfigRow> anyPaymentProviderRowMapper() {
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
                        ? List.of("payment:config:view", "payment:config:update", "payment:config:test")
                        : List.of(permissions),
                List.of(31L),
                41L,
                List.of(41L),
                List.of(41L, 42L),
                List.of(),
                "/payment/settings"
        );
    }

    private static class InsertSuccessJdbcTemplate extends JdbcTemplate {
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = args;
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return null;
        }
    }

    private static class ExistingSuccessJdbcTemplate extends JdbcTemplate {
        private final PaymentProviderConfigRow row;

        private ExistingSuccessJdbcTemplate(PaymentProviderConfigRow row) {
            this.row = row;
        }

        @Override
        public int update(String sql, Object... args) {
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            return (T) row;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(row.getId());
        }
    }
}
