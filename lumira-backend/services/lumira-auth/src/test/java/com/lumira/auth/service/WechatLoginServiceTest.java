package com.lumira.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.common.constant.PlatformConstants;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(systemInternalApi.wechatLoginSettings(PlatformConstants.PLATFORM_TENANT_ID)).thenReturn(
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
        verify(systemInternalApi, times(1)).wechatLoginSettings(PlatformConstants.PLATFORM_TENANT_ID);
        verify(valueOperations, times(2)).set(anyString(), eq("1"), any(Duration.class));
    }
}
