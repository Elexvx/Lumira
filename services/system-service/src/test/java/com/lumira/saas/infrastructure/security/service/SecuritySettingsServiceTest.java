package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.constant.PlatformConstants;
import com.lumira.saas.infrastructure.security.SecurityProperties;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecuritySettingsServiceTest {

    @Test
    void loadSettingsUsesBatchQueryAndCachesSnapshot() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any())).thenReturn(List.of(
                config("security.idle-timeout-seconds", "900"),
                config("security.allow-multi-device-login", "0")
        ));

        SecuritySettingsService service = new SecuritySettingsService(mapper, new SecurityProperties());

        assertEquals(900L, service.getIdleTimeoutSeconds());
        assertFalse(service.isAllowMultiDeviceLogin());
        assertEquals(900L, service.getIdleTimeoutSeconds());

        verify(mapper, times(1)).listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any());
    }

    @Test
    void updateSettingsInvalidatesCachedSnapshot() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any()))
                .thenReturn(List.of(config("security.idle-timeout-seconds", "900")))
                .thenReturn(List.of(config("security.idle-timeout-seconds", "1200")));

        SecuritySettingsService service = new SecuritySettingsService(mapper, new SecurityProperties());
        assertEquals(900L, service.getIdleTimeoutSeconds());

        SecuritySettingsService.SecuritySettingsSnapshot request = service.loadSettings();
        request.setIdleTimeoutSeconds(1200L);
        service.updateSettings(request);

        assertEquals(1200L, service.getIdleTimeoutSeconds());
        verify(mapper, times(2)).listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any());
    }

    private static SysConfigEntity config(String key, String value) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        return entity;
    }
}
