package com.lumira.message.service;

import com.lumira.api.client.SystemInternalApi;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpNotificationMailServiceTest {

    @Test
    void isConfiguredReadsSmtpRuntimeConfigInsteadOfGenericPlatformConfig() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.smtpRuntimeConfigValues())
                .thenReturn(Map.of(
                        "smtp.host", "smtp.example.com",
                        "smtp.port", "587",
                        "smtp.from", "noreply@example.com",
                        "smtp.auth-enabled", "false"
                ));
        SmtpNotificationMailService service = new SmtpNotificationMailService(systemInternalApi);

        assertThat(service.isConfigured()).isTrue();

        verify(systemInternalApi).smtpRuntimeConfigValues();
    }
}
