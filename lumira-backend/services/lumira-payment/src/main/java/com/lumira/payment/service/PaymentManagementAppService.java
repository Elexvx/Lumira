package com.lumira.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.api.payment.PaymentProviderTestResultDTO;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentManagementAppService {

    private static final String SECRET_PLACEHOLDER = "********";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration PROVIDER_LIST_CACHE_TTL = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentConfigCryptoService cryptoService;
    private final PaymentProviderCatalog providerCatalog;
    private final PaymentOutboxService outboxService;
    private final Map<Long, CachedProviderList> providerListCache = new ConcurrentHashMap<>();

    public PaymentManagementAppService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PaymentConfigCryptoService cryptoService,
            PaymentProviderCatalog providerCatalog,
            PaymentOutboxService outboxService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.providerCatalog = providerCatalog;
        this.outboxService = outboxService;
    }

    public List<PaymentProviderSettingsDTO> listProviderSettings(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        CachedProviderList cached = providerListCache.get(effectiveTenantId);
        Instant now = Instant.now();
        if (cached != null && cached.expireAt().isAfter(now)) {
            return cached.settings();
        }
        if (cached != null) {
            providerListCache.remove(effectiveTenantId);
        }
        List<PaymentProviderSettingsDTO> settings = new ArrayList<>();
        for (PaymentProviderCatalog.PaymentProviderDefinition definition : providerCatalog.definitions()) {
            settings.add(loadProviderSettings(effectiveTenantId, definition.providerCode()));
        }
        List<PaymentProviderSettingsDTO> immutableSettings = List.copyOf(settings);
        providerListCache.put(effectiveTenantId, new CachedProviderList(immutableSettings, now.plus(PROVIDER_LIST_CACHE_TTL)));
        return immutableSettings;
    }

    public PaymentProviderSettingsDTO paymentProviderSettings(Long tenantId, String providerCode) {
        return loadProviderSettings(tenantId, providerCode);
    }

    @Transactional
    public PaymentProviderSettingsDTO updatePaymentProviderSettings(Long tenantId, Long operatorId, String providerCode, PaymentProviderSettingsDTO request) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        PaymentProviderSettingsDTO current = loadProviderSettings(tenantId, providerCode);
        PaymentProviderSettingsDTO merged = mergeSettings(definition, current, request);
        merged.setProviderCode(definition.providerCode());
        merged.setProviderName(definition.providerName());
        merged.setEnvironment(resolveText(merged.getEnvironment(), definition.defaultEnvironment()));
        merged.setCurrency(resolveText(merged.getCurrency(), definition.defaultCurrency()));
        merged.setConfigured(isConfigured(definition, merged));
        merged.setConfiguredFields(resolveConfiguredFields(definition, merged));
        if (!merged.isConfigured()) {
            merged.setEnabled(false);
        }

        LocalDateTime eventVersion = LocalDateTime.now();
        upsertProviderConfig(tenantId, operatorId, definition, merged, current);
        providerListCache.remove(tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId);
        outboxService.recordAfterCommit(
                tenantId,
                operatorId,
                "payment",
                "payment.provider.updated",
                providerCode + ":" + UUID.randomUUID(),
                Map.of(
                        "providerCode", providerCode,
                        "enabled", merged.isEnabled(),
                        "configured", merged.isConfigured(),
                        "environment", merged.getEnvironment(),
                        "eventVersion", eventVersion
                )
        );
        return loadProviderSettings(tenantId, providerCode);
    }

    @Transactional
    public PaymentProviderTestResultDTO testPaymentProvider(Long tenantId, Long operatorId, String providerCode) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        PaymentProviderSettingsDTO settings = loadProviderSettings(tenantId, providerCode);
        LocalDateTime checkedAt = LocalDateTime.now();
        boolean success = false;
        String message;

        if (!settings.isEnabled()) {
            message = "支付通道已停用";
        } else if (!settings.isConfigured()) {
            message = "支付配置未完成";
        } else {
            try {
                validateProviderSettings(definition, settings);
                message = performConnectivityProbe(settings);
                success = true;
            } catch (RuntimeException ex) {
                message = ex.getMessage() == null ? "支付通道测试失败" : ex.getMessage();
            }
        }

        updateProviderTestResult(tenantId, operatorId, providerCode, success, message, checkedAt);
        outboxService.recordAfterCommit(
                tenantId,
                operatorId,
                "payment",
                "payment.provider.tested",
                providerCode + ":" + checkedAt,
                Map.of(
                        "providerCode", providerCode,
                        "success", success,
                        "message", message
                )
        );
        return new PaymentProviderTestResultDTO(providerCode, definition.providerName(), success, message, checkedAt);
    }

    public PaymentProviderSettingsDTO getRequiredProviderSettings(Long tenantId, String providerCode) {
        PaymentProviderSettingsDTO settings = loadProviderSettings(tenantId, providerCode, false);
        if (!settings.isConfigured()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "支付配置未完成");
        }
        return settings;
    }

    public Long resolveWebhookTenantId(String providerCode, String payload, Map<String, String> headers) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        List<PaymentProviderConfigRow> rows = jdbcTemplate.query(
                """
                        select id, tenant_id as tenantId, provider_code as providerCode, provider_name as providerName,
                               enabled, environment, encrypted_config_json as encryptedConfigJson, configured,
                               last_tested_at as lastTestedAt, last_test_success as lastTestSuccess,
                               last_test_message as lastTestMessage, created_by as createdBy, created_at as createdAt,
                               updated_by as updatedBy, updated_at as updatedAt, deleted
                        from payment_provider_config
                        where provider_code = ? and enabled = 1 and configured = 1 and deleted = 0
                        order by id desc
                        """,
                new BeanPropertyRowMapper<>(PaymentProviderConfigRow.class),
                definition.providerCode()
        );
        Long claimedTenantId = parseTenantHeader(headers);
        Long matchedTenantId = null;
        for (PaymentProviderConfigRow row : rows) {
            PaymentProviderSettingsDTO settings = cryptoService.decryptJson(row.getEncryptedConfigJson(), PaymentProviderSettingsDTO.class);
            if (!matchesWebhookIdentity(settings, payload, headers)) {
                continue;
            }
            if (matchedTenantId != null && !matchedTenantId.equals(row.getTenantId())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Webhook tenant is ambiguous", "Webhook request is invalid");
            }
            matchedTenantId = row.getTenantId();
        }
        if (matchedTenantId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook tenant cannot be resolved", "Webhook request is invalid");
        }
        if (claimedTenantId != null && !claimedTenantId.equals(matchedTenantId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook tenant header mismatch", "Webhook request is invalid");
        }
        return matchedTenantId;
    }

    private PaymentProviderSettingsDTO loadProviderSettings(Long tenantId, String providerCode) {
        return loadProviderSettings(tenantId, providerCode, true);
    }

    private PaymentProviderSettingsDTO loadProviderSettings(Long tenantId, String providerCode, boolean maskSecrets) {
        PaymentProviderCatalog.PaymentProviderDefinition definition = providerCatalog.requireDefinition(providerCode);
        PaymentProviderConfigRow row = queryProviderRow(tenantId, definition.providerCode());
        if (row == null) {
            return providerCatalog.createBlankSettings(providerCode);
        }

        PaymentProviderSettingsDTO stored = cryptoService.decryptJson(row.getEncryptedConfigJson(), PaymentProviderSettingsDTO.class);
        PaymentProviderSettingsDTO response = new PaymentProviderSettingsDTO();
        response.setProviderCode(definition.providerCode());
        response.setProviderName(definition.providerName());
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

    private boolean matchesWebhookIdentity(PaymentProviderSettingsDTO settings, String payload, Map<String, String> headers) {
        String endpointToken = resolveHeader(headers, "X-Webhook-Token", "X-Endpoint-Token", "Webhook-Id");
        if (StringUtils.hasText(endpointToken) && matchesAny(endpointToken, settings.getWebhookId(), settings.getWebhookSecret())) {
            return true;
        }
        String merchantId = firstText(resolveHeader(headers, "X-Merchant-Id", "Wechatpay-Mchid", "PayPal-Client-Id"),
                extractField(payload, "merchantId", "merchant_id", "mchid", "seller_id", "account"));
        if (StringUtils.hasText(merchantId) && matchesAny(merchantId, settings.getMerchantId(), settings.getClientId())) {
            return true;
        }
        String appId = firstText(resolveHeader(headers, "X-App-Id", "Wechatpay-Appid"),
                extractField(payload, "appId", "app_id", "appid", "client_id"));
        return StringUtils.hasText(appId) && matchesAny(appId, settings.getAppId(), settings.getClientId(), settings.getPublishableKey());
    }

    private String extractField(String payload, String... fieldNames) {
        if (!StringUtils.hasText(payload)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (String fieldName : fieldNames) {
                JsonNode node = root.path(fieldName);
                if (!node.isMissingNode() && !node.isNull() && StringUtils.hasText(node.asText())) {
                    return node.asText();
                }
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String resolveHeader(Map<String, String> headers, String... keys) {
        if (headers == null) {
            return "";
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key) && StringUtils.hasText(entry.getValue())) {
                    return entry.getValue().trim();
                }
            }
        }
        return "";
    }

    private Long parseTenantHeader(Map<String, String> headers) {
        String value = resolveHeader(headers, "X-Tenant-Id");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Webhook tenant header invalid", "Webhook request is invalid");
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean matchesAny(String candidate, String... configuredValues) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        for (String configuredValue : configuredValues) {
            if (StringUtils.hasText(configuredValue) && candidate.trim().equals(configuredValue.trim())) {
                return true;
            }
        }
        return false;
    }

    private void upsertProviderConfig(
            Long tenantId,
            Long operatorId,
            PaymentProviderCatalog.PaymentProviderDefinition definition,
            PaymentProviderSettingsDTO merged,
            PaymentProviderSettingsDTO current
    ) {
        String encryptedJson = cryptoService.encryptJson(buildStoredPayload(merged));
        PaymentProviderConfigRow row = new PaymentProviderConfigRow();
        row.setTenantId(tenantId);
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
        row.setCreatedBy(operatorId);
        row.setDeleted(0);

        Long existingId = queryProviderRowId(tenantId, definition.providerCode());
        if (existingId == null) {
            jdbcTemplate.update(
                    """
                            insert into payment_provider_config (
                                tenant_id, provider_code, provider_name, enabled, environment, encrypted_config_json,
                                configured, last_tested_at, last_test_success, last_test_message,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    row.getTenantId(),
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
                    operatorId
            );
            return;
        }

        jdbcTemplate.update(
                """
                        update payment_provider_config
                        set provider_name = ?, enabled = ?, environment = ?, encrypted_config_json = ?, configured = ?,
                            last_tested_at = ?, last_test_success = ?, last_test_message = ?, updated_by = ?,
                            updated_at = ?, deleted = 0
                        where id = ? and tenant_id = ? and deleted = 0
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
                LocalDateTime.now(),
                existingId,
                tenantId
        );
    }

    private PaymentProviderSettingsDTO mergeSettings(
            PaymentProviderCatalog.PaymentProviderDefinition definition,
            PaymentProviderSettingsDTO current,
            PaymentProviderSettingsDTO request
    ) {
        PaymentProviderSettingsDTO merged = new PaymentProviderSettingsDTO();
        merged.setProviderCode(definition.providerCode());
        merged.setProviderName(definition.providerName());
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
            throw new BizException(ErrorCode.VALIDATION_ERROR, "支付配置缺少必填字段: " + String.join("、", missing));
        }
        if (StringUtils.hasText(settings.getApiBaseUrl())) {
            try {
                java.net.URI.create(settings.getApiBaseUrl().trim());
            } catch (Exception ex) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "支付 API 基地址格式不合法");
            }
        }
    }

    private String performConnectivityProbe(PaymentProviderSettingsDTO settings) {
        if (!StringUtils.hasText(settings.getApiBaseUrl())) {
            return "支付配置已就绪";
        }

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(TEST_TIMEOUT)
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(settings.getApiBaseUrl().trim()))
                    .timeout(TEST_TIMEOUT)
                    .GET()
                    .build();
            java.net.http.HttpResponse<Void> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 500) {
                throw new BizException(ErrorCode.BIZ_ERROR, "支付平台返回错误状态: " + response.statusCode());
            }
            return "支付连通性测试通过";
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BIZ_ERROR, "支付连通性测试失败: " + ex.getMessage());
        }
    }

    private void updateProviderTestResult(Long tenantId, Long operatorId, String providerCode, boolean success, String message, LocalDateTime checkedAt) {
        Long existingId = queryProviderRowId(tenantId, providerCode);
        if (existingId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        update payment_provider_config
                        set last_tested_at = ?, last_test_success = ?, last_test_message = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                checkedAt,
                success ? 1 : 0,
                message,
                operatorId,
                LocalDateTime.now(),
                existingId,
                tenantId
        );
    }

    private PaymentProviderConfigRow queryProviderRow(Long tenantId, String providerCode) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, tenant_id as tenantId, provider_code as providerCode, provider_name as providerName,
                                   enabled, environment, encrypted_config_json as encryptedConfigJson, configured,
                                   last_tested_at as lastTestedAt, last_test_success as lastTestSuccess,
                                   last_test_message as lastTestMessage, created_by as createdBy, created_at as createdAt,
                                   updated_by as updatedBy, updated_at as updatedAt, deleted
                            from payment_provider_config
                            where tenant_id = ? and provider_code = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    new BeanPropertyRowMapper<>(PaymentProviderConfigRow.class),
                    tenantId,
                    providerCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private Long queryProviderRowId(Long tenantId, String providerCode) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id
                            from payment_provider_config
                            where tenant_id = ? and provider_code = ? and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    tenantId,
                    providerCode
            );
        } catch (EmptyResultDataAccessException ignored) {
            return null;
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

    private void addIfText(LinkedHashSet<String> values, String fieldName, String fieldValue) {
        if (StringUtils.hasText(fieldValue)) {
            values.add(fieldName);
        }
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
