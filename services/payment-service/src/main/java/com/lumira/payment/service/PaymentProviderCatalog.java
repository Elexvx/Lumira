package com.lumira.payment.service;

import com.lumira.api.payment.PaymentProviderSettingsDTO;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("paymentProviderCatalog")
public final class PaymentProviderCatalog {

    private static final Map<String, PaymentProviderDefinition> DEFINITIONS = Map.of(
            "alipay", new PaymentProviderDefinition(
                    "alipay",
                    "支付宝",
                    "SANDBOX",
                    "CNY",
                    List.of("appId", "privateKey", "publicKey", "notifyUrl"),
                    Set.of("privateKey", "publicKey"),
                    "RSA2"
            ),
            "wechat_pay", new PaymentProviderDefinition(
                    "wechat_pay",
                    "微信支付",
                    "SANDBOX",
                    "CNY",
                    List.of("appId", "merchantId", "merchantSerialNo", "apiV3Key", "platformCertSerialNo", "privateKey", "notifyUrl"),
                    Set.of("privateKey", "apiV3Key", "platformCertSerialNo"),
                    "HMAC-SHA256"
            ),
            "stripe", new PaymentProviderDefinition(
                    "stripe",
                    "Stripe",
                    "SANDBOX",
                    "USD",
                    List.of("clientId", "secretKey", "webhookSecret"),
                    Set.of("secretKey", "webhookSecret"),
                    "STRIPE-SIGNATURE"
            ),
            "paypal", new PaymentProviderDefinition(
                    "paypal",
                    "PayPal",
                    "SANDBOX",
                    "USD",
                    List.of("clientId", "clientSecret", "webhookId", "webhookSecret"),
                    Set.of("clientSecret", "webhookId", "webhookSecret"),
                    "PAYPAL-SIG"
            )
    );

    public PaymentProviderDefinition requireDefinition(String providerCode) {
        PaymentProviderDefinition definition = DEFINITIONS.get(normalize(providerCode));
        if (definition == null) {
            throw new IllegalArgumentException("不支持的支付平台: " + providerCode);
        }
        return definition;
    }

    public List<PaymentProviderDefinition> definitions() {
        List<PaymentProviderDefinition> definitions = new ArrayList<>(DEFINITIONS.values());
        definitions.sort((left, right) -> left.providerCode().compareToIgnoreCase(right.providerCode()));
        return definitions;
    }

    public PaymentProviderSettingsDTO createBlankSettings(String providerCode) {
        PaymentProviderDefinition definition = requireDefinition(providerCode);
        PaymentProviderSettingsDTO dto = new PaymentProviderSettingsDTO();
        dto.setProviderCode(definition.providerCode());
        dto.setProviderName(definition.providerName());
        dto.setEnabled(false);
        dto.setConfigured(false);
        dto.setPersisted(false);
        dto.setEnvironment(definition.defaultEnvironment());
        dto.setCurrency(definition.defaultCurrency());
        dto.setConfiguredFields(List.of());
        return dto;
    }

    public boolean isSecretField(String providerCode, String fieldName) {
        PaymentProviderDefinition definition = requireDefinition(providerCode);
        return definition.secretFields().contains(fieldName);
    }

    public boolean isRequiredField(String providerCode, String fieldName) {
        return requireDefinition(providerCode).requiredFields().contains(fieldName);
    }

    public List<String> requiredFields(String providerCode) {
        return requireDefinition(providerCode).requiredFields();
    }

    public String defaultEnvironment(String providerCode) {
        return requireDefinition(providerCode).defaultEnvironment();
    }

    public String defaultCurrency(String providerCode) {
        return requireDefinition(providerCode).defaultCurrency();
    }

    public String providerName(String providerCode) {
        return requireDefinition(providerCode).providerName();
    }

    public String normalize(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase();
    }

    public String resolveConfiguredFieldLabel(String fieldName) {
        return switch (fieldName) {
            case "appId" -> "App ID";
            case "merchantId" -> "商户号";
            case "merchantSerialNo" -> "商户证书序列号";
            case "platformCertSerialNo" -> "平台证书序列号";
            case "apiV3Key" -> "APIv3 Key";
            case "clientId" -> "Client ID";
            case "clientSecret" -> "Client Secret";
            case "publishableKey" -> "Publishable Key";
            case "secretKey" -> "Secret Key";
            case "privateKey" -> "私钥";
            case "publicKey" -> "公钥";
            case "apiBaseUrl" -> "API 基地址";
            case "notifyUrl" -> "异步通知地址";
            case "returnUrl" -> "同步跳转地址";
            case "refundNotifyUrl" -> "退款通知地址";
            case "successUrl" -> "成功跳转地址";
            case "cancelUrl" -> "取消跳转地址";
            case "webhookSecret" -> "Webhook Secret";
            case "webhookId" -> "Webhook ID";
            case "currency" -> "货币";
            case "extraConfig" -> "扩展参数";
            case "environment" -> "环境";
            default -> fieldName;
        };
    }

    public record PaymentProviderDefinition(
            String providerCode,
            String providerName,
            String defaultEnvironment,
            String defaultCurrency,
            List<String> requiredFields,
            Set<String> secretFields,
            String signatureAlgorithm
    ) {
    }
}
