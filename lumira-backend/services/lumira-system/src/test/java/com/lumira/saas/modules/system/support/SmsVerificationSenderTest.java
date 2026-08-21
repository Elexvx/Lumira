package com.lumira.saas.modules.system.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsVerificationSenderTest {

    @Test
    void debugProviderIsAvailableInDevelopmentWithoutSendingAnExternalMessage() {
        BuiltinMockSmsAvailability availability = mock(BuiltinMockSmsAvailability.class);
        SmsVerificationSender sender = new SmsVerificationSender(new ObjectMapper(), availability);

        SmsVerificationSender.SmsSendResult result = sender.send(debugSettings(), "13800138000", "123456");

        assertThat(result.code()).isEqualTo("OK");
        assertThat(result.requestId()).startsWith("mock-request-");
        assertThat(result.templateParam()).isEqualTo("{\"code\":\"123456\"}");
        verify(availability).requireEnabledForWrite();
    }

    @Test
    void unsupportedProviderIsRejected() {
        SmsVerificationSender sender = new SmsVerificationSender(
                new ObjectMapper(),
                mock(BuiltinMockSmsAvailability.class)
        );
        SmsVerificationSender.SmsSettings unsupported = new SmsVerificationSender.SmsSettings(
                "custom", "sign", "template", "id", "secret", "", "", true
        );

        assertThatThrownBy(() -> sender.send(unsupported, "13800138000", "123456"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("暂不支持的短信服务商");
    }

    @Test
    void providerConfigurationRequirementsAreSeparated() {
        BuiltinMockSmsAvailability availability = mock(BuiltinMockSmsAvailability.class);
        org.mockito.Mockito.when(availability.isEnabled()).thenReturn(true);
        SmsVerificationSender sender = new SmsVerificationSender(new ObjectMapper(), availability);

        assertThat(sender.isConfigured(debugSettings())).isTrue();
        assertThat(sender.isConfigured(new SmsVerificationSender.SmsSettings(
                "aliyun", "sign", "template", "", "", "", "", true
        ))).isFalse();
        assertThat(sender.isConfigured(new SmsVerificationSender.SmsSettings(
                "aliyun", "sign", "template", "access-id", "access-secret", "", "", true
        ))).isTrue();
        assertThat(sender.isConfigured(new SmsVerificationSender.SmsSettings(
                "custom", "sign", "template", "access-id", "access-secret", "", "", true
        ))).isFalse();
    }

    private SmsVerificationSender.SmsSettings debugSettings() {
        return new SmsVerificationSender.SmsSettings(
                BuiltinMockSmsAvailability.PROVIDER_CODE,
                "Lumira调试",
                "SMS_DEBUG_VERIFICATION",
                "",
                "",
                "",
                "",
                true
        );
    }
}
