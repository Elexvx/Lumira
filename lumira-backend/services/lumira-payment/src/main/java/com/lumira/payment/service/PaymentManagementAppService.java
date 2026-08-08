package com.lumira.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentCheckoutOptionDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentManagementAppService {

    private static final String PERMISSION_PAYMENT_CONFIG_VIEW = "payment:config:view";
    private static final String PERMISSION_PAYMENT_CONFIG_UPDATE = "payment:config:update";
    private static final String PERMISSION_PAYMENT_CONFIG_TEST = "payment:config:test";
    private static final String SECRET_PLACEHOLDER = "********";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration PROVIDER_LIST_CACHE_TTL = Duration.ofSeconds(30);
    private static final Set<String> BLOCKED_PUBLIC_URL_HOSTS = Set.of("localhost", "metadata.google.internal");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentConfigCryptoService cryptoService;
    private final PaymentProviderCatalog providerCatalog;
    private final PaymentOutboxService outboxService;
    private final PaymentActorResolver actorResolver;
    private volatile CachedProviderList providerListCache;

    @Autowired
    public PaymentManagementAppService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentConfigCryptoService cryptoService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService
    ) {
        this(jdbcTemplate, objectMapper, cryptoService, providerCatalog, outboxService, null);
    }

    public PaymentManagementAppService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentConfigCryptoService cryptoService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.providerCatalog = providerCatalog;
        this.outboxService = outboxService;
        this.actorResolver = new PaymentActorResolver();
    }

    public List<PaymentProviderSettingsDTO> listProviderSettings(CurrentUser currentUser) {
        trustedActor(currentUser, PERMISSION_PAYMENT_CONFIG_VIEW);
        return listProviderSettingsInternal();
    }

    private List<PaymentProviderSettingsDTO> listProviderSettingsInternal() {
        CachedProviderList cached = providerListCache;
        Instant now = Instant.now();
        if (cached != null && cached.expireAt().isAfter(now)) {
            return cached.settings();
        }
        if (cached != null) {
            providerListCache = null;
        }
        List<PaymentProviderSettingsDTO> settings = new ArrayList<>();
        for (PaymentProviderCatalog.PaymentProviderDefinition definition : providerCatalog.definitions()) {
            settings.add(loadProviderSettings(definition.providerCode()));
        }
        settings.sort(java.util.Comparator
                .comparing((PaymentProviderSettingsDTO item) -> item.getSortOrder() == null ? 100 : item.getSortOrder())
                .thenComparing(PaymentProviderSettingsDTO::getProviderCode));
        List<PaymentProviderSettingsDTO> immutableSettings = List.copyOf(settings);
        providerListCache = new CachedProviderList(immutableSettings, now.plus(PROVIDER_LIST_CACHE_TTL));
        return immutableSettings;
    }

    public PaymentProviderSettingsDTO paymentProviderSettings(CurrentUser currentUser, String providerCode) {
        trustedActor(currentUser, PERMISSION_PAYMENT_CONFIG_VIEW);
        return loadProviderSettings(providerCode);
    }

    @Transactional
    public PaymentProviderSettingsDTO updatePaymentProviderSettings(CurrentUser currentUser, String providerCode, PaymentProviderSettingsDTO request) {
        return updatePaymentProviderSettings(trustedActor(currentUser, PERMISSION_PAYMENT_CONFIG_UPDATE), providerCode, request);
    }

    private PaymentProviderSettingsDTO updatePaymentProviderSettings(Actor actor, String providerCode, PaymentProviderSettingsDTO request) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        // Updates must merge against the decrypted configuration. The public view masks
        // secrets, which would otherwise make an unchanged masked value erase the key.
        PaymentProviderSettingsDTO current = loadProviderSettings(providerCode, false);
        PaymentProviderSettingsDTO merged = mergeSettings(definition, current, request);
        merged.setProviderCode(definition.providerCode());
        merged.setProviderName(definition.providerName());
        merged.setEnvironment(resolveText(merged.getEnvironment(), definition.defaultEnvironment()));
        merged.setCurrency(resolveText(merged.getCurrency(), definition.defaultCurrency()));
        validateProviderUrls(merged);
        merged.setConfigured(isConfigured(definition, merged));
        merged.setConfiguredFields(resolveConfiguredFields(definition, merged));
        // A complete saved configuration is immediately usable; incomplete drafts stay disabled.
        merged.setEnabled(merged.isConfigured());

        LocalDateTime eventVersion = LocalDateTime.now();
        upsertProviderConfig(actor, definition, merged, current);
        providerListCache = null;
        outboxService.record(
                outboxUserId(actor),
                "payment",
                "payment.provider.updated",
                providerCode + ":" + UUID.randomUUID(),
                actorPayload(actor, Map.of(
                        "providerCode", providerCode,
                        "enabled", merged.isEnabled(),
                        "configured", merged.isConfigured(),
                        "environment", merged.getEnvironment(),
                        "eventVersion", eventVersion
                ))
        );
        return loadProviderSettings(providerCode);
    }

    @Transactional
    public PaymentProviderTestResultDTO testPaymentProvider(CurrentUser currentUser, String providerCode) {
        return testPaymentProvider(trustedActor(currentUser, PERMISSION_PAYMENT_CONFIG_TEST), providerCode);
    }

    private PaymentProviderTestResultDTO testPaymentProvider(Actor actor, String providerCode) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        // Connectivity validation needs the decrypted secrets. The public settings view
        // deliberately masks them and would make a valid provider look incomplete.
        PaymentProviderSettingsDTO settings = loadProviderSettings(providerCode, false);
        LocalDateTime checkedAt = LocalDateTime.now();
        boolean success = false;
        String message;

        if (!settings.isEnabled()) {
            message = "Payment provider is disabled";
        } else if (!settings.isConfigured()) {
            message = "Payment provider is not configured";
        } else {
            try {
                validateProviderSettings(definition, settings);
                message = performConnectivityProbe(settings);
                success = true;
            } catch (RuntimeException ex) {
                message = ex.getMessage() == null ? "Payment provider test failed" : ex.getMessage();
            }
        }

        updateProviderTestResult(actor, providerCode, success, message, checkedAt, settings.isPersisted());
        providerListCache = null;
        outboxService.record(
                outboxUserId(actor),
                "payment",
                "payment.provider.tested",
                providerCode + ":" + checkedAt,
                actorPayload(actor, Map.of(
                        "providerCode", providerCode,
                        "success", success,
                        "message", message
                ))
        );
        return new PaymentProviderTestResultDTO(providerCode, definition.providerName(), success, message, checkedAt);
    }

    private Actor trustedActor(CurrentUser currentUser, String requiredPermission) {
        PaymentActorResolver.Actor actor = actorResolver.require(currentUser, requiredPermission);
        return new Actor(actor.userId(), actor.userUuid());
    }

    private Map<String, Object> actorPayload(Actor actor, Map<String, Object> payload) {
        if (!StringUtils.hasText(actor.userUuid())) {
            return payload;
        }
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(payload);
        enriched.put("userUuid", actor.userUuid());
        return enriched;
    }

    private Long outboxUserId(Actor actor) {
        return actor != null && StringUtils.hasText(actor.userUuid()) ? actor.userId() : null;
    }

    private record Actor(Long userId, String userUuid) {
    }

    public PaymentProviderSettingsDTO getRequiredProviderSettings(String providerCode) {
        PaymentProviderSettingsDTO settings = loadProviderSettings(providerCode, false);
        if (!settings.isConfigured()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Payment provider is not configured");
        }
        return settings;
    }

    private PaymentProviderSettingsDTO loadProviderSettings(String providerCode) {
        return loadProviderSettings(providerCode, true);
    }

    private PaymentProviderSettingsDTO loadProviderSettings(String providerCode, boolean maskSecrets) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        PaymentProviderConfigRow row = queryProviderRow(definition.providerCode());
        if (row == null) {
            return providerCatalog.createBlankSettings(providerCode);
        }

        PaymentProviderSettingsDTO stored = cryptoService.decryptJson(row.getEncryptedConfigJson(), PaymentProviderSettingsDTO.class);
        PaymentProviderSettingsDTO response = new PaymentProviderSettingsDTO();
        response.setProviderCode(definition.providerCode());
        response.setProviderName(definition.providerName());
        response.setDisplayName(resolveText(stored.getDisplayName(), definition.providerName()));
        response.setSortOrder(stored.getSortOrder() == null ? 100 : stored.getSortOrder());
        response.setSupportedScenes(providerCatalog.supportedScenes(definition.providerCode()));
        response.setEnabledScenes(normalizeEnabledScenes(definition.providerCode(), stored.getEnabledScenes()));
        response.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        response.setConfigured(row.getConfigured() != null && row.getConfigured() == 1);
        response.setPersisted(true);
        response.setEnvironment(resolveText(row.getEnvironment(), definition.defaultEnvironment()));
        response.setConfiguredFields(resolveConfiguredFields(definition, stored));
        response.setLastTestedAt(row.getLastTestedAt());
        response.setLastTestSuccess(row.getLastTestSuccess() != null ? row.getLastTestSuccess() == 1 : null);
        response.setLastTestMessage(row.getLastTestMessage());
        copyProviderValues(response, stored, maskSecrets);
        return response;
    }

    private void upsertProviderConfig(
            Actor actor,
            PaymentProviderCatalog.PaymentProviderDefinition definition,
            PaymentProviderSettingsDTO merged,
            PaymentProviderSettingsDTO current
    ) {
        Long operatorId = actor.userId();
        String operatorUuid = actor.userUuid();
        String encryptedJson = cryptoService.encryptJson(buildStoredPayload(merged));
        PaymentProviderConfigRow row = new PaymentProviderConfigRow();
        row.setProviderCode(definition.providerCode());
        row.setProviderName(definition.providerName());
        row.setEnabled(merged.isEnabled() ? 1 : 0);
        row.setEnvironment(resolveText(merged.getEnvironment(), definition.defaultEnvironment()));
        row.setEncryptedConfigJson(encryptedJson);
        row.setConfigured(merged.isConfigured() ? 1 : 0);
        row.setLastTestedAt(current.getLastTestedAt());
        row.setLastTestSuccess(current.getLastTestSuccess() == null ? null : (current.getLastTestSuccess() ? 1 : 0));
        row.setLastTestMessage(current.getLastTestMessage());
        row.setUpdatedBy(operatorId);
        row.setUpdatedByUuid(operatorUuid);
        row.setCreatedBy(operatorId);
        row.setCreatedByUuid(operatorUuid);
        row.setDeleted(0);

        Long existingId = queryProviderRowId(definition.providerCode());
        if (existingId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into payment_provider_config (
                                provider_code, provider_name, enabled, environment, encrypted_config_json,
                                configured, last_tested_at, last_test_success, last_test_message,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    row.getProviderCode(),
                    row.getProviderName(),
                    row.getEnabled(),
                    row.getEnvironment(),
                    row.getEncryptedConfigJson(),
                    row.getConfigured(),
                    row.getLastTestedAt(),
                    row.getLastTestSuccess(),
                    row.getLastTestMessage(),
                    operatorId,
                    operatorUuid,
                    operatorId,
                    operatorUuid
            );
            requirePaymentConfigWrite(inserted, "Payment provider config changed, please retry");
            return;
        }

        int updated = jdbcTemplate.update(
                """
                        update payment_provider_config
                        set provider_name = ?, enabled = ?, environment = ?, encrypted_config_json = ?, configured = ?,
                            last_tested_at = ?, last_test_success = ?, last_test_message = ?, updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?, deleted = 0
                        where id = ? and provider_code = ? and deleted = 0
                        """,
                row.getProviderName(),
                row.getEnabled(),
                row.getEnvironment(),
                row.getEncryptedConfigJson(),
                row.getConfigured(),
                row.getLastTestedAt(),
                row.getLastTestSuccess(),
                row.getLastTestMessage(),
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                existingId,
                definition.providerCode()
        );
        requirePaymentConfigWrite(updated, "Payment provider config changed, please retry");
    }

    private PaymentProviderSettingsDTO mergeSettings(
            PaymentProviderCatalog.PaymentProviderDefinition definition,
            PaymentProviderSettingsDTO current,
            PaymentProviderSettingsDTO request
    ) {
        PaymentProviderSettingsDTO merged = new PaymentProviderSettingsDTO();
        merged.setProviderCode(definition.providerCode());
        merged.setProviderName(definition.providerName());
        merged.setDisplayName(resolveText(request == null ? null : request.getDisplayName(), resolveText(current.getDisplayName(), definition.providerName())));
        merged.setSortOrder(request == null || request.getSortOrder() == null ? (current.getSortOrder() == null ? 100 : current.getSortOrder()) : Math.max(0, request.getSortOrder()));
        merged.setSupportedScenes(providerCatalog.supportedScenes(definition.providerCode()));
        merged.setEnabledScenes(normalizeEnabledScenes(definition.providerCode(), request == null ? current.getEnabledScenes() : request.getEnabledScenes()));
        merged.setEnabled(request == null ? current.isEnabled() : request.isEnabled());
        merged.setEnvironment(resolveText(request == null ? null : request.getEnvironment(), current.getEnvironment()));
        merged.setCurrency(resolveText(request == null ? null : request.getCurrency(), current.getCurrency()));
        merged.setSandboxEnabled(resolveBoolean(request == null ? null : request.getSandboxEnabled(), current.getSandboxEnabled()));
        merged.setAppId(resolveText(request == null ? null : request.getAppId(), current.getAppId()));
        merged.setMerchantId(resolveText(request == null ? null : request.getMerchantId(), current.getMerchantId()));
        merged.setMerchantSerialNo(resolveText(request == null ? null : request.getMerchantSerialNo(), current.getMerchantSerialNo()));
        merged.setPlatformCertSerialNo(resolveText(request == null ? null : request.getPlatformCertSerialNo(), current.getPlatformCertSerialNo()));
        merged.setApiV3Key(resolveSecret(request == null ? null : request.getApiV3Key(), current.getApiV3Key()));
        merged.setClientId(resolveText(request == null ? null : request.getClientId(), current.getClientId()));
        merged.setClientSecret(resolveSecret(request == null ? null : request.getClientSecret(), current.getClientSecret()));
        merged.setPublishableKey(resolveText(request == null ? null : request.getPublishableKey(), current.getPublishableKey()));
        merged.setSecretKey(resolveSecret(request == null ? null : request.getSecretKey(), current.getSecretKey()));
        merged.setPrivateKey(resolveSecret(request == null ? null : request.getPrivateKey(), current.getPrivateKey()));
        merged.setPublicKey(resolveText(request == null ? null : request.getPublicKey(), current.getPublicKey()));
        merged.setApiBaseUrl(resolveText(request == null ? null : request.getApiBaseUrl(), current.getApiBaseUrl()));
        merged.setNotifyUrl(resolveText(request == null ? null : request.getNotifyUrl(), current.getNotifyUrl()));
        merged.setReturnUrl(resolveText(request == null ? null : request.getReturnUrl(), current.getReturnUrl()));
        merged.setRefundNotifyUrl(resolveText(request == null ? null : request.getRefundNotifyUrl(), current.getRefundNotifyUrl()));
        merged.setSuccessUrl(resolveText(request == null ? null : request.getSuccessUrl(), current.getSuccessUrl()));
        merged.setCancelUrl(resolveText(request == null ? null : request.getCancelUrl(), current.getCancelUrl()));
        merged.setWebhookSecret(resolveSecret(request == null ? null : request.getWebhookSecret(), current.getWebhookSecret()));
        merged.setWebhookId(resolveSecret(request == null ? null : request.getWebhookId(), current.getWebhookId()));
        merged.setExtraConfig(resolveText(request == null ? null : request.getExtraConfig(), current.getExtraConfig()));
        merged.setLastTestedAt(current.getLastTestedAt());
        merged.setLastTestSuccess(current.getLastTestSuccess());
        merged.setLastTestMessage(current.getLastTestMessage());
        return merged;
    }

    private PaymentProviderSettingsDTO buildStoredPayload(PaymentProviderSettingsDTO merged) {
        PaymentProviderSettingsDTO stored = new PaymentProviderSettingsDTO();
        stored.setProviderCode(merged.getProviderCode());
        stored.setProviderName(merged.getProviderName());
        stored.setDisplayName(merged.getDisplayName());
        stored.setSortOrder(merged.getSortOrder());
        stored.setEnabledScenes(merged.getEnabledScenes());
        stored.setEnabled(merged.isEnabled());
        stored.setConfigured(merged.isConfigured());
        stored.setEnvironment(merged.getEnvironment());
        stored.setAppId(merged.getAppId());
        stored.setMerchantId(merged.getMerchantId());
        stored.setMerchantSerialNo(merged.getMerchantSerialNo());
        stored.setPlatformCertSerialNo(merged.getPlatformCertSerialNo());
        stored.setApiV3Key(merged.getApiV3Key());
        stored.setClientId(merged.getClientId());
        stored.setClientSecret(merged.getClientSecret());
        stored.setPublishableKey(merged.getPublishableKey());
        stored.setSecretKey(merged.getSecretKey());
        stored.setPrivateKey(merged.getPrivateKey());
        stored.setPublicKey(merged.getPublicKey());
        stored.setApiBaseUrl(merged.getApiBaseUrl());
        stored.setNotifyUrl(merged.getNotifyUrl());
        stored.setReturnUrl(merged.getReturnUrl());
        stored.setRefundNotifyUrl(merged.getRefundNotifyUrl());
        stored.setSuccessUrl(merged.getSuccessUrl());
        stored.setCancelUrl(merged.getCancelUrl());
        stored.setWebhookSecret(merged.getWebhookSecret());
        stored.setWebhookId(merged.getWebhookId());
        stored.setCurrency(merged.getCurrency());
        stored.setExtraConfig(merged.getExtraConfig());
        stored.setSandboxEnabled(merged.getSandboxEnabled());
        stored.setConfiguredFields(merged.getConfiguredFields());
        stored.setLastTestedAt(merged.getLastTestedAt());
        stored.setLastTestSuccess(merged.getLastTestSuccess());
        stored.setLastTestMessage(merged.getLastTestMessage());
        return stored;
    }

    private void copyProviderValues(PaymentProviderSettingsDTO target, PaymentProviderSettingsDTO source, boolean maskSecrets) {
        target.setAppId(source.getAppId());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantSerialNo(source.getMerchantSerialNo());
        target.setPlatformCertSerialNo(maskSecrets ? "" : source.getPlatformCertSerialNo());
        target.setApiV3Key(maskSecrets ? "" : source.getApiV3Key());
        target.setClientId(source.getClientId());
        target.setClientSecret(maskSecrets ? "" : source.getClientSecret());
        target.setPublishableKey(source.getPublishableKey());
        target.setSecretKey(maskSecrets ? "" : source.getSecretKey());
        target.setPrivateKey(maskSecrets ? "" : source.getPrivateKey());
        target.setPublicKey(source.getPublicKey());
        target.setApiBaseUrl(source.getApiBaseUrl());
        target.setNotifyUrl(source.getNotifyUrl());
        target.setReturnUrl(source.getReturnUrl());
        target.setRefundNotifyUrl(source.getRefundNotifyUrl());
        target.setSuccessUrl(source.getSuccessUrl());
        target.setCancelUrl(source.getCancelUrl());
        target.setWebhookSecret(maskSecrets ? "" : source.getWebhookSecret());
        target.setWebhookId(maskSecrets ? "" : source.getWebhookId());
        target.setCurrency(source.getCurrency());
        target.setExtraConfig(source.getExtraConfig());
        target.setSandboxEnabled(source.getSandboxEnabled());
    }

    private void validateProviderSettings(PaymentProviderCatalog.PaymentProviderDefinition definition, PaymentProviderSettingsDTO settings) {
        List<String> missing = new ArrayList<>();
        for (String field : definition.requiredFields()) {
            if (!hasConfiguredField(settings, field)) {
                missing.add(providerCatalog.resolveConfiguredFieldLabel(field));
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Missing required payment fields: " + String.join(", ", missing));
        }
        if (settings.getEnabledScenes().isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "At least one payment scene must be enabled");
        }
        if (StringUtils.hasText(settings.getApiBaseUrl())) {
            validatePublicHttpUrl(settings.getApiBaseUrl(), "API base URL");
        }
    }

    public List<PaymentCheckoutOptionDTO> listCheckoutOptions() {
        return listProviderSettingsInternal().stream()
                .filter(PaymentProviderSettingsDTO::isEnabled)
                .filter(PaymentProviderSettingsDTO::isConfigured)
                .filter(item -> item.getEnabledScenes() != null && !item.getEnabledScenes().isEmpty())
                .map(item -> new PaymentCheckoutOptionDTO(
                        item.getProviderCode(),
                        resolveText(item.getDisplayName(), item.getProviderName()),
                        item.getSortOrder(),
                        item.getCurrency(),
                        List.copyOf(item.getEnabledScenes())
                ))
                .toList();
    }

    private List<String> normalizeEnabledScenes(String providerCode, List<String> requestedScenes) {
        List<String> supported = providerCatalog.supportedScenes(providerCode);
        if (requestedScenes == null || requestedScenes.isEmpty()) {
            return supported;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String scene : requestedScenes) {
            String value = scene == null ? "" : scene.trim().toUpperCase(Locale.ROOT);
            if (supported.contains(value)) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String performConnectivityProbe(PaymentProviderSettingsDTO settings) {
        if (!StringUtils.hasText(settings.getApiBaseUrl())) {
            return "Payment provider is ready";
        }

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(TEST_TIMEOUT)
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(validatePublicHttpUrl(settings.getApiBaseUrl(), "API base URL"))
                    .timeout(TEST_TIMEOUT)
                    .GET()
                    .build();
            java.net.http.HttpResponse<Void> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 500) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Payment provider returned error status: " + response.statusCode());
            }
            return "Payment connectivity test passed";
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Payment connectivity test failed: " + ex.getMessage());
        }
    }

    private void updateProviderTestResult(
            Actor actor,
            String providerCode,
            boolean success,
            String message,
            LocalDateTime checkedAt,
            boolean expectPersistedConfig
    ) {
        Long operatorId = actor.userId();
        String operatorUuid = actor.userUuid();
        Long existingId = queryProviderRowId(providerCode);
        if (existingId == null) {
            if (expectPersistedConfig) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Payment provider config changed, please retry");
            }
            return;
        }
        int updated = jdbcTemplate.update(
                """
                        update payment_provider_config
                        set last_tested_at = ?, last_test_success = ?, last_test_message = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and provider_code = ? and deleted = 0
                        """,
                checkedAt,
                success ? 1 : 0,
                message,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                existingId,
                providerCode
        );
        requirePaymentConfigWrite(updated, "Payment provider config changed, please retry");
    }

    private PaymentProviderConfigRow queryProviderRow(String providerCode) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, provider_code as providerCode, provider_name as providerName,
                                   enabled, environment, encrypted_config_json as encryptedConfigJson, configured,
                                   last_tested_at as lastTestedAt, last_test_success as lastTestSuccess,
                                   last_test_message as lastTestMessage,
                                   created_by as createdBy, created_by_uuid as createdByUuid, created_at as createdAt,
                                   updated_by as updatedBy, updated_by_uuid as updatedByUuid, updated_at as updatedAt, deleted
                            from payment_provider_config
                            where provider_code = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentProviderConfigRow.class),
                    providerCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Long queryProviderRowId(String providerCode) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from payment_provider_config
                            where provider_code = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    providerCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private void requirePaymentConfigWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }

    private boolean hasConfiguredField(PaymentProviderSettingsDTO settings, String fieldName) {
        return switch (fieldName) {
            case "appId" -> StringUtils.hasText(settings.getAppId());
            case "merchantId" -> StringUtils.hasText(settings.getMerchantId());
            case "merchantSerialNo" -> StringUtils.hasText(settings.getMerchantSerialNo());
            case "platformCertSerialNo" -> StringUtils.hasText(settings.getPlatformCertSerialNo());
            case "apiV3Key" -> StringUtils.hasText(settings.getApiV3Key());
            case "clientId" -> StringUtils.hasText(settings.getClientId());
            case "clientSecret" -> StringUtils.hasText(settings.getClientSecret());
            case "publishableKey" -> StringUtils.hasText(settings.getPublishableKey());
            case "secretKey" -> StringUtils.hasText(settings.getSecretKey());
            case "privateKey" -> StringUtils.hasText(settings.getPrivateKey());
            case "publicKey" -> StringUtils.hasText(settings.getPublicKey());
            case "apiBaseUrl" -> StringUtils.hasText(settings.getApiBaseUrl());
            case "notifyUrl" -> StringUtils.hasText(settings.getNotifyUrl());
            case "returnUrl" -> StringUtils.hasText(settings.getReturnUrl());
            case "refundNotifyUrl" -> StringUtils.hasText(settings.getRefundNotifyUrl());
            case "successUrl" -> StringUtils.hasText(settings.getSuccessUrl());
            case "cancelUrl" -> StringUtils.hasText(settings.getCancelUrl());
            case "webhookSecret" -> StringUtils.hasText(settings.getWebhookSecret());
            case "webhookId" -> StringUtils.hasText(settings.getWebhookId());
            case "currency" -> StringUtils.hasText(settings.getCurrency());
            case "extraConfig" -> StringUtils.hasText(settings.getExtraConfig());
            default -> false;
        };
    }

    private List<String> resolveConfiguredFields(PaymentProviderCatalog.PaymentProviderDefinition definition, PaymentProviderSettingsDTO settings) {
        LinkedHashSet<String> configured = new LinkedHashSet<>();
        for (String field : definition.requiredFields()) {
            if (hasConfiguredField(settings, field)) {
                configured.add(field);
            }
        }
        addIfText(configured, "appId", settings.getAppId());
        addIfText(configured, "merchantId", settings.getMerchantId());
        addIfText(configured, "merchantSerialNo", settings.getMerchantSerialNo());
        addIfText(configured, "platformCertSerialNo", settings.getPlatformCertSerialNo());
        addIfText(configured, "apiV3Key", settings.getApiV3Key());
        addIfText(configured, "clientId", settings.getClientId());
        addIfText(configured, "clientSecret", settings.getClientSecret());
        addIfText(configured, "publishableKey", settings.getPublishableKey());
        addIfText(configured, "secretKey", settings.getSecretKey());
        addIfText(configured, "privateKey", settings.getPrivateKey());
        addIfText(configured, "publicKey", settings.getPublicKey());
        addIfText(configured, "apiBaseUrl", settings.getApiBaseUrl());
        addIfText(configured, "notifyUrl", settings.getNotifyUrl());
        addIfText(configured, "returnUrl", settings.getReturnUrl());
        addIfText(configured, "refundNotifyUrl", settings.getRefundNotifyUrl());
        addIfText(configured, "successUrl", settings.getSuccessUrl());
        addIfText(configured, "cancelUrl", settings.getCancelUrl());
        addIfText(configured, "webhookSecret", settings.getWebhookSecret());
        addIfText(configured, "webhookId", settings.getWebhookId());
        addIfText(configured, "currency", settings.getCurrency());
        addIfText(configured, "extraConfig", settings.getExtraConfig());
        if (settings.getSandboxEnabled() != null) {
            configured.add("sandboxEnabled");
        }
        return new ArrayList<>(configured);
    }

    private void validateProviderUrls(PaymentProviderSettingsDTO settings) {
        validateOptionalPublicHttpUrl(settings.getApiBaseUrl(), "API base URL");
        validateOptionalPublicHttpUrl(settings.getNotifyUrl(), "Notify URL");
        validateOptionalPublicHttpUrl(settings.getReturnUrl(), "Return URL");
        validateOptionalPublicHttpUrl(settings.getRefundNotifyUrl(), "Refund notify URL");
        validateOptionalPublicHttpUrl(settings.getSuccessUrl(), "Success URL");
        validateOptionalPublicHttpUrl(settings.getCancelUrl(), "Cancel URL");
    }

    private void validateOptionalPublicHttpUrl(String value, String name) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        validatePublicHttpUrl(value, name);
    }

    private URI validatePublicHttpUrl(String value, String name) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (Exception exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid payment " + name);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!StringUtils.hasText(scheme)
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(host)
                || StringUtils.hasText(uri.getUserInfo())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid payment " + name);
        }
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        if (BLOCKED_PUBLIC_URL_HOSTS.contains(trimTrailingDot(normalizedHost)) || normalizedHost.endsWith(".localhost")) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment " + name + " must not target local hosts");
        }
        rejectUnsafeHost(normalizedHost, name);
        return uri;
    }

    private void rejectUnsafeHost(String host, String name) {
        byte[] ipv4Address = parseIpv4Literal(host);
        if (ipv4Address != null) {
            rejectPrivateAddress(ipv4Address, name);
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                rejectPrivateAddress(address.getAddress(), name);
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception ignored) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid payment " + name);
        }
    }

    private byte[] parseIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] address = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isBlank() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int value = Integer.parseInt(part);
            if (value > 255) {
                return null;
            }
            address[index] = (byte) value;
        }
        return address;
    }

    private void rejectPrivateAddress(byte[] rawAddress, String name) {
        try {
            InetAddress address = InetAddress.getByAddress(rawAddress);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment " + name + " must not target private networks");
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Invalid payment " + name);
        }
    }

    private void addIfText(LinkedHashSet<String> values, String fieldName, String fieldValue) {
        if (StringUtils.hasText(fieldValue)) {
            values.add(fieldName);
        }
    }

    private String trimTrailingDot(String value) {
        String result = value;
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String resolveText(String candidate, String fallback) {
        if (!StringUtils.hasText(candidate)) {
            return fallback;
        }
        String normalized = candidate.trim();
        return SECRET_PLACEHOLDER.equals(normalized) ? fallback : normalized;
    }

    private Boolean resolveBoolean(Boolean candidate, Boolean fallback) {
        return candidate == null ? fallback : candidate;
    }

    private String resolveSecret(String candidate, String fallback) {
        if (!StringUtils.hasText(candidate)) {
            return fallback;
        }
        String normalized = candidate.trim();
        return SECRET_PLACEHOLDER.equals(normalized) ? fallback : normalized;
    }

    private boolean isConfigured(PaymentProviderCatalog.PaymentProviderDefinition definition, PaymentProviderSettingsDTO settings) {
        for (String requiredField : definition.requiredFields()) {
            if (!hasConfiguredField(settings, requiredField)) {
                return false;
            }
        }
        return true;
    }

    private record CachedProviderList(List<PaymentProviderSettingsDTO> settings, Instant expireAt) {
    }
}
