package com.lumira.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatOfficialAccountNotificationServiceTest {

    @Test
    void isConfiguredReadsWechatRuntimeConfigInsteadOfGenericPlatformConfig() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.wechatOfficialRuntimeConfigValues())
                .thenReturn(Map.of(
                        "notification.wechat-official.enabled", "true",
                        "notification.wechat-official.app-id", "wx-app-id",
                        "notification.wechat-official.app-secret", "wx-secret",
                        "notification.wechat-official.template-id", "template-1",
                        "notification.wechat-official.detail-url", "https://example.com/detail"
                ));
        WechatOfficialAccountNotificationService service = new WechatOfficialAccountNotificationService(
                systemInternalApi,
                mock(CacheTemplate.class),
                new ObjectMapper()
        );

        assertThat(service.isConfigured()).isTrue();

        verify(systemInternalApi).wechatOfficialRuntimeConfigValues();
    }
}
