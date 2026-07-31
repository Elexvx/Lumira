package com.lumira.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ReadModelVersionCache;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatLoginServiceTest {

    @Test
    void createAuthorizeUrlShouldReuseCachedSettingsSnapshot() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(
                new WechatLoginSettingsDTO(
                        true,
                        "appid-1",
                        "secret-1",
                        "https://example.com/callback",
                        15,
                        true,
                        true
                )
        );

        WechatLoginService service = new WechatLoginService(systemInternalApi, redisTemplate, new ObjectMapper());

        var first = service.createAuthorizeUrl();
        var second = service.createAuthorizeUrl();

        assertThat(first.authorizeUrl()).contains("appid-1");
        assertThat(second.authorizeUrl()).contains("appid-1");
        verify(systemInternalApi, times(1)).wechatLoginSettings();
        verify(systemInternalApi, times(1)).readModelVersion("platform", "public-bootstrap");
        verify(valueOperations, times(2)).set(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void createAuthorizeUrlShouldRefreshWhenPublicBootstrapVersionChanges() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L, 12L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(
                new WechatLoginSettingsDTO(
                        true,
                        "appid-1",
                        "secret-1",
                        "https://example.com/callback-1",
                        15,
                        true,
                        true
                ),
                new WechatLoginSettingsDTO(
                        true,
                        "appid-2",
                        "secret-2",
                        "https://example.com/callback-2",
                        15,
                        true,
                        true
                )
        );

        WechatLoginService service = new WechatLoginService(systemInternalApi, redisTemplate, new ObjectMapper());

        var first = service.createAuthorizeUrl();
        Thread.sleep(2100L);
        var second = service.createAuthorizeUrl();

        assertThat(first.authorizeUrl()).contains("appid-1");
        assertThat(second.authorizeUrl()).contains("appid-2");
        verify(systemInternalApi, times(2)).wechatLoginSettings();
        verify(systemInternalApi, times(2)).readModelVersion("platform", "public-bootstrap");
        verify(valueOperations, times(2)).set(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void createAuthorizeUrlShouldRejectConfiguredButDisabledSettings() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(
                new WechatLoginSettingsDTO(
                        false,
                        "appid-1",
                        "secret-1",
                        "https://example.com/callback",
                        15,
                        true,
                        true
                )
        );

        WechatLoginService service = new WechatLoginService(systemInternalApi, redisTemplate, new ObjectMapper());

        assertThatThrownBy(service::createAuthorizeUrl)
                .isInstanceOf(com.lumira.common.exception.BizException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exchangeCodeShouldFollowWebsiteLoginFlowAndLoadProfile() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        HttpResponse<String> profileResponse = mock(HttpResponse.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn("1");
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(availableSettings());
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn(
                """
                        {
                          "access_token": "oauth-access-token",
                          "openid": "openid-1",
                          "unionid": "unionid-1",
                          "scope": "snsapi_login"
                        }
                        """
        );
        when(profileResponse.statusCode()).thenReturn(200);
        when(profileResponse.body()).thenReturn(
                """
                        {
                          "openid": "openid-1",
                          "unionid": "unionid-1",
                          "nickname": "Wechat User",
                          "headimgurl": "https://example.com/avatar.png"
                        }
                        """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse, profileResponse);

        WechatLoginService service = new WechatLoginService(
                systemInternalApi,
                redisTemplate,
                new ObjectMapper(),
                new ReadModelVersionCache(2_000L),
                httpClient
        );

        WechatLoginService.WechatOAuthUser user = service.exchangeCode("code-1", "state-1");

        assertThat(user.openid()).isEqualTo("openid-1");
        assertThat(user.unionid()).isEqualTo("unionid-1");
        assertThat(user.scope()).isEqualTo("snsapi_login");
        assertThat(user.nickname()).isEqualTo("Wechat User");
        assertThat(user.avatarUrl()).isEqualTo("https://example.com/avatar.png");
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exchangeCodeShouldContinueWhenOptionalWechatProfileIsUnavailable() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        HttpResponse<String> profileResponse = mock(HttpResponse.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn("1");
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(availableSettings());
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn(
                """
                        {
                          "access_token": "oauth-access-token",
                          "openid": "openid-1",
                          "unionid": "unionid-1",
                          "scope": "snsapi_login"
                        }
                        """
        );
        when(profileResponse.statusCode()).thenReturn(200);
        when(profileResponse.body()).thenReturn(
                """
                        {
                          "errcode": 48001,
                          "errmsg": "api unauthorized"
                        }
                        """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse, profileResponse);

        WechatLoginService service = new WechatLoginService(
                systemInternalApi,
                redisTemplate,
                new ObjectMapper(),
                new ReadModelVersionCache(2_000L),
                httpClient
        );

        WechatLoginService.WechatOAuthUser user = service.exchangeCode("code-1", "state-1");

        assertThat(user.openid()).isEqualTo("openid-1");
        assertThat(user.unionid()).isEqualTo("unionid-1");
        assertThat(user.scope()).isEqualTo("snsapi_login");
        assertThat(user.nickname()).isNull();
        assertThat(user.avatarUrl()).isNull();
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exchangeCodeShouldRejectUnexpectedOAuthScope() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn("1");
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(availableSettings());
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn(
                """
                        {
                          "access_token": "oauth-access-token",
                          "openid": "openid-1",
                          "scope": "snsapi_userinfo"
                        }
                        """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse);

        WechatLoginService service = new WechatLoginService(
                systemInternalApi,
                redisTemplate,
                new ObjectMapper(),
                new ReadModelVersionCache(2_000L),
                httpClient
        );

        assertThatThrownBy(() -> service.exchangeCode("code-1", "state-1"))
                .isInstanceOf(com.lumira.common.exception.BizException.class)
                .hasMessageContaining("snsapi_login");
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exchangeCodeShouldStillRejectWechatTokenExchangeFailure() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn("1");
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(availableSettings());
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn(
                """
                        {
                          "errcode": 40029,
                          "errmsg": "invalid code"
                        }
                        """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse);

        WechatLoginService service = new WechatLoginService(
                systemInternalApi,
                redisTemplate,
                new ObjectMapper(),
                new ReadModelVersionCache(2_000L),
                httpClient
        );

        assertThatThrownBy(() -> service.exchangeCode("code-1", "state-1"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getMessage()).contains("40029", "invalid code");
                    assertThat(exception.getUserMessage()).contains("授权码无效或已过期", "40029");
                });
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void exchangeCodeShouldExplainInvalidWechatAppSecret() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn("1");
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.wechatLoginSettings()).thenReturn(availableSettings());
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn(
                """
                        {
                          "errcode": 40125,
                          "errmsg": "invalid appsecret"
                        }
                        """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tokenResponse);

        WechatLoginService service = new WechatLoginService(
                systemInternalApi,
                redisTemplate,
                new ObjectMapper(),
                new ReadModelVersionCache(2_000L),
                httpClient
        );

        assertThatThrownBy(() -> service.exchangeCode("code-1", "state-1"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getMessage()).contains("40125", "invalid appsecret");
                    assertThat(exception.getUserMessage()).contains("AppSecret", "40125");
                });
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private WechatLoginSettingsDTO availableSettings() {
        return new WechatLoginSettingsDTO(
                true,
                "appid-1",
                "secret-1",
                "https://example.com/callback",
                15,
                true,
                true
        );
    }
}
