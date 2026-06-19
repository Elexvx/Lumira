package com.lumira.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.auth.WechatAuthorizeUrlDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Service
public class WechatLoginService {

    private static final Logger log = LoggerFactory.getLogger(WechatLoginService.class);
    private static final long SETTINGS_CACHE_TTL_MS = 15_000L;
    private static final String SCOPE = "snsapi_login";
    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final Long PLATFORM_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;

    private final SystemInternalApi systemInternalApi;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile WechatLoginSettingsDTO cachedSettings;
    private volatile long cachedSettingsUntilMillis;

    public WechatLoginService(
            SystemInternalApi systemInternalApi,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.systemInternalApi = systemInternalApi;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WechatAuthorizeUrlDTO createAuthorizeUrl() {
        WechatLoginSettingsDTO settings = requireAvailableSettings();
        String state = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(CacheKeyConstants.wechatLoginStateKey(state), "1", stateTtl(settings));
        String redirectUri = URLEncoder.encode(settings.redirectUri().trim(), StandardCharsets.UTF_8);
        String authorizeUrl = AUTHORIZE_URL
                + "?appid=" + encode(settings.appId())
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + SCOPE
                + "&state=" + state
                + "#wechat_redirect";
        return new WechatAuthorizeUrlDTO(authorizeUrl, state);
    }

    public WechatOAuthUser exchangeCode(String code, String state) {
        WechatLoginSettingsDTO settings = requireAvailableSettings();
        String cachedState = redisTemplate.opsForValue().getAndDelete(CacheKeyConstants.wechatLoginStateKey(state.trim()));
        if (!StringUtils.hasText(cachedState)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "微信授权状态已失效，请重新扫码登录");
        }

        URI uri = UriComponentsBuilder.fromUriString(ACCESS_TOKEN_URL)
                .queryParam("appid", settings.appId().trim())
                .queryParam("secret", settings.appSecret().trim())
                .queryParam("code", code.trim())
                .queryParam("grant_type", "authorization_code")
                .build()
                .toUri();
        WechatAccessTokenResponse response = requestWechat(uri, WechatAccessTokenResponse.class);
        if (response.errcode() != null && response.errcode() != 0) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "微信授权失败: " + response.errmsg(), "微信授权失败，请重新扫码登录");
        }
        if (!StringUtils.hasText(response.openid())) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "微信授权失败，未返回 openid", "微信授权失败，请重新扫码登录");
        }
        return new WechatOAuthUser(response.openid(), response.unionid(), response.scope());
    }

    private <T> T requestWechat(URI uri, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(ErrorCode.LOGIN_FAILED, "微信接口调用失败，HTTP " + response.statusCode(), "微信授权失败，请稍后重试");
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.LOGIN_FAILED, "微信接口响应解析失败", "微信授权失败，请稍后重试");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.LOGIN_FAILED, "微信接口调用被中断", "微信授权失败，请稍后重试");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8);
    }

    private WechatLoginSettingsDTO requireAvailableSettings() {
        WechatLoginSettingsDTO cached = loadSettings();
        if (cached == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信登录未启用或配置不完整", "微信登录暂不可用");
        }
        return cached;
    }

    private WechatLoginSettingsDTO loadSettings() {
        long now = System.currentTimeMillis();
        WechatLoginSettingsDTO cached = cachedSettings;
        if (cached != null && now < cachedSettingsUntilMillis) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            cached = cachedSettings;
            if (cached != null && now < cachedSettingsUntilMillis) {
                return cached;
            }
            WechatLoginSettingsDTO loaded = loadSettingsFresh();
            if (loaded != null) {
                cachedSettings = loaded;
                cachedSettingsUntilMillis = now + SETTINGS_CACHE_TTL_MS;
            }
            return loaded;
        }
    }

    private WechatLoginSettingsDTO loadSettingsFresh() {
        try {
            WechatLoginSettingsDTO settings = systemInternalApi.wechatLoginSettings(PLATFORM_TENANT_ID);
            if (settings != null
                    && settings.configured()
                    && StringUtils.hasText(settings.appId())
                    && StringUtils.hasText(settings.appSecret())
                    && StringUtils.hasText(settings.redirectUri())) {
                return settings;
            }
            WechatLoginSettingsDTO cached = cachedSettings;
            if (cached != null && StringUtils.hasText(cached.appId()) && StringUtils.hasText(cached.appSecret()) && StringUtils.hasText(cached.redirectUri())) {
                log.warn("Failed to refresh Wechat login settings from system-service, using cached snapshot");
                return cached;
            }
        } catch (Exception ex) {
            WechatLoginSettingsDTO cached = cachedSettings;
            if (cached != null && StringUtils.hasText(cached.appId()) && StringUtils.hasText(cached.appSecret()) && StringUtils.hasText(cached.redirectUri())) {
                log.warn("Failed to load Wechat login settings from system-service, using cached snapshot", ex);
                return cached;
            }
        }
        throw new BizException(ErrorCode.BIZ_ERROR, "微信登录未启用或配置不完整", "微信登录暂不可用");
    }

    private Duration stateTtl(WechatLoginSettingsDTO settings) {
        return Duration.ofMinutes(Math.max(1, settings.stateExpireMinutes()));
    }

    public record WechatOAuthUser(String openid, String unionid, String scope) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WechatAccessTokenResponse(
            String access_token,
            Integer expires_in,
            String refresh_token,
            String openid,
            String scope,
            String unionid,
            Integer errcode,
            String errmsg
    ) {
    }
}
