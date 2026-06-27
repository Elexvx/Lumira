package com.lumira.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.auth.WechatAuthorizeUrlDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ReadModelVersionCache;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Objects;
import java.util.UUID;

@Service
public class WechatLoginService {

    private static final Logger log = LoggerFactory.getLogger(WechatLoginService.class);
    private static final long SETTINGS_CACHE_TTL_MS = 15_000L;
    private static final long READ_MODEL_VERSION_CACHE_TTL_MS = 2_000L;
    private static final String PUBLIC_BOOTSTRAP_CACHE_KEY = "wechat:platform/public-bootstrap";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";
    private static final String SCOPE = "snsapi_login";
    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private final SystemInternalApi systemInternalApi;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ReadModelVersionCache readModelVersionCache;
    private volatile CachedWechatLoginSettings cachedSettings;

    @Autowired
    public WechatLoginService(
            SystemInternalApi systemInternalApi,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this(systemInternalApi, redisTemplate, objectMapper, new ReadModelVersionCache(READ_MODEL_VERSION_CACHE_TTL_MS));
    }

    public WechatLoginService(
            SystemInternalApi systemInternalApi,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ReadModelVersionCache readModelVersionCache
    ) {
        this.systemInternalApi = systemInternalApi;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.readModelVersionCache = readModelVersionCache;
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
        Long publicBootstrapVersion = currentPublicBootstrapVersion();
        CachedWechatLoginSettings cached = cachedSettings;
        if (isSettingsCacheCurrent(cached, publicBootstrapVersion, now)) {
            return cached.settings();
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            publicBootstrapVersion = currentPublicBootstrapVersion();
            cached = cachedSettings;
            if (isSettingsCacheCurrent(cached, publicBootstrapVersion, now)) {
                return cached.settings();
            }
            WechatLoginSettingsDTO loaded = loadSettingsFresh();
            if (loaded != null) {
                cachedSettings = new CachedWechatLoginSettings(
                        loaded,
                        publicBootstrapVersion,
                        now + SETTINGS_CACHE_TTL_MS
                );
            }
            return loaded;
        }
    }

    private WechatLoginSettingsDTO loadSettingsFresh() {
        try {
            WechatLoginSettingsDTO settings = systemInternalApi.wechatLoginSettings();
            if (settings != null
                    && settings.configured()
                    && StringUtils.hasText(settings.appId())
                    && StringUtils.hasText(settings.appSecret())
                    && StringUtils.hasText(settings.redirectUri())) {
                return settings;
            }
            WechatLoginSettingsDTO cached = cachedSettings == null ? null : cachedSettings.settings();
            if (cached != null && StringUtils.hasText(cached.appId()) && StringUtils.hasText(cached.appSecret()) && StringUtils.hasText(cached.redirectUri())) {
                log.warn("Failed to refresh Wechat login settings from system-service, using cached snapshot");
                return cached;
            }
        } catch (Exception ex) {
            WechatLoginSettingsDTO cached = cachedSettings == null ? null : cachedSettings.settings();
            if (cached != null && StringUtils.hasText(cached.appId()) && StringUtils.hasText(cached.appSecret()) && StringUtils.hasText(cached.redirectUri())) {
                log.warn("Failed to load Wechat login settings from system-service, using cached snapshot", ex);
                return cached;
            }
        }
        throw new BizException(ErrorCode.BIZ_ERROR, "微信登录未启用或配置不完整", "微信登录暂不可用");
    }

    private boolean isSettingsCacheCurrent(
            CachedWechatLoginSettings cached,
            Long publicBootstrapVersion,
            long now
    ) {
        if (cached == null || now >= cached.expiresAtEpochMillis()) {
            return false;
        }
        if (publicBootstrapVersion != null) {
            return Objects.equals(cached.publicBootstrapVersion(), publicBootstrapVersion);
        }
        return true;
    }

    private Long currentPublicBootstrapVersion() {
        try {
            return readModelVersionCache.readValue(
                    PUBLIC_BOOTSTRAP_CACHE_KEY,
                    READ_MODEL_VERSION_CACHE_TTL_MS,
                    () -> systemInternalApi.readModelVersion(
                            READ_MODEL_CONTEXT_PLATFORM,
                            READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP
                    )
            );
        } catch (Exception ex) {
            log.debug("Failed to read public bootstrap version for Wechat login settings", ex);
            return null;
        }
    }

    private Duration stateTtl(WechatLoginSettingsDTO settings) {
        return Duration.ofMinutes(Math.max(1, settings.stateExpireMinutes()));
    }

    public record WechatOAuthUser(String openid, String unionid, String scope) {
    }

    private record CachedWechatLoginSettings(
            WechatLoginSettingsDTO settings,
            Long publicBootstrapVersion,
            long expiresAtEpochMillis
    ) {
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
