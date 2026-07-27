package com.lumira.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WechatPayV3Service {

    private static final String DEFAULT_API_BASE_URL = "https://api.mch.weixin.qq.com";
    private static final String AUTHORIZATION_SCHEME = "WECHATPAY2-SHA256-RSA2048";
    private static final String NOTIFICATION_ALGORITHM = "AEAD_AES_256_GCM";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;

    @Autowired
    public WechatPayV3Service(ObjectMapper objectMapper) {
        this(
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                Clock.systemUTC()
        );
    }

    WechatPayV3Service(ObjectMapper objectMapper, HttpClient httpClient, Clock clock) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    public WechatPaymentResult createPayment(
            PaymentProviderSettingsDTO settings,
            String orderNo,
            String subject,
            long amountMinor,
            String currency,
            String notifyUrl,
            String clientIp,
            Map<String, Object> metadata
    ) {
        requireMerchantSettings(settings);
        String scene = resolveScene(metadata);
        requireEnabledScene(settings, scene);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("appid", requireText(settings.getAppId(), "WeChat Pay App ID"));
        body.put("mchid", requireText(settings.getMerchantId(), "WeChat Pay merchant ID"));
        body.put("description", requireText(subject, "Payment subject"));
        body.put("out_trade_no", requireText(orderNo, "Payment order number"));
        body.put("notify_url", requireText(notifyUrl, "WeChat Pay notification URL"));
        ObjectNode amount = body.putObject("amount");
        amount.put("total", amountMinor);
        amount.put("currency", normalizeCurrency(currency));

        String attach = metadataText(metadata, "attach");
        if (StringUtils.hasText(attach)) {
            body.put("attach", attach);
        }

        String path;
        if ("H5".equals(scene)) {
            path = "/v3/pay/transactions/h5";
            ObjectNode sceneInfo = body.putObject("scene_info");
            sceneInfo.put("payer_client_ip", requireText(clientIp, "Payer client IP"));
            sceneInfo.putObject("h5_info").put("type", normalizeH5Type(metadataText(metadata, "h5Type")));
        } else if ("JSAPI".equals(scene)) {
            path = "/v3/pay/transactions/jsapi";
            body.putObject("payer").put("openid", requireText(metadataText(metadata, "openid"), "WeChat OpenID"));
        } else {
            path = "/v3/pay/transactions/native";
        }

        String responseBody = send(settings, "POST", path, serialize(body), false);
        JsonNode response = readJson(responseBody, "WeChat Pay create-order response");
        if ("H5".equals(scene)) {
            return new WechatPaymentResult(scene, requireResponseText(response, "h5_url"), responseBody);
        }
        if ("JSAPI".equals(scene)) {
            String prepayId = requireResponseText(response, "prepay_id");
            return new WechatPaymentResult(scene, buildJsapiPaymentUri(settings, prepayId), responseBody);
        }
        return new WechatPaymentResult(scene, requireResponseText(response, "code_url"), responseBody);
    }

    public void closePayment(PaymentProviderSettingsDTO settings, String orderNo) {
        requireMerchantSettings(settings);
        String path = "/v3/pay/transactions/out-trade-no/" + encodePathSegment(requireText(orderNo, "Payment order number")) + "/close";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("mchid", requireText(settings.getMerchantId(), "WeChat Pay merchant ID"));
        send(settings, "POST", path, serialize(body), true);
    }

    public boolean verifyNotificationSignature(
            PaymentProviderSettingsDTO settings,
            String timestamp,
            String nonce,
            String signature,
            String serial,
            String rawBody
    ) {
        if (!StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)
                || !StringUtils.hasText(serial)
                || !StringUtils.hasText(rawBody)
                || !StringUtils.hasText(settings.getPublicKey())
                || !StringUtils.hasText(settings.getPlatformCertSerialNo())) {
            return false;
        }
        if (!constantTimeEquals(settings.getPlatformCertSerialNo().trim(), serial.trim())) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(parsePlatformPublicKey(settings.getPublicKey()));
            verifier.update(buildCallbackSignedMessage(timestamp, nonce, rawBody).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature.trim()));
        } catch (Exception exception) {
            return false;
        }
    }

    public WechatNotification decryptNotification(PaymentProviderSettingsDTO settings, String rawBody) {
        String apiV3Key = requireText(settings.getApiV3Key(), "WeChat Pay APIv3 key");
        byte[] keyBytes = apiV3Key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw invalidNotification("WeChat Pay APIv3 key must contain exactly 32 UTF-8 bytes");
        }
        try {
            JsonNode root = readJson(rawBody, "WeChat Pay notification");
            JsonNode resource = root.path("resource");
            String algorithm = resource.path("algorithm").asText();
            if (!NOTIFICATION_ALGORITHM.equals(algorithm)) {
                throw invalidNotification("Unsupported WeChat Pay notification algorithm");
            }
            String nonce = requireJsonText(resource, "nonce");
            String ciphertext = requireJsonText(resource, "ciphertext");
            String associatedData = resource.path("associated_data").asText("");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8))
            );
            if (StringUtils.hasText(associatedData)) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            String plaintext = new String(
                    cipher.doFinal(Base64.getDecoder().decode(ciphertext)),
                    StandardCharsets.UTF_8
            );
            JsonNode decrypted = readJson(plaintext, "WeChat Pay encrypted notification resource");
            return normalizeNotification(root, decrypted, plaintext);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidNotification("Unable to decrypt WeChat Pay notification");
        }
    }

    String buildAuthorization(
            PaymentProviderSettingsDTO settings,
            String method,
            String canonicalUrl,
            String body,
            String nonce,
            long timestamp
    ) {
        String message = method.toUpperCase(Locale.ROOT)
                + "\n" + canonicalUrl
                + "\n" + timestamp
                + "\n" + nonce
                + "\n" + body
                + "\n";
        String signature = sign(settings.getPrivateKey(), message);
        return AUTHORIZATION_SCHEME
                + " mchid=\"" + requireText(settings.getMerchantId(), "WeChat Pay merchant ID") + "\""
                + ",nonce_str=\"" + nonce + "\""
                + ",timestamp=\"" + timestamp + "\""
                + ",serial_no=\"" + requireText(settings.getMerchantSerialNo(), "Merchant certificate serial number") + "\""
                + ",signature=\"" + signature + "\"";
    }

    private String send(
            PaymentProviderSettingsDTO settings,
            String method,
            String path,
            String body,
            boolean allowEmptyResponse
    ) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        long timestamp = Instant.now(clock).getEpochSecond();
        String authorization = buildAuthorization(settings, method, path, body, nonce, timestamp);
        HttpRequest request = HttpRequest.newBuilder(resolveUri(settings, path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", authorization)
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String responseBody = response.body() == null ? "" : response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerRequestFailure(response.statusCode(), responseBody);
            }
            if (!allowEmptyResponse && !StringUtils.hasText(responseBody)) {
                throw new BizException(
                        ErrorCode.BIZ_ERROR,
                        "WeChat Pay API returned an empty response",
                        "微信支付未返回下单结果，请稍后重试"
                );
            }
            return responseBody;
        } catch (BizException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(
                    ErrorCode.BIZ_ERROR,
                    "WeChat Pay API request was interrupted",
                    "微信支付请求已中断，请重试"
            );
        } catch (Exception exception) {
            throw new BizException(
                    ErrorCode.BIZ_ERROR,
                    "Unable to call WeChat Pay API: " + exception.getClass().getSimpleName(),
                    "暂时无法连接微信支付接口，请稍后重试"
            );
        }
    }

    private WechatNotification normalizeNotification(JsonNode root, JsonNode decrypted, String plaintext) {
        String eventId = requireJsonText(root, "id");
        String eventType = requireJsonText(root, "event_type");
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("eventId", eventId);
        normalized.put("eventType", eventType);
        copyText(decrypted, normalized, "appid", "appid");
        copyText(decrypted, normalized, "mchid", "mchid");
        copyText(decrypted, normalized, "out_trade_no", "orderNo");
        copyText(decrypted, normalized, "transaction_id", "providerTxnId");
        copyText(decrypted, normalized, "trade_state", "tradeState");
        copyText(decrypted, normalized, "out_refund_no", "refundNo");
        copyText(decrypted, normalized, "refund_id", "providerRefundId");
        copyText(decrypted, normalized, "refund_status", "refundStatus");
        JsonNode amount = decrypted.path("amount");
        if (amount.path("total").canConvertToLong()) {
            normalized.put("amountMinor", amount.path("total").asLong());
        }
        copyText(amount, normalized, "currency", "currency");
        return new WechatNotification(
                eventId,
                eventType,
                serialize(normalized),
                plaintext,
                normalized.path("mchid").asText(""),
                normalized.path("appid").asText("")
        );
    }

    private String buildJsapiPaymentUri(PaymentProviderSettingsDTO settings, String prepayId) {
        String appId = requireText(settings.getAppId(), "WeChat Pay App ID");
        String timeStamp = Long.toString(Instant.now(clock).getEpochSecond());
        String nonceStr = UUID.randomUUID().toString().replace("-", "");
        String packageValue = "prepay_id=" + prepayId;
        String paySign = sign(
                settings.getPrivateKey(),
                appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + packageValue + "\n"
        );
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("appId", appId);
        parameters.put("timeStamp", timeStamp);
        parameters.put("nonceStr", nonceStr);
        parameters.put("package", packageValue);
        parameters.put("signType", "RSA");
        parameters.put("paySign", paySign);
        return "wechat-jsapi://pay?" + parameters.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String sign(String privateKeyPem, String message) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(parsePrivateKey(privateKeyPem));
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Unable to sign WeChat Pay request");
        }
    }

    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String pem = requireText(privateKeyPem, "WeChat Pay merchant private key");
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        byte[] keyBytes = Base64.getDecoder().decode(
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                        .replace("-----END RSA PRIVATE KEY-----", "")
                        .replaceAll("\\s", "")
        );
        if (pkcs1) {
            keyBytes = wrapPkcs1InPkcs8(keyBytes);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private PublicKey parsePlatformPublicKey(String pem) throws Exception {
        String normalized = requireText(pem, "WeChat Pay platform public key");
        if (normalized.contains("BEGIN CERTIFICATE")) {
            return CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(normalized.getBytes(StandardCharsets.US_ASCII)))
                    .getPublicKey();
        }
        byte[] keyBytes = Base64.getDecoder().decode(
                normalized.replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "")
        );
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        byte[] rsaAlgorithmIdentifier = new byte[]{
                0x30, 0x0d, 0x06, 0x09,
                0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] version = new byte[]{0x02, 0x01, 0x00};
        byte[] privateKey = derValue((byte) 0x04, pkcs1);
        byte[] content = concat(version, rsaAlgorithmIdentifier, privateKey);
        return derValue((byte) 0x30, content);
    }

    private byte[] derValue(byte tag, byte[] value) {
        byte[] length = derLength(value.length);
        return concat(new byte[]{tag}, length, value);
    }

    private byte[] derLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }
        int bytes = 0;
        int candidate = length;
        while (candidate > 0) {
            bytes++;
            candidate >>= 8;
        }
        ByteBuffer buffer = ByteBuffer.allocate(bytes + 1);
        buffer.put((byte) (0x80 | bytes));
        for (int shift = (bytes - 1) * 8; shift >= 0; shift -= 8) {
            buffer.put((byte) (length >> shift));
        }
        return buffer.array();
    }

    private byte[] concat(byte[]... values) {
        int length = 0;
        for (byte[] value : values) {
            length += value.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] value : values) {
            buffer.put(value);
        }
        return buffer.array();
    }

    private URI resolveUri(PaymentProviderSettingsDTO settings, String path) {
        String baseUrl = StringUtils.hasText(settings.getApiBaseUrl())
                ? settings.getApiBaseUrl().trim()
                : DEFAULT_API_BASE_URL;
        URI base = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(base.getScheme())
                && !"http".equalsIgnoreCase(base.getScheme())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "WeChat Pay API base URL must use HTTP or HTTPS");
        }
        return URI.create(baseUrl.replaceAll("/+$", "") + path);
    }

    private JsonNode readJson(String json, String description) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, description + " is not valid JSON");
        }
    }

    BizException providerRequestFailure(int statusCode, String responseBody) {
        ProviderError providerError = extractProviderError(responseBody);
        String providerCode = StringUtils.hasText(providerError.code())
                ? providerError.code().trim().toUpperCase(Locale.ROOT)
                : "HTTP_" + statusCode;
        String providerMessage = StringUtils.hasText(providerError.message())
                ? providerError.message().trim()
                : "unknown provider error";
        return new BizException(
                ErrorCode.BIZ_ERROR,
                "WeChat Pay API request failed with status " + statusCode
                        + ": " + providerCode + " " + providerMessage,
                providerUserMessage(providerCode, providerMessage)
        );
    }

    private ProviderError extractProviderError(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return new ProviderError("", "empty response");
        }
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            String code = response.path("code").asText("");
            String message = response.path("message").asText("");
            return new ProviderError(code, StringUtils.hasText(message) ? message : "unknown provider error");
        } catch (Exception ignored) {
            return new ProviderError("", "unparseable provider error");
        }
    }

    private String providerUserMessage(String providerCode, String providerMessage) {
        return switch (providerCode) {
            case "SIGN_ERROR" ->
                    "微信支付签名校验未通过，请检查商户证书序列号与商户私钥是否匹配";
            case "APPID_MCHID_NOT_MATCH" ->
                    "微信支付 AppID 与商户号不匹配，请检查该 AppID 是否已绑定当前商户号";
            case "MCH_NOT_EXISTS" ->
                    "微信支付商户号不存在或不可用，请检查商户号配置";
            case "NO_AUTH" ->
                    "当前商户号未开通微信支付对应产品，请先在微信支付商户平台开通";
            case "PARAM_ERROR" ->
                    "微信支付请求参数错误（PARAM_ERROR）：" + providerMessage;
            case "OUT_TRADE_NO_USED" ->
                    "微信支付订单号已被使用，请重新创建订单";
            case "ORDERPAID" ->
                    "该微信支付订单已支付，请刷新订单状态";
            case "ORDER_CLOSED" ->
                    "该微信支付订单已关闭，请重新创建订单";
            case "FREQUENCY_LIMITED" ->
                    "微信支付请求过于频繁，请稍后重试";
            case "SYSTEM_ERROR", "BANK_ERROR" ->
                    "微信支付系统暂时繁忙，请稍后重试";
            default ->
                    "微信支付下单失败（" + providerCode + "），请检查微信商户配置后重试";
        };
    }

    private void requireMerchantSettings(PaymentProviderSettingsDTO settings) {
        if (settings == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "WeChat Pay settings are required");
        }
        requireText(settings.getAppId(), "WeChat Pay App ID");
        requireText(settings.getMerchantId(), "WeChat Pay merchant ID");
        requireText(settings.getMerchantSerialNo(), "Merchant certificate serial number");
        requireText(settings.getPrivateKey(), "WeChat Pay merchant private key");
    }

    private void requireEnabledScene(PaymentProviderSettingsDTO settings, String scene) {
        if (settings.getEnabledScenes() != null
                && !settings.getEnabledScenes().isEmpty()
                && settings.getEnabledScenes().stream().noneMatch(value -> scene.equalsIgnoreCase(value))) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "WeChat Pay scene is not enabled: " + scene);
        }
    }

    private String resolveScene(Map<String, Object> metadata) {
        String scene = firstMetadataText(metadata, "paymentScene", "scene", "tradeType");
        if (!StringUtils.hasText(scene)) {
            return "NATIVE";
        }
        String normalized = scene.trim().toUpperCase(Locale.ROOT);
        if (!"NATIVE".equals(normalized) && !"H5".equals(normalized) && !"JSAPI".equals(normalized)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Unsupported WeChat Pay scene: " + normalized);
        }
        return normalized;
    }

    private String firstMetadataText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            String value = metadataText(metadata, key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return "";
        }
        return String.valueOf(metadata.get(key)).trim();
    }

    private String normalizeH5Type(String h5Type) {
        if (!StringUtils.hasText(h5Type)) {
            return "Wap";
        }
        return switch (h5Type.trim().toLowerCase(Locale.ROOT)) {
            case "ios" -> "iOS";
            case "android" -> "Android";
            default -> "Wap";
        };
    }

    private String normalizeCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : "CNY";
    }

    private String requireResponseText(JsonNode response, String field) {
        String value = response.path(field).asText("");
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "WeChat Pay response is missing " + field);
        }
        return value.trim();
    }

    private String requireJsonText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (!StringUtils.hasText(value)) {
            throw invalidNotification("WeChat Pay notification is missing " + field);
        }
        return value.trim();
    }

    private String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, name + " is required");
        }
        return value.trim();
    }

    private BizException invalidNotification(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message, "Webhook request is invalid");
    }

    private String buildCallbackSignedMessage(String timestamp, String nonce, String rawBody) {
        return timestamp.trim() + "\n" + nonce.trim() + "\n" + rawBody + "\n";
    }

    private void copyText(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        String value = source.path(sourceField).asText("");
        if (StringUtils.hasText(value)) {
            target.put(targetField, value);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < leftBytes.length; index++) {
            difference |= leftBytes[index] ^ rightBytes[index];
        }
        return difference == 0;
    }

    private String encodePathSegment(String value) {
        return urlEncode(value).replace("+", "%20");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Unable to serialize WeChat Pay request");
        }
    }

    public record WechatPaymentResult(String scene, String paymentUrl, String responseBody) {
    }

    private record ProviderError(String code, String message) {
    }

    public record WechatNotification(
            String eventId,
            String eventType,
            String normalizedPayload,
            String decryptedResource,
            String merchantId,
            String appId
    ) {
    }
}
