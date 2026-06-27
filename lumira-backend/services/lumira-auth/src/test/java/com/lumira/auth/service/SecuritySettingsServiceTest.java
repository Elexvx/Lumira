package com.lumira.auth.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecuritySettingsServiceTest {

    @Test
    void snapshotReusesCachedSettingsWhenBootstrapVersionStable() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        AuthSecurityProperties securityProperties = new AuthSecurityProperties();
        SecuritySettingsDTO settings = new SecuritySettingsDTO(
                1800L,
                7200L,
                129600L,
                true,
                false,
                "IMAGE",
                10L,
                5L,
                20L,
                300L,
                60L
        );
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L);
        when(systemInternalApi.securitySettings()).thenReturn(settings);

        SecuritySettingsService service = new SecuritySettingsService(securityProperties, systemInternalApi);

        SecuritySettingsDTO first = service.snapshot();
        SecuritySettingsDTO second = service.snapshot();

        assertEquals(1800L, first.idleTimeoutSeconds());
        assertEquals(1800L, second.idleTimeoutSeconds());
        verify(systemInternalApi, times(1)).readModelVersion("platform", "public-bootstrap");
        verify(systemInternalApi, times(1)).securitySettings();
    }

    @Test
    void snapshotReloadsWhenBootstrapVersionChanges() throws Exception {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        AuthSecurityProperties securityProperties = new AuthSecurityProperties();
        SecuritySettingsDTO firstSettings = new SecuritySettingsDTO(
                1800L,
                7200L,
                129600L,
                true,
                false,
                "IMAGE",
                10L,
                5L,
                20L,
                300L,
                60L
        );
        SecuritySettingsDTO secondSettings = new SecuritySettingsDTO(
                900L,
                3600L,
                86400L,
                false,
                true,
                "IMAGE",
                8L,
                3L,
                10L,
                180L,
                30L
        );
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L, 12L);
        when(systemInternalApi.securitySettings()).thenReturn(firstSettings, secondSettings);

        SecuritySettingsService service = new SecuritySettingsService(securityProperties, systemInternalApi);

        SecuritySettingsDTO first = service.snapshot();
        Thread.sleep(2100L);
        SecuritySettingsDTO second = service.snapshot();

        assertEquals(1800L, first.idleTimeoutSeconds());
        assertEquals(900L, second.idleTimeoutSeconds());
        verify(systemInternalApi, times(2)).readModelVersion("platform", "public-bootstrap");
        verify(systemInternalApi, times(2)).securitySettings();
    }

    @Test
    void snapshotFallsBackToTtlCacheWhenReadModelVersionUnavailable() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        AuthSecurityProperties securityProperties = new AuthSecurityProperties();
        SecuritySettingsDTO settings = new SecuritySettingsDTO(
                1800L,
                7200L,
                129600L,
                true,
                false,
                "IMAGE",
                10L,
                5L,
                20L,
                300L,
                60L
        );
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap"))
                .thenThrow(new IllegalStateException("version unavailable"));
        when(systemInternalApi.securitySettings()).thenReturn(settings);

        SecuritySettingsService service = new SecuritySettingsService(securityProperties, systemInternalApi);

        SecuritySettingsDTO first = service.snapshot();
        SecuritySettingsDTO second = service.snapshot();

        assertEquals(1800L, first.idleTimeoutSeconds());
        assertEquals(1800L, second.idleTimeoutSeconds());
        verify(systemInternalApi, times(1)).securitySettings();
    }
}
