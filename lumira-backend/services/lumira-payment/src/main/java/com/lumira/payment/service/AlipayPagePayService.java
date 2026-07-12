package com.lumira.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Builds a signed Alipay page-pay request without exposing the application private key. */
final class AlipayPagePayService {

    static final String SANDBOX_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    static final String PRODUCTION_GATEWAY = "https://openapi.alipay.com/gateway.do";
    private static final DateTimeFormatter ALIPAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    AlipayPagePayService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String buildPagePayUrl(
            PaymentProviderSettingsDTO settings,
            String orderNo,
            String subject,
            Long amountMinor,
            String notifyUrl,
            String returnUrl
    ) {
        requireText(settings.getAppId(), "Alipay app ID is required");
        requireText(settings.getPrivateKey(), "Alipay application private key is required");
        if (amountMinor == null || amountMinor <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Payment amount must be greater than zero");
        }

        TreeMap<String, String> parameters = new TreeMap<>();
        parameters.put("app_id", settings.getAppId().trim());
        parameters.put("biz_content", serializeBizContent(orderNo, subject, amountMinor));
        parameters.put("charset", "utf-8");
        parameters.put("format", "JSON");
        parameters.put("method", "alipay.trade.page.pay");
        if (StringUtils.hasText(notifyUrl)) {
            parameters.put("notify_url", notifyUrl.trim());
        }
        if (StringUtils.hasText(returnUrl)) {
            parameters.put("return_url", returnUrl.trim());
        }
        parameters.put("sign_type", "RSA2");
        parameters.put("timestamp", ALIPAY_TIMESTAMP.format(LocalDateTime.now()));
        parameters.put("version", "1.0");

        String contentToSign = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
        parameters.put("sign", sign(contentToSign, settings.getPrivateKey()));

        String query = parameters.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
        return resolveGateway(settings) + "?" + query;
    }

    void closeTrade(PaymentProviderSettingsDTO settings, String orderNo) {
        requireText(settings.getAppId(), "Alipay app ID is required");
        requireText(settings.getPrivateKey(), "Alipay application private key is required");
        requireText(orderNo, "Alipay order number is required");
        TreeMap<String, String> parameters = new TreeMap<>();
        parameters.put("app_id", settings.getAppId().trim());
        parameters.put("biz_content", serialize(Map.of("out_trade_no", orderNo.trim())));
        parameters.put("charset", "utf-8");
        parameters.put("format", "JSON");
        parameters.put("method", "alipay.trade.close");
        parameters.put("sign_type", "RSA2");
        parameters.put("timestamp", ALIPAY_TIMESTAMP.format(LocalDateTime.now()));
        parameters.put("version", "1.0");
        parameters.put("sign", sign(buildContentToSign(parameters), settings.getPrivateKey()));
        String requestBody = parameters.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveGateway(settings)))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Alipay close-order request failed");
            }
            var payload = objectMapper.readTree(response.body()).path("alipay_trade_close_response");
            String code = payload.path("code").asText();
            String subCode = payload.path("sub_code").asText();
            if ("10000".equals(code) || "ACQ.TRADE_NOT_EXIST".equals(subCode)) {
                return;
            }
            throw new BizException(
                    ErrorCode.BIZ_ERROR,
                    "支付宝订单关闭失败：" + payload.path("sub_msg").asText(payload.path("msg").asText("未知错误"))
            );
        } catch (BizException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Alipay close-order request was interrupted");
        } catch (Exception exception) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Unable to close Alipay order");
        }
    }

    private String buildContentToSign(TreeMap<String, String> parameters) {
        return parameters.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Unable to serialize Alipay request");
        }
    }

    private String serializeBizContent(String orderNo, String subject, Long amountMinor) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("out_trade_no", orderNo);
        content.put("product_code", "FAST_INSTANT_TRADE_PAY");
        content.put("total_amount", BigDecimal.valueOf(amountMinor, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        content.put("subject", subject);
        content.put("timeout_express", "2h");
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Unable to serialize Alipay order");
        }
    }

    private String sign(String content, String privateKeyPem) {
        try {
            String normalized = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception exception) {
            throw new BizException(
                    ErrorCode.VALIDATION_ERROR,
                    "Alipay application private key must be a valid PKCS#8 RSA key"
            );
        }
    }

    private String resolveGateway(PaymentProviderSettingsDTO settings) {
        if ("SANDBOX".equalsIgnoreCase(settings.getEnvironment())) {
            return SANDBOX_GATEWAY;
        }
        if (!StringUtils.hasText(settings.getApiBaseUrl())) {
            return PRODUCTION_GATEWAY;
        }
        String configured = settings.getApiBaseUrl().trim();
        if (configured.endsWith("/gateway.do")) {
            return configured;
        }
        return configured.replaceAll("/+$", "") + "/gateway.do";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BIZ_ERROR, message);
        }
    }
}
