package com.lumira.saas.modules.system.support;

import com.lumira.common.constant.PlatformConstants;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import com.lumira.saas.modules.system.config.mapper.SysConfigMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpMailServiceTest {

    @Test
    void isConfiguredShouldReuseCachedSnapshot() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("smtp.enabled", "true"),
                        config("smtp.host", "smtp.example.com"),
                        config("smtp.port", "587"),
                        config("smtp.from", "noreply@example.com"),
                        config("smtp.auth-enabled", "false")
                ));

        SmtpMailService service = new SmtpMailService(mapper);

        assertThat(service.isConfigured(PlatformConstants.PLATFORM_TENANT_ID)).isTrue();
        assertThat(service.isConfigured(PlatformConstants.PLATFORM_TENANT_ID)).isTrue();
        verify(mapper, times(1)).listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any());
    }

    @Test
    void invalidateTenantShouldForceReloadOnNextRead() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        when(mapper.listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any()))
                .thenReturn(List.of(
                        config("smtp.enabled", "true"),
                        config("smtp.host", "smtp.example.com"),
                        config("smtp.port", "587"),
                        config("smtp.from", "noreply@example.com"),
                        config("smtp.auth-enabled", "false")
                ))
                .thenReturn(List.of(
                        config("smtp.enabled", "false"),
                        config("smtp.host", "smtp2.example.com"),
                        config("smtp.port", "465"),
                        config("smtp.from", "noreply2@example.com"),
                        config("smtp.auth-enabled", "true")
                ));

        SmtpMailService service = new SmtpMailService(mapper);

        assertThat(service.isConfigured(PlatformConstants.PLATFORM_TENANT_ID)).isTrue();
        service.invalidateTenant(PlatformConstants.PLATFORM_TENANT_ID);
        assertThat(service.isConfigured(PlatformConstants.PLATFORM_TENANT_ID)).isFalse();
        verify(mapper, times(2)).listEffectiveValues(eq(PlatformConstants.PLATFORM_TENANT_ID), eq("PLATFORM"), any());
    }

    private static SysConfigEntity config(String key, String value) {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        return entity;
    }
}
