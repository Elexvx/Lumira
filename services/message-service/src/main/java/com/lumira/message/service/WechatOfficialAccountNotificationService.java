package com.lumira.message.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WechatOfficialAccountNotificationService {

    private static final String ENABLED_KEY = "notification.wechat-official.enabled";
    private static final String APP_ID_KEY = "notification.wechat-official.app-id";
    private static final String APP_SECRET_KEY = "notification.wechat-official.app-secret";
    private static final String TEMPLATE_ID_KEY = "notification.wechat-official.template-id";
    private static final String DETAIL_URL_KEY = "notification.wechat-official.detail-url";
    private static final List<String> CONFIG_KEYS = List.of(
            ENABLED_KEY,
            APP_ID_KEY,
            APP_SECRET_KEY,
            TEMPLATE_ID_KEY,
            DETAIL_URL_KEY
    );
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String TEMPLATE_SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send";

    private final SystemInternalApi systemInternalApi;
    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WechatOfficialAccountNotificationService(
            SystemInternalApi systemInternalApi,
            CacheTemplate cacheTemplate,
            ObjectMapper objectMapper
    ) {
        this.systemInternalApi = systemInternalApi;
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured(Long tenantId) {
        WechatOfficialAccountSettings settings = loadSettings(tenantId);
        return settings.configured();
    }

    public void send(Long tenantId, String openid, String title, String content) {
        if (!StringUtils.hasText(openid)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "微信 OpenID 不能为空");
        }
        WechatOfficialAccountSettings settings = loadSettings(tenantId);
        if (!settings.configured()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号通知未启用或配置不完整");
        }
        String accessToken = accessToken(tenantId, settings);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", openid.trim());
        payload.put("template_id", settings.templateId());
        if (StringUtils.hasText(settings.detailUrl())) {
            payload.put("url", settings.detailUrl());
        }
        payload.put("data", Map.of(
                "first", templateValue(defaultIfBlank(title, "系统通知")),
                "keyword1", templateValue(defaultIfBlank(title, "系统通知")),
                "keyword2", templateValue(abbreviate(defaultIfBlank(content, ""), 180)),
                "remark", templateValue("请登录系统查看完整通知内容")
        ));
        WechatApiResponse response = postJson(
                URI.create(TEMPLATE_SEND_URL + "?access_token=" + accessToken),
                payload,
                WechatApiResponse.class
        );
        if (response.errcode() != null && response.errcode() != 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号通知发送失败: " + response.errmsg());
        }
    }

    private String accessToken(Long tenantId, WechatOfficialAccountSettings settings) {
        String cacheKey = "notification:wechat-official:access-token:" + effectiveTenantId(tenantId) + ":" + settings.appId();
        String cached = cacheTemplate.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        URI uri = URI.create(ACCESS_TOKEN_URL
                + "?grant_type=client_credential"
                + "&appid=" + encode(settings.appId())
                + "&secret=" + encode(settings.appSecret()));
        WechatAccessTokenResponse response = getJson(uri, WechatAccessTokenResponse.class);
        if (response.errcode() != null && response.errcode() != 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号 access_token 获取失败: " + response.errmsg());
        }
        if (!StringUtils.hasText(response.access_token())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号 access_token 获取失败");
        }
        int ttlSeconds = Math.max(60, response.expires_in() == null ? 6600 : response.expires_in() - 300);
        cacheTemplate.put(cacheKey, response.access_token(), Duration.ofSeconds(ttlSeconds));
        return response.access_token();
    }

    private WechatOfficialAccountSettings loadSettings(Long tenantId) {
        Map<String, String> values = loadValues(tenantId);
        boolean enabled = Boolean.parseBoolean(defaultIfBlank(values.get(ENABLED_KEY), "false"));
        String appId = defaultIfBlank(values.get(APP_ID_KEY), "");
        String appSecret = defaultIfBlank(values.get(APP_SECRET_KEY), "");
        String templateId = defaultIfBlank(values.get(TEMPLATE_ID_KEY), "");
        String detailUrl = defaultIfBlank(values.get(DETAIL_URL_KEY), "");
        boolean configured = enabled
                && StringUtils.hasText(appId)
                && StringUtils.hasText(appSecret)
                && StringUtils.hasText(templateId);
        return new WechatOfficialAccountSettings(enabled, appId, appSecret, templateId, detailUrl, configured);
    }

    private Map<String, String> loadValues(Long tenantId) {
        Long effectiveTenantId = effectiveTenantId(tenantId);
        Map<String, String> rawValues = systemInternalApi.platformConfigValues(effectiveTenantId, CONFIG_KEYS);
        Map<String, String> values = new LinkedHashMap<>();
        if (rawValues != null) {
            rawValues.forEach((key, value) -> values.put(key, value == null ? null : value.trim()));
        }
        return values;
    }

    private <T> T getJson(URI uri, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return send(request, responseType);
    }

    private <T> T postJson(URI uri, Object body, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return send(request, responseType);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号通知请求构造失败");
        }
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号接口调用失败，HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号接口响应解析失败");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.BIZ_ERROR, "微信公众号接口调用被中断");
        }
    }

    private Map<String, String> templateValue(String value) {
        return Map.of("value", defaultIfBlank(value, ""));
    }

    private Long effectiveTenantId(Long tenantId) {
        return tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String encode(String value) {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record WechatOfficialAccountSettings(
            boolean enabled,
            String appId,
            String appSecret,
            String templateId,
            String detailUrl,
            boolean configured
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WechatAccessTokenResponse(
            String access_token,
            Integer expires_in,
            Integer errcode,
            String errmsg
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WechatApiResponse(
            Integer errcode,
            String errmsg,
            Long msgid
    ) {
    }
}
