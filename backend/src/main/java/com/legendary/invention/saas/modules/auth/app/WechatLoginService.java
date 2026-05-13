package com.legendary.invention.saas.modules.auth.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.constant.CacheKeyConstants;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.redis.CacheTemplate;
import com.legendary.invention.saas.modules.auth.config.WechatLoginProperties;
import com.legendary.invention.saas.modules.auth.vo.WechatAuthorizeUrlVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

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

    private static final String SCOPE = "snsapi_login";
    private static final String AUTHORIZE_URL = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private final WechatLoginProperties properties;
    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WechatLoginService(
            WechatLoginProperties properties,
            CacheTemplate cacheTemplate,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isAvailable() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getAppId())
                && StringUtils.hasText(properties.getAppSecret())
                && StringUtils.hasText(properties.getRedirectUri());
    }

    public WechatAuthorizeUrlVO createAuthorizeUrl() {
        ensureAvailable();
        String state = UUID.randomUUID().toString().replace("-", "");
        cacheTemplate.put(CacheKeyConstants.wechatLoginStateKey(state), "1", stateTtl());

        String redirectUri = URLEncoder.encode(properties.getRedirectUri().trim(), StandardCharsets.UTF_8);
        String authorizeUrl = AUTHORIZE_URL
                + "?appid=" + encode(properties.getAppId())
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=" + SCOPE
                + "&state=" + state
                + "#wechat_redirect";

        WechatAuthorizeUrlVO vo = new WechatAuthorizeUrlVO();
        vo.setAuthorizeUrl(authorizeUrl);
        vo.setState(state);
        return vo;
    }

    public WechatOAuthUser exchangeCode(String code, String state) {
        ensureAvailable();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "微信授权参数不完整");
        }
        String cachedState = cacheTemplate.getAndRemove(CacheKeyConstants.wechatLoginStateKey(state.trim()));
        if (!StringUtils.hasText(cachedState)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "微信授权状态已失效，请重新扫码登录");
        }

        URI uri = UriComponentsBuilder.fromUriString(ACCESS_TOKEN_URL)
                .queryParam("appid", properties.getAppId().trim())
                .queryParam("secret", properties.getAppSecret().trim())
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
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
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

    private void ensureAvailable() {
        if (!isAvailable()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "微信登录未启用或配置不完整", "微信登录暂不可用");
        }
    }

    private Duration stateTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getStateExpireMinutes()));
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
