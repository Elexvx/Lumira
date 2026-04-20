package com.yourcompany.saas.modules.message.service;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.redis.CacheTemplate;
import com.yourcompany.saas.modules.message.config.MessageOpenApiProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class OpenApiSignatureService {

    public static final String HEADER_APP_ID = "X-OpenAPI-App-Id";
    public static final String HEADER_TIMESTAMP = "X-OpenAPI-Timestamp";
    public static final String HEADER_NONCE = "X-OpenAPI-Nonce";
    public static final String HEADER_SIGNATURE = "X-OpenAPI-Signature";
    public static final String REQUEST_ATTR_APP_ID = "message.openapi.appId";

    private static final String NONCE_KEY_PREFIX = "message:openapi:nonce:";

    private final MessageOpenApiProperties openApiProperties;
    private final CacheTemplate cacheTemplate;

    public OpenApiSignatureService(MessageOpenApiProperties openApiProperties, CacheTemplate cacheTemplate) {
        this.openApiProperties = openApiProperties;
        this.cacheTemplate = cacheTemplate;
    }

    public String authenticate(CachedBodyHttpServletRequest request) {
        if (!openApiProperties.isEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "开放接口已关闭");
        }

        String appId = trimToNull(request.getHeader(HEADER_APP_ID));
        String timestampHeader = trimToNull(request.getHeader(HEADER_TIMESTAMP));
        String nonce = trimToNull(request.getHeader(HEADER_NONCE));
        String signature = trimToNull(request.getHeader(HEADER_SIGNATURE));
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(timestampHeader) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "开放接口签名参数缺失");
        }
        if (!openApiProperties.getAppId().equals(appId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "开放接口调用方不匹配");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "开放接口时间戳格式错误");
        }

        long now = Instant.now().toEpochMilli();
        long skewMillis = openApiProperties.getTimestampSkewSeconds() * 1000L;
        if (Math.abs(now - timestamp) > skewMillis) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "开放接口请求已过期");
        }

        String canonical = buildCanonicalRequest(request, timestampHeader, nonce);
        String expectedSignature = sign(canonical, openApiProperties.getAppSecret());
        if (!expectedSignature.equalsIgnoreCase(signature)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "开放接口签名校验失败");
        }

        String nonceKey = NONCE_KEY_PREFIX + appId + ":" + nonce;
        boolean firstSeen = cacheTemplate.putIfAbsent(nonceKey, "1", java.time.Duration.ofSeconds(openApiProperties.getNonceTtlSeconds()));
        if (!firstSeen) {
            throw new BizException(ErrorCode.FORBIDDEN, "开放接口请求已被重复提交");
        }
        request.setAttribute(REQUEST_ATTR_APP_ID, appId);
        return appId;
    }

    private String buildCanonicalRequest(CachedBodyHttpServletRequest request, String timestamp, String nonce) {
        String bodyHash = sha256Hex(request.getBody());
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();
        return String.join("\n", method, path, timestamp, nonce, bodyHash);
    }

    private String sign(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signed = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signed);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "开放接口签名计算失败");
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "开放接口摘要计算失败");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
