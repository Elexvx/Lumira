package com.lumira.saas.modules.system.verification;

import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatLoginSettingsServiceTest {

    @Test
    void loadSettingsShouldReuseCachedSnapshots() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "true"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", "secret-1"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "15")
                ));

        WechatLoginSettingsService service = new WechatLoginSettingsService(mapper, new WechatLoginProperties(), cryptoService());

        WechatLoginSettingsService.WechatLoginSettingsRecord first = service.loadSettings(PlatformConstants.PLATFORM_TENANT_ID);
        WechatLoginSettingsService.WechatLoginSettingsRecord second = service.loadSettings(PlatformConstants.PLATFORM_TENANT_ID);

        assertThat(first.enabled()).isTrue();
        assertThat(second.configured()).isTrue();
        verify(mapper, times(1)).listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any());
    }

    @Test
    void updateSettingsShouldInvalidateCachedSnapshot() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "true"),
                        config("verification.wechat-login.app-id", "appid-1"),
                        config("verification.wechat-login.app-secret", "secret-1"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/callback"),
                        config("verification.wechat-login.state-expire-minutes", "15")
                ))
                .thenReturn(List.of(
                        config("verification.wechat-login.enabled", "false"),
                        config("verification.wechat-login.app-id", "appid-2"),
                        config("verification.wechat-login.app-secret", "secret-2"),
                        config("verification.wechat-login.redirect-uri", "https://example.com/new-callback"),
                        config("verification.wechat-login.state-expire-minutes", "20")
                ));

        WechatLoginSettingsService service = new WechatLoginSettingsService(mapper, new WechatLoginProperties(), cryptoService());
        WechatLoginSettingsService.WechatLoginSettingsRecord before = service.loadSettings(PlatformConstants.PLATFORM_TENANT_ID);
        assertThat(before.appId()).isEqualTo("appid-1");

        SystemDTO.WechatLoginSettingsRequest request = new SystemDTO.WechatLoginSettingsRequest();
        request.setEnabled(Boolean.FALSE);
        request.setAppId("appid-2");
        request.setAppSecret("secret-2");
        request.setRedirectUri("https://example.com/new-callback");
        request.setStateExpireMinutes(20);

        SystemVO.WechatLoginSettingsVO updated = service.updateSettings(PlatformConstants.PLATFORM_TENANT_ID, 9L, request);

        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getAppId()).isEqualTo("appid-2");
        verify(mapper, times(2)).listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any());
        verify(mapper, times(5)).upsertPlatformConfig(any());
    }

    private static FieldCryptoService cryptoService() {
        FieldCryptoService fieldCryptoService = Mockito.mock(FieldCryptoService.class);
        when(fieldCryptoService.encrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fieldCryptoService.decrypt(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return fieldCryptoService;
    }

    private static SysConfigEntity config(String key, String value) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        return entity;
    }
}
